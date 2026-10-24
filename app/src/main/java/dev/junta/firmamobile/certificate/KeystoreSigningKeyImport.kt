package dev.junta.firmamobile.certificate

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import dev.junta.firmamobile.signing.SigningAlgorithm
import dev.junta.firmamobile.signing.jcaName
import java.security.KeyFactory
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.ProviderException
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.X509Certificate
import org.bouncycastle.asn1.ASN1Encoding
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers
import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.DigestInfo

/** Placement confirmed by the keystore after import, never inferred from a feature flag. */
enum class KeystoreSecurityLevel { STRONGBOX, TRUSTED_ENVIRONMENT, SECURE_HARDWARE_UNSPECIFIED, SOFTWARE }

internal enum class KeystorePlacement { STRONGBOX, DEFAULT }

enum class SigningKeyImportFailure {
    UNSUPPORTED_KEY,
    IMPORT_REJECTED,
    KEY_EXPORTABLE,
    NOT_HARDWARE_BACKED,
    CHALLENGE_FAILED,
}

sealed interface SigningKeyImportResult {
    data class Imported(val alias: String, val securityLevel: KeystoreSecurityLevel) : SigningKeyImportResult

    /** StrongBox is absent or cannot sign every algorithm; TEE needs the owner's explicit consent. */
    data object StrongBoxUnsuitable : SigningKeyImportResult

    data class Failed(val reason: SigningKeyImportFailure) : SigningKeyImportResult
}

/** Thrown by a backend when StrongBox refuses the key or one of its authorizations. */
internal class StrongBoxRejectedException(cause: Throwable? = null) : Exception(cause)

/** Seam over AndroidKeyStore: JVM tests cannot reach the real keystore. */
internal interface SigningKeyStoreBackend {
    val strongBoxAvailable: Boolean
    fun importKey(alias: String, key: PrivateKey, chain: List<X509Certificate>, placement: KeystorePlacement)
    fun privateKey(alias: String): PrivateKey?
    fun securityLevel(alias: String): KeystoreSecurityLevel
    fun delete(alias: String)
}

/**
 * Copies an unlocked software RSA key into AndroidKeyStore (ADR 0005). Nothing is published:
 * the caller stores the returned alias only after it commits its own reference record.
 */
internal class KeystoreSigningKeyImporter(
    private val backend: SigningKeyStoreBackend,
    private val random: SecureRandom = SecureRandom(),
) {
    fun importIdentity(identity: UnlockedIdentity, allowTrustedEnvironment: Boolean): SigningKeyImportResult {
        if (!identity.certificate.publicKey.algorithm.equals(RSA, ignoreCase = true)) {
            return SigningKeyImportResult.Failed(SigningKeyImportFailure.UNSUPPORTED_KEY)
        }
        val strongBox = if (backend.strongBoxAvailable) attempt(identity, KeystorePlacement.STRONGBOX) else null
        return strongBox
            ?: if (allowTrustedEnvironment) {
                attempt(identity, KeystorePlacement.DEFAULT)
                    ?: SigningKeyImportResult.Failed(SigningKeyImportFailure.IMPORT_REJECTED)
            } else {
                SigningKeyImportResult.StrongBoxUnsuitable
            }
    }

    /** Returns null only when StrongBox is unsuitable, so the caller may ask about TEE. */
    private fun attempt(identity: UnlockedIdentity, placement: KeystorePlacement): SigningKeyImportResult? {
        val alias = newAlias()
        val outcome = try {
            identity.withPrivateKey { key -> backend.importKey(alias, key, identity.chainOrLeaf(), placement) }
            verify(alias, identity.certificate, placement)
        } catch (_: StrongBoxRejectedException) {
            null
        } catch (_: Exception) {
            SigningKeyImportResult.Failed(SigningKeyImportFailure.IMPORT_REJECTED)
        }
        // Orphans left by a failed delete are reclaimed later through the owned alias prefix.
        if (outcome !is SigningKeyImportResult.Imported) runCatching { backend.delete(alias) }
        return outcome
    }

    private fun verify(
        alias: String,
        certificate: X509Certificate,
        placement: KeystorePlacement,
    ): SigningKeyImportResult? {
        val key = checkNotNull(backend.privateKey(alias))
        val level = backend.securityLevel(alias)
        val failure = when {
            key.encoded != null -> SigningKeyImportFailure.KEY_EXPORTABLE
            !level.acceptableFor(placement) -> SigningKeyImportFailure.NOT_HARDWARE_BACKED
            !signsEveryAlgorithm(key, certificate, placement) -> SigningKeyImportFailure.CHALLENGE_FAILED
            else -> null
        }
        return when {
            failure == null -> SigningKeyImportResult.Imported(alias, level)
            // A StrongBox key that cannot do every algorithm is unsuitable, not a hard failure.
            placement == KeystorePlacement.STRONGBOX && failure == SigningKeyImportFailure.CHALLENGE_FAILED -> null
            else -> SigningKeyImportResult.Failed(failure)
        }
    }

    private fun signsEveryAlgorithm(key: PrivateKey, certificate: X509Certificate, placement: KeystorePlacement) =
        SigningAlgorithm.entries.all { algorithm ->
            val challenge = ByteArray(CHALLENGE_BYTES).also(random::nextBytes)
            runCatching {
                val signature = KeystoreRsaSigning.sign(key, algorithm, challenge, placement)
                Signature.getInstance(algorithm.jcaName()).run {
                    initVerify(certificate.publicKey)
                    update(challenge)
                    verify(signature)
                }
            }.getOrDefault(false)
        }

    private fun newAlias(): String {
        val bytes = ByteArray(ALIAS_RANDOM_BYTES).also(random::nextBytes)
        return ALIAS_PREFIX + bytes.joinToString("") { "%02x".format(it) }
    }

    private fun KeystoreSecurityLevel.acceptableFor(placement: KeystorePlacement) = when (placement) {
        KeystorePlacement.STRONGBOX -> this == KeystoreSecurityLevel.STRONGBOX ||
            this == KeystoreSecurityLevel.SECURE_HARDWARE_UNSPECIFIED
        KeystorePlacement.DEFAULT -> this != KeystoreSecurityLevel.SOFTWARE
    }

    private fun UnlockedIdentity.chainOrLeaf() = chain.ifEmpty { listOf(certificate) }

    companion object {
        /** Owned namespace: orphan cleanup may delete only aliases with this prefix. */
        const val ALIAS_PREFIX = "dev.junta.firmamobile.signing_key_v1."
        private const val RSA = "RSA"
        private const val ALIAS_RANDOM_BYTES = 16
        private const val CHALLENGE_BYTES = 32
    }
}

/**
 * StrongBox only guarantees SHA-256 (IKeyMintDevice). Other digests are hashed in software and the
 * chip signs the PKCS#1 DigestInfo through NONEwithRSA, the same route Chromium uses for keystore keys.
 */
internal object KeystoreRsaSigning {
    fun sign(key: PrivateKey, algorithm: SigningAlgorithm, data: ByteArray, placement: KeystorePlacement): ByteArray =
        if (placement == KeystorePlacement.DEFAULT || algorithm == SigningAlgorithm.SHA256_WITH_RSA) {
            Signature.getInstance(algorithm.jcaName()).run {
                initSign(key)
                update(data)
                sign()
            }
        } else {
            Signature.getInstance("NONEwithRSA").run {
                initSign(key)
                update(digestInfo(algorithm, data))
                sign()
            }
        }

    fun digestInfo(algorithm: SigningAlgorithm, data: ByteArray): ByteArray {
        val (oid, name) = digestOf(algorithm)
        val hash = MessageDigest.getInstance(name).digest(data)
        return DigestInfo(AlgorithmIdentifier(oid, DERNull.INSTANCE), hash).getEncoded(ASN1Encoding.DER)
    }

    private fun digestOf(algorithm: SigningAlgorithm): Pair<ASN1ObjectIdentifier, String> = when (algorithm) {
        SigningAlgorithm.SHA1_WITH_RSA -> OIWObjectIdentifiers.idSHA1 to "SHA-1"
        SigningAlgorithm.SHA256_WITH_RSA -> NISTObjectIdentifiers.id_sha256 to "SHA-256"
        SigningAlgorithm.SHA384_WITH_RSA -> NISTObjectIdentifiers.id_sha384 to "SHA-384"
        SigningAlgorithm.SHA512_WITH_RSA -> NISTObjectIdentifiers.id_sha512 to "SHA-512"
    }
}

internal class AndroidSigningKeyStoreBackend(context: Context) : SigningKeyStoreBackend {
    override val strongBoxAvailable: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

    override fun importKey(alias: String, key: PrivateKey, chain: List<X509Certificate>, placement: KeystorePlacement) {
        val strongBox = placement == KeystorePlacement.STRONGBOX
        val protection = KeyProtection.Builder(KeyProperties.PURPOSE_SIGN)
            .apply {
                if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256)
                    setIsStrongBoxBacked(true)
                } else {
                    setDigests(
                        KeyProperties.DIGEST_NONE,
                        KeyProperties.DIGEST_SHA1,
                        KeyProperties.DIGEST_SHA256,
                        KeyProperties.DIGEST_SHA384,
                        KeyProperties.DIGEST_SHA512,
                    )
                }
            }
            // PSS too: TLS 1.3 client authentication cannot use PKCS#1 v1.5.
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
            // ADR 0005: the gate is the app-level unlock, never a prompt per signature.
            .setUserAuthenticationRequired(false)
            .build()
        try {
            keyStore().setEntry(alias, KeyStore.PrivateKeyEntry(key, chain.toTypedArray()), protection)
        } catch (error: ProviderException) {
            // StrongBoxUnavailableException (API 28) is a ProviderException; naming it would break API 26.
            throw if (strongBox) StrongBoxRejectedException(error) else error
        } catch (error: KeyStoreException) {
            // Any StrongBox refusal only leads to a consented TEE question, never a silent downgrade.
            throw if (strongBox) StrongBoxRejectedException(error) else error
        }
    }

    override fun privateKey(alias: String): PrivateKey? = keyStore().getKey(alias, null) as? PrivateKey

    override fun securityLevel(alias: String): KeystoreSecurityLevel {
        val key = privateKey(alias) ?: return KeystoreSecurityLevel.SOFTWARE
        val info = KeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE).getKeySpec(key, KeyInfo::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            when (info.securityLevel) {
                KeyProperties.SECURITY_LEVEL_STRONGBOX -> KeystoreSecurityLevel.STRONGBOX
                KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> KeystoreSecurityLevel.TRUSTED_ENVIRONMENT
                KeyProperties.SECURITY_LEVEL_UNKNOWN_SECURE -> KeystoreSecurityLevel.SECURE_HARDWARE_UNSPECIFIED
                else -> KeystoreSecurityLevel.SOFTWARE
            }
        } else if (info.isInsideSecureHardware) {
            KeystoreSecurityLevel.SECURE_HARDWARE_UNSPECIFIED
        } else {
            KeystoreSecurityLevel.SOFTWARE
        }
    }

    override fun delete(alias: String) {
        if (alias.startsWith(KeystoreSigningKeyImporter.ALIAS_PREFIX)) keyStore().deleteEntry(alias)
    }

    private fun keyStore() = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}

package dev.junta.firmamobile.certificate

import dev.junta.firmamobile.signing.SoftwareEcCurvePolicy
import dev.junta.firmamobile.signing.SoftwareEcSignatureAlgorithm
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.Signature
import java.security.UnrecoverableKeyException
import java.security.cert.CertificateException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import java.security.cert.X509Certificate
import java.time.Clock
import java.util.Collections
import java.util.Date
import java.util.IdentityHashMap
import java.util.concurrent.CancellationException
import javax.crypto.BadPaddingException
import javax.crypto.NoSuchPaddingException
import javax.security.auth.x500.X500Principal

class Pkcs12Loader internal constructor(
    private val clock: Clock = Clock.systemUTC(),
    private val secureRandom: SecureRandom = SecureRandom(),
    private val sensitiveCopyObserver: SensitiveCopyObserver = SensitiveCopyObserver { _, _ -> },
    private val monotonicNanos: () -> Long = System::nanoTime,
) {
    suspend fun load(
        input: InputStream,
        declaredSize: Long?,
        password: CharArray,
    ): CertificateLoadResult = withInput(
        input, declaredSize, password, CertificateLoadResult::Failure,
    ) { bytes, passwordCopy ->
        loadEntry(readPkcs12KeyStore(bytes, passwordCopy), passwordCopy)
    }

    /** Backend-only preview. It does not unlock, persist, cache, or select an identity. */
    internal suspend fun inspect(
        input: InputStream,
        declaredSize: Long?,
        password: CharArray,
    ): Pkcs12InspectionResult = withInput(
        input, declaredSize, password, Pkcs12InspectionResult::Failure,
    ) { bytes, passwordCopy ->
        val keyStore = readPkcs12KeyStore(bytes, passwordCopy)
        val aliases = privateKeyAliases(keyStore)
            ?: return@withInput Pkcs12InspectionResult.Failure(CertificateErrorCode.TOO_MANY_ENTRIES)
        if (aliases.isEmpty()) {
            return@withInput Pkcs12InspectionResult.Failure(CertificateErrorCode.PRIVATE_KEY_MISSING)
        }
        val entries = aliases.mapIndexed { index, alias ->
            val certificate = keyStore.getCertificate(alias) as? X509Certificate
                ?: return@withInput Pkcs12InspectionResult.Failure(CertificateErrorCode.CERTIFICATE_NOT_X509)
            val summary = certificateSummary(certificate).let {
                it.copy(
                    ownerName = CertificateDisplayNamePolicy.sanitize(it.ownerName),
                    issuerName = CertificateDisplayNamePolicy.sanitize(it.issuerName),
                )
            }
            alias to Pkcs12Candidate(
                number = index + 1,
                summary = summary,
                certificateSha256 = MessageDigest.getInstance("SHA-256")
                    .digest(certificate.encoded).joinToString("") { "%02x".format(it) },
            )
        }
        Pkcs12InspectionResult.Success(Pkcs12AliasSelection(
            inputFingerprint = MessageDigest.getInstance("SHA-256").digest(bytes),
            entries = entries,
            monotonicNanos = monotonicNanos,
        ))
    }

    /** Reopen with caller-owned credentials; every attempt invalidates the pending selection. */
    internal suspend fun loadSelected(
        input: InputStream,
        declaredSize: Long?,
        password: CharArray,
        selection: Pkcs12AliasSelection,
        candidate: Pkcs12Candidate?,
    ): CertificateLoadResult {
        val selected = selection.consume(candidate)
            ?: return CertificateLoadResult.Failure(CertificateErrorCode.NO_CERTIFICATE_SELECTED)
        return selected.use {
            withInput(
                input, declaredSize, password, CertificateLoadResult::Failure,
            ) { bytes, passwordCopy ->
                if (!selected.matchesInput(bytes)) {
                    CertificateLoadResult.Failure(CertificateErrorCode.INVALID_PASSWORD_OR_FILE)
                } else {
                    loadEntry(readPkcs12KeyStore(bytes, passwordCopy), passwordCopy, selected.alias())
                }
            }
        }
    }

    private fun <T> withInput(
        input: InputStream,
        declaredSize: Long?,
        password: CharArray,
        failure: (CertificateErrorCode) -> T,
        block: (ByteArray, CharArray) -> T,
    ): T {
        if (declaredSize != null && declaredSize >= 0 && declaredSize > MAX_INPUT_BYTES) {
            return failure(CertificateErrorCode.FILE_TOO_LARGE)
        }
        val passwordCopy = password.copyOf()
        var pkcs12Bytes: ByteArray? = null
        return try {
            pkcs12Bytes = readBounded(input)
            if (pkcs12Bytes == null) {
                failure(CertificateErrorCode.FILE_TOO_LARGE)
            } else {
                block(pkcs12Bytes, passwordCopy)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Pkcs12ReadFailure) {
            failure(error.code)
        } catch (_: Exception) {
            failure(CertificateErrorCode.INVALID_PASSWORD_OR_FILE)
        } finally {
            passwordCopy.clearAndReport(SensitiveCopyKind.PASSWORD)
            pkcs12Bytes?.clearAndReport(SensitiveCopyKind.PKCS12_BYTES)
        }
    }

    private fun loadEntry(
        keyStore: KeyStore,
        password: CharArray,
        selectedAlias: String? = null,
    ): CertificateLoadResult {
        val aliases = privateKeyAliases(keyStore)
            ?: return CertificateLoadResult.Failure(CertificateErrorCode.TOO_MANY_ENTRIES)
        val alias = selectedAlias ?: aliases.singleOrNull()
        val selectionError = when {
            aliases.isEmpty() -> CertificateErrorCode.PRIVATE_KEY_MISSING
            alias == null -> CertificateErrorCode.MULTIPLE_PRIVATE_KEYS
            alias !in aliases -> CertificateErrorCode.NO_CERTIFICATE_SELECTED
            (keyStore.getCertificateChain(alias)?.size ?: 0) > MAX_CHAIN_LENGTH -> CertificateErrorCode.CHAIN_TOO_LONG
            else -> null
        }
        return if (selectionError != null) {
            CertificateLoadResult.Failure(selectionError)
        } else {
            val protection = KeyStore.PasswordProtection(password)
            try {
                val entry = pkcs12Operation(null, authenticatedKeyRecovery = true) {
                    keyStore.getEntry(checkNotNull(alias), protection)
                } as? KeyStore.PrivateKeyEntry
                if (entry == null) CertificateLoadResult.Failure(CertificateErrorCode.PRIVATE_KEY_MISSING)
                else validateEntry(entry)
            } finally {
                protection.destroy()
            }
        }
    }

    private fun validateEntry(entry: KeyStore.PrivateKeyEntry): CertificateLoadResult {
        val rawChain = entry.certificateChain
        return if (rawChain.size > MAX_CHAIN_LENGTH) {
            CertificateLoadResult.Failure(CertificateErrorCode.CHAIN_TOO_LONG)
        } else {
            val chain = rawChain.filterIsInstance<X509Certificate>()
            val certificate = entry.certificate as? X509Certificate
            if (chain.size != rawChain.size || certificate == null) {
                CertificateLoadResult.Failure(CertificateErrorCode.CERTIFICATE_NOT_X509)
            } else {
                val error = certificateError(entry.privateKey, certificate)
                if (error != null) CertificateLoadResult.Failure(error)
                else CertificateLoadResult.Success(UnlockedIdentity(
                    privateKey = entry.privateKey,
                    certificate = certificate,
                    chain = chain,
                    summary = certificateSummary(certificate),
                ))
            }
        }
    }

    private fun certificateError(
        privateKey: java.security.PrivateKey,
        certificate: X509Certificate,
    ): CertificateErrorCode? {
        val matchAlgorithm = keyMatchAlgorithm(privateKey, certificate)
            ?: return CertificateErrorCode.UNSUPPORTED_KEY_TYPE
        val validityError = try {
            certificate.checkValidity(Date.from(clock.instant()))
            null
        } catch (_: CertificateExpiredException) {
            CertificateErrorCode.CERTIFICATE_EXPIRED
        } catch (_: CertificateNotYetValidException) {
            CertificateErrorCode.CERTIFICATE_NOT_YET_VALID
        }
        return validityError ?: run {
            val keyUsage = certificate.keyUsage
            when {
                keyUsage != null && !keyUsage.getOrElse(0) { false } &&
                    !keyUsage.getOrElse(1) { false } -> CertificateErrorCode.KEY_USAGE_NOT_PERMITTED
                !privateKeyMatchesCertificate(privateKey, certificate, matchAlgorithm) ->
                    CertificateErrorCode.KEY_CERTIFICATE_MISMATCH
                else -> null
            }
        }
    }

    private fun privateKeyMatchesCertificate(
        privateKey: java.security.PrivateKey,
        certificate: X509Certificate,
        algorithm: String,
    ): Boolean {
        val challenge = ByteArray(CHALLENGE_BYTES)
        var signatureBytes: ByteArray? = null
        return try {
            secureRandom.nextBytes(challenge)
            val signer = Signature.getInstance(algorithm)
            signer.initSign(privateKey, secureRandom)
            signer.update(challenge)
            signatureBytes = signer.sign()

            val verifier = Signature.getInstance(algorithm)
            verifier.initVerify(certificate.publicKey)
            verifier.update(challenge)
            verifier.verify(signatureBytes)
        } catch (_: Exception) {
            false
        } finally {
            challenge.clearAndReport(SensitiveCopyKind.KEY_MATCH_CHALLENGE)
            signatureBytes?.fill(0)
        }
    }

    private fun CharArray.clearAndReport(kind: SensitiveCopyKind) {
        fill('\u0000')
        sensitiveCopyObserver.onCleared(kind, all { it == '\u0000' })
    }

    private fun ByteArray.clearAndReport(kind: SensitiveCopyKind) {
        fill(0)
        sensitiveCopyObserver.onCleared(kind, all { it == 0.toByte() })
    }

    companion object {
        const val MAX_INPUT_BYTES = 10 * 1024 * 1024
        private const val MAX_CHAIN_LENGTH = 16
        private const val CHALLENGE_BYTES = 32
    }
}

/** The bound includes certificate-only aliases, but they can never become candidates. */
private fun privateKeyAliases(keyStore: KeyStore): List<String>? {
    val aliases = mutableListOf<String>()
    val enumeration = keyStore.aliases()
    while (enumeration.hasMoreElements()) {
        if (aliases.size == MAX_ALIASES) return null
        aliases += enumeration.nextElement()
    }
    return aliases.filter { keyStore.entryInstanceOf(it, KeyStore.PrivateKeyEntry::class.java) }
}

internal fun certificateSummary(certificate: X509Certificate): CertificateSummary = CertificateSummary(
    ownerName = commonName(certificate.subjectX500Principal),
    issuerName = commonName(certificate.issuerX500Principal),
    validFrom = certificate.notBefore.toInstant(),
    validUntil = certificate.notAfter.toInstant(),
)

private fun commonName(principal: X500Principal): String {
    val distinguishedName = principal.getName(X500Principal.RFC2253)
    val commonName = splitRdns(distinguishedName)
        .firstOrNull { it.startsWith("CN=", ignoreCase = true) }
        ?.substringAfter('=')
        ?.replace(Regex("\\\\([,+=<>#;\\\\\" ])"), "\$1")
        ?.take(MAX_DISPLAY_NAME_LENGTH)
    return commonName.orEmpty().ifBlank {
        distinguishedName.take(MAX_DISPLAY_NAME_LENGTH)
    }
}

private fun splitRdns(distinguishedName: String): List<String> {
    val parts = mutableListOf<String>()
    val current = StringBuilder()
    var escaped = false
    distinguishedName.forEach { character ->
        when {
            escaped -> {
                current.append('\\').append(character)
                escaped = false
            }
            character == '\\' -> escaped = true
            character == ',' || character == '+' -> {
                parts += current.toString().trim()
                current.clear()
            }
            else -> current.append(character)
        }
    }
    if (escaped) current.append('\\')
    if (current.isNotEmpty()) parts += current.toString().trim()
    return parts
}

private const val MAX_ALIASES = 32
private const val MAX_DISPLAY_NAME_LENGTH = 256

private fun readPkcs12KeyStore(bytes: ByteArray, password: CharArray): KeyStore =
    pkcs12Operation(bytes) {
        KeyStore.getInstance("PKCS12").apply { load(ByteArrayInputStream(bytes), password) }
    }

private fun readBounded(input: InputStream): ByteArray? {
    val output = ClearingByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    return try {
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read
            if (total > Pkcs12Loader.MAX_INPUT_BYTES) return null
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    } finally {
        buffer.fill(0)
        output.clear()
    }
}

private class ClearingByteArrayOutputStream : ByteArrayOutputStream() {
    fun clear() {
        buf.fill(0)
        reset()
    }
}

/** Provider messages are neither stable diagnostics nor safe display text. */
internal fun classifyPkcs12Failure(
    error: Exception,
    bytes: ByteArray?,
    authenticatedKeyRecovery: Boolean = false,
): CertificateErrorCode {
    val causes = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    var cause: Throwable? = error
    while (cause != null && causes.add(cause)) cause = cause.cause
    return when {
        bytes != null && invalidPkcs12Envelope(bytes) -> CertificateErrorCode.INVALID_PKCS12_FILE
        causes.any { it is NoSuchAlgorithmException || it is NoSuchPaddingException } ->
            CertificateErrorCode.UNSUPPORTED_PKCS12_ENCRYPTION
        // Once the container opens, key recovery may lose the BadPaddingException cause.
        authenticatedKeyRecovery && causes.any { it is UnrecoverableKeyException } ->
            CertificateErrorCode.INVALID_PASSWORD
        causes.any { it is EOFException || it is CertificateException } -> CertificateErrorCode.INVALID_PKCS12_FILE
        causes.any { it is UnrecoverableKeyException } && causes.any { it is BadPaddingException } ->
            CertificateErrorCode.INVALID_PASSWORD
        // Android providers can discard the cause of both MAC and encryption failures.
        else -> CertificateErrorCode.INVALID_PASSWORD_OR_FILE
    }
}

private fun invalidPkcs12Envelope(bytes: ByteArray): Boolean {
    if (bytes.size < 2 || bytes[0] != ASN1_SEQUENCE_TAG.toByte()) return true
    val firstLength = bytes[1].toInt() and UNSIGNED_BYTE_MASK
    return when {
        firstLength == ASN1_LONG_LENGTH_FLAG -> false // BER indefinite length needs the platform parser.
        firstLength < ASN1_LONG_LENGTH_FLAG -> firstLength != bytes.size - 2
        else -> {
            val lengthBytes = firstLength and ASN1_LENGTH_MASK
            if (lengthBytes == ASN1_LENGTH_MASK || lengthBytes > bytes.size - 2) {
                true
            } else {
                var length = 0L
                for (index in 2 until 2 + lengthBytes) {
                    length = minOf(
                        (length shl BITS_PER_BYTE) or (bytes[index].toLong() and UNSIGNED_BYTE_MASK.toLong()),
                        bytes.size + 1L,
                    )
                }
                length != (bytes.size - 2 - lengthBytes).toLong()
            }
        }
    }
}

private class Pkcs12ReadFailure(val code: CertificateErrorCode) : Exception()

private inline fun <T> pkcs12Operation(
    bytes: ByteArray?,
    authenticatedKeyRecovery: Boolean = false,
    block: () -> T,
): T = try {
    block()
} catch (error: IOException) {
    throw Pkcs12ReadFailure(classifyPkcs12Failure(error, bytes, authenticatedKeyRecovery))
} catch (error: GeneralSecurityException) {
    throw Pkcs12ReadFailure(classifyPkcs12Failure(error, bytes, authenticatedKeyRecovery))
}

private const val ASN1_SEQUENCE_TAG = 0x30
private const val ASN1_LONG_LENGTH_FLAG = 0x80
private const val ASN1_LENGTH_MASK = 0x7f
private const val UNSIGNED_BYTE_MASK = 0xff
private const val BITS_PER_BYTE = 8

/** RSA, or an in-memory EC key on exactly P-256/P-384 checked with that curve's own digest. */
private fun keyMatchAlgorithm(
    privateKey: java.security.PrivateKey,
    certificate: X509Certificate,
): String? {
    val keyAlgorithm = privateKey.algorithm.uppercase(java.util.Locale.ROOT)
    if (keyAlgorithm != certificate.publicKey.algorithm.uppercase(java.util.Locale.ROOT)) return null
    return when (keyAlgorithm) {
        "RSA" -> "SHA256withRSA"
        "EC" -> SoftwareEcSignatureAlgorithm.entries.firstOrNull { algorithm ->
            val parameters = SoftwareEcCurvePolicy.parameters(algorithm)
            SoftwareEcCurvePolicy.privateSpec(privateKey, parameters) != null &&
                SoftwareEcCurvePolicy.publicSpec(certificate, parameters) != null
        }?.jcaName
        else -> null
    }
}

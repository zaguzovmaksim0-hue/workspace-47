package dev.junta.firmamobile.certificate

import dev.junta.firmamobile.signing.SigningAlgorithm
import dev.junta.firmamobile.signing.jcaName
import dev.junta.firmamobile.signing.syntheticIdentity
import java.security.AlgorithmParameters
import java.security.InvalidKeyException
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import java.security.Security
import java.security.Signature
import java.security.SignatureSpi
import java.security.cert.X509Certificate
import java.security.spec.AlgorithmParameterSpec
import java.util.Date
import java.util.UUID
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KeystoreSigningKeyImporterTest {
    private val provider = HardwareKeyProvider()
    private val identity = syntheticIdentity()

    @Before fun installProvider() {
        check(Security.insertProviderAt(provider, 1) == 1)
    }

    @After fun removeProvider() {
        Security.removeProvider(provider.name)
    }

    @Test fun strongBoxKeySignsEveryAlgorithmThroughSha256OrDigestInfo() {
        val backend = FakeBackend(strongBoxAvailable = true)

        val result = KeystoreSigningKeyImporter(backend).importIdentity(identity, allowTrustedEnvironment = false)

        val imported = result as SigningKeyImportResult.Imported
        assertEquals(KeystoreSecurityLevel.STRONGBOX, imported.securityLevel)
        assertTrue(imported.alias.startsWith(KeystoreSigningKeyImporter.ALIAS_PREFIX))
        assertEquals(listOf(KeystorePlacement.STRONGBOX), backend.placements)
        assertEquals(setOf("SHA256withRSA", "NONEwithRSA"), provider.usedAlgorithms.toSet())
        assertTrue(backend.deleted.isEmpty())
    }

    @Test fun strongBoxRefusalNeedsConsentBeforeTrustedEnvironment() {
        val backend = FakeBackend(strongBoxAvailable = true, strongBoxRejects = true)

        val result = KeystoreSigningKeyImporter(backend).importIdentity(identity, allowTrustedEnvironment = false)

        assertEquals(SigningKeyImportResult.StrongBoxUnsuitable, result)
        assertEquals(listOf(KeystorePlacement.STRONGBOX), backend.placements)
        assertTrue(backend.keys.isEmpty())
    }

    @Test fun consentedFallbackImportsIntoTrustedEnvironment() {
        val backend = FakeBackend(strongBoxAvailable = true, strongBoxRejects = true)

        val result = KeystoreSigningKeyImporter(backend).importIdentity(identity, allowTrustedEnvironment = true)

        assertEquals(KeystoreSecurityLevel.TRUSTED_ENVIRONMENT, (result as SigningKeyImportResult.Imported).securityLevel)
        assertEquals(listOf(KeystorePlacement.STRONGBOX, KeystorePlacement.DEFAULT), backend.placements)
        assertEquals(setOf(result.alias), backend.keys.keys)
    }

    @Test fun strongBoxWithoutDigestNoneIsUnsuitableAndItsAliasIsDeleted() {
        val backend = FakeBackend(strongBoxAvailable = true, strongBoxAlgorithms = setOf("SHA256withRSA"))

        val result = KeystoreSigningKeyImporter(backend).importIdentity(identity, allowTrustedEnvironment = false)

        assertEquals(SigningKeyImportResult.StrongBoxUnsuitable, result)
        assertEquals(1, backend.deleted.size)
        assertTrue(backend.keys.isEmpty())
    }

    @Test fun missingStrongBoxNeverImportsWithoutConsent() {
        val backend = FakeBackend(strongBoxAvailable = false)

        val result = KeystoreSigningKeyImporter(backend).importIdentity(identity, allowTrustedEnvironment = false)

        assertEquals(SigningKeyImportResult.StrongBoxUnsuitable, result)
        assertTrue(backend.placements.isEmpty())
    }

    @Test fun exportableOrSoftwareKeysAreRejectedAndRemoved() {
        val exportable = FakeBackend(strongBoxAvailable = false, exportable = true)
        val software = FakeBackend(strongBoxAvailable = false, defaultLevel = KeystoreSecurityLevel.SOFTWARE)

        assertEquals(
            SigningKeyImportResult.Failed(SigningKeyImportFailure.KEY_EXPORTABLE),
            KeystoreSigningKeyImporter(exportable).importIdentity(identity, allowTrustedEnvironment = true),
        )
        assertEquals(
            SigningKeyImportResult.Failed(SigningKeyImportFailure.NOT_HARDWARE_BACKED),
            KeystoreSigningKeyImporter(software).importIdentity(identity, allowTrustedEnvironment = true),
        )
        assertTrue(exportable.keys.isEmpty() && software.keys.isEmpty())
    }

    @Test fun keyThatDoesNotMatchTheCertificateFailsTheChallenge() {
        val backend = FakeBackend(strongBoxAvailable = false, substituteKey = true)

        val result = KeystoreSigningKeyImporter(backend).importIdentity(identity, allowTrustedEnvironment = true)

        assertEquals(SigningKeyImportResult.Failed(SigningKeyImportFailure.CHALLENGE_FAILED), result)
        assertTrue(backend.keys.isEmpty())
    }

    @Test fun digestInfoRouteProducesStandardPkcs1Signatures() {
        val key = identity.withPrivateKey { it }
        val data = "documento".toByteArray()
        for (algorithm in SigningAlgorithm.entries) {
            val viaDigestInfo = Signature.getInstance("NONEwithRSA", BouncyCastleProvider()).run {
                initSign(key)
                update(KeystoreRsaSigning.digestInfo(algorithm, data))
                sign()
            }
            val direct = Signature.getInstance(algorithm.jcaName(), BouncyCastleProvider()).run {
                initSign(key)
                update(data)
                sign()
            }
            assertArrayEquals(algorithm.name, direct, viaDigestInfo)
        }
    }

    @Test fun nonRsaCertificatesAreNotImported() {
        val ec = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val ecIdentity = UnlockedIdentity(ec.private, selfSigned(ec), emptyList(), identity.summary)

        val result = KeystoreSigningKeyImporter(FakeBackend(strongBoxAvailable = true))
            .importIdentity(ecIdentity, allowTrustedEnvironment = true)

        assertEquals(SigningKeyImportResult.Failed(SigningKeyImportFailure.UNSUPPORTED_KEY), result)
    }

    /** Opaque keystore handle: material stays inside the synthetic provider, like AndroidKeyStore. */
    private class HardwareKey(val material: PrivateKey, val algorithms: Set<String>) : PrivateKey {
        override fun getAlgorithm() = "RSA"
        override fun getFormat(): String? = null
        override fun getEncoded(): ByteArray? = null
    }

    private inner class FakeBackend(
        override val strongBoxAvailable: Boolean,
        private val strongBoxRejects: Boolean = false,
        private val strongBoxAlgorithms: Set<String> = setOf("SHA256withRSA", "NONEwithRSA"),
        private val exportable: Boolean = false,
        private val defaultLevel: KeystoreSecurityLevel = KeystoreSecurityLevel.TRUSTED_ENVIRONMENT,
        private val substituteKey: Boolean = false,
    ) : SigningKeyStoreBackend {
        val keys = linkedMapOf<String, PrivateKey>()
        val levels = mutableMapOf<String, KeystoreSecurityLevel>()
        val placements = mutableListOf<KeystorePlacement>()
        val deleted = mutableListOf<String>()

        override fun importKey(alias: String, key: PrivateKey, chain: List<X509Certificate>, placement: KeystorePlacement) {
            placements += placement
            if (placement == KeystorePlacement.STRONGBOX && strongBoxRejects) throw StrongBoxRejectedException()
            val material = if (substituteKey) {
                KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().private
            } else {
                key
            }
            keys[alias] = when {
                exportable -> material
                placement == KeystorePlacement.STRONGBOX -> HardwareKey(material, strongBoxAlgorithms)
                else -> HardwareKey(material, SigningAlgorithm.entries.map { it.jcaName() }.toSet() + "NONEwithRSA")
            }
            levels[alias] = if (placement == KeystorePlacement.STRONGBOX) KeystoreSecurityLevel.STRONGBOX else defaultLevel
        }

        override fun privateKey(alias: String) = keys[alias]
        override fun securityLevel(alias: String) = levels.getValue(alias)
        override fun delete(alias: String) {
            deleted += alias
            keys.remove(alias)
        }
    }

    private class HardwareKeyProvider : Provider("JfmHardwareKeyTest-" + UUID.randomUUID(), 1.0, "Synthetic keystore") {
        val usedAlgorithms = mutableListOf<String>()

        init {
            for (name in SigningAlgorithm.entries.map { it.jcaName() } + "NONEwithRSA") {
                putService(object : Service(this, "Signature", name, Spi::class.java.name, emptyList(), emptyMap()) {
                    override fun supportsParameter(parameter: Any?) = parameter is HardwareKey
                    override fun newInstance(parameter: Any?): Any = Spi(name)
                })
            }
        }

        private inner class Spi(private val algorithm: String) : SignatureSpi() {
            private var delegate: Signature? = null

            override fun engineInitSign(privateKey: PrivateKey) {
                val key = privateKey as? HardwareKey ?: throw InvalidKeyException("Not a hardware handle")
                // Mirrors a keystore key whose authorizations lack this digest.
                if (algorithm !in key.algorithms) throw InvalidKeyException("Digest not authorized: $algorithm")
                usedAlgorithms += algorithm
                delegate = Signature.getInstance(algorithm, BouncyCastleProvider()).apply { initSign(key.material) }
            }

            override fun engineInitVerify(publicKey: PublicKey) {
                throw InvalidKeyException("Signing-only test provider")
            }
            override fun engineUpdate(value: Byte) { checkNotNull(delegate).update(value) }
            override fun engineUpdate(bytes: ByteArray, offset: Int, length: Int) {
                checkNotNull(delegate).update(bytes, offset, length)
            }
            override fun engineSign(): ByteArray = checkNotNull(delegate).sign()
            override fun engineVerify(signature: ByteArray): Boolean = false
            @Deprecated("Legacy JCA API")
            override fun engineSetParameter(param: String?, value: Any?) { throw UnsupportedOperationException() }
            @Deprecated("Legacy JCA API")
            override fun engineGetParameter(param: String?): Any? = throw UnsupportedOperationException()
            override fun engineSetParameter(params: AlgorithmParameterSpec?) {
                if (params != null) throw UnsupportedOperationException()
            }
            override fun engineGetParameters(): AlgorithmParameters? = null
        }
    }

    private fun selfSigned(keyPair: KeyPair): X509Certificate {
        val name = X500Name("CN=EC sintetico")
        val holder = JcaX509v3CertificateBuilder(
            name, BigInteger.ONE, Date(0), Date(4_102_444_800_000), name, keyPair.public,
        ).build(JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.private))
        return JcaX509CertificateConverter().getCertificate(holder)
    }
}

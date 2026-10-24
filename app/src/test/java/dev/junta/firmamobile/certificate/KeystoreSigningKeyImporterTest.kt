package dev.junta.firmamobile.certificate

import dev.junta.firmamobile.signing.SigningAlgorithm
import dev.junta.firmamobile.signing.jcaName
import dev.junta.firmamobile.signing.syntheticIdentity
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Security
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.Date
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
        val backend = FakeSigningKeyStoreBackend(strongBoxAvailable = true)

        val result = KeystoreSigningKeyImporter(backend).importIdentity(identity, allowTrustedEnvironment = false)

        val imported = result as SigningKeyImportResult.Imported
        assertEquals(KeystoreSecurityLevel.STRONGBOX, imported.securityLevel)
        assertTrue(imported.alias.startsWith(KeystoreSigningKeyImporter.ALIAS_PREFIX))
        assertEquals(listOf(KeystorePlacement.STRONGBOX), backend.placements)
        assertEquals(setOf("SHA256withRSA", "NONEwithRSA"), provider.usedAlgorithms.toSet())
        assertTrue(backend.deleted.isEmpty())
    }

    @Test fun strongBoxRefusalNeedsConsentBeforeTrustedEnvironment() {
        val backend = FakeSigningKeyStoreBackend(strongBoxAvailable = true, strongBoxRejects = true)

        val result = KeystoreSigningKeyImporter(backend).importIdentity(identity, allowTrustedEnvironment = false)

        assertEquals(SigningKeyImportResult.StrongBoxUnsuitable, result)
        assertEquals(listOf(KeystorePlacement.STRONGBOX), backend.placements)
        assertTrue(backend.keys.isEmpty())
    }

    @Test fun consentedFallbackImportsIntoTrustedEnvironment() {
        val backend = FakeSigningKeyStoreBackend(strongBoxAvailable = true, strongBoxRejects = true)

        val result = KeystoreSigningKeyImporter(backend).importIdentity(identity, allowTrustedEnvironment = true)

        assertEquals(KeystoreSecurityLevel.TRUSTED_ENVIRONMENT, (result as SigningKeyImportResult.Imported).securityLevel)
        assertEquals(listOf(KeystorePlacement.STRONGBOX, KeystorePlacement.DEFAULT), backend.placements)
        assertEquals(setOf(result.alias), backend.keys.keys)
    }

    @Test fun strongBoxWithoutDigestNoneIsUnsuitableAndItsAliasIsDeleted() {
        val backend = FakeSigningKeyStoreBackend(strongBoxAvailable = true, strongBoxAlgorithms = setOf("SHA256withRSA"))

        val result = KeystoreSigningKeyImporter(backend).importIdentity(identity, allowTrustedEnvironment = false)

        assertEquals(SigningKeyImportResult.StrongBoxUnsuitable, result)
        assertEquals(1, backend.deleted.size)
        assertTrue(backend.keys.isEmpty())
    }

    @Test fun missingStrongBoxNeverImportsWithoutConsent() {
        val backend = FakeSigningKeyStoreBackend(strongBoxAvailable = false)

        val result = KeystoreSigningKeyImporter(backend).importIdentity(identity, allowTrustedEnvironment = false)

        assertEquals(SigningKeyImportResult.StrongBoxUnsuitable, result)
        assertTrue(backend.placements.isEmpty())
    }

    @Test fun exportableOrSoftwareKeysAreRejectedAndRemoved() {
        val exportable = FakeSigningKeyStoreBackend(strongBoxAvailable = false, exportable = true)
        val software = FakeSigningKeyStoreBackend(strongBoxAvailable = false, defaultLevel = KeystoreSecurityLevel.SOFTWARE)

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
        val backend = FakeSigningKeyStoreBackend(strongBoxAvailable = false, substituteKey = true)

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

        val result = KeystoreSigningKeyImporter(FakeSigningKeyStoreBackend(strongBoxAvailable = true))
            .importIdentity(ecIdentity, allowTrustedEnvironment = true)

        assertEquals(SigningKeyImportResult.Failed(SigningKeyImportFailure.UNSUPPORTED_KEY), result)
    }

    private fun selfSigned(keyPair: KeyPair): X509Certificate {
        val name = X500Name("CN=EC sintetico")
        val holder = JcaX509v3CertificateBuilder(
            name, BigInteger.ONE, Date(0), Date(4_102_444_800_000), name, keyPair.public,
        ).build(JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.private))
        return JcaX509CertificateConverter().getCertificate(holder)
    }
}

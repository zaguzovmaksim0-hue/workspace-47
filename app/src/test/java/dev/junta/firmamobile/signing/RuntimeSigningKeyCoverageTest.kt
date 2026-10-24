package dev.junta.firmamobile.signing

import dev.junta.firmamobile.afirma.servlet.NativeCadesEngine
import dev.junta.firmamobile.afirma.servlet.NativePadesEngine
import dev.junta.firmamobile.afirma.servlet.NativeSigningXml
import dev.junta.firmamobile.afirma.servlet.NativeXadesEngine
import dev.junta.firmamobile.afirma.servlet.NativeXadesOptions
import dev.junta.firmamobile.afirma.servlet.NativeXadesPackaging
import dev.junta.firmamobile.afirma.servlet.nativePadesFixture
import dev.junta.firmamobile.certificate.CertificateSummary
import dev.junta.firmamobile.certificate.UnlockedIdentity
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.time.Clock
import java.time.ZoneOffset
import java.util.Base64
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Direct engine calls deliberately bypass import; this does not enable EC identities in the app. */
@RunWith(Parameterized::class)
class RuntimeSigningKeyCoverageTest(private val keyType: String, private val algorithmName: String) {
    private val keys = if (keyType == "RSA") {
        KeyPairGenerator.getInstance("RSA").run { initialize(2048); generateKeyPair() }
    } else {
        SoftwareEcTestFixtures.keys(keyType)
    }
    private val certificate = SoftwareEcTestFixtures.certificate(keys)
    private val identity = UnlockedIdentity(keys.private, certificate, listOf(certificate), CertificateSummary(
        "Synthetic curve audit", "Synthetic curve audit", certificate.notBefore.toInstant(), certificate.notAfter.toInstant(),
    ))
    private val clock = Clock.fixed(certificate.notBefore.toInstant().plusSeconds(60), ZoneOffset.UTC)
    private val algorithm = SigningAlgorithm.valueOf(algorithmName)
    private val input = "Synthetic signing capability audit".toByteArray()

    @Test
    fun rawJcaEngineAcceptsRsaButNotEitherEcCurve() {
        checkResult(JcaLocalSignatureEngine().sign(input, identity, algorithm)) { bytes ->
            assertEquals((certificate.publicKey as RSAPublicKey).modulus.bitLength() / 8, bytes.size)
            assertTrue(Signature.getInstance(algorithm.jcaName()).run {
                initVerify(certificate.publicKey); update(input); verify(bytes)
            })
        }
    }

    @Test
    fun standaloneCadesRetainsRsaOnlyBoundary() {
        checkResult(BouncyCastleCadesSigner(clock = clock).signDetached(input, identity, algorithm)) { bytes ->
            checkRsaCms(bytes)
        }
    }

    @Test
    fun nativeCadesRetainsRsaOnlyBoundaryForBothPackages() {
        val engine = NativeCadesEngine(clock = clock)
        for (detached in listOf(true, false)) {
            checkResult(engine.sign(input, identity, algorithm, detached)) { bytes ->
                checkRsaCms(bytes)
                assertEquals(detached, CMSSignedData(bytes).isDetachedSignature)
                assertTrue(engine.verify(bytes, input, certificate, algorithm, detached))
            }
        }
    }

    @Test
    fun nativeXadesDoesNotTreatEcDerAsXmlSignatureValue() {
        val engine = NativeXadesEngine(clock)
        for (packaging in NativeXadesPackaging.entries) {
            val payload = "<audit>synthetic</audit>".toByteArray()
            val options = NativeXadesOptions(packaging, "application/xml", null)
            checkResult(engine.sign(payload, identity, algorithm, options)) { bytes ->
                assertTrue(engine.verify(bytes, payload, certificate, algorithm, options))
                val doc = NativeSigningXml.parse(bytes)
                val info = doc.getElementsByTagNameNS(NativeSigningXml.DS, "SignedInfo").item(0)
                val value = doc.getElementsByTagNameNS(NativeSigningXml.DS, "SignatureValue").item(0)
                val raw = Base64.getDecoder().decode(value.textContent)
                assertEquals((certificate.publicKey as RSAPublicKey).modulus.bitLength() / 8, raw.size)
                assertTrue(Signature.getInstance(algorithm.jcaName()).run {
                    initVerify(certificate.publicKey); update(NativeSigningXml.canonical(info)); verify(raw)
                })
            }
        }
    }

    @Test
    fun nativePadesRejectsEcWithAValidPdfAndAcceptsRsaControl() {
        val engine = NativePadesEngine(clock = clock)
        val pdf = nativePadesFixture()
        checkResult(engine.sign(pdf, identity, algorithm), LocalSignatureError.SIGNATURE_FAILED) { bytes ->
            assertTrue(engine.verify(bytes, pdf, certificate, algorithm))
        }
    }

    private fun checkResult(
        result: LocalSignatureResult,
        ecError: LocalSignatureError = LocalSignatureError.UNSUPPORTED_KEY,
        verify: (ByteArray) -> Unit,
    ) {
        if (keyType != "RSA") {
            assertEquals(LocalSignatureResult.Failure(ecError), result)
        } else {
            assertTrue("RSA positive control failed: $result", result is LocalSignatureResult.Success)
            (result as LocalSignatureResult.Success).signature.use { signature -> signature.withBytes(verify) }
        }
    }

    private fun checkRsaCms(encoded: ByteArray) {
        val cms = CMSSignedData(CMSProcessableByteArray(input), encoded)
        val signer = cms.signerInfos.signers.single()
        val expectedDigest = if (algorithm == SigningAlgorithm.SHA256_WITH_RSA) {
            "2.16.840.1.101.3.4.2.1"
        } else {
            "2.16.840.1.101.3.4.2.2"
        }
        assertEquals(expectedDigest, signer.digestAlgOID)
        // BC 1.85 emits the digest-specific RSA signature identifier for SHA-2.
        val expectedSignature = if (algorithm == SigningAlgorithm.SHA256_WITH_RSA) {
            "1.2.840.113549.1.1.11"
        } else {
            "1.2.840.113549.1.1.12"
        }
        assertEquals(expectedSignature, signer.encryptionAlgOID)
        assertTrue(signer.verify(JcaSimpleSignerInfoVerifierBuilder().build(certificate)))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}/{1}")
        fun cases(): List<Array<String>> = listOf(
            arrayOf("RSA", "SHA256_WITH_RSA"),
            arrayOf("RSA", "SHA384_WITH_RSA"),
            arrayOf("secp256r1", "SHA256_WITH_RSA"),
            arrayOf("secp384r1", "SHA384_WITH_RSA"),
        )
    }
}

package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.LocalSignatureResult
import dev.junta.firmamobile.signing.LocalSignatureError
import dev.junta.firmamobile.signing.SigningAlgorithm
import dev.junta.firmamobile.signing.nonExportableSyntheticIdentity
import dev.junta.firmamobile.signing.freshSyntheticIdentity
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.*
import org.junit.Test

class NativeCadesEngineTest {
    @Test fun attachedAndDetachedCadesAreValidatedAgainstOriginalContentAndSelectedCertificate() {
        val identity = nonExportableSyntheticIdentity(); val engine = NativeCadesEngine(); val payload = "synthetic document".toByteArray()
        for (detached in listOf(true, false)) for (algorithm in SigningAlgorithm.entries) {
            val outcome = engine.sign(payload, identity.identity, algorithm, detached)
            assertTrue(outcome.toString(), outcome is LocalSignatureResult.Success)
            (outcome as LocalSignatureResult.Success).signature.use { result ->
                result.withBytes { encoded ->
                    assertTrue(engine.verify(encoded, payload, identity.identity.certificate, algorithm, detached))
                    val envelope = CMSSignedData(encoded)
                    assertEquals(detached, envelope.signedContent == null)
                    val signed = if (detached) CMSSignedData(CMSProcessableByteArray(payload), encoded) else envelope
                    val signer = signed.signerInfos.signers.single()
                    assertNotNull(signer.signedAttributes.get(PKCSObjectIdentifiers.id_aa_signingCertificateV2))
                    assertTrue(signer.verify(JcaSimpleSignerInfoVerifierBuilder().setProvider(BouncyCastleProvider()).build(identity.identity.certificate)))
                    assertEquals(1, signed.certificates.getMatches(null).filter(signer.sid::match).size)
                    assertTrue(signer.sid.match(JcaX509CertificateHolder(identity.identity.certificate)))
                }
            }
        }
        assertEquals(0, identity.encodedReads.get())
        assertArrayEquals("synthetic document".toByteArray(), payload)
    }

    @Test fun mutatedContentCertificateAlgorithmAndModeAreRejectedIndependently() {
        val identity = freshSyntheticIdentity(); val other = freshSyntheticIdentity(); val payload = "abc".toByteArray()
        val engine = NativeCadesEngine()
        for (detached in listOf(true, false)) {
            val result = engine.sign(payload, identity, SigningAlgorithm.SHA256_WITH_RSA, detached) as LocalSignatureResult.Success
            result.signature.use { signature -> signature.withBytes { encoded ->
                assertFalse(engine.verify(encoded, "abd".toByteArray(), identity.certificate, SigningAlgorithm.SHA256_WITH_RSA, detached))
                assertFalse(engine.verify(encoded, payload, other.certificate, SigningAlgorithm.SHA256_WITH_RSA, detached))
                assertFalse(engine.verify(encoded, payload, identity.certificate, SigningAlgorithm.SHA512_WITH_RSA, detached))
                assertFalse(engine.verify(encoded, payload, identity.certificate, SigningAlgorithm.SHA256_WITH_RSA, !detached))
                val damaged = encoded.copyOf(); damaged[damaged.lastIndex] = (damaged.last().toInt() xor 1).toByte()
                assertFalse(engine.verify(damaged, payload, identity.certificate, SigningAlgorithm.SHA256_WITH_RSA, detached))
            } }
        }
    }

    @Test fun invalidAndOversizedInputFailsWithoutExposingAnUnverifiedSignature() {
        val identity = freshSyntheticIdentity(); val engine = NativeCadesEngine(maxInputBytes = 4, maxOutputBytes = 2048)
        val result = engine.sign(ByteArray(5), identity, SigningAlgorithm.SHA256_WITH_RSA, true)
        assertEquals(LocalSignatureError.INPUT_TOO_LARGE, (result as LocalSignatureResult.Failure).error)
        assertFalse(engine.verify(byteArrayOf(1,2,3), byteArrayOf(1), identity.certificate, SigningAlgorithm.SHA256_WITH_RSA, true))
        val small = NativeCadesEngine(maxOutputBytes = 16).sign(byteArrayOf(1), identity, SigningAlgorithm.SHA256_WITH_RSA, true)
        assertEquals(LocalSignatureError.OUTPUT_TOO_LARGE, (small as LocalSignatureResult.Failure).error)
    }

    @Test fun returnedSignatureIsCloseableAndCannotBeReadAfterRelease() {
        val identity = freshSyntheticIdentity()
        val result = NativeCadesEngine().sign(byteArrayOf(1), identity, SigningAlgorithm.SHA256_WITH_RSA, true) as LocalSignatureResult.Success
        result.signature.close(); result.signature.close()
        assertThrows(IllegalStateException::class.java) { result.signature.withBytes { it.size } }
    }
}

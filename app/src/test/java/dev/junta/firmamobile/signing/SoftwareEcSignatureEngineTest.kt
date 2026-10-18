package dev.junta.firmamobile.signing

import java.security.Signature
import java.security.interfaces.ECPrivateKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftwareEcSignatureEngineTest {
    @Test
    fun p256SignsOriginalBytesAsDerWithoutReadingPrivateKeyEncoding() {
        val keys = SoftwareEcTestFixtures.keys("secp256r1")
        val key = SoftwareEcTestFixtures.EncodingGuard(keys.private as ECPrivateKey)
        val certificate = SoftwareEcTestFixtures.certificate(keys)
        val input = byteArrayOf(0, 1, -1, 10, 13, 42)
        val result = SoftwareEcSignatureEngine().sign(
            input,
            SoftwareEcSignatureRequest(key, certificate, SoftwareEcSignatureAlgorithm.P256_SHA256),
        )

        assertTrue("Expected a verified DER signature, got $result", result is SoftwareEcSignatureResult.Success)
        val signature = (result as SoftwareEcSignatureResult.Success).derSignature
        assertTrue(Signature.getInstance("SHA256withECDSA").run {
            initVerify(certificate.publicKey)
            update(input)
            verify(signature)
        })
        assertEquals(0, key.encodingReads.get())
    }
    @Test
    fun p384SignsOnlyWithItsExplicitSha384Pair() {
        val keys = SoftwareEcTestFixtures.keys("secp384r1")
        val certificate = SoftwareEcTestFixtures.certificate(keys)
        val input = "raw P-384 challenge".encodeToByteArray()
        val result = SoftwareEcSignatureEngine().sign(
            input,
            SoftwareEcSignatureRequest(keys.private, certificate, SoftwareEcSignatureAlgorithm.P384_SHA384),
        )
        assertTrue("Expected P-384 support, got $result", result is SoftwareEcSignatureResult.Success)
        val bytes = (result as SoftwareEcSignatureResult.Success).derSignature
        assertTrue(Signature.getInstance("SHA384withECDSA").run {
            initVerify(certificate.publicKey)
            update(input)
            verify(bytes)
        })
    }

    @Test
    fun crossCurveHashPairsAreRejectedEvenWhenJcaCouldSignThem() {
        val cases = listOf(
            "secp256r1" to SoftwareEcSignatureAlgorithm.P384_SHA384,
            "secp384r1" to SoftwareEcSignatureAlgorithm.P256_SHA256,
        )
        for ((curve, algorithm) in cases) {
            val keys = SoftwareEcTestFixtures.keys(curve)
            val result = SoftwareEcSignatureEngine().sign(
                byteArrayOf(1),
                SoftwareEcSignatureRequest(keys.private, SoftwareEcTestFixtures.certificate(keys), algorithm),
            )
            assertEquals(
                SoftwareEcSignatureResult.Failure(SoftwareEcSignatureError.UNSUPPORTED_KEY),
                result,
            )
        }
    }

    @Test
    fun oversizedOriginalInputIsRejected() {
        val keys = SoftwareEcTestFixtures.keys("secp256r1")
        val request = SoftwareEcSignatureRequest(
            keys.private, SoftwareEcTestFixtures.certificate(keys), SoftwareEcSignatureAlgorithm.P256_SHA256,
        )
        assertEquals(
            SoftwareEcSignatureResult.Failure(SoftwareEcSignatureError.INPUT_TOO_LARGE),
            SoftwareEcSignatureEngine().sign(ByteArray(524_289), request),
        )
    }

    @Test
    fun oversizedGeneratedOutputIsNotReturned() {
        val keys = SoftwareEcTestFixtures.keys("secp256r1")
        val request = SoftwareEcSignatureRequest(
            keys.private, SoftwareEcTestFixtures.certificate(keys), SoftwareEcSignatureAlgorithm.P256_SHA256,
        )
        assertEquals(
            SoftwareEcSignatureResult.Failure(SoftwareEcSignatureError.OUTPUT_TOO_LARGE),
            SoftwareEcSignatureEngine(maxOutputBytes = 1).sign(byteArrayOf(1), request),
        )
    }

    @Test
    fun verifiesDerAgainstTheSuppliedCertificateAndOriginalBytes() {
        val keys = SoftwareEcTestFixtures.keys("secp256r1")
        val certificate = SoftwareEcTestFixtures.certificate(keys)
        val algorithm = SoftwareEcSignatureAlgorithm.P256_SHA256
        val input = "independent verification".encodeToByteArray()
        val engine = SoftwareEcSignatureEngine()
        val result = engine.sign(input, SoftwareEcSignatureRequest(keys.private, certificate, algorithm))
            as SoftwareEcSignatureResult.Success
        assertTrue(engine.verify(input, result.derSignature, certificate, algorithm))
    }

}

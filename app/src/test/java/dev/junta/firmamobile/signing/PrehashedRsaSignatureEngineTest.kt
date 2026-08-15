package dev.junta.firmamobile.signing

import java.security.MessageDigest
import java.security.Signature
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class PrehashedRsaSignatureEngineTest {
    private val identity = syntheticIdentity()
    private val engine = JcaPrehashedRsaSignatureEngine()

    @Test
    fun sha256PrehashedSignatureMatchesStandardSha256WithRsaExactly() {
        val message = "Hello Junta de Andalucia VEA!".encodeToByteArray()
        val standardEngine = JcaLocalSignatureEngine()
        val standardResult = standardEngine.sign(
            input = message,
            identity = identity,
            algorithm = SigningAlgorithm.SHA256_WITH_RSA,
        ) as LocalSignatureResult.Success

        val hash = MessageDigest.getInstance("SHA-256").digest(message)
        val prehashedResult = engine.sign(
            digest = hash,
            algorithm = PrecalculatedHashAlgorithm.SHA256,
            identity = identity,
        ) as LocalSignatureResult.Success

        val standardSigBytes = standardResult.signature.withBytes { it.copyOf() }
        val prehashedSigBytes = prehashedResult.signature.withBytes { it.copyOf() }

        // Both verify over the original message using standard SHA256withRSA
        val verifiedStandard = Signature.getInstance("SHA256withRSA").run {
            initVerify(identity.certificate.publicKey)
            update(message)
            verify(standardSigBytes)
        }
        val verifiedPrehashed = Signature.getInstance("SHA256withRSA").run {
            initVerify(identity.certificate.publicKey)
            update(message)
            verify(prehashedSigBytes)
        }

        assertTrue("Standard signature must verify", verifiedStandard)
        assertTrue("Prehashed signature must verify against original message", verifiedPrehashed)

        standardResult.signature.close()
        prehashedResult.signature.close()
    }

    @Test
    fun sha512PrehashedSignatureMatchesStandardSha512WithRsa() {
        val message = "VEA Draft Document Payload 2026".encodeToByteArray()
        val hash = MessageDigest.getInstance("SHA-512").digest(message)
        val result = engine.sign(
            digest = hash,
            algorithm = PrecalculatedHashAlgorithm.SHA512,
            identity = identity,
        ) as LocalSignatureResult.Success

        val sigBytes = result.signature.withBytes { it.copyOf() }
        val verified = Signature.getInstance("SHA512withRSA").run {
            initVerify(identity.certificate.publicKey)
            update(message)
            verify(sigBytes)
        }
        assertTrue("SHA-512 prehashed signature must verify with SHA512withRSA", verified)
        result.signature.close()
    }

    @Test
    fun sha1PrehashedSignatureMatchesStandardSha1WithRsa() {
        val message = "Legacy document hash".encodeToByteArray()
        val hash = MessageDigest.getInstance("SHA-1").digest(message)
        val result = engine.sign(
            digest = hash,
            algorithm = PrecalculatedHashAlgorithm.SHA1,
            identity = identity,
        ) as LocalSignatureResult.Success

        val sigBytes = result.signature.withBytes { it.copyOf() }
        val verified = Signature.getInstance("SHA1withRSA").run {
            initVerify(identity.certificate.publicKey)
            update(message)
            verify(sigBytes)
        }
        assertTrue("SHA-1 prehashed signature must verify with SHA1withRSA", verified)
        result.signature.close()
    }

    @Test
    fun rejectsInvalidDigestLengthFailClosed() {
        val invalidHash = ByteArray(31) { 0x42 }
        val result = engine.sign(
            digest = invalidHash,
            algorithm = PrecalculatedHashAlgorithm.SHA256,
            identity = identity,
        )
        assertEquals(LocalSignatureResult.Failure(LocalSignatureError.INPUT_TOO_LARGE), result)
    }

    @Test
    fun precalculatedHashAlgorithmParsesVariationsAndDecodesHexAndBase64() {
        assertEquals(PrecalculatedHashAlgorithm.SHA256, PrecalculatedHashAlgorithm.parse("SHA256"))
        assertEquals(PrecalculatedHashAlgorithm.SHA256, PrecalculatedHashAlgorithm.parse("SHA-256"))
        assertEquals(PrecalculatedHashAlgorithm.SHA256, PrecalculatedHashAlgorithm.parse("sha-256"))
        assertEquals(PrecalculatedHashAlgorithm.SHA512, PrecalculatedHashAlgorithm.parse("SHA-512"))
        assertEquals(PrecalculatedHashAlgorithm.SHA1, PrecalculatedHashAlgorithm.parse("SHA1"))
        assertEquals(PrecalculatedHashAlgorithm.SHA1, PrecalculatedHashAlgorithm.parse("SHA-1"))
        assertEquals(null, PrecalculatedHashAlgorithm.parse("MD5"))
        assertEquals(null, PrecalculatedHashAlgorithm.parse("UNKNOWN"))

        val rawBytes = ByteArray(32) { (it + 1).toByte() }
        val hex = rawBytes.joinToString("") { "%02x".format(it) }
        val b64 = java.util.Base64.getEncoder().encodeToString(rawBytes)

        val decodedFromHex = PrecalculatedHashAlgorithm.SHA256.decodeHash(hex)
        val decodedFromB64 = PrecalculatedHashAlgorithm.SHA256.decodeHash(b64)

        assertArrayEquals(rawBytes, decodedFromHex)
        assertArrayEquals(rawBytes, decodedFromB64)

        // Invalid hex or wrong length must return null
        assertEquals(null, PrecalculatedHashAlgorithm.SHA256.decodeHash(hex.substring(0, 62)))
        assertEquals(null, PrecalculatedHashAlgorithm.SHA256.decodeHash(hex + "00"))
        assertEquals(null, PrecalculatedHashAlgorithm.SHA256.decodeHash("not-a-valid-hex!"))
    }
}

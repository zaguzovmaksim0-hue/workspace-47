package dev.junta.firmamobile.signing

import java.security.Signature
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Sequence
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftwareEcSignatureVerificationTest {
    @Test
    fun bothPairsProduceCanonicalDerAndVerifyWithIndependentJcaProviders() {
        for (algorithm in SoftwareEcSignatureAlgorithm.entries) {
            val fixture = signed(algorithm)
            val der = ASN1Sequence.getInstance(fixture.signature)
            assertEquals(2, der.size())
            assertTrue((der.getObjectAt(0) as ASN1Integer).value.signum() > 0)
            assertTrue((der.getObjectAt(1) as ASN1Integer).value.signum() > 0)
            assertArrayEquals(fixture.signature, der.getEncoded("DER"))
            assertTrue(fixture.signature.size <= algorithm.maxDerBytes)
            val verifier = Signature.getInstance(algorithm.jcaName)
            assertNotSame(BundledBouncyCastle.provider, verifier.provider)
            assertTrue(verifier.run {
                initVerify(fixture.certificate.publicKey); update(fixture.input); verify(fixture.signature)
            })
            assertTrue(fixture.engine.verify(fixture.input, fixture.signature, fixture.certificate, algorithm))
        }
    }

    @Test
    fun tamperedOriginalBytesFailClosed() {
        val f = signed()
        val changed = f.input.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertFalse(f.engine.verify(changed, f.signature, f.certificate, f.algorithm))
    }

    @Test
    fun tamperedDerSignatureFailsClosed() {
        val f = signed()
        val changed = f.signature.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertFalse(f.engine.verify(f.input, changed, f.certificate, f.algorithm))
    }

    @Test
    fun wrongSameCurveCertificateAndCrossCurveAlgorithmFailClosed() {
        val f = signed()
        val wrong = SoftwareEcTestFixtures.certificate(SoftwareEcTestFixtures.keys("secp256r1"))
        assertFalse(f.engine.verify(f.input, f.signature, wrong, f.algorithm))
        assertFalse(f.engine.verify(f.input, f.signature, f.certificate, SoftwareEcSignatureAlgorithm.P384_SHA384))
    }

    @Test
    fun malformedNonCanonicalAndOutOfRangeDerAreRejected() {
        val f = signed()
        val malformed = listOf(
            byteArrayOf(),
            byteArrayOf(0x30, 0),
            byteArrayOf(0x05, 0),
            hex("3006020180020101"),
            hex("3006020100020101"),
            hex("300702020001020101"),
            hex("3009020101020101020101"),
            hex("308106020101020101"),
            hex("30800201010201010000"),
            f.signature + byteArrayOf(0),
            f.signature.copyOf(f.signature.size - 1),
        )
        for (bytes in malformed) {
            assertFalse("Accepted malformed DER ${bytes.joinToString { "%02x".format(it) }}",
                f.engine.verify(f.input, bytes, f.certificate, f.algorithm))
        }
    }

    @Test
    fun p1363ConcatenatedIntegersAreNotAcceptedAsDer() {
        for (algorithm in SoftwareEcSignatureAlgorithm.entries) {
            val f = signed(algorithm)
            val width = if (algorithm == SoftwareEcSignatureAlgorithm.P256_SHA256) 32 else 48
            val sequence = ASN1Sequence.getInstance(f.signature)
            val raw = ByteArray(width * 2)
            for (index in 0..1) {
                val integer = (sequence.getObjectAt(index) as ASN1Integer).positiveValue.toByteArray()
                val length = minOf(width, integer.size)
                integer.copyInto(raw, (index + 1) * width - length, integer.size - length)
            }
            assertFalse(f.engine.verify(f.input, raw, f.certificate, algorithm))
        }
    }

    @Test
    fun verificationEnforcesExactInputAndOutputBoundsWithoutMutatingCallers() {
        val f = signed()
        val inputCopy = f.input.copyOf()
        val signatureCopy = f.signature.copyOf()
        assertTrue(SoftwareEcSignatureEngine(maxInputBytes = f.input.size, maxOutputBytes = f.signature.size)
            .verify(f.input, f.signature, f.certificate, f.algorithm))
        assertFalse(SoftwareEcSignatureEngine(maxInputBytes = f.input.size - 1)
            .verify(f.input, f.signature, f.certificate, f.algorithm))
        assertFalse(SoftwareEcSignatureEngine(maxOutputBytes = f.signature.size - 1)
            .verify(f.input, f.signature, f.certificate, f.algorithm))
        assertFalse(f.engine.verify(f.input, ByteArray(105), f.certificate, f.algorithm))
        assertArrayEquals(inputCopy, f.input)
        assertArrayEquals(signatureCopy, f.signature)
    }

    @Test
    fun emptyAndMaximumOriginalInputsAreSignedWithoutPrehashingOrCallerMutation() {
        val algorithm = SoftwareEcSignatureAlgorithm.P384_SHA384
        val keys = SoftwareEcTestFixtures.keys(algorithm.namedCurve)
        val certificate = SoftwareEcTestFixtures.certificate(keys)
        for (input in listOf(byteArrayOf(), ByteArray(SoftwareEcSignatureEngine.MAX_INPUT_BYTES) { (it % 251).toByte() })) {
            val before = input.copyOf()
            val engine = SoftwareEcSignatureEngine()
            val result = engine.sign(input, SoftwareEcSignatureRequest(keys.private, certificate, algorithm))
                as SoftwareEcSignatureResult.Success
            assertTrue(Signature.getInstance(algorithm.jcaName).run {
                initVerify(certificate.publicKey); update(input); verify(result.derSignature)
            })
            assertArrayEquals(before, input)
        }
    }

    @Test
    fun signaturesDoNotVerifyWithTheOtherHashOnTheSameCurve() {
        for (algorithm in SoftwareEcSignatureAlgorithm.entries) {
            val f = signed(algorithm)
            val otherHash = if (algorithm == SoftwareEcSignatureAlgorithm.P256_SHA256)
                "SHA384withECDSA" else "SHA256withECDSA"
            assertFalse(Signature.getInstance(otherHash).run {
                initVerify(f.certificate.publicKey); update(f.input); verify(f.signature)
            })
        }
    }

    private fun signed(algorithm: SoftwareEcSignatureAlgorithm = SoftwareEcSignatureAlgorithm.P256_SHA256): Signed {
        val keys = SoftwareEcTestFixtures.keys(algorithm.namedCurve)
        val certificate = SoftwareEcTestFixtures.certificate(keys)
        val input = byteArrayOf(0, 1, 127, -128, -1)
        val engine = SoftwareEcSignatureEngine()
        val result = engine.sign(input, SoftwareEcSignatureRequest(keys.private, certificate, algorithm))
            as SoftwareEcSignatureResult.Success
        return Signed(engine, algorithm, certificate, input, result.derSignature)
    }

    private fun hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private class Signed(
        val engine: SoftwareEcSignatureEngine,
        val algorithm: SoftwareEcSignatureAlgorithm,
        val certificate: java.security.cert.X509Certificate,
        val input: ByteArray,
        val signature: ByteArray,
    )
}

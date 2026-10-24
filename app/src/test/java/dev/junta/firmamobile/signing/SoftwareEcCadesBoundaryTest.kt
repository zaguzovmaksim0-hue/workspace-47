package dev.junta.firmamobile.signing

import java.security.SecureRandom
import java.security.interfaces.ECPrivateKey
import java.util.concurrent.atomic.AtomicInteger
import org.bouncycastle.asn1.ASN1Encoding
import org.bouncycastle.asn1.DEROctetString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftwareEcCadesBoundaryTest {
    @Test
    fun certificateEncodingBackingArrayIsNeverClearedOrMutated() {
        val f = SoftwareEcCadesTestFixtures()
        val encoded = f.certificate.encoded
        val before = encoded.copyOf()
        val view = SoftwareEcCadesCertificateView(f.certificate, encoding = { encoded })
        val result = f.engine.signDetached(f.input, SoftwareEcSignatureRequest(f.keys.private, view, f.algorithm))
        assertTrue(result is SoftwareEcCadesResult.Success)
        assertArrayEquals(before, encoded)
        assertTrue(f.engine.verifyDetached(f.input, (result as SoftwareEcCadesResult.Success).cmsDer, view, f.algorithm))
        assertArrayEquals(before, encoded)
    }

    @Test
    fun inputAndCertificateAreOwnedBeforePrivateScalarCallback() {
        val f = SoftwareEcCadesTestFixtures()
        val input = f.input.copyOf()
        val encoded = f.certificate.encoded
        val expectedEncoded = encoded.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        val view = SoftwareEcCadesCertificateView(f.certificate, encoding = { encoded })
        val delegate = f.keys.private as ECPrivateKey
        val key = SoftwareEcTestFixtures.EncodingGuard(delegate, scalar = {
            input.fill(99)
            encoded[0] = (encoded[0].toInt() xor 1).toByte()
            delegate.s
        })
        val result = f.engine.signDetached(input, SoftwareEcSignatureRequest(key, view, f.algorithm))
        assertTrue("Expected owned snapshots despite caller mutation: $result", result is SoftwareEcCadesResult.Success)
        assertArrayEquals(expectedEncoded, encoded)
        assertTrue(f.verifies((result as SoftwareEcCadesResult.Success).cmsDer))
        assertEquals(1, key.scalarReads.get())
        assertEquals(0, key.encodingReads.get())
    }

    @Test
    fun inputBoundRejectsBeforeScalarAndSigningAndAcceptsEmptyAndExactLimit() {
        val f = SoftwareEcCadesTestFixtures()
        val key = SoftwareEcTestFixtures.EncodingGuard(f.keys.private as ECPrivateKey)
        val random = CountingRandom()
        val engine = SoftwareEcCadesEngine(SoftwareEcSignatureEngine(random), SoftwareEcCadesLimits(maxInputBytes = 3))
        assertEquals(failure(SoftwareEcCadesError.INPUT_TOO_LARGE), engine.signDetached(ByteArray(4),
            SoftwareEcSignatureRequest(key, f.certificate, f.algorithm)))
        assertEquals(0, key.scalarReads.get())
        assertEquals(0, key.encodingReads.get())
        assertEquals(0, random.calls.get())
        for (input in listOf(byteArrayOf(), ByteArray(3))) {
            val cms = f.sign(engine, input)
            assertTrue(engine.verifyDetached(input, cms, f.certificate, f.algorithm))
        }
        assertFalse(engine.verifyDetached(ByteArray(4), f.encoded, f.certificate, f.algorithm))
    }

    @Test
    fun signedAttributeBoundIsExactAndCheckedBeforeSigning() {
        val f = SoftwareEcCadesTestFixtures()
        val size = f.signer.authenticatedAttributes.getEncoded(ASN1Encoding.DER).size
        val exact = SoftwareEcCadesEngine(limits = SoftwareEcCadesLimits(maxSignedAttributesBytes = size))
        assertTrue(exact.verifyDetached(f.input, f.sign(exact), f.certificate, f.algorithm))
        val random = CountingRandom()
        val key = SoftwareEcTestFixtures.EncodingGuard(f.keys.private as ECPrivateKey)
        val smaller = SoftwareEcCadesEngine(SoftwareEcSignatureEngine(random),
            SoftwareEcCadesLimits(maxSignedAttributesBytes = size - 1))
        assertEquals(failure(SoftwareEcCadesError.SIGNED_ATTRIBUTES_TOO_LARGE), smaller.signDetached(f.input,
            SoftwareEcSignatureRequest(key, f.certificate, f.algorithm)))
        assertFalse(smaller.verifyDetached(f.input, f.encoded, f.certificate, f.algorithm))
        assertEquals(0, random.calls.get())
        assertEquals(0, key.scalarReads.get())
    }

    @Test
    fun certificateBoundIsExactAndCheckedBeforeSigning() {
        val f = SoftwareEcCadesTestFixtures()
        val size = f.certificate.encoded.size
        val exact = SoftwareEcCadesEngine(limits = SoftwareEcCadesLimits(maxCertificateBytes = size))
        assertTrue(exact.verifyDetached(f.input, f.sign(exact), f.certificate, f.algorithm))
        val random = CountingRandom()
        val smaller = SoftwareEcCadesEngine(SoftwareEcSignatureEngine(random),
            SoftwareEcCadesLimits(maxCertificateBytes = size - 1))
        assertEquals(failure(SoftwareEcCadesError.CERTIFICATE_TOO_LARGE), smaller.signDetached(f.input, f.request))
        assertFalse(smaller.verifyDetached(f.input, f.encoded, f.certificate, f.algorithm))
        assertEquals(0, random.calls.get())
    }

    @Test
    fun outputBoundRejectsWithoutReturningPartialCmsAndBoundsVerification() {
        val f = SoftwareEcCadesTestFixtures()
        val tiny = SoftwareEcCadesEngine(limits = SoftwareEcCadesLimits(maxOutputBytes = 1))
        val before = f.input.copyOf()
        assertEquals(failure(SoftwareEcCadesError.OUTPUT_TOO_LARGE), tiny.signDetached(f.input, f.request))
        assertArrayEquals(before, f.input)
        val exact = SoftwareEcCadesEngine(limits = SoftwareEcCadesLimits(maxOutputBytes = f.encoded.size))
        assertTrue(exact.verifyDetached(f.input, f.encoded, f.certificate, f.algorithm))
        val smaller = SoftwareEcCadesEngine(limits = SoftwareEcCadesLimits(maxOutputBytes = f.encoded.size - 1))
        assertFalse(smaller.verifyDetached(f.input, f.encoded, f.certificate, f.algorithm))
    }

    @Test
    fun absoluteSourceLimitAcceptedAndOneExtraByteRejected() {
        val f = SoftwareEcCadesTestFixtures(SoftwareEcSignatureAlgorithm.P384_SHA384)
        val source = ByteArray(SoftwareEcCadesLimits.MAX_INPUT_BYTES) { (it and 0xff).toByte() }
        val cms = f.sign(input = source)
        assertTrue(f.verifies(cms, source))
        assertEquals(failure(SoftwareEcCadesError.INPUT_TOO_LARGE), f.engine.signDetached(source + 1.toByte(), f.request))
        assertFalse(f.verifies(cms, source + 1.toByte()))
    }

    @Test
    fun limitsCannotBeZeroNegativeOrRaisedAboveHardBounds() {
        listOf(0, -1, SoftwareEcCadesLimits.MAX_INPUT_BYTES + 1).forEach {
            assertThrows(IllegalArgumentException::class.java) { SoftwareEcCadesLimits(maxInputBytes = it) }
        }
        listOf(0, -1, SoftwareEcCadesLimits.MAX_OUTPUT_BYTES + 1).forEach {
            assertThrows(IllegalArgumentException::class.java) { SoftwareEcCadesLimits(maxOutputBytes = it) }
        }
        listOf(0, -1, SoftwareEcCadesLimits.MAX_SIGNED_ATTRIBUTES_BYTES + 1).forEach {
            assertThrows(IllegalArgumentException::class.java) { SoftwareEcCadesLimits(maxSignedAttributesBytes = it) }
        }
        listOf(0, -1, SoftwareEcCadesLimits.MAX_CERTIFICATE_BYTES + 1).forEach {
            assertThrows(IllegalArgumentException::class.java) { SoftwareEcCadesLimits(maxCertificateBytes = it) }
        }
    }

    @Test
    fun ownedBuffersClearOnSuccessFailureAndPreserveReleasedOutput() {
        val owned = byteArrayOf(1, 2, 3)
        val released = byteArrayOf(4, 5)
        SoftwareEcCadesBuffers().use {
            it.own(owned)
            it.own(released)
            assertTrue(it.release(released) === released)
        }
        assertArrayEquals(ByteArray(3), owned)
        assertArrayEquals(byteArrayOf(4, 5), released)
        val failed = byteArrayOf(7, 8, 9)
        assertThrows(SoftwareEcCadesFailure::class.java) {
            SoftwareEcCadesBuffers().use {
                it.own(failed)
                it.encode(DEROctetString(ByteArray(8)), 1, SoftwareEcCadesError.OUTPUT_TOO_LARGE)
            }
        }
        assertArrayEquals(ByteArray(3), failed)
    }

    private fun failure(error: SoftwareEcCadesError) = SoftwareEcCadesResult.Failure(error)

    private class CountingRandom : SecureRandom() {
        val calls = AtomicInteger()
        override fun nextBytes(bytes: ByteArray) {
            calls.incrementAndGet()
            super.nextBytes(bytes)
        }
    }
}

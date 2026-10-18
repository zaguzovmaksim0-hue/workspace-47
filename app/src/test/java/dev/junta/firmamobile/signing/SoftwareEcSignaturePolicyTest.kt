package dev.junta.firmamobile.signing

import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Security
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECFieldFp
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.EllipticCurve
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftwareEcSignaturePolicyTest {
    private val algorithm = SoftwareEcSignatureAlgorithm.P256_SHA256

    @Test
    fun sameBitCountAndOtherCurvesAreRejectedBeforeSigning() {
        for (curve in listOf("secp256k1", "brainpoolP256r1", "secp521r1")) {
            val keys = SoftwareEcTestFixtures.keys(curve)
            val key = SoftwareEcTestFixtures.EncodingGuard(keys.private as ECPrivateKey)
            val random = CountingRandom()
            val result = SoftwareEcSignatureEngine(random).sign(
                byteArrayOf(1), SoftwareEcSignatureRequest(key, SoftwareEcTestFixtures.certificate(keys), algorithm),
            )
            assertEquals(unsupported(), result)
            assertEquals(0, key.encodingReads.get())
            assertEquals(0, random.calls.get())
        }
    }

    @Test
    fun privateAndCertificateCurveMustBothMatchTheSelectedPair() {
        val p256 = SoftwareEcTestFixtures.keys("secp256r1")
        val p384 = SoftwareEcTestFixtures.keys("secp384r1")
        for ((privateKey, certificate) in listOf(
            p256.private to SoftwareEcTestFixtures.certificate(p384),
            p384.private to SoftwareEcTestFixtures.certificate(p256),
        )) {
            val random = CountingRandom()
            assertEquals(unsupported(), SoftwareEcSignatureEngine(random).sign(
                byteArrayOf(1), SoftwareEcSignatureRequest(privateKey, certificate, algorithm),
            ))
            assertEquals(0, random.calls.get())
        }
    }

    @Test
    fun everyPrivateDomainComponentIsCheckedBeforeSignerDispatch() {
        val keys = SoftwareEcTestFixtures.keys("secp256r1")
        val privateKey = keys.private as ECPrivateKey
        val certificate = SoftwareEcTestFixtures.certificate(keys)
        for (parameters in changedParameters(privateKey.params)) {
            val key = SoftwareEcTestFixtures.EncodingGuard(privateKey, parameters)
            val random = CountingRandom()
            assertEquals(unsupported(), SoftwareEcSignatureEngine(random).sign(
                byteArrayOf(1), SoftwareEcSignatureRequest(key, certificate, algorithm),
            ))
            assertEquals(0, key.scalarReads.get())
            assertEquals(0, key.encodingReads.get())
            assertEquals(0, random.calls.get())
        }
    }

    @Test
    fun everyCertificateDomainComponentIsCheckedBeforeSignerDispatch() {
        val keys = SoftwareEcTestFixtures.keys("secp256r1")
        val publicKey = keys.public as ECPublicKey
        val certificate = SoftwareEcTestFixtures.certificate(keys)
        for (parameters in changedParameters(publicKey.params)) {
            val key = object : ECPublicKey {
                override fun getParams(): ECParameterSpec = parameters
                override fun getW(): ECPoint = publicKey.w
                override fun getAlgorithm(): String = "EC"
                override fun getFormat(): String = "X.509"
                override fun getEncoded(): ByteArray = publicKey.encoded
            }
            val random = CountingRandom()
            assertEquals(unsupported(), SoftwareEcSignatureEngine(random).sign(
                byteArrayOf(1),
                SoftwareEcSignatureRequest(keys.private, SoftwareEcCertificateView(certificate, key), algorithm),
            ))
            assertEquals(0, random.calls.get())
        }
    }

    @Test
    fun rsaPrivateKeysAndRsaCertificatesAreRejectedBeforeSigning() {
        val ec = SoftwareEcTestFixtures.keys("secp256r1")
        val rsa = KeyPairGenerator.getInstance("RSA").run { initialize(1024); generateKeyPair() }
        for ((key, certificate) in listOf(
            rsa.private to SoftwareEcTestFixtures.certificate(ec),
            ec.private to SoftwareEcTestFixtures.certificate(rsa),
        )) {
            val random = CountingRandom()
            assertEquals(unsupported(), SoftwareEcSignatureEngine(random).sign(
                byteArrayOf(1), SoftwareEcSignatureRequest(key, certificate, algorithm),
            ))
            assertEquals(0, random.calls.get())
        }
    }

    @Test
    fun ecLabelAloneDoesNotMakeAnOpaqueKeySupported() {
        val keys = SoftwareEcTestFixtures.keys("secp256r1")
        val reads = AtomicInteger()
        val opaque = object : PrivateKey {
            override fun getAlgorithm(): String = "EC"
            override fun getFormat(): String? = null
            override fun getEncoded(): ByteArray {
                reads.incrementAndGet()
                error("Opaque key encoding is unavailable")
            }
        }
        val random = CountingRandom()
        assertEquals(unsupported(), SoftwareEcSignatureEngine(random).sign(
            byteArrayOf(1), SoftwareEcSignatureRequest(opaque, SoftwareEcTestFixtures.certificate(keys), algorithm),
        ))
        assertEquals(0, reads.get())
        assertEquals(0, random.calls.get())
    }

    @Test
    fun opaqueScalarAndImplicitPrivateParametersAreRejectedBeforeSigning() {
        val keys = SoftwareEcTestFixtures.keys("secp256r1")
        val delegate = keys.private as ECPrivateKey
        val certificate = SoftwareEcTestFixtures.certificate(keys)
        val unsupportedKeys = listOf(
            SoftwareEcTestFixtures.EncodingGuard(delegate, scalar = { throw UnsupportedOperationException() }),
            SoftwareEcTestFixtures.EncodingGuard(delegate, parameters = null),
            SoftwareEcTestFixtures.EncodingGuard(delegate, scalar = { null }),
        )
        for (key in unsupportedKeys) {
            val random = CountingRandom()
            assertEquals(unsupported(), SoftwareEcSignatureEngine(random).sign(
                byteArrayOf(1), SoftwareEcSignatureRequest(key, certificate, algorithm),
            ))
            assertEquals(0, key.encodingReads.get())
            assertEquals(0, random.calls.get())
        }
    }

    @Test
    fun outOfRangeSoftwareScalarsAreRejectedBeforeSigning() {
        val keys = SoftwareEcTestFixtures.keys("secp256r1")
        val delegate = keys.private as ECPrivateKey
        val certificate = SoftwareEcTestFixtures.certificate(keys)
        for (scalar in listOf(BigInteger.ZERO, BigInteger.ONE.negate(), delegate.params.order,
            delegate.params.order + BigInteger.ONE)) {
            val key = SoftwareEcTestFixtures.EncodingGuard(delegate, scalar = { scalar })
            val random = CountingRandom()
            assertEquals(unsupported(), SoftwareEcSignatureEngine(random).sign(
                byteArrayOf(1), SoftwareEcSignatureRequest(key, certificate, algorithm),
            ))
            assertEquals(0, key.encodingReads.get())
            assertEquals(0, random.calls.get())
        }
    }

    @Test
    fun readableSourceScalarIsSnapshottedExactlyOnceWithoutEncoding() {
        val keys = SoftwareEcTestFixtures.keys("secp256r1")
        val delegate = keys.private as ECPrivateKey
        val reads = AtomicInteger()
        val key = SoftwareEcTestFixtures.EncodingGuard(delegate, scalar = {
            check(reads.incrementAndGet() == 1) { "Source key was reused after preflight" }
            delegate.s
        })
        val result = SoftwareEcSignatureEngine().sign(
            byteArrayOf(1), SoftwareEcSignatureRequest(key, SoftwareEcTestFixtures.certificate(keys), algorithm),
        )
        assertTrue(result is SoftwareEcSignatureResult.Success)
        assertEquals(1, key.scalarReads.get())
        assertEquals(0, key.encodingReads.get())
    }

    @Test
    fun wrongSameCurveCertificateFailsGeneratedSignatureVerification() {
        val keys = SoftwareEcTestFixtures.keys("secp256r1")
        val wrongCertificate = SoftwareEcTestFixtures.certificate(SoftwareEcTestFixtures.keys("secp256r1"))
        val random = CountingRandom()
        assertEquals(failed(), SoftwareEcSignatureEngine(random).sign(
            byteArrayOf(1), SoftwareEcSignatureRequest(keys.private, wrongCertificate, algorithm),
        ))
        assertTrue(random.calls.get() > 0)
    }

    @Test
    fun entropyFailureIsNotRetriedWithAnotherProvider() {
        val keys = SoftwareEcTestFixtures.keys("secp256r1")
        val random = CountingRandom { throw IllegalStateException("Synthetic entropy failure") }
        assertEquals(failed(), SoftwareEcSignatureEngine(random).sign(
            byteArrayOf(1), SoftwareEcSignatureRequest(keys.private, SoftwareEcTestFixtures.certificate(keys), algorithm),
        ))
        assertEquals(1, random.calls.get())
    }

    @Test
    fun signingUsesAnOwnedSnapshotOfOriginalBytes() {
        val keys = SoftwareEcTestFixtures.keys("secp256r1")
        val certificate = SoftwareEcTestFixtures.certificate(keys)
        val input = byteArrayOf(0, -1, 2, 3)
        val original = input.copyOf()
        val random = CountingRandom { input.fill(42) }
        val result = SoftwareEcSignatureEngine(random).sign(
            input, SoftwareEcSignatureRequest(keys.private, certificate, algorithm),
        ) as SoftwareEcSignatureResult.Success
        assertTrue(Signature.getInstance(algorithm.jcaName).run {
            initVerify(certificate.publicKey); update(original); verify(result.derSignature)
        })
        assertFalse(Signature.getInstance(algorithm.jcaName).run {
            initVerify(certificate.publicKey); update(input); verify(result.derSignature)
        })
    }

    @Test
    fun inputRejectionDoesNotReadTheSourceScalarOrUseEntropy() {
        val keys = SoftwareEcTestFixtures.keys("secp256r1")
        val key = SoftwareEcTestFixtures.EncodingGuard(keys.private as ECPrivateKey)
        val input = ByteArray(SoftwareEcSignatureEngine.MAX_INPUT_BYTES + 1) { 7 }
        val before = input.copyOf()
        val random = CountingRandom()
        assertEquals(
            SoftwareEcSignatureResult.Failure(SoftwareEcSignatureError.INPUT_TOO_LARGE),
            SoftwareEcSignatureEngine(random).sign(
                input, SoftwareEcSignatureRequest(key, SoftwareEcTestFixtures.certificate(keys), algorithm),
            ),
        )
        assertArrayEquals(before, input)
        assertEquals(0, key.scalarReads.get())
        assertEquals(0, key.encodingReads.get())
        assertEquals(0, random.calls.get())
    }

    @Test
    fun globalProvidersRemainTheSameObjectsInTheSameOrder() {
        val before = Security.getProviders().toList()
        for (selected in SoftwareEcSignatureAlgorithm.entries) {
            val keys = SoftwareEcTestFixtures.keys(selected.namedCurve)
            assertTrue(SoftwareEcSignatureEngine().sign(
                byteArrayOf(1), SoftwareEcSignatureRequest(keys.private, SoftwareEcTestFixtures.certificate(keys), selected),
            ) is SoftwareEcSignatureResult.Success)
        }
        assertEquals(before, Security.getProviders().toList())
    }

    @Test
    fun configurableLimitsCanOnlyNarrowHardBounds() {
        for (bad in listOf(-1, 0, SoftwareEcSignatureEngine.MAX_INPUT_BYTES + 1)) {
            assertThrows(IllegalArgumentException::class.java) { SoftwareEcSignatureEngine(maxInputBytes = bad) }
        }
        for (bad in listOf(-1, 0, SoftwareEcSignatureEngine.MAX_OUTPUT_BYTES + 1)) {
            assertThrows(IllegalArgumentException::class.java) { SoftwareEcSignatureEngine(maxOutputBytes = bad) }
        }
    }

    @Test
    fun standardJcaSoftwareKeysWorkForBothExplicitPairsWithoutEncoding() {
        for (selected in SoftwareEcSignatureAlgorithm.entries) {
            val keys = KeyPairGenerator.getInstance("EC").run {
                initialize(java.security.spec.ECGenParameterSpec(selected.namedCurve))
                generateKeyPair()
            }
            val key = SoftwareEcTestFixtures.EncodingGuard(keys.private as ECPrivateKey)
            val certificate = SoftwareEcTestFixtures.certificate(keys)
            val result = SoftwareEcSignatureEngine().sign(
                byteArrayOf(1), SoftwareEcSignatureRequest(key, certificate, selected),
            ) as SoftwareEcSignatureResult.Success
            assertTrue(Signature.getInstance(selected.jcaName).run {
                initVerify(certificate.publicKey); update(byteArrayOf(1)); verify(result.derSignature)
            })
            assertEquals(1, key.scalarReads.get())
            assertEquals(0, key.encodingReads.get())
        }
    }

    @Test
    fun implicitCertificateParametersAreRejectedBeforeSigning() {
        val keys = SoftwareEcTestFixtures.keys("secp256r1")
        val original = keys.public as ECPublicKey
        val key = object : ECPublicKey {
            override fun getParams(): ECParameterSpec? = null
            override fun getW(): ECPoint = original.w
            override fun getAlgorithm(): String = "EC"
            override fun getFormat(): String? = null
            override fun getEncoded(): ByteArray = error("Implicit public-key parameters must not be encoded")
        }
        val random = CountingRandom()
        assertEquals(unsupported(), SoftwareEcSignatureEngine(random).sign(
            byteArrayOf(1), SoftwareEcSignatureRequest(
                keys.private, SoftwareEcCertificateView(SoftwareEcTestFixtures.certificate(keys), key), algorithm,
            ),
        ))
        assertEquals(0, random.calls.get())
    }

    @Test
    fun invalidPublicPointsFailBeforeSigningEvenWithAllowedDomainParameters() {
        val keys = SoftwareEcTestFixtures.keys("secp256r1")
        val original = keys.public as ECPublicKey
        val certificate = SoftwareEcTestFixtures.certificate(keys)
        for (point in listOf(ECPoint.POINT_INFINITY, ECPoint(BigInteger.ONE, BigInteger.ONE))) {
            val key = object : ECPublicKey {
                override fun getParams(): ECParameterSpec = original.params
                override fun getW(): ECPoint = point
                override fun getAlgorithm(): String = "EC"
                override fun getFormat(): String? = null
                override fun getEncoded(): ByteArray = error("Invalid public points must not be encoded")
            }
            val random = CountingRandom()
            assertTrue(SoftwareEcSignatureEngine(random).sign(
                byteArrayOf(1), SoftwareEcSignatureRequest(
                    keys.private, SoftwareEcCertificateView(certificate, key), algorithm,
                ),
            ) is SoftwareEcSignatureResult.Failure)
            assertEquals(0, random.calls.get())
        }
    }

    private fun changedParameters(base: ECParameterSpec): List<ECParameterSpec> {
        val curve = base.curve
        val prime = (curve.field as ECFieldFp).p
        fun withCurve(changed: EllipticCurve) = ECParameterSpec(changed, base.generator, base.order, base.cofactor)
        return listOf(
            withCurve(EllipticCurve(ECFieldFp(prime - BigInteger.TWO), curve.a, curve.b)),
            withCurve(EllipticCurve(curve.field, curve.a + BigInteger.ONE, curve.b)),
            withCurve(EllipticCurve(curve.field, curve.a, curve.b + BigInteger.ONE)),
            ECParameterSpec(curve, ECPoint(base.generator.affineX + BigInteger.ONE, base.generator.affineY),
                base.order, base.cofactor),
            ECParameterSpec(curve, base.generator, base.order + BigInteger.ONE, base.cofactor),
            ECParameterSpec(curve, base.generator, base.order, base.cofactor + 1),
        )
    }

    private fun unsupported() = SoftwareEcSignatureResult.Failure(SoftwareEcSignatureError.UNSUPPORTED_KEY)
    private fun failed() = SoftwareEcSignatureResult.Failure(SoftwareEcSignatureError.SIGNATURE_FAILED)

    private class CountingRandom(private val beforeEntropy: () -> Unit = {}) : SecureRandom() {
        val calls = AtomicInteger()
        private val delegate = SecureRandom()
        override fun nextBytes(bytes: ByteArray) {
            calls.incrementAndGet()
            beforeEntropy()
            delegate.nextBytes(bytes)
        }
    }
}

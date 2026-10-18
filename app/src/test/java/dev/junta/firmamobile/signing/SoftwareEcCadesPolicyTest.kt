package dev.junta.firmamobile.signing

import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.ProviderException
import java.security.SecureRandom
import java.security.Security
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECFieldFp
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.EllipticCurve
import java.util.concurrent.atomic.AtomicInteger
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftwareEcCadesPolicyTest {
    private val algorithm = SoftwareEcSignatureAlgorithm.P256_SHA256
    private val input = byteArrayOf(0, 1, -1, 12)

    @Test
    fun otherCurvesIncludingSameBitCountRejectBeforeSignerDispatch() {
        for (curve in listOf("secp256k1", "brainpoolP256r1", "secp521r1")) {
            val keys = SoftwareEcTestFixtures.keys(curve)
            val key = SoftwareEcTestFixtures.EncodingGuard(keys.private as ECPrivateKey)
            assertUnsupported(SoftwareEcSignatureRequest(key, SoftwareEcTestFixtures.certificate(keys), algorithm))
            assertEquals(0, key.encodingReads.get())
            assertEquals(0, key.scalarReads.get())
        }
    }

    @Test
    fun privateCertificateAndSelectedPairMustAllMatch() {
        val p256 = SoftwareEcTestFixtures.keys("secp256r1")
        val p384 = SoftwareEcTestFixtures.keys("secp384r1")
        for (selected in SoftwareEcSignatureAlgorithm.entries) {
            for (privateKeys in listOf(p256, p384)) {
                for (certificateKeys in listOf(p256, p384)) {
                    val allowed = if (selected == algorithm) p256 else p384
                    if (privateKeys === allowed && certificateKeys === allowed) continue
                    val key = SoftwareEcTestFixtures.EncodingGuard(privateKeys.private as ECPrivateKey)
                    assertUnsupported(SoftwareEcSignatureRequest(key,
                        SoftwareEcTestFixtures.certificate(certificateKeys), selected))
                    assertEquals(0, key.encodingReads.get())
                }
            }
        }
    }

    @Test
    fun everyPrivateDomainComponentIsCheckedWithoutEncodingOrSigning() {
        val keys = SoftwareEcTestFixtures.keys("secp256r1")
        val delegate = keys.private as ECPrivateKey
        val certificate = SoftwareEcTestFixtures.certificate(keys)
        for (parameters in changedParameters(delegate.params) + listOf(null)) {
            val key = SoftwareEcTestFixtures.EncodingGuard(delegate, parameters)
            assertUnsupported(SoftwareEcSignatureRequest(key, certificate, algorithm))
            assertEquals(0, key.scalarReads.get())
            assertEquals(0, key.encodingReads.get())
        }
    }

    @Test
    fun originalCertificateDomainCannotBeBypassedByItsValidDerEncoding() {
        val keys = SoftwareEcTestFixtures.keys("secp256r1")
        val original = keys.public as ECPublicKey
        val certificate = SoftwareEcTestFixtures.certificate(keys)
        for (parameters in changedParameters(original.params) + listOf(null)) {
            val public = publicView(original, parameters)
            val encodings = AtomicInteger()
            val view = SoftwareEcCadesCertificateView(certificate, public, encoding = {
                encodings.incrementAndGet()
                certificate.encoded
            })
            assertUnsupported(SoftwareEcSignatureRequest(keys.private, view, algorithm))
            assertEquals(0, encodings.get())
        }
    }

    @Test
    fun suppliedPublicPointMustEqualTheCertificateDerPoint() {
        val a = SoftwareEcTestFixtures.keys("secp256r1")
        val b = SoftwareEcTestFixtures.keys("secp256r1")
        val certificate = SoftwareEcTestFixtures.certificate(a)
        val key = SoftwareEcTestFixtures.EncodingGuard(a.private as ECPrivateKey)
        val original = a.public as ECPublicKey
        for (public in listOf(b.public, publicView(original, original.params, ECPoint.POINT_INFINITY))) {
            assertUnsupported(SoftwareEcSignatureRequest(key, SoftwareEcCadesCertificateView(certificate, public), algorithm))
        }
        assertEquals(0, key.scalarReads.get())
        assertEquals(0, key.encodingReads.get())
    }

    @Test
    fun rsaPrivateAndRsaCertificateAreNotAdmittedIntoEcApi() {
        val ec = SoftwareEcTestFixtures.keys("secp256r1")
        val rsa = KeyPairGenerator.getInstance("RSA").run { initialize(1024); generateKeyPair() }
        assertUnsupported(SoftwareEcSignatureRequest(rsa.private, SoftwareEcTestFixtures.certificate(ec), algorithm))
        assertUnsupported(SoftwareEcSignatureRequest(ec.private, SoftwareEcTestFixtures.certificate(rsa), algorithm))
    }

    @Test
    fun opaqueHandlesMissingScalarsAndOutOfRangeScalarsFailWithoutExportOrRetry() {
        val keys = SoftwareEcTestFixtures.keys("secp256r1")
        val delegate = keys.private as ECPrivateKey
        val certificate = SoftwareEcTestFixtures.certificate(keys)
        val encodings = AtomicInteger()
        val opaque = object : PrivateKey {
            override fun getAlgorithm(): String = "EC"
            override fun getFormat(): String? = null
            override fun getEncoded(): ByteArray {
                encodings.incrementAndGet()
                error("Opaque private key must not be exported")
            }
        }
        assertUnsupported(SoftwareEcSignatureRequest(opaque, certificate, algorithm))
        assertEquals(0, encodings.get())
        val guards = listOf(
            SoftwareEcTestFixtures.EncodingGuard(delegate, scalar = { throw UnsupportedOperationException() }),
            SoftwareEcTestFixtures.EncodingGuard(delegate, scalar = { null }),
            SoftwareEcTestFixtures.EncodingGuard(delegate, scalar = { BigInteger.ZERO }),
            SoftwareEcTestFixtures.EncodingGuard(delegate, scalar = { BigInteger.ONE.negate() }),
            SoftwareEcTestFixtures.EncodingGuard(delegate, scalar = { delegate.params.order }),
            SoftwareEcTestFixtures.EncodingGuard(delegate, scalar = { delegate.params.order + BigInteger.ONE }),
        )
        guards.forEach {
            assertUnsupported(SoftwareEcSignatureRequest(it, certificate, algorithm))
            assertEquals(0, it.encodingReads.get())
        }
    }

    @Test
    fun readableScalarIsSnapshottedOnceAcrossSigningAndFinalCmsVerification() {
        val f = SoftwareEcCadesTestFixtures()
        val delegate = f.keys.private as ECPrivateKey
        val reads = AtomicInteger()
        val key = SoftwareEcTestFixtures.EncodingGuard(delegate, scalar = {
            check(reads.incrementAndGet() == 1)
            delegate.s
        })
        val result = f.engine.signDetached(input, SoftwareEcSignatureRequest(key, f.certificate, algorithm))
        assertTrue(result is SoftwareEcCadesResult.Success)
        assertEquals(1, reads.get())
        assertEquals(1, key.scalarReads.get())
        assertEquals(0, key.encodingReads.get())
    }

    @Test
    fun wrongSameCurveCertificateFailsClosedInsteadOfReturningCms() {
        val f = SoftwareEcCadesTestFixtures()
        val key = SoftwareEcTestFixtures.EncodingGuard(f.keys.private as ECPrivateKey)
        val wrong = SoftwareEcTestFixtures.certificate(SoftwareEcTestFixtures.keys("secp256r1"))
        val random = RecordingRandom()
        val result = SoftwareEcCadesEngine(SoftwareEcSignatureEngine(random)).signDetached(input,
            SoftwareEcSignatureRequest(key, wrong, algorithm))
        assertEquals(failure(SoftwareEcCadesError.SIGNATURE_FAILED), result)
        assertEquals(1, key.scalarReads.get())
        assertEquals(0, key.encodingReads.get())
        assertTrue(random.calls.get() > 0)
    }

    @Test
    fun providerFailureIsOneAttemptWithoutFallbackAndProviderOrderNeverChanges() {
        val providers = Security.getProviders()
        for (selected in SoftwareEcSignatureAlgorithm.entries) {
            val f = SoftwareEcCadesTestFixtures(selected)
            assertTrue(f.verifies())
            val key = SoftwareEcTestFixtures.EncodingGuard(f.keys.private as ECPrivateKey)
            val random = RecordingRandom(fail = true)
            val engine = SoftwareEcCadesEngine(SoftwareEcSignatureEngine(random))
            assertEquals(failure(SoftwareEcCadesError.SIGNATURE_FAILED), engine.signDetached(input,
                SoftwareEcSignatureRequest(key, f.certificate, selected)))
            assertEquals(1, random.calls.get())
            assertEquals(1, key.scalarReads.get())
            assertEquals(0, key.encodingReads.get())
            assertArrayEquals(providers, Security.getProviders())
        }
    }

    @Test
    fun malformedCertificateDerFailsBeforePrivateScalarOrSigning() {
        val f = SoftwareEcCadesTestFixtures()
        val encodings = listOf(byteArrayOf(), byteArrayOf(0x30, 0), f.certificate.encoded + 0.toByte(),
            byteArrayOf(0x30, 0x80.toByte(), 0, 0))
        for (encoding in encodings) {
            val key = SoftwareEcTestFixtures.EncodingGuard(f.keys.private as ECPrivateKey)
            val random = RecordingRandom()
            val view = SoftwareEcCadesCertificateView(f.certificate, encoding = { encoding })
            val engine = SoftwareEcCadesEngine(SoftwareEcSignatureEngine(random))
            assertEquals(failure(SoftwareEcCadesError.SIGNATURE_FAILED), engine.signDetached(input,
                SoftwareEcSignatureRequest(key, view, algorithm)))
            assertFalse(engine.verifyDetached(f.input, f.encoded, view, algorithm))
            assertEquals(0, random.calls.get())
            assertEquals(0, key.scalarReads.get())
            assertEquals(0, key.encodingReads.get())
        }
    }

    @Test
    fun deeplyNestedCertificateExtensionFailsBeforePrivateDispatch() {
        val f = SoftwareEcCadesTestFixtures()
        var nested = byteArrayOf(0x30, 0)
        repeat(2_000) {
            val length = when {
                nested.size < 128 -> byteArrayOf(nested.size.toByte())
                nested.size < 256 -> byteArrayOf(0x81.toByte(), nested.size.toByte())
                else -> byteArrayOf(0x82.toByte(), (nested.size ushr 8).toByte(), nested.size.toByte())
            }
            nested = byteArrayOf(0x30) + length + nested
        }
        val holder = X509CertificateHolder(f.certificate.encoded)
        val encoded = JcaX509v3CertificateBuilder(holder.issuer, BigInteger.TWO, f.certificate.notBefore,
            f.certificate.notAfter, holder.subject, f.keys.public)
            .addExtension(Extension.basicConstraints, true, nested)
            .build(JcaContentSignerBuilder(f.algorithm.jcaName).setProvider(BouncyCastleProvider()).build(f.keys.private))
            .encoded
        val random = RecordingRandom()
        val key = SoftwareEcTestFixtures.EncodingGuard(f.keys.private as ECPrivateKey)
        val view = SoftwareEcCadesCertificateView(f.certificate, encoding = { encoded })
        assertEquals(failure(SoftwareEcCadesError.SIGNATURE_FAILED),
            SoftwareEcCadesEngine(SoftwareEcSignatureEngine(random)).signDetached(input,
                SoftwareEcSignatureRequest(key, view, algorithm)))
        assertEquals(0, random.calls.get())
        assertEquals(0, key.scalarReads.get())
        assertEquals(0, key.encodingReads.get())
    }

    private fun assertUnsupported(request: SoftwareEcSignatureRequest) {
        val random = RecordingRandom()
        assertEquals(failure(SoftwareEcCadesError.UNSUPPORTED_KEY),
            SoftwareEcCadesEngine(SoftwareEcSignatureEngine(random)).signDetached(input, request))
        assertEquals(0, random.calls.get())
    }

    private fun changedParameters(p: ECParameterSpec): List<ECParameterSpec> {
        val field = p.curve.field as ECFieldFp
        return listOf(
            ECParameterSpec(EllipticCurve(ECFieldFp(field.p - BigInteger.TWO), p.curve.a, p.curve.b),
                p.generator, p.order, p.cofactor),
            ECParameterSpec(EllipticCurve(field, p.curve.a + BigInteger.ONE, p.curve.b), p.generator, p.order, p.cofactor),
            ECParameterSpec(EllipticCurve(field, p.curve.a, p.curve.b + BigInteger.ONE), p.generator, p.order, p.cofactor),
            ECParameterSpec(p.curve, ECPoint(p.generator.affineX + BigInteger.ONE, p.generator.affineY), p.order, p.cofactor),
            ECParameterSpec(p.curve, p.generator, p.order - BigInteger.ONE, p.cofactor),
            ECParameterSpec(p.curve, p.generator, p.order, p.cofactor + 1),
        )
    }

    private fun publicView(
        key: ECPublicKey,
        parameters: ECParameterSpec?,
        point: ECPoint = key.w,
    ): ECPublicKey = object : ECPublicKey {
        override fun getParams(): ECParameterSpec? = parameters
        override fun getW(): ECPoint = point
        override fun getAlgorithm(): String = "EC"
        override fun getFormat(): String? = null
        override fun getEncoded(): ByteArray = error("Public view encoding is not the certificate DER")
    }

    private fun failure(error: SoftwareEcCadesError) = SoftwareEcCadesResult.Failure(error)

    private class RecordingRandom(private val fail: Boolean = false) : SecureRandom() {
        val calls = AtomicInteger()
        override fun nextBytes(bytes: ByteArray) {
            calls.incrementAndGet()
            if (fail) throw ProviderException("Synthetic entropy failure; no retry allowed")
            super.nextBytes(bytes)
        }
    }
}

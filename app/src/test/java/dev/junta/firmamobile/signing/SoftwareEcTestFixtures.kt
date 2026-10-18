package dev.junta.firmamobile.signing

import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.cert.X509Certificate
import java.security.interfaces.ECPrivateKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

/** Disposable named-curve keys/certificates generated in memory; no PKCS#12 or real identity. */
internal object SoftwareEcTestFixtures {
    private val provider = BouncyCastleProvider()

    fun keys(curve: String): KeyPair = KeyPairGenerator.getInstance("EC", provider).run {
        initialize(ECGenParameterSpec(curve))
        generateKeyPair()
    }

    fun certificate(keys: KeyPair): X509Certificate {
        val name = X500Name("CN=Synthetic EC Engine,O=Junta Firma Mobile Tests,C=ES")
        val signatureAlgorithm = if (keys.private.algorithm == "RSA") "SHA256withRSA" else "SHA256withECDSA"
        val signer = JcaContentSignerBuilder(signatureAlgorithm).setProvider(provider).build(keys.private)
        return JcaX509CertificateConverter().setProvider(provider).getCertificate(
            JcaX509v3CertificateBuilder(
                name,
                BigInteger.ONE,
                Date(1_577_836_800_000L),
                Date(4_102_444_800_000L),
                name,
                keys.public,
            ).build(signer),
        )
    }

    class EncodingGuard(
        private val delegate: ECPrivateKey,
        private val parameters: ECParameterSpec? = delegate.params,
        private val scalar: () -> BigInteger? = { delegate.s },
    ) : ECPrivateKey {
        val encodingReads = AtomicInteger()
        val scalarReads = AtomicInteger()
        override fun getAlgorithm(): String = "EC"
        override fun getFormat(): String? = null
        override fun getParams(): ECParameterSpec? = parameters
        override fun getS(): BigInteger? {
            scalarReads.incrementAndGet()
            return scalar()
        }
        override fun getEncoded(): ByteArray {
            encodingReads.incrementAndGet()
            error("Private-key encoding must never be requested")
        }
    }
}

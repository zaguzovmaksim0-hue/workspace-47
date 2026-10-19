package dev.junta.firmamobile.verification

import dev.junta.firmamobile.signing.BundledBouncyCastle
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Provider
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import java.util.Hashtable
import java.util.concurrent.atomic.AtomicLong
import javax.security.auth.x500.X500Principal
import org.bouncycastle.asn1.ASN1Encoding
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.DERSet
import org.bouncycastle.asn1.cms.Attribute
import org.bouncycastle.asn1.cms.AttributeTable
import org.bouncycastle.asn1.cms.CMSAttributes
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers
import org.bouncycastle.cert.X509CRLHolder
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v2CRLBuilder
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cms.CMSAttributeTableGenerator
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder

internal data class OfflineCmsSignerMaterial(
    val keyPair: KeyPair,
    val certificate: X509Certificate,
    val signatureAlgorithm: String,
)

internal data class OfflineCmsOptions(
    val certificates: Collection<X509Certificate>? = null,
    val encapsulate: Boolean = true,
    val directSignature: Boolean = false,
    val contentType: ASN1ObjectIdentifier = CMSObjectIdentifiers.data,
    val crls: List<X509CRLHolder> = emptyList(),
)

internal object OfflineCmsFixtures {
    const val MAX_CMS_BYTES = 1_048_576
    const val MAX_CONTENT_BYTES = 524_288
    const val MAX_SIGNERS = 8
    const val MAX_CERTIFICATES = 32

    private const val RSA_KEY_SIZE_BITS = 2_048
    private const val FIRST_CERTIFICATE_SERIAL = 1L
    private const val CERTIFICATE_VALID_FROM_MILLIS = 0L
    private const val FUTURE_CERT_END_MILLIS = 4_102_444_800_000L
    private val serials = AtomicLong(FIRST_CERTIFICATE_SERIAL)
    private val keyPairs = mutableMapOf<String, KeyPair>()
    private val issuer = X500Principal("CN=Offline CMS Verifier Test")

    val provider: Provider
        get() = BundledBouncyCastle.provider

    fun signer(
        signatureAlgorithm: String = "SHA256withRSA",
        keyAlgorithm: String = "RSA",
        curve: String? = null,
        serial: Long = serials.getAndIncrement(),
        notBefore: Date = Date(CERTIFICATE_VALID_FROM_MILLIS),
        notAfter: Date = Date(FUTURE_CERT_END_MILLIS),
    ): OfflineCmsSignerMaterial {
        val keyPair = cachedKeyPair(keyAlgorithm, curve)
        return OfflineCmsSignerMaterial(
            keyPair = keyPair,
            certificate = certificate(keyPair, serial, notBefore, notAfter),
            signatureAlgorithm = signatureAlgorithm,
        )
    }

    fun certificate(
        keyPair: KeyPair,
        serial: Long,
        notBefore: Date = Date(0L),
        notAfter: Date = Date(FUTURE_CERT_END_MILLIS),
    ): X509Certificate {
        val holder = JcaX509v3CertificateBuilder(
            issuer,
            BigInteger.valueOf(serial),
            notBefore,
            notAfter,
            issuer,
            keyPair.public,
        ).build(
            JcaContentSignerBuilder(keyPair.fixtureSignatureAlgorithm())
                .setProvider(provider)
                .build(keyPair.private),
        )
        return JcaX509CertificateConverter().setProvider(provider).getCertificate(holder)
    }

    fun signed(
        content: ByteArray,
        signers: List<OfflineCmsSignerMaterial>,
        options: OfflineCmsOptions = OfflineCmsOptions(),
    ): ByteArray {
        val generator = CMSSignedDataGenerator()
        signers.forEach { material ->
            val signerBuilder = JcaSignerInfoGeneratorBuilder(
                JcaDigestCalculatorProviderBuilder().setProvider(provider).build(),
            )
            if (options.directSignature) {
                signerBuilder.setDirectSignature(true)
            } else {
                signerBuilder.setSignedAttributeGenerator(allowedSignedAttributes())
            }
            generator.addSignerInfoGenerator(
                signerBuilder.build(
                    JcaContentSignerBuilder(material.signatureAlgorithm)
                        .setProvider(provider)
                        .build(material.keyPair.private),
                    material.certificate,
                ),
            )
        }
        val certificates = options.certificates ?: signers.map { it.certificate }
        if (certificates.isNotEmpty()) {
            generator.addCertificates(JcaCertStore(certificates))
        }
        options.crls.forEach { generator.addCRL(it) }
        return generator.generate(
            CMSProcessableByteArray(options.contentType, content),
            options.encapsulate,
        ).getEncoded(ASN1Encoding.DER)
    }

    fun crl(signer: OfflineCmsSignerMaterial): X509CRLHolder {
        val builder = JcaX509v2CRLBuilder(signer.certificate, Date())
        val contentSigner = JcaContentSignerBuilder(signer.signatureAlgorithm)
            .setProvider(provider)
            .build(signer.keyPair.private)
        return builder.build(contentSigner)
    }

    private fun cachedKeyPair(keyAlgorithm: String, curve: String?): KeyPair =
        synchronized(keyPairs) {
            val cacheKey = keyAlgorithm + ":" + (curve ?: "default")
            keyPairs.getOrPut(cacheKey) {
                val generator = KeyPairGenerator.getInstance(keyAlgorithm, provider)
                when (keyAlgorithm) {
                    "RSA" -> generator.initialize(RSA_KEY_SIZE_BITS)
                    "EC" -> generator.initialize(ECGenParameterSpec(curve ?: "secp256r1"))
                    else -> error("Unsupported fixture key algorithm: $keyAlgorithm")
                }
                generator.generateKeyPair()
            }
        }

    private fun allowedSignedAttributes(): CMSAttributeTableGenerator =
        CMSAttributeTableGenerator { parameters ->
            val contentType = parameters[CMSAttributeTableGenerator.CONTENT_TYPE]
                as ASN1ObjectIdentifier
            val digest = parameters[CMSAttributeTableGenerator.DIGEST] as ByteArray
            val attributes = Hashtable<ASN1ObjectIdentifier, Attribute>()
            attributes[CMSAttributes.contentType] =
                Attribute(CMSAttributes.contentType, DERSet(contentType))
            attributes[CMSAttributes.messageDigest] =
                Attribute(CMSAttributes.messageDigest, DERSet(DEROctetString(digest)))
            AttributeTable(attributes)
        }

    private fun KeyPair.fixtureSignatureAlgorithm(): String =
        if (private.algorithm.equals("EC", ignoreCase = true)) {
            "SHA256withECDSA"
        } else {
            "SHA256withRSA"
        }
}

package dev.junta.firmamobile.signing

import dev.junta.firmamobile.certificate.UnlockedIdentity
import java.security.MessageDigest
import java.security.Provider
import java.security.SecureRandom
import java.time.Clock
import java.util.Date
import java.util.Hashtable
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERSet
import org.bouncycastle.asn1.cms.Attribute
import org.bouncycastle.asn1.cms.AttributeTable
import org.bouncycastle.asn1.cms.CMSAttributes
import org.bouncycastle.asn1.cms.Time
import org.bouncycastle.asn1.ess.ESSCertIDv2
import org.bouncycastle.asn1.ess.SigningCertificateV2
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.DefaultSignedAttributeTableGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder

interface CadesSigner {
    fun signDetached(
        content: ByteArray,
        identity: UnlockedIdentity,
        algorithm: SigningAlgorithm,
    ): LocalSignatureResult
}

class BouncyCastleCadesSigner internal constructor(
    private val maxInputBytes: Int = MAX_INPUT_BYTES,
    private val maxOutputBytes: Int = MAX_OUTPUT_BYTES,
    private val provider: Provider = BouncyCastleProvider(),
    private val secureRandom: SecureRandom = SecureRandom(),
    private val clock: Clock = Clock.systemUTC(),
    private val signatureObserver: SensitiveSignatureCopyObserver =
        SensitiveSignatureCopyObserver {},
) : CadesSigner {
    init {
        require(maxInputBytes > 0)
        require(maxOutputBytes > 0)
    }

    override fun signDetached(
        content: ByteArray,
        identity: UnlockedIdentity,
        algorithm: SigningAlgorithm,
    ): LocalSignatureResult {
        if (content.size > maxInputBytes) {
            return LocalSignatureResult.Failure(LocalSignatureError.INPUT_TOO_LARGE)
        }

        val contentCopy = content.copyOf()
        var certificateBytes: ByteArray? = null
        var certificateHash: ByteArray? = null
        var encodedSignature: ByteArray? = null
        return try {
            certificateBytes = identity.certificate.encoded
            certificateHash = MessageDigest.getInstance(SHA_256).digest(certificateBytes)

            val signingCertificate = SigningCertificateV2(
                ESSCertIDv2(certificateHash),
            )
            val suppliedAttributes = Hashtable<ASN1ObjectIdentifier, Attribute>().apply {
                put(
                    PKCSObjectIdentifiers.id_aa_signingCertificateV2,
                    Attribute(
                        PKCSObjectIdentifiers.id_aa_signingCertificateV2,
                        DERSet(signingCertificate),
                    ),
                )
                put(
                    CMSAttributes.signingTime,
                    Attribute(
                        CMSAttributes.signingTime,
                        DERSet(Time(Date.from(clock.instant()))),
                    ),
                )
            }
            val contentSigner = identity.withPrivateKey { privateKey ->
                if (!privateKey.algorithm.equals(RSA, ignoreCase = true)) {
                    null
                } else {
                    JcaContentSignerBuilder(algorithm.jcaName())
                        .setProvider(provider)
                        .setSecureRandom(secureRandom)
                        .build(privateKey)
                }
            } ?: return LocalSignatureResult.Failure(LocalSignatureError.UNSUPPORTED_KEY)
            val digestProvider = JcaDigestCalculatorProviderBuilder()
                .setProvider(provider)
                .build()
            val signerInfo = JcaSignerInfoGeneratorBuilder(digestProvider)
                .setSignedAttributeGenerator(
                    DefaultSignedAttributeTableGenerator(AttributeTable(suppliedAttributes)),
                )
                .build(contentSigner, JcaX509CertificateHolder(identity.certificate))

            val generator = CMSSignedDataGenerator().apply {
                addSignerInfoGenerator(signerInfo)
                addCertificates(
                    JcaCertStore(
                        identity.chain.ifEmpty { listOf(identity.certificate) },
                    ),
                )
            }
            val generated = generator.generate(CMSProcessableByteArray(contentCopy), false).encoded
            encodedSignature = generated
            if (generated.size > maxOutputBytes) {
                return LocalSignatureResult.Failure(LocalSignatureError.OUTPUT_TOO_LARGE)
            }
            encodedSignature = null
            LocalSignatureResult.Success(LocalSignature(generated, signatureObserver))
        } catch (_: Exception) {
            LocalSignatureResult.Failure(LocalSignatureError.SIGNATURE_FAILED)
        } finally {
            contentCopy.fill(0)
            certificateBytes?.fill(0)
            certificateHash?.fill(0)
            encodedSignature?.fill(0)
        }
    }

    companion object {
        const val MAX_INPUT_BYTES = 524_288
        const val MAX_OUTPUT_BYTES = 2_097_152
        private const val RSA = "RSA"
        private const val SHA_256 = "SHA-256"
    }
}

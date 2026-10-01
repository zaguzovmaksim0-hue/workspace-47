package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.*
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import java.security.cert.X509Certificate

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

internal class NativeCadesEngine(
    private val maxInputBytes: Int = MAX_INPUT_BYTES,
    private val maxOutputBytes: Int = MAX_OUTPUT_BYTES,
    private val provider: Provider = BouncyCastleProvider(),
    private val secureRandom: SecureRandom = SecureRandom(),
    private val clock: Clock = Clock.systemUTC(),
    private val signatureObserver: SensitiveSignatureCopyObserver =
        SensitiveSignatureCopyObserver {},
) {
    init {
        require(maxInputBytes > 0)
        require(maxOutputBytes > 0)
    }

    fun sign(
        content: ByteArray,
        identity: UnlockedIdentity,
        algorithm: SigningAlgorithm,
        detached: Boolean,
    ): LocalSignatureResult {
        if (content.size > maxInputBytes) {
            return LocalSignatureResult.Failure(LocalSignatureError.INPUT_TOO_LARGE)
        }

        val contentCopy = content.copyOf()
        var certificateBytes: ByteArray? = null
        var certificateHash: ByteArray? = null
        var encodedSignature: ByteArray? = null
        return try {
            identity.certificate.checkValidity(Date.from(clock.instant()))
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
                    JcaContentSignerBuilder(when (algorithm) {
                        SigningAlgorithm.SHA1_WITH_RSA -> "SHA1withRSA"
                        SigningAlgorithm.SHA256_WITH_RSA -> "SHA256withRSA"
                        SigningAlgorithm.SHA512_WITH_RSA -> "SHA512withRSA"
                    })
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
            val generated = generator.generate(CMSProcessableByteArray(contentCopy), !detached).encoded
            encodedSignature = generated
            if (generated.size > maxOutputBytes) {
                return LocalSignatureResult.Failure(LocalSignatureError.OUTPUT_TOO_LARGE)
            }
            if (!verify(generated, contentCopy, identity.certificate, algorithm, detached)) {
                return LocalSignatureResult.Failure(LocalSignatureError.SIGNATURE_FAILED)
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

    /** Validation is content/certificate/algorithm/mode bound, not merely a
     * parse-success check. No caller-supplied certificate is trusted implicitly. */
    fun verify(encoded: ByteArray, payload: ByteArray, certificate: X509Certificate,
        algorithm: SigningAlgorithm, detached: Boolean): Boolean = runCatching {
        if (encoded.isEmpty() || encoded.size > maxOutputBytes || payload.size > maxInputBytes) return false
        val envelope = CMSSignedData(encoded)
        if ((envelope.signedContent == null) != detached) return false
        if (!detached) {
            val content = envelope.signedContent.content as? ByteArray ?: return false
            if (!MessageDigest.isEqual(content, payload)) return false
        }
        val signed = if (detached) CMSSignedData(CMSProcessableByteArray(payload), encoded) else envelope
        val signer = signed.signerInfos.signers.singleOrNull() ?: return false
        val expectedDigest = when (algorithm) {
            SigningAlgorithm.SHA1_WITH_RSA -> "1.3.14.3.2.26"
            SigningAlgorithm.SHA256_WITH_RSA -> "2.16.840.1.101.3.4.2.1"
            SigningAlgorithm.SHA512_WITH_RSA -> "2.16.840.1.101.3.4.2.3"
        }
        // BC 1.85 emits digest-specific rsa signature identifiers for SHA-2;
        // rsaEncryption remains valid CMS encoding. Never accept an identifier
        // for another digest merely because the key happens to be RSA.
        val expectedRsaSignature = when (algorithm) {
            SigningAlgorithm.SHA1_WITH_RSA -> "1.2.840.113549.1.1.5"
            SigningAlgorithm.SHA256_WITH_RSA -> "1.2.840.113549.1.1.11"
            SigningAlgorithm.SHA512_WITH_RSA -> "1.2.840.113549.1.1.13"
        }
        if (signer.digestAlgOID != expectedDigest ||
            signer.encryptionAlgOID !in setOf("1.2.840.113549.1.1.1", expectedRsaSignature)
        ) return false
        val expectedCertificate = JcaX509CertificateHolder(certificate)
        if (!signer.sid.match(expectedCertificate)) return false
        val included = signed.certificates.getMatches(null).filter(signer.sid::match)
        if (included.size != 1 || included.single() != expectedCertificate) return false
        val attrs = signer.signedAttributes ?: return false
        val references = attrs.getAll(PKCSObjectIdentifiers.id_aa_signingCertificateV2)
        if (references.size() != 1) return false
        val reference = attrs.get(PKCSObjectIdentifiers.id_aa_signingCertificateV2)
        if (reference.attrValues.size() != 1) return false
        val certId = SigningCertificateV2.getInstance(reference.attrValues.getObjectAt(0)).certs.singleOrNull() ?: return false
        if (certId.hashAlgorithm.algorithm.id != "2.16.840.1.101.3.4.2.1") return false
        val expectedHash = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
        try {
            if (!MessageDigest.isEqual(expectedHash, certId.certHash)) return false
        } finally { expectedHash.fill(0) }
        signer.verify(JcaSimpleSignerInfoVerifierBuilder().setProvider(provider).build(certificate))
    }.getOrDefault(false)

    companion object {
        const val MAX_INPUT_BYTES = 524_288
        const val MAX_OUTPUT_BYTES = 2_097_152
        private const val RSA = "RSA"
        private const val SHA_256 = "SHA-256"
    }
}

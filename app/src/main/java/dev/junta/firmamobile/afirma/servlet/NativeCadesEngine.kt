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
    private val includeSigningTime: Boolean = true,
) {
    init {
        require(maxInputBytes > 0)
        require(maxOutputBytes > 0)
    }

    fun sign(content: ByteArray, identity: UnlockedIdentity, algorithm: SigningAlgorithm,
        detached: Boolean): LocalSignatureResult = signInternal(content, identity, algorithm, detached, null, null)

    fun signWithPolicy(content: ByteArray, identity: UnlockedIdentity, algorithm: SigningAlgorithm,
        detached: Boolean, policy: NativeCadesPolicy?): LocalSignatureResult = signInternal(content, identity, algorithm, detached, null, policy)

    /** A verified co-sign digest or explicitly requested site-provided digest
     * enters this path. Request validation and consent distinguish those uses.
     * Neither use asserts that an absent original document was inspected. */
    internal fun signDigest(digest: ByteArray, identity: UnlockedIdentity, algorithm: SigningAlgorithm): LocalSignatureResult {
        if (digest.size != NativeCadesHistory.digestLength(digestOid(algorithm))) {
            return LocalSignatureResult.Failure(LocalSignatureError.SIGNATURE_FAILED)
        }
        return signInternal(ByteArray(0), identity, algorithm, true, digest, null)
    }

    internal fun signDigestWithPolicy(digest: ByteArray, identity: UnlockedIdentity, algorithm: SigningAlgorithm,
        policy: NativeCadesPolicy?): LocalSignatureResult {
        if (digest.size != NativeCadesHistory.digestLength(digestOid(algorithm))) return LocalSignatureResult.Failure(LocalSignatureError.SIGNATURE_FAILED)
        return signInternal(ByteArray(0), identity, algorithm, true, digest, policy)
    }

    private fun signInternal(content: ByteArray, identity: UnlockedIdentity, algorithm: SigningAlgorithm,
        detached: Boolean, suppliedDigest: ByteArray?, policy: NativeCadesPolicy?): LocalSignatureResult {
        if (content.size > maxInputBytes) {
            return LocalSignatureResult.Failure(LocalSignatureError.INPUT_TOO_LARGE)
        }

        val contentCopy = content.copyOf()
        val contentDigest = suppliedDigest?.copyOf()
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
            policy?.let { suppliedAttributes[NativeCadesPolicy.ATTRIBUTE_OID] = it.attribute() }
            val contentSigner = identity.withPrivateKey { privateKey ->
                if (!privateKey.algorithm.equals(RSA, ignoreCase = true)) {
                    null
                } else {
                    JcaContentSignerBuilder(when (algorithm) {
                        SigningAlgorithm.SHA1_WITH_RSA -> "SHA1withRSA"
                        SigningAlgorithm.SHA256_WITH_RSA -> "SHA256withRSA"
                        SigningAlgorithm.SHA384_WITH_RSA -> "SHA384withRSA"
                        SigningAlgorithm.SHA512_WITH_RSA -> "SHA512withRSA"
                    })
                        .apply {
                            // Software keys use the isolated bundled provider.
                            // Opaque KeyChain/Keystore handles must stay with
                            // their JCA provider; never export or retry them.
                            if (privateKey is java.security.interfaces.RSAPrivateKey) setProvider(provider)
                        }
                        .setSecureRandom(secureRandom)
                        .build(privateKey)
                }
            } ?: return LocalSignatureResult.Failure(LocalSignatureError.UNSUPPORTED_KEY)
            val ordinaryDigestProvider = JcaDigestCalculatorProviderBuilder().setProvider(provider).build()
            val digestProvider = if (contentDigest == null) ordinaryDigestProvider else
                org.bouncycastle.operator.DigestCalculatorProvider { identifier ->
                    require(identifier.algorithm.id == digestOid(algorithm))
                    object : org.bouncycastle.operator.DigestCalculator {
                        override fun getAlgorithmIdentifier() = identifier
                        override fun getOutputStream(): java.io.OutputStream = object : java.io.OutputStream() {
                            override fun write(value: Int) { error("Prehashed signing has no content stream") }
                            override fun write(bytes: ByteArray, offset: Int, length: Int) { require(length == 0) }
                        }
                        override fun getDigest() = contentDigest.copyOf()
                    }
                }
            val signerInfo = JcaSignerInfoGeneratorBuilder(digestProvider)
                .setSignedAttributeGenerator(
                    org.bouncycastle.cms.CMSAttributeTableGenerator { parameters ->
                        val generated = DefaultSignedAttributeTableGenerator(AttributeTable(suppliedAttributes))
                            .getAttributes(parameters)
                        // PAdES expresses the claimed time in PDF /M. BC's
                        // default generator otherwise adds signingTime itself.
                        if (includeSigningTime) generated else generated.remove(CMSAttributes.signingTime)
                    },
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
            if (!verifyInternal(generated, contentCopy, identity.certificate, algorithm, detached, contentDigest, policy)) {
                return LocalSignatureResult.Failure(LocalSignatureError.SIGNATURE_FAILED)
            }
            encodedSignature = null
            LocalSignatureResult.Success(LocalSignature(generated, signatureObserver))
        } catch (_: Exception) {
            LocalSignatureResult.Failure(LocalSignatureError.SIGNATURE_FAILED)
        } finally {
            contentCopy.fill(0)
            contentDigest?.fill(0)
            certificateBytes?.fill(0)
            certificateHash?.fill(0)
            encodedSignature?.fill(0)
        }
    }

    /** Validation is content/certificate/algorithm/mode bound, not merely a
     * parse-success check. No caller-supplied certificate is trusted implicitly. */
    fun verify(encoded: ByteArray, payload: ByteArray, certificate: X509Certificate,
        algorithm: SigningAlgorithm, detached: Boolean): Boolean = verifyInternal(encoded, payload, certificate, algorithm, detached, null, null)

    internal fun verifyDigest(encoded: ByteArray, digest: ByteArray, certificate: X509Certificate,
        algorithm: SigningAlgorithm): Boolean = digest.size == NativeCadesHistory.digestLength(digestOid(algorithm)) &&
            verifyInternal(encoded, ByteArray(0), certificate, algorithm, true, digest, null)

    fun verifyWithPolicy(encoded: ByteArray, payload: ByteArray, certificate: X509Certificate,
        algorithm: SigningAlgorithm, detached: Boolean, policy: NativeCadesPolicy?): Boolean =
        verifyInternal(encoded, payload, certificate, algorithm, detached, null, policy)

    fun verifyDigestWithPolicy(encoded: ByteArray, digest: ByteArray, certificate: X509Certificate,
        algorithm: SigningAlgorithm, policy: NativeCadesPolicy?): Boolean =
        digest.size == NativeCadesHistory.digestLength(digestOid(algorithm)) &&
            verifyInternal(encoded, ByteArray(0), certificate, algorithm, true, digest, policy)

    private fun verifyInternal(encoded: ByteArray, payload: ByteArray, certificate: X509Certificate,
        algorithm: SigningAlgorithm, detached: Boolean, contentDigest: ByteArray?, policy: NativeCadesPolicy?): Boolean = runCatching {
        if (encoded.isEmpty() || encoded.size > maxOutputBytes || payload.size > maxInputBytes) return false
        val envelope = CMSSignedData(encoded)
        if ((envelope.signedContent == null) != detached) return false
        if (!detached) {
            val content = envelope.signedContent.content as? ByteArray ?: return false
            if (!MessageDigest.isEqual(content, payload)) return false
        }
        val signed = if (contentDigest != null) CMSSignedData(mapOf(digestOid(algorithm) to contentDigest), encoded)
            else if (detached) CMSSignedData(CMSProcessableByteArray(payload), encoded) else envelope
        val signer = signed.signerInfos.signers.singleOrNull() ?: return false
        val expectedDigest = when (algorithm) {
            SigningAlgorithm.SHA1_WITH_RSA -> "1.3.14.3.2.26"
            SigningAlgorithm.SHA256_WITH_RSA -> "2.16.840.1.101.3.4.2.1"
            SigningAlgorithm.SHA384_WITH_RSA -> "2.16.840.1.101.3.4.2.2"
            SigningAlgorithm.SHA512_WITH_RSA -> "2.16.840.1.101.3.4.2.3"
        }
        // BC 1.85 emits digest-specific rsa signature identifiers for SHA-2;
        // rsaEncryption remains valid CMS encoding. Never accept an identifier
        // for another digest merely because the key happens to be RSA.
        val expectedRsaSignature = when (algorithm) {
            SigningAlgorithm.SHA1_WITH_RSA -> "1.2.840.113549.1.1.5"
            SigningAlgorithm.SHA256_WITH_RSA -> "1.2.840.113549.1.1.11"
            SigningAlgorithm.SHA384_WITH_RSA -> "1.2.840.113549.1.1.12"
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
        if (!NativeCadesPolicy.matches(attrs, policy)) return false
        if (!includeSigningTime && attrs.getAll(CMSAttributes.signingTime).size() != 0) return false
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
        internal fun digestOid(algorithm: SigningAlgorithm): String = when (algorithm) {
            SigningAlgorithm.SHA1_WITH_RSA -> "1.3.14.3.2.26"
            SigningAlgorithm.SHA256_WITH_RSA -> "2.16.840.1.101.3.4.2.1"
            SigningAlgorithm.SHA384_WITH_RSA -> "2.16.840.1.101.3.4.2.2"
            SigningAlgorithm.SHA512_WITH_RSA -> "2.16.840.1.101.3.4.2.3"
        }
        const val MAX_INPUT_BYTES = 524_288
        const val MAX_OUTPUT_BYTES = 2_097_152
        private const val RSA = "RSA"
        private const val SHA_256 = "SHA-256"
    }
}

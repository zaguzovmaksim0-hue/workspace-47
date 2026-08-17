package dev.junta.firmamobile.signing

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.Provider
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.time.Clock
import java.util.Date
import java.util.Base64
import java.util.Hashtable
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.DERIA5String
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.DERSet
import org.bouncycastle.asn1.cms.Attribute
import org.bouncycastle.asn1.cms.AttributeTable
import org.bouncycastle.asn1.cms.CMSAttributes
import org.bouncycastle.asn1.cms.ContentInfo
import org.bouncycastle.asn1.cms.SignedData
import org.bouncycastle.asn1.cms.SignerInfo
import org.bouncycastle.asn1.cms.Time
import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers
import org.bouncycastle.asn1.ess.ESSCertIDv2
import org.bouncycastle.asn1.ess.SigningCertificateV2
import org.bouncycastle.asn1.x509.DigestInfo
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.DefaultSignedAttributeTableGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder

/** Local detached CAdES adapter for Aragón SIRAW's observed MiniApplet login contract. */
class LocalCadesDetachedAdapter internal constructor(
    private val clock: Clock = Clock.systemUTC(),
    private val provider: Provider = BouncyCastleProvider(),
) : SigningProtocolAdapter {
    override val id: SigningProtocolId = ID

    override suspend fun prepare(
        request: NormalizedSignRequest,
        certificateChain: List<X509Certificate>,
    ): ProtocolPrepareResult {
        if (!request.matchesContract() || certificateChain.isEmpty() ||
            certificateChain.size > MAX_CERTIFICATES
        ) {
            return ProtocolPrepareResult.Failure(SigningErrorCode.UNSUPPORTED_PROTOCOL)
        }
        return try {
            val material = request.withPayload { payload ->
                MiniAppletPayloadCodec.withDecoded(payload) { challenge, extraProperties ->
                    require(challenge.size == CHALLENGE_BYTES)
                    require(extraProperties == EXPECTED_EXTRA_PROPERTIES)
                    CadesDetachedCodec.createPreSign(challenge, CHALLENGE_BYTES, certificateChain, clock, provider)
                }
            }
            ProtocolPrepareResult.Success(
                PreSignResult(
                    requestOwner = request,
                    bytesToSign = material.signedAttributes,
                    state = CadesPreSignState(
                        placeholderCms = material.placeholderCms,
                        detachedContent = material.detachedContent,
                        signingCertificateFingerprint = material.signingCertificateFingerprint,
                    ),
                ),
            )
        } catch (_: Exception) {
            ProtocolPrepareResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
        }
    }

    override suspend fun complete(
        request: NormalizedSignRequest,
        preSign: PreSignResult,
        localSignature: LocalSignature,
    ): ProtocolCompletionResult {
        if (!request.matchesContract()) {
            return ProtocolCompletionResult.Failure(SigningErrorCode.UNSUPPORTED_PROTOCOL)
        }
        val state = preSign.consumeState(request) as? CadesPreSignState
            ?: return ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
        return state.use { ownedState ->
            try {
                val result = localSignature.withBytes { signature ->
                    CadesDetachedCodec.complete(
                        placeholderCms = ownedState.placeholderCms(),
                        detachedContent = ownedState.detachedContent(),
                        expectedContentBytes = CHALLENGE_BYTES,
                        signingCertificateFingerprint = ownedState.signingCertificateFingerprint(),
                        signatureValue = signature,
                        provider = provider,
                    )
                }
                ProtocolCompletionResult.Success(LocalSignature(result))
            } catch (_: Exception) {
                ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
            }
        }
    }

    private fun NormalizedSignRequest.matchesContract(): Boolean =
        protocolId == ID &&
            context.profileId == PROFILE_ID &&
            context.profileVersion == PROFILE_VERSION &&
            context.origin.serialized == INITIATOR_ORIGIN &&
            format == SigningFormat.CADES &&
            algorithm == SigningAlgorithm.SHA1_WITH_RSA

    companion object {
        val ID = SigningProtocolId("aragon-siraw-local-cades-v1")
        const val PROFILE_ID = "aragon-siraw"
        const val PROFILE_VERSION = 1
        const val INITIATOR_ORIGIN = "https://aplicaciones.aragon.es"
        internal const val EXPECTED_EXTRA_PROPERTIES = "mode=explicit\nfilter=nonexpired"
        internal const val CHALLENGE_BYTES = 20
        private const val MAX_CERTIFICATES = 10
    }
}

internal class CadesPreSignState(
    placeholderCms: ByteArray,
    detachedContent: ByteArray,
    signingCertificateFingerprint: ByteArray,
) : PreSignState {
    private var placeholder = placeholderCms
    private var content = detachedContent
    private var fingerprint = signingCertificateFingerprint
    private var closed = false

    @Synchronized
    fun placeholderCms(): ByteArray = check(!closed).let { placeholder }

    @Synchronized
    fun detachedContent(): ByteArray = check(!closed).let { content }

    @Synchronized
    fun signingCertificateFingerprint(): ByteArray = check(!closed).let { fingerprint }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        placeholder.fill(0)
        content.fill(0)
        fingerprint.fill(0)
        placeholder = ByteArray(0)
        content = ByteArray(0)
        fingerprint = ByteArray(0)
    }
}

internal data class CadesPreSignMaterial(
    val placeholderCms: ByteArray,
    val detachedContent: ByteArray,
    val signedAttributes: ByteArray,
    val signingCertificateFingerprint: ByteArray,
)

internal data class CadesSignaturePolicy(
    val policyIdentifierOid: String,
    val hashAlgorithmOid: String,
    val policyHashBase64: String,
    val qualifierUrl: String,
)

internal object CadesDetachedCodec {
    fun createPreSign(
        content: ByteArray,
        expectedContentBytes: Int,
        certificateChain: List<X509Certificate>,
        clock: Clock,
        provider: Provider = BouncyCastleProvider(),
        signingAlgorithm: SigningAlgorithm = SigningAlgorithm.SHA1_WITH_RSA,
        signaturePolicy: CadesSignaturePolicy? = null,
    ): CadesPreSignMaterial {
        requireContentSize(content, expectedContentBytes)
        require(certificateChain.isNotEmpty())
        val signingCertificate = certificateChain.first()
        val rsaPublicKey = signingCertificate.publicKey as? RSAPublicKey
            ?: error("RSA certificate required")
        val certificateHash = MessageDigest.getInstance(SHA_256).digest(signingCertificate.encoded)
        val contentCopy = content.copyOf()
        val attributes = Hashtable<ASN1ObjectIdentifier, Attribute>().apply {
            put(
                PKCSObjectIdentifiers.id_aa_signingCertificateV2,
                Attribute(
                    PKCSObjectIdentifiers.id_aa_signingCertificateV2,
                    DERSet(SigningCertificateV2(ESSCertIDv2(certificateHash))),
                ),
            )
            put(
                CMSAttributes.signingTime,
                Attribute(CMSAttributes.signingTime, DERSet(Time(Date.from(clock.instant())))),
            )
            signaturePolicy?.let { policy ->
                put(PKCSObjectIdentifiers.id_aa_ets_sigPolicyId, signaturePolicyAttribute(policy))
            }
        }
        val contentSigner = CapturingContentSigner(
            rsaPublicKey.modulus.bitLength(),
            signingAlgorithm.cadesJcaName(),
        )
        return try {
            val digestProvider = JcaDigestCalculatorProviderBuilder().setProvider(provider).build()
            val signerInfo = JcaSignerInfoGeneratorBuilder(digestProvider)
                .setSignedAttributeGenerator(
                    DefaultSignedAttributeTableGenerator(AttributeTable(attributes)),
                )
                .build(contentSigner, X509CertificateHolder(signingCertificate.encoded))
            val generator = CMSSignedDataGenerator().apply {
                addSignerInfoGenerator(signerInfo)
                addCertificates(JcaCertStore(certificateChain))
            }
            val cms = generator.generate(CMSProcessableByteArray(contentCopy), false).encoded
            require(cms.isNotEmpty() && cms.size <= MAX_CMS_BYTES)
            val signedAttributes = contentSigner.signedBytes()
            require(signedAttributes.isNotEmpty() && signedAttributes.size <= MAX_SIGNED_ATTRIBUTES_BYTES)
            CadesPreSignMaterial(
                placeholderCms = cms,
                detachedContent = contentCopy,
                signedAttributes = signedAttributes,
                signingCertificateFingerprint = certificateHash,
            )
        } catch (error: Exception) {
            contentCopy.fill(0)
            certificateHash.fill(0)
            throw error
        } finally {
            contentSigner.close()
        }
    }

    fun complete(
        placeholderCms: ByteArray,
        detachedContent: ByteArray,
        expectedContentBytes: Int,
        signingCertificateFingerprint: ByteArray,
        signatureValue: ByteArray,
        provider: Provider = BouncyCastleProvider(),
        signingAlgorithm: SigningAlgorithm = SigningAlgorithm.SHA1_WITH_RSA,
        signaturePolicy: CadesSignaturePolicy? = null,
    ): ByteArray {
        require(placeholderCms.isNotEmpty() && placeholderCms.size <= MAX_CMS_BYTES)
        requireContentSize(detachedContent, expectedContentBytes)
        require(signingCertificateFingerprint.size == SHA_256_BYTES)
        require(signatureValue.isNotEmpty() && signatureValue.size <= MAX_SIGNATURE_BYTES)

        val contentInfo = ContentInfo.getInstance(ASN1Primitive.fromByteArray(placeholderCms))
        require(contentInfo.contentType == PKCSObjectIdentifiers.signedData)
        val signedData = SignedData.getInstance(contentInfo.content)
        require(signedData.encapContentInfo.content == null)
        require(signedData.signerInfos.size() == 1)
        val oldSigner = SignerInfo.getInstance(signedData.signerInfos.getObjectAt(0))
        val replacement = SignerInfo(
            oldSigner.sid,
            oldSigner.digestAlgorithm,
            oldSigner.authenticatedAttributes,
            oldSigner.digestEncryptionAlgorithm,
            DEROctetString(signatureValue.copyOf()),
            oldSigner.unauthenticatedAttributes,
        )
        val result = ContentInfo(
            contentInfo.contentType,
            SignedData(
                signedData.digestAlgorithms,
                signedData.encapContentInfo,
                signedData.certificates,
                signedData.crLs,
                DERSet(replacement),
            ),
        ).encoded
        require(result.size <= MAX_CMS_BYTES)
        require(
            validate(
                signatureDocument = result,
                detachedContent = detachedContent,
                expectedContentBytes = expectedContentBytes,
                expectedCertificateFingerprint = signingCertificateFingerprint,
                provider = provider,
                signingAlgorithm = signingAlgorithm,
                signaturePolicy = signaturePolicy,
            ),
        )
        return result
    }

    internal fun validate(
        signatureDocument: ByteArray,
        detachedContent: ByteArray,
        expectedContentBytes: Int,
        expectedCertificateFingerprint: ByteArray? = null,
        provider: Provider = BouncyCastleProvider(),
        signingAlgorithm: SigningAlgorithm = SigningAlgorithm.SHA1_WITH_RSA,
        signaturePolicy: CadesSignaturePolicy? = null,
    ): Boolean = runCatching {
        require(signatureDocument.isNotEmpty() && signatureDocument.size <= MAX_CMS_BYTES)
        requireContentSize(detachedContent, expectedContentBytes)
        val cms = CMSSignedData(CMSProcessableByteArray(detachedContent), signatureDocument)
        require(cms.isDetachedSignature)
        val signers = cms.signerInfos.signers
        require(signers.size == 1)
        val signer = signers.single()
        require(signer.digestAlgOID == signingAlgorithm.digestOid())
        require(
            signer.encryptionAlgOID == PKCSObjectIdentifiers.rsaEncryption.id ||
                signer.encryptionAlgOID == signingAlgorithm.signatureOid(),
        )
        require(signer.signedAttributes[PKCSObjectIdentifiers.id_aa_signingCertificateV2] != null)
        require(signer.signedAttributes[CMSAttributes.signingTime] != null)
        if (signaturePolicy == null) {
            require(signer.signedAttributes[PKCSObjectIdentifiers.id_aa_ets_sigPolicyId] == null)
        } else {
            require(matchesSignaturePolicy(
                signer.signedAttributes[PKCSObjectIdentifiers.id_aa_ets_sigPolicyId],
                signaturePolicy,
            ))
        }
        val certificates = cms.certificates.getMatches(null).filter(signer.sid::match)
        require(certificates.size == 1)
        val holder = certificates.single()
        expectedCertificateFingerprint?.let { expected ->
            require(expected.size == SHA_256_BYTES)
            require(MessageDigest.isEqual(MessageDigest.getInstance(SHA_256).digest(holder.encoded), expected))
        }
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(holder.encoded)) as X509Certificate
        require(Signature.getInstance(signingAlgorithm.cadesJcaName()).run {
            initVerify(certificate.publicKey)
            update(signer.encodedSignedAttributes)
            verify(signer.signature)
        })
        require(signer.verify(JcaSimpleSignerInfoVerifierBuilder().setProvider(provider).build(holder)))
        true
    }.getOrDefault(false)

    private fun signaturePolicyAttribute(policy: CadesSignaturePolicy): Attribute {
        require(policy.policyIdentifierOid.matches(Regex("[0-9]+(?:\\.[0-9]+)+")))
        require(policy.hashAlgorithmOid == OIWObjectIdentifiers.idSHA1.id)
        val policyHash = Base64.getDecoder().decode(policy.policyHashBase64)
        require(policyHash.size == 20)
        val digestInfo = DigestInfo(
            AlgorithmIdentifier(ASN1ObjectIdentifier(policy.hashAlgorithmOid), DERNull.INSTANCE),
            policyHash,
        )
        val qualifier = DERSequence(ASN1EncodableVector().apply {
            add(PKCSObjectIdentifiers.id_spq_ets_uri)
            add(DERIA5String(policy.qualifierUrl))
        })
        val signaturePolicyId = DERSequence(ASN1EncodableVector().apply {
            add(ASN1ObjectIdentifier(policy.policyIdentifierOid))
            add(digestInfo.toASN1Primitive())
            add(DERSequence(qualifier))
        })
        policyHash.fill(0)
        return Attribute(PKCSObjectIdentifiers.id_aa_ets_sigPolicyId, DERSet(signaturePolicyId))
    }

    private fun matchesSignaturePolicy(attribute: Attribute?, expected: CadesSignaturePolicy): Boolean =
        runCatching {
            require(attribute != null && attribute.attrValues.size() == 1)
            val sequence = ASN1Sequence.getInstance(attribute.attrValues.getObjectAt(0))
            require(sequence.size() == 3)
            require(ASN1ObjectIdentifier.getInstance(sequence.getObjectAt(0)).id == expected.policyIdentifierOid)
            val digestInfo = DigestInfo.getInstance(sequence.getObjectAt(1))
            require(digestInfo.algorithm.algorithm.id == expected.hashAlgorithmOid)
            require(digestInfo.algorithm.parameters?.toASN1Primitive() == DERNull.INSTANCE)
            val expectedHash = Base64.getDecoder().decode(expected.policyHashBase64)
            try {
                require(MessageDigest.isEqual(digestInfo.digest, expectedHash))
            } finally {
                expectedHash.fill(0)
            }
            val qualifiers = ASN1Sequence.getInstance(sequence.getObjectAt(2))
            require(qualifiers.size() == 1)
            val qualifier = ASN1Sequence.getInstance(qualifiers.getObjectAt(0))
            require(qualifier.size() == 2)
            require(ASN1ObjectIdentifier.getInstance(qualifier.getObjectAt(0)) == PKCSObjectIdentifiers.id_spq_ets_uri)
            require(DERIA5String.getInstance(qualifier.getObjectAt(1)).string == expected.qualifierUrl)
            true
        }.getOrDefault(false)

    private fun requireContentSize(content: ByteArray, expectedContentBytes: Int) {
        require(expectedContentBytes in 1..MAX_CONTENT_BYTES)
        require(content.size == expectedContentBytes)
    }

    private class CapturingContentSigner(
        rsaBits: Int,
        private val jcaSignatureAlgorithm: String,
    ) : ContentSigner, AutoCloseable {
        private val output = ClearingByteArrayOutputStream()
        private var closed = false
        private val placeholder = ByteArray((rsaBits + 7) / 8)

        override fun getAlgorithmIdentifier() =
            DefaultSignatureAlgorithmIdentifierFinder().find(jcaSignatureAlgorithm)

        override fun getOutputStream(): OutputStream = check(!closed).let { output }

        override fun getSignature(): ByteArray = check(!closed).let { placeholder.copyOf() }

        fun signedBytes(): ByteArray = check(!closed).let { output.toByteArray() }

        override fun close() {
            if (closed) return
            closed = true
            output.clear()
            placeholder.fill(0)
        }
    }

    private class ClearingByteArrayOutputStream : ByteArrayOutputStream() {
        fun clear() {
            buf.fill(0)
            reset()
        }
    }

    private const val MAX_CONTENT_BYTES = 524_288
    private const val MAX_CMS_BYTES = 2_097_152
    private const val MAX_SIGNATURE_BYTES = 16_384
    private const val MAX_SIGNED_ATTRIBUTES_BYTES = 65_536
    private const val SHA_256 = "SHA-256"
    private const val SHA_256_BYTES = 32
    private fun SigningAlgorithm.cadesJcaName(): String = when (this) {
        SigningAlgorithm.SHA1_WITH_RSA -> "SHA1withRSA"
        SigningAlgorithm.SHA256_WITH_RSA -> "SHA256withRSA"
        SigningAlgorithm.SHA512_WITH_RSA -> "SHA512withRSA"
    }

    private fun SigningAlgorithm.digestOid(): String = when (this) {
        SigningAlgorithm.SHA1_WITH_RSA -> OID_SHA_1
        SigningAlgorithm.SHA256_WITH_RSA -> OID_SHA_256
        SigningAlgorithm.SHA512_WITH_RSA -> OID_SHA_512
    }

    private fun SigningAlgorithm.signatureOid(): String = when (this) {
        SigningAlgorithm.SHA1_WITH_RSA -> "1.2.840.113549.1.1.5"
        SigningAlgorithm.SHA256_WITH_RSA -> "1.2.840.113549.1.1.11"
        SigningAlgorithm.SHA512_WITH_RSA -> "1.2.840.113549.1.1.13"
    }

    private const val OID_SHA_1 = "1.3.14.3.2.26"
    private const val OID_SHA_256 = "2.16.840.1.101.3.4.2.1"
    private const val OID_SHA_512 = "2.16.840.1.101.3.4.2.3"
}

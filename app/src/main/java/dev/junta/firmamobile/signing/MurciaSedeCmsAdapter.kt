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
import java.util.Hashtable
import org.bouncycastle.asn1.ASN1ObjectIdentifier
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
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
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

/** Dedicated QA-only local attached CMS/PKCS#7 adapter for Murcia Sede (CARM) signing contract. */
class MurciaSedeCmsAdapter internal constructor(
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
                MiniAppletPayloadCodec.withDecoded(payload) { content, extraProperties ->
                    require(content.isNotEmpty() && content.size <= MAX_PAYLOAD_BYTES)
                    require(extraProperties == EXPECTED_EXTRA_PROPERTIES)
                    MurciaCmsCodec.createPreSign(
                        content = content,
                        certificateChain = certificateChain,
                        clock = clock,
                        provider = provider,
                    )
                }
            }
            ProtocolPrepareResult.Success(
                PreSignResult(
                    requestOwner = request,
                    bytesToSign = material.signedAttributes,
                    state = MurciaCmsPreSignState(
                        placeholderCms = material.placeholderCms,
                        content = material.content,
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
        if (!request.matchesPayloadContract()) {
            return ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
        }
        val state = preSign.consumeState(request) as? MurciaCmsPreSignState
            ?: return ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
        return state.use { ownedState ->
            try {
                val content = ownedState.content()
                val result = localSignature.withBytes { signature ->
                    MurciaCmsCodec.complete(
                        placeholderCms = ownedState.placeholderCms(),
                        content = content,
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
            safeDescription == SAFE_DESCRIPTION &&
            algorithm == SigningAlgorithm.SHA256_WITH_RSA &&
            format == SigningFormat.CMS

    private fun NormalizedSignRequest.matchesPayloadContract(): Boolean = runCatching {
        withPayload { payload ->
            MiniAppletPayloadCodec.withDecoded(payload) { content, extraProperties ->
                content.isNotEmpty() && content.size <= MAX_PAYLOAD_BYTES &&
                    extraProperties == EXPECTED_EXTRA_PROPERTIES
            }
        }
    }.getOrDefault(false)

    companion object {
        val ID = SigningProtocolId("murcia-sede-local-cms-v1")
        const val PROFILE_ID = "murcia-sede"
        const val PROFILE_VERSION = 1
        const val INITIATOR_ORIGIN = "https://sede.carm.es"
        const val SAFE_DESCRIPTION = "Prueba pública de firma AutoFirma de la CARM"
        const val EXPECTED_EXTRA_PROPERTIES = "filters=nonexpired:\nmode=implicit"
        const val MAX_PAYLOAD_BYTES = 524_288
        private const val MAX_CERTIFICATES = 10
    }
}

internal class MurciaCmsPreSignState(
    placeholderCms: ByteArray,
    content: ByteArray,
    signingCertificateFingerprint: ByteArray,
) : PreSignState {
    private var placeholder = placeholderCms
    private var ownedContent = content
    private var fingerprint = signingCertificateFingerprint
    private var closed = false

    @Synchronized
    fun placeholderCms(): ByteArray = check(!closed).let { placeholder }

    @Synchronized
    fun content(): ByteArray = check(!closed).let { ownedContent }

    @Synchronized
    fun signingCertificateFingerprint(): ByteArray = check(!closed).let { fingerprint }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        placeholder.fill(0)
        ownedContent.fill(0)
        fingerprint.fill(0)
        placeholder = ByteArray(0)
        ownedContent = ByteArray(0)
        fingerprint = ByteArray(0)
    }
}

internal data class MurciaCmsPreSignMaterial(
    val placeholderCms: ByteArray,
    val content: ByteArray,
    val signedAttributes: ByteArray,
    val signingCertificateFingerprint: ByteArray,
)

internal object MurciaCmsCodec {
    fun createPreSign(
        content: ByteArray,
        certificateChain: List<X509Certificate>,
        clock: Clock,
        provider: Provider = BouncyCastleProvider(),
    ): MurciaCmsPreSignMaterial {
        require(content.isNotEmpty() && content.size <= MAX_CONTENT_BYTES)
        require(certificateChain.isNotEmpty())
        val signingCertificate = certificateChain.first()
        signingCertificate.checkValidity(Date.from(clock.instant()))
        val rsaPublicKey = signingCertificate.publicKey as? RSAPublicKey
            ?: error("RSA certificate required")
        val certificateHash = MessageDigest.getInstance(SHA_256).digest(signingCertificate.encoded)
        val contentCopy = content.copyOf()
        val attributes = Hashtable<ASN1ObjectIdentifier, Attribute>().apply {
            put(
                CMSAttributes.signingTime,
                Attribute(CMSAttributes.signingTime, DERSet(Time(Date.from(clock.instant())))),
            )
        }
        val contentSigner = CapturingContentSigner(
            rsaPublicKey.modulus.bitLength(),
            "SHA256withRSA",
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
            val cms = generator.generate(CMSProcessableByteArray(contentCopy), true).encoded
            require(cms.isNotEmpty() && cms.size <= MAX_CMS_BYTES)
            val signedAttributes = contentSigner.signedBytes()
            require(signedAttributes.isNotEmpty() && signedAttributes.size <= MAX_SIGNED_ATTRIBUTES_BYTES)
            MurciaCmsPreSignMaterial(
                placeholderCms = cms,
                content = contentCopy,
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
        content: ByteArray,
        signingCertificateFingerprint: ByteArray,
        signatureValue: ByteArray,
        provider: Provider = BouncyCastleProvider(),
    ): ByteArray {
        require(placeholderCms.isNotEmpty() && placeholderCms.size <= MAX_CMS_BYTES)
        require(content.isNotEmpty() && content.size <= MAX_CONTENT_BYTES)
        require(signingCertificateFingerprint.size == SHA_256_BYTES)
        require(signatureValue.isNotEmpty() && signatureValue.size <= MAX_SIGNATURE_BYTES)

        val contentInfo = ContentInfo.getInstance(ASN1Primitive.fromByteArray(placeholderCms))
        require(contentInfo.contentType == PKCSObjectIdentifiers.signedData)
        val signedData = SignedData.getInstance(contentInfo.content)
        require(signedData.encapContentInfo.content != null)
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
                expectedContent = content,
                expectedCertificateFingerprint = signingCertificateFingerprint,
                provider = provider,
            ),
        )
        return result
    }

    fun validate(
        signatureDocument: ByteArray,
        expectedContent: ByteArray,
        expectedCertificateFingerprint: ByteArray? = null,
        provider: Provider = BouncyCastleProvider(),
    ): Boolean = runCatching {
        require(signatureDocument.isNotEmpty() && signatureDocument.size <= MAX_CMS_BYTES)
        require(expectedContent.isNotEmpty() && expectedContent.size <= MAX_CONTENT_BYTES)
        val cms = CMSSignedData(signatureDocument)
        require(!cms.isDetachedSignature)
        require(cms.signedContent != null)
        val stream = ByteArrayOutputStream()
        cms.signedContent.write(stream)
        val extractedContent = stream.toByteArray()
        require(extractedContent.contentEquals(expectedContent))
        val signers = cms.signerInfos.signers
        require(signers.size == 1)
        val signer = signers.single()
        require(signer.digestAlgOID == OID_SHA_256)
        require(
            signer.encryptionAlgOID == PKCSObjectIdentifiers.rsaEncryption.id ||
                signer.encryptionAlgOID == OID_SHA256_RSA,
        )
        require(signer.signedAttributes[PKCSObjectIdentifiers.id_aa_signingCertificateV2] == null)
        require(signer.signedAttributes[CMSAttributes.signingTime] != null)
        val certificates = cms.certificates.getMatches(null).filter(signer.sid::match)
        require(certificates.size == 1)
        val holder = certificates.single()
        expectedCertificateFingerprint?.let { expected ->
            require(expected.size == SHA_256_BYTES)
            require(MessageDigest.isEqual(MessageDigest.getInstance(SHA_256).digest(holder.encoded), expected))
        }
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(holder.encoded)) as X509Certificate
        require(Signature.getInstance("SHA256withRSA").run {
            initVerify(certificate.publicKey)
            update(signer.encodedSignedAttributes)
            verify(signer.signature)
        })
        require(signer.verify(JcaSimpleSignerInfoVerifierBuilder().setProvider(provider).build(holder)))
        true
    }.getOrDefault(false)

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
    private const val OID_SHA_256 = "2.16.840.1.101.3.4.2.1"
    private const val OID_SHA256_RSA = "1.2.840.113549.1.1.11"
}

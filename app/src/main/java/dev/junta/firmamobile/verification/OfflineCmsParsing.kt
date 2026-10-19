package dev.junta.firmamobile.verification

import java.math.BigInteger
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1Encoding
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.ASN1Set
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers
import org.bouncycastle.asn1.cms.ContentInfo
import org.bouncycastle.asn1.cms.SignedData
import org.bouncycastle.asn1.cms.SignerInfo
import org.bouncycastle.cert.X509CertificateHolder

private const val OFFLINE_CMS_MAX_SIGNERS = 8
private const val OFFLINE_CMS_MAX_CERTIFICATES = 32
private const val OFFLINE_CMS_VERSION_ISSUER_SERIAL = 1L
private const val OFFLINE_CMS_VERSION_SUBJECT_KEY_ID = 3L

internal data class OfflineCmsParsedContainer(
    val attachedContent: ByteArray?,
    val certificates: List<X509CertificateHolder>,
    val signerCount: Int,
)

internal object OfflineCmsParsing {
    fun parse(encoded: ByteArray): OfflineCmsParsedContainer {
        require(OfflineCmsDer.hasSafeNesting(encoded)) {
            "Invalid CMS nesting"
        }
        val signedData = readSignedData(encoded)
        val signerInfos = signedData.getSignerInfos()
        val certificateSet = signedData.getCertificates()
        validateStructure(signedData, signerInfos, certificateSet)

        var attachedContent: ByteArray? = null
        var ownershipTransferred = false
        try {
            val encapsulated = signedData.getEncapContentInfo()
            if (encapsulated.getContentType() != CMSObjectIdentifiers.data) {
                throw OfflineCmsUnsupportedProfileException()
            }
            encapsulated.getContent()?.let {
                attachedContent = ASN1OctetString.getInstance(it).getOctets()
                if (attachedContent!!.size > OFFLINE_CMS_MAX_CONTENT_BYTES) {
                    throw OfflineCmsLimitException()
                }
            }

            requireTypedDer(encoded, signedData, signerInfos)
            validateVersions(signedData, signerInfos)
            OfflineCmsAlgorithmPolicy.validateSignedData(signedData)
            val certificates = readCertificates(certificateSet)
            val parsed = OfflineCmsParsedContainer(attachedContent, certificates, signerInfos.size())
            ownershipTransferred = true
            return parsed
        } finally {
            if (!ownershipTransferred) {
                attachedContent?.fill(0)
            }
        }
    }

    private fun validateStructure(
        signedData: SignedData,
        signerInfos: ASN1Set,
        certificateSet: ASN1Set?,
    ) {
        if ((certificateSet?.size() ?: 0) > OFFLINE_CMS_MAX_CERTIFICATES ||
            signerInfos.size() > OFFLINE_CMS_MAX_SIGNERS
        ) {
            throw OfflineCmsLimitException()
        }
        require(signerInfos.size() != 0) {
            "Missing CMS signers"
        }
        if (signedData.getCRLs() != null) {
            throw OfflineCmsUnsupportedProfileException()
        }
    }

    private fun readSignedData(encoded: ByteArray): SignedData {
        val root = readCanonicalRoot(encoded)
        val contentInfo = ContentInfo.getInstance(root)
        if (contentInfo.getContentType() != CMSObjectIdentifiers.signedData) {
            throw OfflineCmsUnsupportedProfileException()
        }
        return SignedData.getInstance(contentInfo.getContent())
    }

    private fun validateVersions(signedData: SignedData, signerInfos: ASN1Set) {
        var hasSubjectKeyIdentifier = false
        for (index in 0 until signerInfos.size()) {
            val signer = SignerInfo.getInstance(signerInfos.getObjectAt(index))
            val usesSubjectKeyIdentifier = signer.getSID().isTagged()
            val expected = if (usesSubjectKeyIdentifier) {
                OFFLINE_CMS_VERSION_SUBJECT_KEY_ID
            } else {
                OFFLINE_CMS_VERSION_ISSUER_SERIAL
            }
            require(signer.getVersion().getValue() == BigInteger.valueOf(expected)) {
                "Invalid CMS signer version"
            }
            hasSubjectKeyIdentifier = hasSubjectKeyIdentifier || usesSubjectKeyIdentifier
        }

        val expected = if (hasSubjectKeyIdentifier) {
            OFFLINE_CMS_VERSION_SUBJECT_KEY_ID
        } else {
            OFFLINE_CMS_VERSION_ISSUER_SERIAL
        }
        require(signedData.getVersion().getValue() == BigInteger.valueOf(expected)) {
            "Invalid CMS version"
        }
    }

    private fun readCanonicalRoot(encoded: ByteArray): ASN1Primitive {
        val root = ASN1InputStream(encoded).use { input ->
            val value = requireNotNull(input.readObject()) { "Missing CMS root" }
            require(input.readObject() == null) { "Trailing CMS data" }
            value
        }
        requireDerEncoding(root, encoded)
        return root
    }

    private fun requireTypedDer(
        encoded: ByteArray,
        signedData: SignedData,
        signerInfos: ASN1Set,
    ) {
        requireDerEncoding(ContentInfo(CMSObjectIdentifiers.signedData, signedData), encoded)
        for (index in 0 until signerInfos.size()) {
            requireSignerDer(signerInfos.getObjectAt(index))
        }
    }

    private fun requireSignerDer(rawSigner: ASN1Encodable) {
        val rawDer = rawSigner.toASN1Primitive().getEncoded(ASN1Encoding.DER)
        try {
            val typedDer = SignerInfo.getInstance(rawSigner).getEncoded(ASN1Encoding.DER)
            try {
                require(rawDer.contentEquals(typedDer)) { "Noncanonical CMS signer encoding" }
            } finally {
                typedDer.fill(0)
            }
        } finally {
            rawDer.fill(0)
        }
    }

    private fun requireDerEncoding(value: ASN1Encodable, source: ByteArray) {
        val der = value.toASN1Primitive().getEncoded(ASN1Encoding.DER)
        try {
            require(der.contentEquals(source)) { "Noncanonical CMS encoding" }
        } finally {
            der.fill(0)
        }
    }

    private fun readCertificates(certificateSet: ASN1Set?): List<X509CertificateHolder> {
        if (certificateSet == null) {
            return emptyList()
        }

        val certificates = mutableListOf<X509CertificateHolder>()
        for (index in 0 until certificateSet.size()) {
            val choice = certificateSet.getObjectAt(index).toASN1Primitive()
            if (choice !is ASN1Sequence) {
                throw OfflineCmsUnsupportedProfileException()
            }

            val encoded = choice.getEncoded(ASN1Encoding.DER)
            try {
                certificates.add(X509CertificateHolder(encoded))
            } finally {
                encoded.fill(0)
            }
        }
        return certificates
    }
}

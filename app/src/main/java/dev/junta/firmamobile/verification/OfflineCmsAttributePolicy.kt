package dev.junta.firmamobile.verification

import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.ASN1Set
import org.bouncycastle.asn1.cms.Attribute
import org.bouncycastle.asn1.cms.CMSAlgorithmProtection
import org.bouncycastle.asn1.cms.CMSAttributes
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cms.SignerInformation

internal object OfflineCmsAttributePolicy {
    fun validate(
        signer: SignerInformation,
        certificate: X509CertificateHolder,
        declaredDigestOids: Set<String>,
    ) {
        val info = signer.toASN1Structure()
        if (info.getUnauthenticatedAttributes() != null) {
            throw OfflineCmsUnsupportedProfileException()
        }
        info.getAuthenticatedAttributes()?.let {
            validateSignedAttributes(it, signer, certificate, declaredDigestOids)
        }
    }

    private fun validateSignedAttributes(
        attributes: ASN1Set,
        signer: SignerInformation,
        certificate: X509CertificateHolder,
        declaredDigestOids: Set<String>,
    ) {
        val seen = mutableSetOf<ASN1ObjectIdentifier>()
        for (index in 0 until attributes.size()) {
            val fields = ASN1Sequence.getInstance(attributes.getObjectAt(index))
            require(fields.size() == 2) { "Invalid CMS attribute structure" }
            val attribute = Attribute.getInstance(fields)
            require(seen.add(attribute.getAttrType())) { "Duplicate CMS signed attribute" }
            validateAttribute(attribute, signer, certificate, declaredDigestOids)
        }
        require(seen.contains(CMSAttributes.contentType) && seen.contains(CMSAttributes.messageDigest)) {
            "Missing CMS signed attributes"
        }
    }

    private fun validateAttribute(
        attribute: Attribute,
        signer: SignerInformation,
        certificate: X509CertificateHolder,
        declaredDigestOids: Set<String>,
    ) {
        when (attribute.getAttrType()) {
            CMSAttributes.contentType -> {
                val contentType = ASN1ObjectIdentifier.getInstance(singleValue(attribute))
                requireSupported(contentType == CMSObjectIdentifiers.data)
            }
            CMSAttributes.messageDigest -> ASN1OctetString.getInstance(singleValue(attribute))
            PKCSObjectIdentifiers.id_aa_signingCertificateV2 -> OfflineCmsSigningCertificatePolicy.validate(
                singleValue(attribute),
                certificate,
                declaredDigestOids,
            )
            CMSAttributes.signingTime ->
                OfflineCmsSigningTimePolicy.validate(singleValue(attribute))
            CMSAttributes.cmsAlgorithmProtect -> {
                // RFC 6211: must name exactly the SignerInfo algorithms (BC verify re-checks this too).
                val protection = CMSAlgorithmProtection.getInstance(singleValue(attribute))
                requireSupported(
                    protection.digestAlgorithm.algorithm.id == signer.getDigestAlgOID() &&
                        protection.signatureAlgorithm?.algorithm?.id == signer.getEncryptionAlgOID(),
                )
            }
            else -> throw OfflineCmsUnsupportedProfileException()
        }
    }

    private fun requireSupported(supported: Boolean) {
        if (!supported) throw OfflineCmsUnsupportedProfileException()
    }

    private fun singleValue(attribute: Attribute): ASN1Encodable {
        val values = attribute.getAttrValues()
        require(values.size() == 1) {
            "Invalid CMS attribute values"
        }
        return values.getObjectAt(0)
    }
}

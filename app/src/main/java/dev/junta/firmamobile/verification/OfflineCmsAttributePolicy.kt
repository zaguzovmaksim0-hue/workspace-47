package dev.junta.firmamobile.verification

import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.cms.Attribute
import org.bouncycastle.asn1.cms.AttributeTable
import org.bouncycastle.asn1.cms.CMSAttributes
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers
import org.bouncycastle.cms.SignerInformation

internal object OfflineCmsAttributePolicy {
    fun validate(signer: SignerInformation) {
        if (signer.getUnsignedAttributes() != null) {
            throw OfflineCmsUnsupportedProfileException()
        }
        signer.getSignedAttributes()?.let { validateSignedAttributes(it) }
    }

    private fun validateSignedAttributes(attributes: AttributeTable) {
        var contentTypeCount = 0
        var messageDigestCount = 0
        val values = attributes.toASN1EncodableVector()

        for (index in 0 until values.size()) {
            val attribute = Attribute.getInstance(values.get(index))
            when (attribute.getAttrType()) {
                CMSAttributes.contentType -> {
                    require(++contentTypeCount <= 1) {
                        "Duplicate CMS content type"
                    }
                    val contentType = ASN1ObjectIdentifier.getInstance(singleValue(attribute))
                    if (contentType != CMSObjectIdentifiers.data) {
                        throw OfflineCmsUnsupportedProfileException()
                    }
                }
                CMSAttributes.messageDigest -> {
                    require(++messageDigestCount <= 1) {
                        "Duplicate CMS message digest"
                    }
                    ASN1OctetString.getInstance(singleValue(attribute))
                }
                else -> throw OfflineCmsUnsupportedProfileException()
            }
        }

        require(contentTypeCount == 1 && messageDigestCount == 1) {
            "Missing CMS signed attributes"
        }
    }

    private fun singleValue(attribute: Attribute): ASN1Encodable {
        val values = attribute.getAttrValues()
        require(values.size() == 1) {
            "Invalid CMS attribute values"
        }
        return values.getObjectAt(0)
    }
}

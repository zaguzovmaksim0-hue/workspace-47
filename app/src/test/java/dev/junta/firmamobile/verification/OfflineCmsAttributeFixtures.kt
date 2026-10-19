package dev.junta.firmamobile.verification

import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.ASN1Set
import org.bouncycastle.asn1.DERSet
import org.bouncycastle.asn1.cms.Attribute
import org.bouncycastle.asn1.cms.SignerInfo

internal object OfflineCmsAttributeFixtures {
    fun appendSignedAttribute(cms: ByteArray, attribute: Attribute): ByteArray =
        OfflineCmsSignerInfoFixtures.changeSigner(cms, SIGNER_INDEX) { signer ->
            val attributes = requiredAttributes(signer)
            val vector = copyAttributes(attributes)
            vector.add(attribute)
            copySignerAttributes(signer, signedAttributes = DERSet(vector))
        }

    fun duplicateSignedAttribute(cms: ByteArray, oid: ASN1ObjectIdentifier): ByteArray {
        val attributes = requiredAttributes(OfflineCmsSignerInfoFixtures.signerInfos(cms).first())
        val duplicate = (0 until attributes.size())
            .map { Attribute.getInstance(attributes.getObjectAt(it)) }
            .first { it.getAttrType() == oid }
        return appendSignedAttribute(cms, duplicate)
    }

    fun replaceSignedAttribute(
        cms: ByteArray,
        oid: ASN1ObjectIdentifier,
        replacement: Attribute,
    ): ByteArray = OfflineCmsSignerInfoFixtures.changeSigner(cms, SIGNER_INDEX) { signer ->
        val attributes = requiredAttributes(signer)
        val vector = ASN1EncodableVector()
        var replaced = false
        for (index in 0 until attributes.size()) {
            val current = attributes.getObjectAt(index)
            if (!replaced && Attribute.getInstance(current).getAttrType() == oid) {
                vector.add(replacement)
                replaced = true
            } else {
                vector.add(current)
            }
        }
        check(replaced) { "CMS fixture does not contain signed attribute $oid" }
        copySignerAttributes(signer, signedAttributes = DERSet(vector))
    }

    fun removeSignedAttribute(cms: ByteArray, oid: ASN1ObjectIdentifier): ByteArray =
        OfflineCmsSignerInfoFixtures.changeSigner(cms, SIGNER_INDEX) { signer ->
            val attributes = requiredAttributes(signer)
            val vector = ASN1EncodableVector()
            var removed = false
            for (index in 0 until attributes.size()) {
                val current = attributes.getObjectAt(index)
                if (Attribute.getInstance(current).getAttrType() == oid) {
                    removed = true
                } else {
                    vector.add(current)
                }
            }
            check(removed) { "CMS fixture does not contain signed attribute $oid" }
            copySignerAttributes(signer, signedAttributes = DERSet(vector))
        }

    fun appendUnsignedAttribute(cms: ByteArray, attribute: Attribute): ByteArray =
        OfflineCmsSignerInfoFixtures.changeSigner(cms, SIGNER_INDEX) { signer ->
            val vector = ASN1EncodableVector()
            signer.getUnauthenticatedAttributes()?.let { attributes ->
                for (index in 0 until attributes.size()) {
                    vector.add(attributes.getObjectAt(index))
                }
            }
            vector.add(attribute)
            copySignerAttributes(signer, unsignedAttributes = DERSet(vector))
        }

    private fun requiredAttributes(signer: SignerInfo): ASN1Set =
        signer.getAuthenticatedAttributes() ?: error("CMS fixture has no signed attributes")

    private fun copyAttributes(attributes: ASN1Set): ASN1EncodableVector =
        ASN1EncodableVector().also { vector ->
            for (index in 0 until attributes.size()) {
                vector.add(attributes.getObjectAt(index))
            }
        }

    private fun copySignerAttributes(
        signer: SignerInfo,
        signedAttributes: ASN1Set? = signer.getAuthenticatedAttributes(),
        unsignedAttributes: ASN1Set? = signer.getUnauthenticatedAttributes(),
    ): SignerInfo = SignerInfo(
        signer.getSID(),
        signer.getDigestAlgorithm(),
        signedAttributes,
        signer.getDigestEncryptionAlgorithm(),
        signer.getEncryptedDigest(),
        unsignedAttributes,
    )

    private const val SIGNER_INDEX = 0
}

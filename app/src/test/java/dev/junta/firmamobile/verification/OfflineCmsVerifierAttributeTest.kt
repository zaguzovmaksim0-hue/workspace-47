package dev.junta.firmamobile.verification

import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.DERSet
import org.bouncycastle.asn1.cms.Attribute
import org.bouncycastle.asn1.cms.CMSAttributes
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers
import org.junit.Test

class OfflineCmsVerifierAttributeTest {
    @Test
    fun rejectsDuplicateAndMissingRequiredSignedAttributes() {
        val cms = signed()
        val duplicateContentType =
            OfflineCmsAttributeFixtures.duplicateSignedAttribute(cms, CMSAttributes.contentType)
        val duplicateDigest =
            OfflineCmsAttributeFixtures.duplicateSignedAttribute(cms, CMSAttributes.messageDigest)
        val missingContentType =
            OfflineCmsAttributeFixtures.removeSignedAttribute(cms, CMSAttributes.contentType)
        val missingDigest =
            OfflineCmsAttributeFixtures.removeSignedAttribute(cms, CMSAttributes.messageDigest)

        OfflineCmsAssertions.assertRejected(OfflineCmsStatus.INVALID, duplicateContentType)
        OfflineCmsAssertions.assertRejected(OfflineCmsStatus.INVALID, duplicateDigest)
        OfflineCmsAssertions.assertRejected(OfflineCmsStatus.INVALID, missingContentType)
        OfflineCmsAssertions.assertRejected(OfflineCmsStatus.INVALID, missingDigest)
    }

    @Test
    fun rejectsNonSingletonSignedAttributeValues() {
        val cms = signed()
        val contentTypes = ASN1EncodableVector().apply {
            add(CMSObjectIdentifiers.data)
            add(CMSObjectIdentifiers.data)
        }
        val digests = ASN1EncodableVector().apply {
            add(DEROctetString(ByteArray(SHA256_DIGEST_BYTES)))
            add(DEROctetString(ByteArray(SHA256_DIGEST_BYTES)))
        }
        val emptyContentType = Attribute(
            CMSAttributes.contentType,
            DERSet(ASN1EncodableVector()),
        )
        val emptyDigest = Attribute(
            CMSAttributes.messageDigest,
            DERSet(ASN1EncodableVector()),
        )
        val multipleContentTypes = Attribute(CMSAttributes.contentType, DERSet(contentTypes))
        val multipleDigests = Attribute(CMSAttributes.messageDigest, DERSet(digests))

        listOf(
            emptyContentType,
            emptyDigest,
            multipleContentTypes,
            multipleDigests,
        ).forEach { attribute ->
            val oid = attribute.getAttrType()
            val altered = OfflineCmsAttributeFixtures.replaceSignedAttribute(cms, oid, attribute)
            OfflineCmsAssertions.assertRejected(OfflineCmsStatus.INVALID, altered)
        }
    }

    @Test
    fun rejectsIncorrectSignedContentTypeAndDigest() {
        val cms = signed()
        val wrongContentType = Attribute(
            CMSAttributes.contentType,
            DERSet(ASN1ObjectIdentifier(NON_DATA_OID)),
        )
        val wrongDigest = Attribute(
            CMSAttributes.messageDigest,
            DERSet(DEROctetString(ByteArray(SHA256_DIGEST_BYTES))),
        )
        val alteredContentType = OfflineCmsAttributeFixtures.replaceSignedAttribute(
            cms,
            CMSAttributes.contentType,
            wrongContentType,
        )
        val alteredDigest = OfflineCmsAttributeFixtures.replaceSignedAttribute(
            cms,
            CMSAttributes.messageDigest,
            wrongDigest,
        )

        OfflineCmsAssertions.assertNotVerified(alteredContentType)
        OfflineCmsAssertions.assertRejected(OfflineCmsStatus.INVALID, alteredDigest)
    }

    @Test
    fun verifiesDirectSignatureWithoutSignedAttributes() {
        val signer = OfflineCmsFixtures.signer()
        val cms = OfflineCmsFixtures.signed(
            CONTENT,
            listOf(signer),
            OfflineCmsOptions(directSignature = true),
        )

        OfflineCmsAssertions.assertVerified(cms)
    }

    private fun signed(): ByteArray =
        OfflineCmsFixtures.signed(CONTENT, listOf(OfflineCmsFixtures.signer()))

    private companion object {
        val CONTENT = "signed attribute profile payload".toByteArray(Charsets.UTF_8)
        const val NON_DATA_OID = "1.2.3.4.5"
        const val SHA256_DIGEST_BYTES = 32
    }
}

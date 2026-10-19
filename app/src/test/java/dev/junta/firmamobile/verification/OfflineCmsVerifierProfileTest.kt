package dev.junta.firmamobile.verification

import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.DERSet
import org.bouncycastle.asn1.cms.Attribute
import org.bouncycastle.asn1.cms.CMSAttributes
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers
import org.bouncycastle.asn1.cms.ContentInfo
import org.junit.Assert.assertFalse
import org.junit.Test

class OfflineCmsVerifierProfileTest {
    @Test
    fun verifiesSupportedRsaAndEcdsaProfiles() {
        val profiles = listOf(
            Profile("SHA256withRSA", "RSA"),
            Profile("SHA384withRSA", "RSA"),
            Profile("SHA512withRSA", "RSA"),
            Profile("SHA256withECDSA", "EC", "secp256r1"),
            Profile("SHA384withECDSA", "EC", "secp384r1"),
        )

        profiles.forEach { profile ->
            val signer = OfflineCmsFixtures.signer(
                signatureAlgorithm = profile.signatureAlgorithm,
                keyAlgorithm = profile.keyAlgorithm,
                curve = profile.curve,
            )
            val cms = OfflineCmsFixtures.signed(CONTENT, listOf(signer))

            OfflineCmsAssertions.assertVerified(cms)
        }
    }

    @Test
    fun rejectsUnsupportedDigestPaddingAndCurveProfiles() {
        val profiles = listOf(
            Profile("SHA1withRSA", "RSA"),
            Profile("MD5withRSA", "RSA"),
            Profile("SHA256withRSAandMGF1", "RSA"),
            Profile("SHA256withECDSA", "EC", "secp256k1"),
            Profile("SHA384withECDSA", "EC", "secp256r1"),
            Profile("SHA256withECDSA", "EC", "secp384r1"),
        )

        profiles.forEach { profile ->
            val signer = OfflineCmsFixtures.signer(
                signatureAlgorithm = profile.signatureAlgorithm,
                keyAlgorithm = profile.keyAlgorithm,
                curve = profile.curve,
            )
            val cms = OfflineCmsFixtures.signed(CONTENT, listOf(signer))

            OfflineCmsAssertions.assertRejected(OfflineCmsStatus.UNSUPPORTED, cms)
        }
    }

    @Test
    fun rejectsCrlsUnsignedAttributesCountersignaturesAndTimestamps() {
        val signer = OfflineCmsFixtures.signer()
        val cms = OfflineCmsFixtures.signed(CONTENT, listOf(signer))
        val signerInfo = OfflineCmsSignerInfoFixtures.signerInfos(cms).single()
        val withCrl = OfflineCmsFixtures.signed(
            CONTENT,
            listOf(signer),
            OfflineCmsOptions(crls = listOf(OfflineCmsFixtures.crl(signer))),
        )
        val counterSignature = Attribute(
            CMSAttributes.counterSignature,
            DERSet(signerInfo),
        )
        val timestamp = Attribute(
            ASN1ObjectIdentifier(TIMESTAMP_ATTRIBUTE_OID),
            DERSet(
                ContentInfo(
                    CMSObjectIdentifiers.data,
                    DEROctetString(byteArrayOf(TEST_BYTE_VALUE.toByte())),
                ),
            ),
        )
        val unsignedAttribute = Attribute(
            ASN1ObjectIdentifier(UNKNOWN_ATTRIBUTE_OID),
            DERSet(DEROctetString(byteArrayOf(TEST_BYTE_VALUE.toByte()))),
        )

        OfflineCmsAssertions.assertRejected(OfflineCmsStatus.UNSUPPORTED, withCrl)
        listOf(counterSignature, timestamp, unsignedAttribute).forEach { attribute ->
            val altered = OfflineCmsAttributeFixtures.appendUnsignedAttribute(cms, attribute)
            OfflineCmsAssertions.assertRejected(OfflineCmsStatus.UNSUPPORTED, altered)
        }
    }

    @Test
    fun rejectsNonDataContentType() {
        val signer = OfflineCmsFixtures.signer()
        val cms = OfflineCmsFixtures.signed(
            CONTENT,
            listOf(signer),
            OfflineCmsOptions(contentType = ASN1ObjectIdentifier(NON_DATA_OID)),
        )

        OfflineCmsAssertions.assertRejected(OfflineCmsStatus.UNSUPPORTED, cms)
    }

    @Test
    fun rejectsMismatchedSignedDataAndSignerVersions() {
        val cms = OfflineCmsFixtures.signed(CONTENT, listOf(OfflineCmsFixtures.signer()))

        OfflineCmsAssertions.assertRejected(
            OfflineCmsStatus.INVALID,
            OfflineCmsVersionFixtures.withSignedDataVersion(cms, INVALID_SIGNED_DATA_VERSION),
        )
        OfflineCmsAssertions.assertRejected(
            OfflineCmsStatus.INVALID,
            OfflineCmsVersionFixtures.withSignerVersion(cms, SKI_SIGNER_VERSION),
        )
    }

    @Test
    fun rejectsNonDerMalformedTrailingAndDeeplyNestedInput() {
        val cms = OfflineCmsFixtures.signed(CONTENT, listOf(OfflineCmsFixtures.signer()))
        val ber = indefiniteOuterLength(cms)
        var deepValue: ASN1Encodable = ASN1Integer(DEEP_NESTED_VALUE)
        repeat(DEEP_NESTING_LEVELS) { deepValue = DERSequence(deepValue) }
        val deeplyNested = deepValue.toASN1Primitive().getEncoded("DER")

        assertFalse(cms.contentEquals(ber))
        OfflineCmsAssertions.assertRejected(OfflineCmsStatus.INVALID, ber)
        OfflineCmsAssertions.assertRejected(OfflineCmsStatus.INVALID, cms + byteArrayOf(END_OF_CONTENT.toByte()))
        OfflineCmsAssertions.assertRejected(
            OfflineCmsStatus.INVALID,
            cms.copyOf(cms.size - TRUNCATED_BYTES),
        )
        OfflineCmsAssertions.assertRejected(OfflineCmsStatus.INVALID, byteArrayOf())
        OfflineCmsAssertions.assertNotVerified(deeplyNested)
    }

    private fun indefiniteOuterLength(der: ByteArray): ByteArray {
        val firstLength = der[OUTER_LENGTH_INDEX].toInt() and BYTE_MASK
        val contentOffset = if (firstLength < SHORT_LENGTH_LIMIT) {
            OUTER_SEQUENCE_LENGTH_BYTES
        } else {
            OUTER_SEQUENCE_LENGTH_BYTES + (firstLength and LENGTH_OCTET_MASK)
        }
        return byteArrayOf(der[OUTER_TAG_INDEX], INDEFINITE_LENGTH.toByte()) +
            der.copyOfRange(contentOffset, der.size) +
            byteArrayOf(END_OF_CONTENT.toByte(), END_OF_CONTENT.toByte())
    }

    private data class Profile(
        val signatureAlgorithm: String,
        val keyAlgorithm: String,
        val curve: String? = null,
    )

    private companion object {
        val CONTENT = "profile acceptance payload".toByteArray(Charsets.UTF_8)
        const val NON_DATA_OID = "1.2.3.4"
        const val TIMESTAMP_ATTRIBUTE_OID = "1.2.840.113549.1.9.16.2.14"
        const val UNKNOWN_ATTRIBUTE_OID = "1.2.3.4.5"
        const val TEST_BYTE_VALUE = 1
        const val DEEP_NESTED_VALUE = 1L
        const val OUTER_TAG_INDEX = 0
        const val OUTER_LENGTH_INDEX = 1
        const val DEEP_NESTING_LEVELS = 40
        const val END_OF_CONTENT = 0
        const val TRUNCATED_BYTES = 1
        const val BYTE_MASK = 0xff
        const val SHORT_LENGTH_LIMIT = 0x80
        const val LENGTH_OCTET_MASK = 0x7f
        const val OUTER_SEQUENCE_LENGTH_BYTES = 2
        const val INDEFINITE_LENGTH = 0x80
        const val INVALID_SIGNED_DATA_VERSION = 5L
        const val SKI_SIGNER_VERSION = 3L
    }
}

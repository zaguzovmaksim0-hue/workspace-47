package dev.junta.firmamobile.verification

import java.security.Signature
import java.util.Date
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1Encoding
import org.bouncycastle.asn1.ASN1GeneralizedTime
import org.bouncycastle.asn1.ASN1UTCTime
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.cms.CMSAttributes
import org.bouncycastle.asn1.cms.SignerInfo
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineCadesSigningTimeTest {
    @Test
    fun treatsCanonicalTimesAsAssertionsEvenOutsideCertificateValidity() {
        val material = OfflineCmsFixtures.signer(
            signatureAlgorithm = "SHA384withECDSA",
            keyAlgorithm = "EC",
            curve = "secp384r1",
            notBefore = Date(0L),
            notAfter = Date(1L),
        )
        val fixture = OfflineCadesFixtures(material)
        val times = listOf<ASN1Encodable>(
            ASN1UTCTime("500101000000Z"),
            ASN1UTCTime("491231235959Z"),
            ASN1GeneralizedTime("19491231235959Z"),
            ASN1GeneralizedTime("20500101000000Z"),
        )
        times.forEach { time ->
            val attribute = fixture.attribute(CMSAttributes.signingTime, time)
            OfflineCmsAssertions.assertVerified(fixture.signed(fixture.binding(), attribute))
        }
    }

    @Test
    fun rejectsInvalidCalendarDatesAndClockValues() {
        val fixture = OfflineCadesFixtures()
        val times = listOf(
            rawTime(UTC_TIME_TAG, "240230120000Z"),
            rawTime(UTC_TIME_TAG, "240101240000Z"),
            rawTime(UTC_TIME_TAG, "240101000060Z"),
            rawTime(GENERALIZED_TIME_TAG, "21000229000000Z"),
            rawTime(GENERALIZED_TIME_TAG, "21001301000000Z"),
            rawTime(GENERALIZED_TIME_TAG, "00000101000000Z"),
        )
        times.forEach { time ->
            assertInvalidTime(fixture, time)
        }
    }

    @Test
    fun refusesTimeFormsOutsideTheCanonicalCmsSubset() {
        val fixture = OfflineCadesFixtures()
        val times = listOf(
            ASN1GeneralizedTime("20240101000000Z"),
            ASN1GeneralizedTime("21000101000000.5Z"),
            ASN1GeneralizedTime("21000101000000+0100"),
        )
        times.forEach { time ->
            val attribute = fixture.attribute(CMSAttributes.signingTime, time)
            fixture.assertRejected(OfflineCmsStatus.UNSUPPORTED, fixture.signed(fixture.binding(), attribute))
        }
    }

    @Test
    fun rejectsDuplicateNonSingletonAndNonTimeValues() {
        val fixture = OfflineCadesFixtures()
        val value = ASN1UTCTime("240101000000Z")
        val time = fixture.attribute(CMSAttributes.signingTime, value)
        fixture.assertRejected(OfflineCmsStatus.INVALID, fixture.signed(fixture.binding(), time, time))
        val attributes = listOf(
            fixture.attribute(CMSAttributes.signingTime),
            fixture.attribute(CMSAttributes.signingTime, value, value),
            fixture.attribute(CMSAttributes.signingTime, DEROctetString(byteArrayOf(1))),
            fixture.attribute(CMSAttributes.signingTime, fixture.sequence(value)),
        )
        attributes.forEach { attribute ->
            fixture.assertRejected(OfflineCmsStatus.INVALID, fixture.signed(fixture.binding(), attribute))
        }
    }

    private fun assertInvalidTime(fixture: OfflineCadesFixtures, time: ByteArray) {
        val placeholder: ASN1Encodable = when (time[0].toInt()) {
            UTC_TIME_TAG -> ASN1UTCTime("240101000000Z")
            GENERALIZED_TIME_TAG -> ASN1GeneralizedTime("21000101000000Z")
            else -> error("Unsupported fixture time tag")
        }
        val attribute = fixture.attribute(CMSAttributes.signingTime, placeholder)
        val cms = fixture.signed(fixture.binding(), attribute)
        val attributes = requireNotNull(
            OfflineCmsSignerInfoFixtures.signerInfos(cms).single().getAuthenticatedAttributes(),
        )
        val original = attributes.getEncoded(ASN1Encoding.DER)
        val malformed = replaceOnce(original, placeholder.toASN1Primitive().getEncoded(ASN1Encoding.DER), time)
        val signature = Signature.getInstance(fixture.material.signatureAlgorithm, OfflineCmsFixtures.provider).run {
            initSign(fixture.material.keyPair.private)
            update(malformed)
            sign()
        }
        // Encode the signature before mutating the time so BC only parses the legal placeholder.
        val resigned = OfflineCmsSignerInfoFixtures.changeSigner(cms, 0) { signer ->
            SignerInfo(
                signer.getSID(),
                signer.getDigestAlgorithm(),
                signer.getAuthenticatedAttributes(),
                signer.getDigestEncryptionAlgorithm(),
                DEROctetString(signature),
                signer.getUnauthenticatedAttributes(),
            )
        }
        val rawCms = replaceOnce(
            resigned,
            original.copyOf().also { it[0] = SIGNED_ATTRIBUTES_TAG.toByte() },
            malformed.copyOf().also { it[0] = SIGNED_ATTRIBUTES_TAG.toByte() },
        )
        val signatureBytes = OfflineCmsSignerInfoFixtures.signerInfos(resigned).single()
            .getEncryptedDigest().getOctets()
        val valid = Signature.getInstance(fixture.material.signatureAlgorithm, OfflineCmsFixtures.provider).run {
            initVerify(fixture.material.keyPair.public)
            update(malformed)
            verify(signatureBytes)
        }
        assertTrue("Synthetic CMS signature must verify independently", valid)
        OfflineCmsAssertions.assertRejected(OfflineCmsStatus.INVALID, rawCms)
    }

    private fun replaceOnce(bytes: ByteArray, original: ByteArray, replacement: ByteArray): ByteArray {
        require(original.isNotEmpty() && original.size == replacement.size) { "Fixture mutation must preserve length" }
        val matches = (0..(bytes.size - original.size)).filter { offset ->
            original.indices.all { bytes[offset + it] == original[it] }
        }
        require(matches.size == 1) { "Fixture mutation must match exactly once" }
        return bytes.copyOf().also { replacement.copyInto(it, matches.single()) }
    }

    private fun rawTime(tag: Int, text: String): ByteArray {
        val bytes = text.toByteArray(Charsets.US_ASCII)
        require(bytes.size < SHORT_LENGTH_LIMIT) { "Fixture time must use a short DER length" }
        return byteArrayOf(tag.toByte(), bytes.size.toByte()) + bytes
    }

    private companion object {
        const val UTC_TIME_TAG = 0x17
        const val GENERALIZED_TIME_TAG = 0x18
        const val SHORT_LENGTH_LIMIT = 0x80
        const val SIGNED_ATTRIBUTES_TAG = 0xa0
    }
}

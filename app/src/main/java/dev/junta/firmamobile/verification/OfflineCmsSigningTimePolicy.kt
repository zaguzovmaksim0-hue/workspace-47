package dev.junta.firmamobile.verification

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1GeneralizedTime
import org.bouncycastle.asn1.ASN1UTCTime

private const val OFFLINE_CMS_UTC_TIME_DIGITS = 12
private const val OFFLINE_CMS_GENERALIZED_TIME_DIGITS = 14
private const val OFFLINE_CMS_YEAR_DIGITS = 4
private const val OFFLINE_CMS_UTC_YEAR_PIVOT = 50
private const val OFFLINE_CMS_FIRST_UTC_YEAR = 1_950
private const val OFFLINE_CMS_LAST_UTC_YEAR = 2_049

internal object OfflineCmsSigningTimePolicy {
    private val format = DateTimeFormatter.ofPattern("uuuuMMddHHmmss'Z'", Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT)

    fun validate(value: ASN1Encodable) {
        val canonical = when (val time = value.toASN1Primitive()) {
            is ASN1UTCTime -> utc(time.toString())
            is ASN1GeneralizedTime -> generalized(time.getTimeString())
            else -> throw IllegalArgumentException("Invalid CMS signing time")
        }
        // Signing time is a signer assertion; it never decides certificate validity or timestamp trust.
        LocalDateTime.parse(canonical, format)
    }

    private fun utc(text: String): String {
        requireFormat(text, OFFLINE_CMS_UTC_TIME_DIGITS)
        val year = text.substring(0, 2).toInt()
        return (if (year >= OFFLINE_CMS_UTC_YEAR_PIVOT) "19" else "20") + text
    }

    private fun generalized(text: String): String {
        requireFormat(text, OFFLINE_CMS_GENERALIZED_TIME_DIGITS)
        val year = text.substring(0, OFFLINE_CMS_YEAR_DIGITS).toInt()
        require(year > 0) { "Invalid CMS signing year" }
        if (year in OFFLINE_CMS_FIRST_UTC_YEAR..OFFLINE_CMS_LAST_UTC_YEAR) {
            throw OfflineCmsUnsupportedProfileException()
        }
        return text
    }

    private fun requireFormat(text: String, digits: Int) {
        if (text.length != digits + 1 || text.last() != 'Z' ||
            !text.take(digits).all { it in '0'..'9' }
        ) {
            throw OfflineCmsUnsupportedProfileException()
        }
    }
}

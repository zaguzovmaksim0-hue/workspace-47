package dev.junta.firmamobile.certificate

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

internal enum class CertificateExpirationWindow {
    WITHIN_THIRTY_DAYS,
    WITHIN_SEVEN_DAYS,
    TODAY,
    EXPIRED,
}

internal data class CertificateExpirationWarning(
    val window: CertificateExpirationWindow,
    val daysRemaining: Long,
)

/**
 * Presentation only: this never authorizes signing or replaces certificate validation.
 * Notice thresholds use local calendar dates, not truncated 24-hour durations. Actual
 * expiry uses the full timestamp: X.509 notAfter is inclusive, and expires after it.
 */
internal object CertificateExpirationPolicy {
    fun warning(validUntil: Instant, now: Instant, zoneId: ZoneId): CertificateExpirationWarning? {
        if (now.isAfter(validUntil)) {
            return CertificateExpirationWarning(CertificateExpirationWindow.EXPIRED, 0L)
        }
        val days = ChronoUnit.DAYS.between(
            now.atZone(zoneId).toLocalDate(),
            validUntil.atZone(zoneId).toLocalDate(),
        )
        return when {
            days <= 0L -> CertificateExpirationWarning(CertificateExpirationWindow.TODAY, 0L)
            days <= SEVEN_DAYS -> CertificateExpirationWarning(CertificateExpirationWindow.WITHIN_SEVEN_DAYS, days)
            days <= THIRTY_DAYS -> CertificateExpirationWarning(CertificateExpirationWindow.WITHIN_THIRTY_DAYS, days)
            else -> null
        }
    }
}

private const val SEVEN_DAYS = 7L
private const val THIRTY_DAYS = 30L

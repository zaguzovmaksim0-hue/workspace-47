package dev.junta.firmamobile.certificate

import java.time.Instant
import java.time.ZoneId

internal data class CertificateExpiryReminder(val thresholdDays: Int, val at: Instant)

/** Calendar-day thresholds shared with the in-app notice. A missed older window is not replayed. */
internal object CertificateExpiryReminderPolicy {
    fun next(
        validUntil: Instant,
        now: Instant,
        zoneId: ZoneId,
        delivered: Set<Int> = emptySet(),
    ): CertificateExpiryReminder? {
        val window = CertificateExpirationPolicy.warning(validUntil, now, zoneId)?.window
        val candidates = when {
            SEVEN_DAYS in delivered -> emptyList()
            window == CertificateExpirationWindow.TODAY || window == CertificateExpirationWindow.EXPIRED -> emptyList()
            window == CertificateExpirationWindow.WITHIN_SEVEN_DAYS -> listOf(SEVEN_DAYS)
            else -> listOf(THIRTY_DAYS, SEVEN_DAYS)
        }
        val threshold = candidates.firstOrNull { it !in delivered } ?: return null
        val at = validUntil.atZone(zoneId).toLocalDate()
            .minusDays(threshold.toLong()).atStartOfDay(zoneId).toInstant()
        return CertificateExpiryReminder(threshold, at)
    }

    private const val THIRTY_DAYS = 30
    private const val SEVEN_DAYS = 7
}

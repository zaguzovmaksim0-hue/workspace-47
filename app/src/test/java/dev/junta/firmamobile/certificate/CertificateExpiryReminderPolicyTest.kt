package dev.junta.firmamobile.certificate

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CertificateExpiryReminderPolicyTest {
    @Test
    fun thirtyDayBoundaryIsDueAtTheStartOfTheLocalDate() {
        assertEquals(
            CertificateExpiryReminder(30, Instant.parse("2026-10-24T00:00:00Z")),
            CertificateExpiryReminderPolicy.next(
                validUntil = Instant.parse("2026-11-23T12:00:00Z"),
                now = Instant.parse("2026-10-24T12:00:00Z"),
                zoneId = ZoneOffset.UTC,
            ),
        )
    }

    @Test
    fun futureCertificateSchedulesThirtyDaysBeforeExpiry() {
        assertEquals(CertificateExpiryReminder(30, Instant.parse("2026-10-24T00:00:00Z")), next("2026-10-23T12:00:00Z"))
    }

    @Test
    fun afterTheThirtyDayReminderOnlyTheSevenDayReminderRemains() {
        assertEquals(CertificateExpiryReminder(7, Instant.parse("2026-11-16T00:00:00Z")), next("2026-10-24T12:00:00Z", setOf(30)))
    }

    @Test
    fun eightDaysStillBelongsToTheThirtyDayWindow() {
        assertEquals(CertificateExpiryReminder(30, Instant.parse("2026-10-24T00:00:00Z")), next("2026-11-15T12:00:00Z"))
    }

    @Test
    fun sevenDayBoundaryDoesNotReplayAMissedThirtyDayReminder() {
        assertEquals(CertificateExpiryReminder(7, Instant.parse("2026-11-16T00:00:00Z")), next("2026-11-16T12:00:00Z"))
    }

    @Test
    fun oneRemainingDayUsesTheSevenDayWindow() {
        assertEquals(CertificateExpiryReminder(7, Instant.parse("2026-11-16T00:00:00Z")), next("2026-11-22T12:00:00Z"))
    }

    @Test
    fun alreadyDeliveredSevenDayWindowIsNotRepeated() {
        assertNull(next("2026-11-16T12:00:00Z", setOf(7)))
        assertNull(next("2026-11-16T12:00:00Z", setOf(30, 7)))
    }

    @Test
    fun zeroCalendarDaysDoesNotScheduleEvenBeforeTheExpiryTimestamp() {
        assertNull(next("2026-11-23T00:00:00Z"))
        assertNull(next("2026-11-23T11:59:59Z"))
    }

    @Test
    fun exactNotAfterDoesNotSchedule() {
        assertNull(next("2026-11-23T12:00:00Z"))
    }

    @Test
    fun expiredTimestampDoesNotScheduleOnTheSameDateOrLater() {
        assertNull(next("2026-11-23T12:00:00.000000001Z"))
        assertNull(next("2026-11-24T00:00:00Z"))
    }

    @Test
    fun timezoneUsesLocalDatesAndTheOffsetAtTheReminderRatherThanAtExpiry() {
        assertEquals(
            CertificateExpiryReminder(30, Instant.parse("2026-10-23T22:00:00Z")),
            CertificateExpiryReminderPolicy.next(
                Instant.parse("2026-11-23T12:00:00Z"),
                Instant.parse("2026-10-23T23:00:00Z"),
                ZoneId.of("Europe/Madrid"),
            ),
        )
    }

    @Test
    fun springDaylightSavingDoesNotTruncateThirtyCalendarDays() {
        assertEquals(
            CertificateExpiryReminder(30, Instant.parse("2026-03-25T23:00:00Z")),
            CertificateExpiryReminderPolicy.next(
                Instant.parse("2026-04-25T12:00:00Z"),
                Instant.parse("2026-03-26T12:00:00Z"),
                ZoneId.of("Europe/Madrid"),
            ),
        )
    }

    @Test
    fun aCompletedWindowRemainsCompletedAfterClockRollback() {
        assertEquals(CertificateExpiryReminder(7, Instant.parse("2026-11-16T00:00:00Z")), next("2026-10-01T12:00:00Z", setOf(30)))
        assertNull(next("2026-10-01T12:00:00Z", setOf(30, 7)))
    }

    @Test
    fun aConsumedSevenDayWindowCannotReplayAnOlderWindowAfterClockRollback() {
        assertNull(next("2026-10-01T12:00:00Z", setOf(7)))
    }

    private fun next(now: String, delivered: Set<Int> = emptySet()): CertificateExpiryReminder? =
        CertificateExpiryReminderPolicy.next(
            Instant.parse("2026-11-23T12:00:00Z"),
            Instant.parse(now),
            ZoneOffset.UTC,
            delivered,
        )
}

package dev.junta.firmamobile.certificate

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CertificateExpirationPolicyTest {
    @Test
    fun startsTheFirstNoticeAtExactlyThirtyCalendarDays() {
        assertEquals(
            CertificateExpirationWarning(CertificateExpirationWindow.WITHIN_THIRTY_DAYS, 30L),
            warning(validUntil = "2026-11-17T12:00:00Z"),
        )
    }

    @Test
    fun doesNotWarnAtThirtyOneCalendarDaysEvenWhenTheDurationTruncatesToThirty() {
        assertNull(warning(validUntil = "2026-11-18T00:00:00Z", now = "2026-10-18T23:59:00Z"))
    }

    @Test
    fun eightDaysStillUsesTheThirtyDayNotice() {
        assertEquals(
            CertificateExpirationWarning(CertificateExpirationWindow.WITHIN_THIRTY_DAYS, 8L),
            warning(validUntil = "2026-10-26T12:00:00Z"),
        )
    }

    @Test
    fun exactlySevenDaysUsesTheMoreUrgentNotice() {
        assertEquals(
            CertificateExpirationWarning(CertificateExpirationWindow.WITHIN_SEVEN_DAYS, 7L),
            warning(validUntil = "2026-10-25T12:00:00Z"),
        )
    }

    @Test
    fun oneCalendarDayDoesNotBecomeTodayBecauseThereAreFewerThanTwentyFourHours() {
        assertEquals(
            CertificateExpirationWarning(CertificateExpirationWindow.WITHIN_SEVEN_DAYS, 1L),
            warning(validUntil = "2026-10-19T00:01:00Z", now = "2026-10-18T23:59:00Z"),
        )
    }

    @Test
    fun todayBeforeTheExpiryTimestampIsNotExpired() {
        assertEquals(
            CertificateExpirationWarning(CertificateExpirationWindow.TODAY, 0L),
            warning(validUntil = "2026-10-18T23:59:59Z"),
        )
    }

    @Test
    fun exactNotAfterTimestampIsStillInclusiveLikeX509Validity() {
        assertEquals(
            CertificateExpirationWarning(CertificateExpirationWindow.TODAY, 0L),
            warning(validUntil = "2026-10-18T12:00:00Z"),
        )
    }

    @Test
    fun evenOneNanosecondAfterNotAfterIsExpired() {
        assertEquals(
            CertificateExpirationWarning(CertificateExpirationWindow.EXPIRED, 0L),
            warning(validUntil = "2026-10-18T12:00:00Z", now = "2026-10-18T12:00:00.000000001Z"),
        )
    }

    @Test
    fun anEarlierTimestampTodayIsAlreadyExpiredRatherThanAWholeExtraValidDay() {
        assertEquals(
            CertificateExpirationWarning(CertificateExpirationWindow.EXPIRED, 0L),
            warning(validUntil = "2026-10-18T11:59:59Z"),
        )
    }

    @Test
    fun bothTimestampsUseTheChosenZoneInsteadOfTheirUtcDates() {
        val expiry = "2026-11-18T22:00:00Z"
        val now = "2026-10-18T22:30:00Z"
        assertNull(warning(expiry, now))
        assertEquals(
            CertificateExpirationWarning(CertificateExpirationWindow.WITHIN_THIRTY_DAYS, 30L),
            warning(expiry, now, ZoneId.of("Europe/Madrid")),
        )
    }

    @Test
    fun changingZoneCanDistinguishTomorrowFromTodayWithoutChangingTheExpiryInstant() {
        val expiry = "2026-10-19T01:00:00Z"
        val now = "2026-10-18T23:00:00Z"
        assertEquals(
            CertificateExpirationWarning(CertificateExpirationWindow.WITHIN_SEVEN_DAYS, 1L),
            warning(expiry, now),
        )
        assertEquals(
            CertificateExpirationWarning(CertificateExpirationWindow.TODAY, 0L),
            warning(expiry, now, ZoneId.of("Europe/Madrid")),
        )
    }

    @Test
    fun aTwentyThreeHourSpringDayStillCountsAsOneCalendarDay() {
        assertEquals(
            CertificateExpirationWarning(CertificateExpirationWindow.WITHIN_SEVEN_DAYS, 1L),
            warning("2026-03-29T10:00:00Z", "2026-03-28T11:00:00Z", ZoneId.of("Europe/Madrid")),
        )
    }

    @Test
    fun eightDaysAcrossSpringDstCannotBeMisclassifiedAsSeven() {
        assertEquals(
            CertificateExpirationWarning(CertificateExpirationWindow.WITHIN_THIRTY_DAYS, 8L),
            warning("2026-03-30T10:00:00Z", "2026-03-22T11:00:00Z", ZoneId.of("Europe/Madrid")),
        )
    }

    @Test
    fun aTwentyFiveHourAutumnDayCanStillBeToday() {
        assertEquals(
            CertificateExpirationWarning(CertificateExpirationWindow.TODAY, 0L),
            warning("2026-10-25T22:59:00Z", "2026-10-24T22:00:00Z", ZoneId.of("Europe/Madrid")),
        )
    }

    @Test
    fun repeatedLocalHourCannotMakeAnExpiredCertificateLookValid() {
        assertEquals(
            CertificateExpirationWarning(CertificateExpirationWindow.EXPIRED, 0L),
            warning("2026-10-25T00:30:00Z", "2026-10-25T01:00:00Z", ZoneId.of("Europe/Madrid")),
        )
    }

    @Test
    fun theSevenDayWindowChangesAtLocalMidnightNotAfterATwentyFourHourChunk() {
        assertEquals(
            CertificateExpirationWarning(CertificateExpirationWindow.WITHIN_THIRTY_DAYS, 8L),
            warning("2026-10-25T12:00:00Z", "2026-10-17T23:59:59.999999999Z"),
        )
        assertEquals(
            CertificateExpirationWarning(CertificateExpirationWindow.WITHIN_SEVEN_DAYS, 7L),
            warning("2026-10-25T12:00:00Z", "2026-10-18T00:00:00Z"),
        )
    }

    @Test
    fun leapDayIsOneCalendarDayRatherThanAMonthOrYearBoundaryError() {
        assertEquals(
            CertificateExpirationWarning(CertificateExpirationWindow.WITHIN_SEVEN_DAYS, 1L),
            warning("2028-02-29T12:00:00Z", "2028-02-28T12:00:00Z"),
        )
    }

    @Test
    fun aLongExpiredCertificateNeverProducesNegativeDaysOrAFutureNotice() {
        assertEquals(
            CertificateExpirationWarning(CertificateExpirationWindow.EXPIRED, 0L),
            warning(validUntil = "2020-01-01T00:00:00Z"),
        )
    }

    private fun warning(
        validUntil: String,
        now: String = "2026-10-18T12:00:00Z",
        zoneId: ZoneId = ZoneId.of("UTC"),
    ): CertificateExpirationWarning? = CertificateExpirationPolicy.warning(
        Instant.parse(validUntil), Instant.parse(now), zoneId,
    )
}

package dev.junta.firmamobile.browser

import java.time.Duration
import org.junit.Assert.*
import org.junit.Test

class BrowserExternalReturnLeaseTest {
    @Test fun onlyAnExplicitLaunchCreatesASingleUseLease() {
        val owner = Any(); val lease = BrowserExternalReturnLease<Any> { 100L }
        assertFalse(lease.isValid(owner, 7))
        lease.begin(owner, 7)
        assertTrue(lease.isValid(owner, 7)); assertTrue(lease.isValid(owner, 7))
        assertTrue(lease.consume(owner, 7)); assertFalse(lease.consume(owner, 7))
    }

    @Test fun replacementViewAndNavigationCannotInheritTheLease() {
        val owner = Any(); val lease = BrowserExternalReturnLease<Any> { 100L }
        lease.begin(owner, 7); assertFalse(lease.consume(Any(), 7))
        lease.begin(owner, 7); assertFalse(lease.consume(owner, 8))
        assertFalse(lease.consume(owner, 7))
    }

    @Test fun fiveMinutesAndClockRollbackAreHardBounds() {
        val owner = Any(); var now = 100L; val lease = BrowserExternalReturnLease<Any> { now }
        lease.begin(owner, 7); now += Duration.ofMinutes(5).toNanos()
        assertFalse(lease.isValid(owner, 7))
        now = 100L; lease.begin(owner, 7); now = 99L
        assertFalse(lease.consume(owner, 7))
    }

    @Test fun failedExternalLaunchCancelsOnlyItsOwnToken() {
        val owner = Any(); val lease = BrowserExternalReturnLease<Any> { 100L }
        val first = lease.begin(owner, 7)
        val second = lease.begin(owner, 7)
        lease.cancel(first); assertTrue(lease.isValid(owner, 7))
        lease.cancel(second); assertFalse(lease.isValid(owner, 7))
    }

    @Test fun invalidationDoesNotAllowOldTokensToReturn() {
        val owner = Any(); val lease = BrowserExternalReturnLease<Any> { 100L }
        lease.begin(owner, 7); lease.invalidate(); assertFalse(lease.isValid(owner, 7))
    }

    @Test fun equalButDistinctOwnersAreNotInterchangeable() {
        data class Owner(val id: Int)
        val first = Owner(1); val replacement = Owner(1)
        val lease = BrowserExternalReturnLease<Owner> { 100L }
        lease.begin(first, 0)
        assertFalse(lease.isValid(replacement, 0))
    }
}

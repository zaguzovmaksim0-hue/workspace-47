package dev.junta.firmamobile.security

import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MonotonicSecurityTimeTest {
    @Test
    fun exactBoundaryAndRollbackFailClosedForAuthorizationWindows() {
        val lifetime = MonotonicSecurityTime.durationNanos(Duration.ofSeconds(2))
        val started = 1_000L

        assertFalse(MonotonicSecurityTime.isExpiredOrInvalid(started, lifetime, started))
        assertFalse(
            MonotonicSecurityTime.isExpiredOrInvalid(
                started,
                lifetime,
                started + lifetime - 1,
            ),
        )
        assertTrue(
            MonotonicSecurityTime.isExpiredOrInvalid(
                started,
                lifetime,
                started + lifetime,
            ),
        )
        assertTrue(MonotonicSecurityTime.isExpiredOrInvalid(started, lifetime, started - 1))
        assertEquals(
            Duration.ZERO,
            MonotonicSecurityTime.remaining(started, lifetime, started - 1),
        )
    }

    @Test
    fun replayLedgerRetainsEvidenceOnRollbackAndPrunesAtExactBoundary() {
        var now = 10_000L
        val retention = Duration.ofSeconds(5)
        val ledger = BoundedReplayLedger<String>(
            monotonicNanos = { now },
            retention = retention,
            maxEntries = 1,
        )

        assertTrue(ledger.recordNew("first"))
        now -= 1
        assertTrue(ledger.contains("first"))
        assertFalse(ledger.recordNew("second"))

        now = 10_000L + retention.toNanos() - 1
        assertTrue(ledger.contains("first"))
        now += 1
        assertFalse(ledger.contains("first"))
        assertTrue(ledger.recordNew("second"))
    }

    @Test
    fun zeroNegativeAndOverflowingDurationsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            MonotonicSecurityTime.durationNanos(Duration.ZERO)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MonotonicSecurityTime.durationNanos(Duration.ofNanos(-1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            MonotonicSecurityTime.durationNanos(Duration.ofSeconds(Long.MAX_VALUE))
        }
    }
}

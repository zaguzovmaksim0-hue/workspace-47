package dev.junta.firmamobile.certificate

import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CertificateSessionTest {
    private val mutableClock = MutableClock(TestCertificateFactory.now)

    @Test
    fun unlockExposesOnlySummaryAndManualLockDropsIdentity() = runTest {
        val identity = validIdentity()
        val session = CertificateSession(
            clock = mutableClock,
            unlockDuration = Duration.ofMinutes(10),
        )

        session.unlock(identity)

        val unlocked = session.state() as CertificateSessionState.Unlocked
        assertEquals(identity.summary, unlocked.summary)
        assertSame(identity, session.identityForSigning())

        session.lock()

        assertEquals(CertificateSessionState.Locked(identity.summary), session.state())
        assertNull(session.identityForSigning())
    }

    @Test
    fun identityExpiresAfterTenMinutes() = runTest {
        val identity = validIdentity()
        val session = CertificateSession(mutableClock, Duration.ofMinutes(10))
        session.unlock(identity)

        mutableClock.advance(Duration.ofMinutes(10).plusMillis(1))

        assertNull(session.identityForSigning())
        assertEquals(CertificateSessionState.Locked(identity.summary), session.state())
    }

    @Test
    fun civilClockRollbackCannotExtendUnlockPastMonotonicDuration() = runTest {
        val identity = validIdentity()
        val monotonic = MutableMonotonicClock(1_000_000_000L)
        val session = CertificateSession(
            clock = mutableClock,
            unlockDuration = Duration.ofMinutes(10),
            monotonicNanos = monotonic::nowNanos,
        )
        session.unlock(identity)

        mutableClock.advance(Duration.ofMinutes(9))
        monotonic.advance(Duration.ofMinutes(9))
        mutableClock.rewind(Duration.ofMinutes(8))
        monotonic.advance(Duration.ofMinutes(1).plusMillis(1))

        assertNull(session.identityForSigning())
        assertEquals(CertificateSessionState.Locked(identity.summary), session.state())
    }

    @Test
    fun exactMonotonicLeaseBoundaryExpiresIdentity() = runTest {
        val identity = validIdentity()
        val monotonic = MutableMonotonicClock(2_000_000_000L)
        val session = CertificateSession(
            clock = mutableClock,
            unlockDuration = Duration.ofMinutes(10),
            monotonicNanos = monotonic::nowNanos,
        )
        session.unlock(identity)
        mutableClock.advance(Duration.ofMinutes(9))

        monotonic.advance(Duration.ofMinutes(10))

        assertNull(session.identityForSigning())
        assertEquals(CertificateSessionState.Locked(identity.summary), session.state())
    }

    @Test
    fun monotonicClockRollbackFailsClosed() = runTest {
        val identity = validIdentity()
        val monotonic = MutableMonotonicClock(3_000_000_000L)
        val session = CertificateSession(
            clock = mutableClock,
            unlockDuration = Duration.ofMinutes(10),
            monotonicNanos = monotonic::nowNanos,
        )
        session.unlock(identity)
        mutableClock.advance(Duration.ofMinutes(1))

        monotonic.rewind(Duration.ofNanos(1))

        assertNull(session.identityForSigning())
        assertEquals(CertificateSessionState.Locked(identity.summary), session.state())
    }

    @Test
    fun backgroundKeepsIdentityButMemoryPressureLocksImmediately() = runTest {
        val identity = validIdentity()
        val session = CertificateSession(mutableClock, Duration.ofMinutes(10))

        session.unlock(identity)
        session.onAppBackgrounded()
        assertSame(identity, session.identityForSigning())
        assertTrue(session.state() is CertificateSessionState.Unlocked)

        session.onMemoryPressure()
        assertNull(session.identityForSigning())
        assertEquals(CertificateSessionState.Locked(identity.summary), session.state())
    }

    @Test
    fun defaultUnlockWindowIsTwentyFourHours() = runTest {
        val identity = validIdentity()
        val session = CertificateSession(clock = mutableClock)
        session.unlock(identity)

        mutableClock.advance(Duration.ofHours(24).minusMillis(1))
        assertSame(identity, session.identityForSigning())

        mutableClock.advance(Duration.ofMillis(2))
        assertNull(session.identityForSigning())
        assertEquals(CertificateSessionState.Locked(identity.summary), session.state())
    }


    @Test
    fun restoredIdentityKeepsOriginalExpiryWithoutRenewal() = runTest {
        val identity = validIdentity()
        val session = CertificateSession(clock = mutableClock)
        val originalExpiry = mutableClock.instant().plus(Duration.ofHours(6))

        session.unlock(identity, originalExpiry)
        mutableClock.advance(Duration.ofHours(5).plusMinutes(59))

        assertSame(identity, session.identityForSigning())

        mutableClock.advance(Duration.ofMinutes(1))

        assertNull(session.identityForSigning())
        assertEquals(CertificateSessionState.Locked(identity.summary), session.state())
    }

    @Test
    fun forgetRemovesEvenLockedSummary() = runTest {
        val identity = validIdentity()
        val session = CertificateSession(mutableClock, Duration.ofMinutes(10))
        session.unlock(identity)
        session.lock()

        session.forget()

        assertEquals(CertificateSessionState.Empty, session.state())
    }

    @Test
    fun replacementUsesOnlyNewestIdentity() = runTest {
        val first = validIdentity()
        val second = validIdentity(TestCertificateFactory.freshValidRsa())
        val session = CertificateSession(mutableClock, Duration.ofMinutes(10))

        session.unlock(first)
        session.unlock(second)

        assertSame(second, session.identityForSigning())
    }

    @Test
    fun signingSnapshotMatchesOnlyTheSameUnlockedCertificateAndClearsItsFingerprint() = runTest {
        val clearedFingerprints = mutableListOf<Boolean>()
        val first = validIdentity()
        val second = validIdentity(TestCertificateFactory.freshValidRsa())
        val firstSession = CertificateSession(
            mutableClock,
            Duration.ofMinutes(10),
            SensitiveCertificateFingerprintObserver(clearedFingerprints::add),
        )
        val secondSession = CertificateSession(mutableClock, Duration.ofMinutes(10))
        firstSession.unlock(first)
        secondSession.unlock(second)

        firstSession.signingSnapshot().use { matching ->
            assertSame(first, firstSession.identityForSigning(checkNotNull(matching)))
        }
        secondSession.signingSnapshot().use { different ->
            assertNull(firstSession.identityForSigning(checkNotNull(different)))
        }

        assertTrue(clearedFingerprints.isNotEmpty())
        assertTrue(clearedFingerprints.all { it })
    }

    @Test
    fun lockedSessionHasNoSigningSnapshotOrFingerprintMatchedIdentity() = runTest {
        val session = CertificateSession(mutableClock, Duration.ofMinutes(10))
        session.unlock(validIdentity())
        val snapshot = checkNotNull(session.signingSnapshot())

        session.lock()

        snapshot.use {
            assertNull(session.signingSnapshot())
            assertNull(session.identityForSigning(it))
        }
    }

    private suspend fun validIdentity(
        bytes: ByteArray = TestCertificateFactory.validRsa(),
    ): UnlockedIdentity {
        val result = Pkcs12Loader(
            clock = Clock.fixed(TestCertificateFactory.now, ZoneOffset.UTC),
        ).load(
            ByteArrayInputStream(bytes),
            bytes.size.toLong(),
            TestCertificateFactory.password(),
        )
        return (result as CertificateLoadResult.Success).identity
    }

    private class MutableClock(
        private var current: Instant,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = current

        fun advance(duration: Duration) {
            current = current.plus(duration)
        }

        fun rewind(duration: Duration) {
            current = current.minus(duration)
        }
    }

    private class MutableMonotonicClock(
        private var current: Long,
    ) {
        fun nowNanos(): Long = current

        fun advance(duration: Duration) {
            current = Math.addExact(current, duration.toNanos())
        }

        fun rewind(duration: Duration) {
            current = Math.subtractExact(current, duration.toNanos())
        }
    }
}

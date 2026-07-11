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
    fun backgroundAndMemoryPressureLockImmediately() = runTest {
        val identity = validIdentity()
        val session = CertificateSession(mutableClock, Duration.ofMinutes(10))

        session.unlock(identity)
        session.onAppBackgrounded()
        assertNull(session.identityForSigning())

        session.unlock(identity)
        session.onMemoryPressure()
        assertNull(session.identityForSigning())
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
        val second = validIdentity()
        val session = CertificateSession(mutableClock, Duration.ofMinutes(10))

        session.unlock(first)
        session.unlock(second)

        assertSame(second, session.identityForSigning())
    }

    private suspend fun validIdentity(): UnlockedIdentity {
        val bytes = TestCertificateFactory.validRsa()
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
    }
}

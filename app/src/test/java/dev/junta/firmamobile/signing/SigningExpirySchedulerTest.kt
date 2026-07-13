package dev.junta.firmamobile.signing

import java.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SigningExpirySchedulerTest {
    @Test
    fun runsExpiryOnlyAfterTheClosedDelay() = runTest {
        val events = mutableListOf<String>()
        CoroutineSigningExpiryScheduler(this).schedule(Duration.ofSeconds(2)) {
            events += "expired"
        }

        advanceTimeBy(1_999)
        runCurrent()
        assertEquals(emptyList<String>(), events)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("expired"), events)
    }

    @Test
    fun cancelledExpiryNeverRuns() = runTest {
        val events = mutableListOf<String>()
        val handle = CoroutineSigningExpiryScheduler(this).schedule(Duration.ofSeconds(2)) {
            events += "expired"
        }

        handle.cancel()
        advanceTimeBy(2_000)
        runCurrent()

        assertEquals(emptyList<String>(), events)
    }
}

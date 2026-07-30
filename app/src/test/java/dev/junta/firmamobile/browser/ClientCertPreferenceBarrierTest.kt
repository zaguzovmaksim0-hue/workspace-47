package dev.junta.firmamobile.browser

import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientCertPreferenceBarrierTest {
    private var nowMillis = 1_000L
    private val barrier = ClientCertPreferenceBarrier<String>(monotonicMillis = { nowMillis })

    @Test
    fun newBarrierIsIdleAndUsesTheRequiredThreeSecondTimeout() {
        assertEquals(ClientCertPreferenceBarrierState.IDLE, barrier.state())
        assertEquals(Duration.ofSeconds(3), ClientCertPreferenceBarrier.CLEAR_TIMEOUT)
    }

    @Test
    fun callbackIsRequiredBeforeAValueCanBeConsumedExactlyOnce() {
        val token = barrier.beginClear("grant-one")

        assertEquals(ClientCertPreferenceBarrierState.CLEARING, barrier.state())
        assertNull(barrier.consumeReady(token))
        assertTrue(barrier.complete(token))
        assertEquals(ClientCertPreferenceBarrierState.READY, barrier.state())
        assertEquals("grant-one", barrier.consumeReady(token))
        assertEquals(ClientCertPreferenceBarrierState.IDLE, barrier.state())
        assertNull(barrier.consumeReady(token))
        assertFalse(barrier.complete(token))
    }

    @Test
    fun invalidationMakesLateCallbackUnableToResurrectOldGrant() {
        val old = barrier.beginClear("old")
        barrier.invalidate()
        val current = barrier.beginClear("current")

        assertFalse(barrier.complete(old))
        assertNull(barrier.consumeReady(old))
        assertEquals(ClientCertPreferenceBarrierState.CLEARING, barrier.state())
        assertTrue(barrier.complete(current))
        assertEquals("current", barrier.consumeReady(current))
    }

    @Test
    fun secondBeginSupersedesFirstGenerationAndItsCallback() {
        val first = barrier.beginClear("first")
        val second = barrier.beginClear("second")

        assertFalse(barrier.complete(first))
        assertTrue(barrier.complete(second))
        assertEquals("second", barrier.consumeReady(second))
        assertNull(barrier.consumeReady(first))
    }

    @Test
    fun timeoutFailsAtExactlyThreeSecondsAndLateCallbackStaysIgnored() {
        val token = barrier.beginClear("expired")
        nowMillis += ClientCertPreferenceBarrier.CLEAR_TIMEOUT.toMillis() - 1
        assertFalse(barrier.expire(token))
        assertEquals(ClientCertPreferenceBarrierState.CLEARING, barrier.state())

        nowMillis += 1
        assertTrue(barrier.expire(token))
        assertEquals(ClientCertPreferenceBarrierState.FAILED, barrier.state())
        assertFalse(barrier.complete(token))
        assertNull(barrier.consumeReady(token))
        assertTrue(barrier.consumeFailure(token))
        assertEquals(ClientCertPreferenceBarrierState.IDLE, barrier.state())
        assertFalse(barrier.consumeFailure(token))
    }

    @Test
    fun callbackAtOrAfterDeadlineFailsClosedWithoutSeparateTimerTick() {
        val token = barrier.beginClear("expired")
        nowMillis += ClientCertPreferenceBarrier.CLEAR_TIMEOUT.toMillis()

        assertFalse(barrier.complete(token))
        assertEquals(ClientCertPreferenceBarrierState.FAILED, barrier.state())
        assertNull(barrier.consumeReady(token))
    }

    @Test
    fun explicitFailureClearsPendingValueAndIgnoresWrongGeneration() {
        val token = barrier.beginClear("grant")
        val stale = ClientCertPreferenceBarrierToken(token.generation - 1)

        assertFalse(barrier.fail(stale))
        assertEquals(ClientCertPreferenceBarrierState.CLEARING, barrier.state())
        assertTrue(barrier.fail(token))
        assertEquals(ClientCertPreferenceBarrierState.FAILED, barrier.state())
        assertNull(barrier.consumeReady(token))
        assertFalse(barrier.fail(token))
    }
}

package dev.junta.firmamobile.afirma.servlet

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AfirmaRelayStoreTest {
    private var nowNanos = 1_000_000_000L
    private val store = AfirmaRelayStore(clock = { nowNanos })

    @Test
    fun scopeRejectsNonHttpsAndMalformedOrigins() {
        listOf("http://page.example", "https://user:pass@page.example", "https://page.example/path?x=1")
            .forEach { origin ->
                try {
                    AfirmaRelayStore.Scope(1L, origin)
                    throw AssertionError("Expected IllegalArgumentException for $origin")
                } catch (_: IllegalArgumentException) {
                }
            }
    }

    @Test
    fun sessionsAndUploadsAreIsolatedByEpochAndOrigin() = runBlocking {
        val first = AfirmaRelayStore.Scope(1L, "https://page.example")
        val otherEpoch = AfirmaRelayStore.Scope(2L, "https://page.example")
        val otherOrigin = AfirmaRelayStore.Scope(1L, "https://other.example")
        assertTrue(store.expectResult(first, "session-1"))
        assertNotNull(store.offerResult(first, "session-1", "result"))
        assertEquals(AfirmaRelayStore.PollResult.Pass, store.poll(otherEpoch, "session-1"))
        assertEquals(AfirmaRelayStore.PollResult.Pass, store.poll(otherOrigin, "session-1"))
        assertEquals(AfirmaRelayStore.PollResult.Ready("result"), store.poll(first, "session-1"))
        assertTrue(store.putUpload(first, "file-1", "upload"))
        assertNull(store.takeUpload(otherEpoch, "file-1"))
        assertNull(store.takeUpload(otherOrigin, "file-1"))
        assertEquals("upload", store.takeUpload(first, "file-1"))
    }

    @Test
    fun resultIsReadExactlyOnce() = runBlocking {
        val scope = AfirmaRelayStore.Scope(1L, "https://page.example")
        assertTrue(store.expectResult(scope, "session-1"))
        assertNotNull(store.offerResult(scope, "session-1", "result"))
        assertEquals(AfirmaRelayStore.PollResult.Ready("result"), store.poll(scope, "session-1"))
        assertEquals(AfirmaRelayStore.PollResult.Pass, store.poll(scope, "session-1"))
    }

    @Test
    fun offerReceiptCompletesWhenPollConsumesResult() = runBlocking {
        val scope = AfirmaRelayStore.Scope(1L, "https://page.example")
        assertTrue(store.expectResult(scope, "session-1"))
        val receipt = store.offerResult(scope, "session-1", "result")
        assertNotNull(receipt)
        assertEquals(AfirmaRelayStore.PollResult.Ready("result"), store.poll(scope, "session-1"))
        assertTrue(receipt!!.isCompleted)
        assertTrue(receipt.getCompleted())
        assertEquals(AfirmaRelayStore.PollResult.Pass, store.poll(scope, "session-1"))
    }

    @Test
    fun invalidationCompletesReceiptAsNotConsumed() = runBlocking {
        val scope = AfirmaRelayStore.Scope(1L, "https://page.example")
        assertTrue(store.expectResult(scope, "session-1"))
        val receipt = store.offerResult(scope, "session-1", "result")
        assertNotNull(receipt)
        store.invalidate()
        assertFalse(receipt!!.getCompleted())
        assertEquals(AfirmaRelayStore.PollResult.Pass, store.poll(scope, "session-1"))
    }

    @Test
    fun withdrawalCompletesReceiptAsNotConsumed() = runBlocking {
        val scope = AfirmaRelayStore.Scope(1L, "https://page.example")
        assertTrue(store.expectResult(scope, "session-1"))
        val receipt = store.offerResult(scope, "session-1", "result")
        assertNotNull(receipt)
        assertTrue(store.withdraw(scope, "session-1"))
        assertFalse(receipt!!.getCompleted())
        assertEquals(AfirmaRelayStore.PollResult.Pass, store.poll(scope, "session-1"))
    }

    @Test
    fun pendingResultWaitsUntilTenMinutesThenPasses() = runBlocking {
        val scope = AfirmaRelayStore.Scope(1L, "https://page.example")
        assertTrue(store.expectResult(scope, "session-1"))
        nowNanos += 9L * 60L * 1_000_000_000L
        assertEquals(AfirmaRelayStore.PollResult.Wait, store.poll(scope, "session-1"))
        nowNanos += 59L * 1_000_000_000L
        assertEquals(AfirmaRelayStore.PollResult.Wait, store.poll(scope, "session-1"))
        nowNanos += 2L * 1_000_000_000L
        assertEquals(AfirmaRelayStore.PollResult.Pass, store.poll(scope, "session-1"))
    }

    @Test
    fun uploadExpiresAfterTwoMinutes() = runBlocking {
        val scope = AfirmaRelayStore.Scope(1L, "https://page.example")
        assertTrue(store.putUpload(scope, "file-1", "upload"))
        nowNanos += 2L * 60L * 1_000_000_000L - 1L
        assertEquals("upload", store.takeUpload(scope, "file-1"))
        assertTrue(store.putUpload(scope, "file-2", "upload"))
        nowNanos += 2L * 60L * 1_000_000_000L
        assertNull(store.takeUpload(scope, "file-2"))
    }

    @Test
    fun oversizedUploadIsRejectedAndOnlyOneUploadMayWait() = runBlocking {
        val scope = AfirmaRelayStore.Scope(1L, "https://page.example")
        val oversized = "a".repeat(AfirmaDeferredInvocation.MAX_WIRE_BYTES + 1)
        assertFalse(store.putUpload(scope, "file-large", oversized))
        assertTrue(store.putUpload(scope, "file-1", "first"))
        assertFalse(store.putUpload(scope, "file-2", "second"))
        assertEquals("first", store.takeUpload(scope, "file-1"))
        assertTrue(store.putUpload(scope, "file-2", "second"))
    }

    @Test
    fun invalidIdsAndOversizedResultsAreRejected() = runBlocking {
        val scope = AfirmaRelayStore.Scope(1L, "https://page.example")
        assertFalse(store.expectResult(scope, "bad/id"))
        assertNull(store.offerResult(scope, "bad/id", "result"))
        assertFalse(store.putUpload(scope, "bad/id", "upload"))
        assertTrue(store.expectResult(scope, "session-1"))
        assertNull(store.offerResult(scope, "session-1", "a".repeat(AFIRMA_MAX_RESULT_CHARS + 1)))
    }

    @Test
    fun invalidationClearsStateAndReleasesWaiters() = runBlocking {
        val scope = AfirmaRelayStore.Scope(1L, "https://page.example")
        assertTrue(store.expectResult(scope, "session-1"))
        val receipt = store.offerResult(scope, "session-1", "result")
        assertNotNull(receipt)
        val waiter = async { store.awaitReceipt(receipt!!, timeoutMillis = 1_000) }
        store.invalidate()
        assertFalse(waiter.await())
        assertEquals(AfirmaRelayStore.PollResult.Pass, store.poll(scope, "session-1"))
    }

    @Test
    fun blockedServletOriginsAreLimitedToFourPerScope() = runBlocking {
        val scope = AfirmaRelayStore.Scope(1L, "https://page.example")
        repeat(4) { index ->
            assertTrue(store.markBlocked(scope, "https://servlet$index.example"))
        }
        assertFalse(store.markBlocked(scope, "https://servlet4.example"))
        assertTrue(store.isBlocked(scope, "https://servlet0.example"))
        assertFalse(store.isBlocked(scope, "https://servlet4.example"))
    }

    @Test
    fun nonHttpsServletOriginIsRejected() = runBlocking {
        val scope = AfirmaRelayStore.Scope(1L, "https://page.example")
        try {
            store.markBlocked(scope, "http://servlet.example")
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
        assertFalse(store.isBlocked(scope, "http://servlet.example"))
    }
}

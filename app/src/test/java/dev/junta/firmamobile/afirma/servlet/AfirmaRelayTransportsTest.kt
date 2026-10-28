package dev.junta.firmamobile.afirma.servlet

import java.net.URI
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AfirmaRelayTransportsTest {
    private val endpoint = URI("https://servlet.example:8443/storage")
    private val scope = AfirmaRelayStore.Scope(1L, "https://page.example")

    private class RecordingResultTransport(
        private val result: AfirmaDeliveryResult,
    ) : AfirmaResultTransport {
        var calls = 0

        override suspend fun store(endpoint: URI, sessionId: String, result: String): AfirmaDeliveryResult {
            calls++
            return this.result
        }
    }

    private class RecordingRequestTransport(private val data: ByteArray) : AfirmaRequestTransport {
        var calls = 0

        override suspend fun retrieve(endpoint: URI, fileId: String): AfirmaRetrievedBytes {
            calls++
            return AfirmaRetrievedBytes(data)
        }
    }

    @Test
    fun unblockedEndpointUsesFallbackImmediately() = runBlocking {
        val store = AfirmaRelayStore()
        val fallback = RecordingResultTransport(AfirmaDeliveryResult.REJECTED)
        val transport = RelayResultTransport(store, scope, fallback)
        assertEquals(AfirmaDeliveryResult.REJECTED, transport.store(endpoint, "session-1", "result"))
        assertEquals(1, fallback.calls)
    }

    @Test
    fun consumedRelayResultAcknowledgesWithoutFallback() = runBlocking {
        val store = AfirmaRelayStore()
        val fallback = RecordingResultTransport(AfirmaDeliveryResult.REJECTED)
        val transport = RelayResultTransport(store, scope, fallback)
        assertTrue(store.markBlocked(scope, "https://servlet.example:8443"))
        assertTrue(store.expectResult(scope, "session-1"))
        val delivery = async { transport.store(endpoint, "session-1", "result") }
        withTimeout(5_000) {
            var polled = store.poll(scope, "session-1")
            while (polled == AfirmaRelayStore.PollResult.Wait) {
                delay(1)
                polled = store.poll(scope, "session-1")
            }
            assertEquals(AfirmaRelayStore.PollResult.Ready("result"), polled)
        }
        assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, delivery.await())
        assertEquals(0, fallback.calls)
    }

    @Test
    fun resultConsumedImmediatelyAfterOfferAcknowledgesWithoutFallback() = runBlocking {
        val store = AfirmaRelayStore()
        val fallback = RecordingResultTransport(AfirmaDeliveryResult.REJECTED)
        val transport = RelayResultTransport(store, scope, fallback)
        assertTrue(store.markBlocked(scope, "https://servlet.example:8443"))
        assertTrue(store.expectResult(scope, "session-1"))
        val delivery = async { transport.store(endpoint, "session-1", "result") }
        withTimeout(5_000) {
            var polled = store.poll(scope, "session-1")
            while (polled == AfirmaRelayStore.PollResult.Wait) {
                delay(1)
                polled = store.poll(scope, "session-1")
            }
            assertEquals(AfirmaRelayStore.PollResult.Ready("result"), polled)
        }
        assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, delivery.await())
        assertEquals(0, fallback.calls)
    }

    @Test
    fun unconsumedRelayResultFallsBackAfterTimeout() = runBlocking {
        val store = AfirmaRelayStore()
        val fallback = RecordingResultTransport(AfirmaDeliveryResult.UNCERTAIN)
        val transport = RelayResultTransport(store, scope, fallback, consumeTimeoutMillis = 20)
        assertTrue(store.markBlocked(scope, "https://servlet.example:8443"))
        assertTrue(store.expectResult(scope, "session-1"))
        assertEquals(AfirmaDeliveryResult.UNCERTAIN, transport.store(endpoint, "session-1", "result"))
        assertEquals(1, fallback.calls)
        assertEquals(AfirmaRelayStore.PollResult.Pass, store.poll(scope, "session-1"))
    }

    @Test
    fun cancelResultIsDeliveredThroughRelay() = runBlocking {
        val store = AfirmaRelayStore()
        val fallback = RecordingResultTransport(AfirmaDeliveryResult.REJECTED)
        val transport = RelayResultTransport(store, scope, fallback)
        assertTrue(store.markBlocked(scope, "https://servlet.example:8443"))
        assertTrue(store.expectResult(scope, "session-1"))
        val delivery = async { transport.store(endpoint, "session-1", "CANCEL") }
        withTimeout(5_000) {
            var polled = store.poll(scope, "session-1")
            while (polled == AfirmaRelayStore.PollResult.Wait) {
                delay(1)
                polled = store.poll(scope, "session-1")
            }
            assertEquals(AfirmaRelayStore.PollResult.Ready("CANCEL"), polled)
        }
        assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, delivery.await())
        assertEquals(0, fallback.calls)
    }

    @Test
    fun retrieveUsesPendingUploadBeforeFallback() = runBlocking {
        val store = AfirmaRelayStore()
        val fallback = RecordingRequestTransport("fallback".toByteArray())
        val transport = RelayRequestTransport(store, scope, fallback)
        assertTrue(store.putUpload(scope, "file-1", "upload"))
        assertArrayEquals("upload".toByteArray(), transport.retrieve(endpoint, "file-1").take())
        assertEquals(0, fallback.calls)
        assertArrayEquals("fallback".toByteArray(), transport.retrieve(endpoint, "file-2").take())
        assertEquals(1, fallback.calls)
    }

    @Test
    fun retrieveWithoutUploadUsesFallback() = runBlocking {
        val store = AfirmaRelayStore()
        val fallback = RecordingRequestTransport("fallback".toByteArray())
        val transport = RelayRequestTransport(store, scope, fallback)
        assertArrayEquals("fallback".toByteArray(), transport.retrieve(endpoint, "file-1").take())
        assertEquals(1, fallback.calls)
    }
}

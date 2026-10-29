package dev.junta.firmamobile.browser

import dev.junta.firmamobile.afirma.servlet.AfirmaRelayStore
import dev.junta.firmamobile.afirma.servlet.AfirmaServletInvocation
import dev.junta.firmamobile.afirma.servlet.AfirmaServletMetadata
import dev.junta.firmamobile.afirma.servlet.AfirmaServletOperation
import dev.junta.firmamobile.afirma.servlet.AfirmaServletTransport
import dev.junta.firmamobile.afirma.servlet.RelayResultTransport
import java.net.URI
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class UniversalAfirmaRelayBridgeTest {
    private var epoch = 1L
    private val operations = FakeRelayOperations()
    private val handler = UniversalAfirmaRelayHandler(operations) { epoch }

    @Test
    fun rejectsNonMainFramesWithoutMarkingServlet() = runTest {
        assertNull(handler.handle(checkMessage(), PAGE_ORIGIN, false, PAGE_URL, epoch))
        assertFalse(operations.markBlockedCalled)
    }

    @Test
    fun rejectsNonHttpsSourceOriginsWithoutMarkingServlet() = runTest {
        assertNull(handler.handle(checkMessage(), "http://page.example", true, PAGE_URL, epoch))
        assertFalse(operations.markBlockedCalled)
    }

    @Test
    fun rejectsForeignSourceOriginsWithoutMarkingServlet() = runTest {
        assertNull(handler.handle(checkMessage(), "https://other.example", true, PAGE_URL, epoch))
        assertFalse(operations.markBlockedCalled)
    }

    @Test
    fun rejectsOversizedMessagesWithoutMarkingServlet() = runTest {
        val extra = "x".repeat(8_389_700)
        val message = "{\"v\":1,\"rid\":1,\"op\":\"check\",\"servlet\":\"$SERVLET\"," +
            "\"extra\":\"$extra\"}"
        assertNull(handler.handle(message, PAGE_ORIGIN, true, PAGE_URL, epoch))
        assertFalse(operations.markBlockedCalled)
    }

    @Test
    fun rejectsMalformedAndProtocolInvalidMessages() = runTest {
        listOf(
            "not-json",
            "[]",
            "{\"v\":2,\"rid\":1,\"op\":\"check\",\"servlet\":\"$SERVLET\"}",
            "{\"v\":1,\"rid\":0,\"op\":\"check\",\"servlet\":\"$SERVLET\"}",
            "{\"v\":1,\"rid\":1,\"op\":\"delete\",\"servlet\":\"$SERVLET\"}",
            "{\"v\":1,\"rid\":1,\"op\":\"check\",\"servlet\":\"http://servlet.example\"}",
            "{\"v\":1,\"rid\":1,\"op\":\"check\",\"servlet\":\"https://servlet.example/path\"}",
            "{\"v\":1,\"rid\":1,\"op\":\"get\",\"servlet\":\"$SERVLET\",\"id\":\"bad id\"}",
            "{\"v\":1,\"rid\":1,\"op\":\"put\",\"servlet\":\"$SERVLET\",\"id\":\"file-1\"}",
        ).forEach { message ->
            assertNull(handler.handle(message, PAGE_ORIGIN, true, PAGE_URL, epoch))
        }
        assertFalse(operations.markBlockedCalled)
    }

    @Test
    fun checkMarksServletAndRepliesOk() = runTest {
        val reply = JSONObject(handler.handle(checkMessage(), PAGE_ORIGIN, true, PAGE_URL, epoch)!!)
        assertTrue(operations.markBlockedCalled)
        assertEquals(1L, reply.getLong("rid"))
        assertEquals(1, reply.getInt("v"))
        assertEquals("ok", reply.getString("status"))
        assertFalse(reply.has("text"))
    }

    @Test
    fun getReturnsReadyWaitAndPass() = runTest {
        operations.nextPollResult = AfirmaRelayStore.PollResult.Ready("signature-result")
        val ready = JSONObject(handler.handle(getMessage(), PAGE_ORIGIN, true, PAGE_URL, epoch)!!)
        assertEquals("ready", ready.getString("status"))
        assertEquals("signature-result", ready.getString("text"))

        operations.nextPollResult = AfirmaRelayStore.PollResult.Wait
        val waiting = JSONObject(handler.handle(getMessage(), PAGE_ORIGIN, true, PAGE_URL, epoch)!!)
        assertEquals("wait", waiting.getString("status"))
        assertFalse(waiting.has("text"))

        operations.nextPollResult = AfirmaRelayStore.PollResult.Pass
        val passed = JSONObject(handler.handle(getMessage(), PAGE_ORIGIN, true, PAGE_URL, epoch)!!)
        assertEquals("pass", passed.getString("status"))
        assertFalse(passed.has("text"))
    }

    @Test
    fun putStoresUploadAndRepliesOk() = runTest {
        val reply = JSONObject(handler.handle(putMessage("upload"), PAGE_ORIGIN, true, PAGE_URL, epoch)!!)
        assertTrue(operations.putUploadCalled)
        assertEquals("upload", operations.uploadData)
        assertEquals("ok", reply.getString("status"))
    }

    @Test
    fun oversizedPutRepliesPassWithoutCallingPutUpload() = runTest {
        val oversized = "x".repeat(8 * 1024 * 1024 + 1)
        val reply = JSONObject(handler.handle(putMessage(oversized), PAGE_ORIGIN, true, PAGE_URL, epoch)!!)
        assertTrue(operations.markBlockedCalled)
        assertFalse(operations.putUploadCalled)
        assertEquals("pass", reply.getString("status"))
    }

    @Test
    fun epochChangeDuringProcessingPreventsReply() = runTest {
        operations.onMarkBlocked = { epoch += 1 }
        assertNull(handler.handle(checkMessage(), PAGE_ORIGIN, true, PAGE_URL, epoch))
        assertTrue(operations.markBlockedCalled)
    }

    @Test
    fun epochChangeBeforeProcessingPreventsReply() = runTest {
        val receivedEpoch = epoch
        epoch += 1
        assertNull(handler.handle(checkMessage(), PAGE_ORIGIN, true, PAGE_URL, receivedEpoch))
        assertFalse(operations.markBlockedCalled)
    }

    @Test
    fun blockedServletRegistersResultBeforeReturningRelayTransport() = runTest {
        val store = AfirmaRelayStore()
        val scope = AfirmaRelayStore.Scope(epoch, PAGE_ORIGIN)
        assertTrue(store.markBlocked(scope, SERVLET))
        resultInvocation().use { invocation ->
            assertEquals(AfirmaRelayStore.PollResult.Pass, store.poll(scope, invocation.sessionId))
            val transport = prepareRelayResultTransport(store, scope, invocation)
            assertTrue(transport is RelayResultTransport)
            assertEquals(AfirmaRelayStore.PollResult.Wait, store.poll(scope, invocation.sessionId))
        }
    }

    @Test
    fun unblockedServletDoesNotRegisterResult() = runTest {
        val store = AfirmaRelayStore()
        val scope = AfirmaRelayStore.Scope(epoch, PAGE_ORIGIN)
        assertTrue(store.markBlocked(scope, "https://other.example"))
        resultInvocation().use { invocation ->
            val transport = prepareRelayResultTransport(store, scope, invocation)
            assertTrue(transport is RelayResultTransport)
            assertFalse(store.isBlocked(scope, SERVLET))
            assertEquals(AfirmaRelayStore.PollResult.Pass, store.poll(scope, invocation.sessionId))
        }
    }

    @Test
    fun missingScopeReturnsServletTransportWithoutChangingStore() = runTest {
        var clockReads = 0
        val store = AfirmaRelayStore(clock = { clockReads++; 0L })
        val scope = AfirmaRelayStore.Scope(epoch, PAGE_ORIGIN)
        assertTrue(store.markBlocked(scope, SERVLET))
        assertTrue(store.expectResult(scope, "existing-session"))
        assertTrue(store.putUpload(scope, "existing-file", "upload"))
        clockReads = 0
        resultInvocation().use { invocation ->
            val transport = prepareRelayResultTransport(store, null, invocation)
            assertTrue(transport is AfirmaServletTransport)
            assertEquals(0, clockReads)
            assertTrue(store.isBlocked(scope, SERVLET))
            assertEquals(AfirmaRelayStore.PollResult.Pass, store.poll(scope, invocation.sessionId))
            assertEquals(AfirmaRelayStore.PollResult.Wait, store.poll(scope, "existing-session"))
            assertEquals("upload", store.takeUpload(scope, "existing-file"))
        }
    }

    private fun resultInvocation() = AfirmaServletInvocation(
        AfirmaServletMetadata(
            operation = AfirmaServletOperation.SIGN,
            sourceOrigin = PAGE_ORIGIN,
            storageUrl = URI("$SERVLET/put"),
            sessionId = "session-1",
            key = null,
            algorithm = null,
            detached = false,
        ),
        byteArrayOf(1),
    )

    private fun checkMessage(): String =
        "{\"v\":1,\"rid\":1,\"op\":\"check\",\"servlet\":\"$SERVLET\"}"

    private fun getMessage(): String =
        "{\"v\":1,\"rid\":2,\"op\":\"get\",\"servlet\":\"$SERVLET\",\"id\":\"session-1\"}"

    private fun putMessage(data: String): String =
        "{\"v\":1,\"rid\":3,\"op\":\"put\",\"servlet\":\"$SERVLET\",\"id\":\"file-1\"," +
            "\"dat\":\"$data\"}"

    private class FakeRelayOperations : UniversalAfirmaRelayOperations {
        var markBlockedCalled = false
        var putUploadCalled = false
        var uploadData: String? = null
        var nextPollResult: AfirmaRelayStore.PollResult = AfirmaRelayStore.PollResult.Pass
        var onMarkBlocked: () -> Unit = {}

        override suspend fun markBlocked(
            scope: AfirmaRelayStore.Scope,
            servletOrigin: String,
        ): Boolean {
            markBlockedCalled = true
            onMarkBlocked()
            return true
        }

        override suspend fun poll(
            scope: AfirmaRelayStore.Scope,
            sessionId: String,
        ): AfirmaRelayStore.PollResult = nextPollResult

        override suspend fun putUpload(
            scope: AfirmaRelayStore.Scope,
            fileId: String,
            data: String,
        ): Boolean {
            putUploadCalled = true
            uploadData = data
            return true
        }
    }

    private companion object {
        const val PAGE_URL = "https://page.example/path"
        const val PAGE_ORIGIN = "https://page.example"
        const val SERVLET = "https://servlet.example"
    }
}

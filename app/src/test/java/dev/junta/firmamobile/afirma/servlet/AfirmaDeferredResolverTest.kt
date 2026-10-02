package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.certificate.UnlockedIdentity
import java.net.URI
import java.net.URLEncoder
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AfirmaDeferredResolverTest {
    @Test fun modernReducedUriDoesNotNeedInlineIdDataFormatOrAlgorithm() {
        val result = deferred()
        assertEquals(AfirmaServletOperation.SIGN, result.operation)
        assertEquals("File-123", result.fileId)
        assertEquals("Response-123", result.expectedResponseId)
        assertEquals("https://retrieve.example/get", result.retrievalDisplay)
        assertEquals("https://page.example", result.sourceOrigin)
        assertFalse(result.toString().contains("File-123"))
        result.close()
    }

    @Test fun inconsistentOrAmbiguousEnvelopesAreRefusedBeforeAnyNetworkCall() {
        for (extra in listOf("&dat=QQ==", "&properties=", "&format=CAdES", "&id=Other", "&fileid=Again")) {
            assertTrue(AfirmaServletInvocationParser.parse(uri() + extra, SOURCE) is AfirmaServletParseResult.Invalid)
        }
        for (url in listOf("http://retrieve.example/get", "https://127.0.0.1/get", "https://retrieve.example/get?op=put")) {
            val raw = uri().replace(form(RETRIEVE), form(url))
            assertTrue(AfirmaServletInvocationParser.parse(raw, SOURCE) is AfirmaServletParseResult.Invalid)
        }
    }

    @Test fun oneLegacyEncryptedDescriptorBecomesTheSameInlineOperationWithoutFetchingTwice() = runBlocking {
        val request = deferred(); val bytes = wire(xml(), "12345678"); var fetched = 0
        var actual: AfirmaServletInvocation? = null
        val op = FakeOperation()
        val resolver = AfirmaDeferredResolver(AfirmaRequestTransport { url, fileId ->
            fetched++; assertEquals(URI(RETRIEVE), url); assertEquals("File-123", fileId)
            AfirmaRetrievedBytes(bytes)
        }, operationFactory = { actual = it; op })
        assertSame(op, resolver.resolve(request))
        assertArrayEquals("hello + world".toByteArray(), actual!!.payloadCopy())
        assertEquals("https://page.example", actual!!.sourceOrigin)
        assertEquals(URI(STORAGE), actual!!.storageUrl)
        assertEquals("Response-123", actual!!.sessionId)
        assertEquals(1, fetched); assertTrue(bytes.all { it == 0.toByte() })
        try { resolver.resolve(request); fail("No duplicate consuming read") } catch (_: IllegalStateException) { }
        assertEquals(1, fetched); actual!!.close()
    }

    @Test fun outerAesDownloadAndInnerResultCipherAreParsedIndependently() = runBlocking {
        val key = ByteArray(32) { it.toByte() }; val iv = ByteArray(16) { (it + 1).toByte() }
        val config = "{\"algo\":\"AES\",\"key\":\"${b64(key)}\",\"iv\":\"${b64(iv)}\"}"
        val request = deferred(extra = "&cipher=" + form(b64(config.toByteArray())))
        val cipher = AfirmaAesParameters(key, iv)
        val response = cipher.encode(xml().toByteArray()).toByteArray()
        var actual: AfirmaServletInvocation? = null
        val resolver = AfirmaDeferredResolver(AfirmaRequestTransport { _, _ -> AfirmaRetrievedBytes(response) },
            operationFactory = { actual = it; FakeOperation() })
        resolver.resolve(request)
        assertEquals("87654321", actual!!.key)
        assertNull(actual!!.cipherCopy())
        assertArrayEquals("hello + world".toByteArray(), actual!!.payloadCopy())
        actual!!.close(); cipher.close(); key.fill(0); iv.fill(0)
    }

    @Test fun responseNewlineFromPrintWriterIsAcceptedButConfiguredCipherNeverFallsBackToPlaintext() = runBlocking {
        val good = (AfirmaIntermediateCipher.encode(xml().toByteArray(), "12345678") + "\r\n").toByteArray()
        AfirmaDeferredResolver(AfirmaRequestTransport { _, _ -> AfirmaRetrievedBytes(good) }).resolve(deferred()).close()
        assertProblem(AfirmaRetrievalProblem.INVALID, xml().toByteArray())
        assertProblem(AfirmaRetrievalProblem.INVALID, wire(xml(), "different".take(8)))
    }

    @Test fun noCipherEnvelopeAcceptsOnlyExplicitRawXmlNotAnAssumedBase64Encoding() = runBlocking {
        val plain = xml().toByteArray()
        AfirmaDeferredResolver(AfirmaRequestTransport { _, _ -> AfirmaRetrievedBytes(plain) }).resolve(deferred(withKey = false)).close()
        val encoded = b64(xml().toByteArray()).toByteArray()
        assertProblem(AfirmaRetrievalProblem.INVALID, encoded, deferred(withKey = false))
    }

    @Test fun outerResponseIdStorageEndpointAndOperationCannotBeReplacedByTheDownloadedRequest() = runBlocking {
        assertProblem(AfirmaRetrievalProblem.INVALID, wire(xml(mapOf("id" to "Other"))))
        assertProblem(AfirmaRetrievalProblem.INVALID, wire(xml(mapOf("stservlet" to "https://other.example/put"))))
        assertProblem(AfirmaRetrievalProblem.INVALID, wire("<selectcert><e k='id' v='Response-123'/></selectcert>"))
        assertProblem(AfirmaRetrievalProblem.INVALID, wire(xml(mapOf("op" to "selectcert"))))
    }

    @Test fun recursiveLookupAndUnsupportedFormatsDoNotStartASecondFetchOrSign() = runBlocking {
        assertProblem(AfirmaRetrievalProblem.UNSUPPORTED, wire(xml(mapOf("fileid" to "Nested", "rtservlet" to RETRIEVE))))
        assertProblem(AfirmaRetrievalProblem.UNSUPPORTED, wire(xml(mapOf("format" to "XAdES"))))
        assertProblem(AfirmaRetrievalProblem.UNSUPPORTED, wire(xml(mapOf("properties" to b64("signaturePolicyIdentifier=not-implemented".toByteArray())))))
    }

    @Test fun malformedOrHostileDescriptorNeverReachesOperationFactory() = runBlocking {
        for (plain in listOf("<!DOCTYPE sign SYSTEM 'https://never.example/xxe'><sign/>", "<sign><e k='id' v='A'/><e k='id' v='B'/></sign>", "ERR-06", "<sign>")) {
            assertProblem(AfirmaRetrievalProblem.INVALID, wire(plain))
        }
        assertProblem(AfirmaRetrievalProblem.INVALID, wire(" ".repeat(AfirmaConfigurationXml.MAX_BYTES + 1)))
    }

    @Test fun optionalOuterHintsDoNotInventMissingInnerRequiredParameters() = runBlocking {
        val raw = uri().replace("&rid=Response-123", "").replace("&stservlet=" + form(STORAGE), "")
        val request = (AfirmaServletInvocationParser.parse(raw, SOURCE) as AfirmaServletParseResult.Deferred).invocation
        AfirmaDeferredResolver(AfirmaRequestTransport { _, _ -> AfirmaRetrievedBytes(wire(xml())) }).resolve(request).close()
        assertProblem(AfirmaRetrievalProblem.INVALID, wire("<sign><e k='format' v='CAdES'/></sign>"))
    }

    @Test fun cancellationWhileRetrievingDoesNotDecodeCreateAnOperationOrLeaveCipherReusable() = runTest {
        var factories = 0; val response = CompletableDeferred<AfirmaRetrievedBytes>()
        val request = deferred()
        val resolver = AfirmaDeferredResolver(AfirmaRequestTransport { _, _ -> response.await() },
            operationFactory = { factories++; FakeOperation() }, parsingDispatcher = StandardTestDispatcher(testScheduler))
        val job = launch { resolver.resolve(request) }; runCurrent(); job.cancel(); runCurrent()
        assertEquals(0, factories)
        assertThrows(IllegalStateException::class.java) { request.begin() }
    }

    @Test fun canceledHandoffAfterTheFactoryReturnsClosesItsPreparedOperation() = runTest {
        val op = FakeOperation(); var job: kotlinx.coroutines.Job? = null
        val resolver = AfirmaDeferredResolver(AfirmaRequestTransport { _, _ -> AfirmaRetrievedBytes(wire(xml())) },
            operationFactory = { it.close(); job!!.cancel(); op }, parsingDispatcher = StandardTestDispatcher(testScheduler))
        job = launch { resolver.resolve(deferred()); fail("Canceled caller must not receive operation") }
        runCurrent(); assertEquals(1, op.closes)
    }

    @Test fun buffersTransferOnceAndCloseErasesOnlyStillOwnedBytes() {
        val bytes = byteArrayOf(1, 2, 3); val owned = AfirmaRetrievedBytes(bytes); owned.close()
        assertArrayEquals(ByteArray(3), bytes)
        assertThrows(IllegalStateException::class.java) { owned.take() }
        val moved = byteArrayOf(4); val second = AfirmaRetrievedBytes(moved)
        assertSame(moved, second.take()); second.close(); assertEquals(4, moved[0].toInt())
        assertThrows(IllegalStateException::class.java) { second.take() }
        moved.fill(0)
    }

    private suspend fun assertProblem(expected: AfirmaRetrievalProblem, response: ByteArray, request: AfirmaDeferredInvocation = deferred()) {
        var fetches = 0; var factories = 0
        val resolver = AfirmaDeferredResolver(AfirmaRequestTransport { _, _ -> fetches++; AfirmaRetrievedBytes(response) },
            operationFactory = { factories++; FakeOperation() })
        try { resolver.resolve(request); fail("Expected $expected") }
        catch (e: AfirmaRetrievalException) { assertEquals(expected, e.problem) }
        assertEquals(1, fetches); assertEquals(0, factories)
        assertTrue(response.all { it == 0.toByte() })
    }

    private class FakeOperation : PreparedAfirmaOperation {
        var closes = 0
        override val details = AfirmaConsentDetails("https://page.example", STORAGE, "sign", "CAdES", "SHA256withRSA", 1, "a".repeat(64))
        override fun certificateCompatible(identity: UnlockedIdentity) = true
        override suspend fun execute(identity: UnlockedIdentity, authorizeUpload: suspend () -> Unit): AfirmaDeliveryResult = error("Not a signing test")
        override fun close() { closes++ }
    }
    private fun deferred(withKey: Boolean = true, extra: String = "") =
        (AfirmaServletInvocationParser.parse(uri(withKey) + extra, SOURCE) as AfirmaServletParseResult.Deferred).invocation
    private fun uri(withKey: Boolean = true) = "afirma://sign?fileid=File-123&rid=Response-123&rtservlet=${form(RETRIEVE)}&stservlet=${form(STORAGE)}" + if (withKey) "&key=12345678" else ""
    private fun xml(extra: Map<String, String> = emptyMap()): String = "<sign>" + (linkedMapOf(
        "id" to "Response-123", "stservlet" to STORAGE, "key" to "87654321", "format" to "CAdES", "algorithm" to "SHA256withRSA",
        "dat" to b64("hello + world".toByteArray()), "properties" to b64("mode=explicit".toByteArray()),
    ) + extra).entries.joinToString("") { "<e k='${it.key}' v='${form(it.value)}'/>" } + "</sign>"
    private fun wire(xml: String, key: String = "12345678") = AfirmaIntermediateCipher.encode(xml.toByteArray(), key).toByteArray()
    private fun form(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
    private companion object { const val SOURCE = "https://page.example/start?not-in-ui=1"; const val RETRIEVE = "https://retrieve.example/get?route=fixture"; const val STORAGE = "https://store.example/put?route=fixture" }
}

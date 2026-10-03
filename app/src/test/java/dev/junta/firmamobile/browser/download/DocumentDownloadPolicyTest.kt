package dev.junta.firmamobile.browser.download

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class DocumentDownloadPolicyTest {
    @Test fun normalNameRetainsUnicodeAndExtension() {
        assertEquals("Resolución final.pdf", DownloadFileName.safe("Resolucio\u0301n final.pdf"))
        assertEquals("archivo.txt", DownloadFileName.safe(" . archivo.txt . "))
    }
    @Test fun fileNameCannotEscapeDirectoryOrHideBidiText() {
        val value = DownloadFileName.safe("../../a\\b\u202ec:\u0000?.pdf")
        assertFalse(value.startsWith('.')); assertFalse(value.contains('/')); assertFalse(value.contains('\\'))
        assertFalse(value.contains('\u202e')); assertFalse(value.contains(':')); assertFalse(value.contains('\u0000'))
    }
    @Test fun unusableAndReservedNamesHaveSafeFallbacks() {
        for (s in listOf(null, "", "...", " \u0000\n ")) assertEquals("documento.bin", DownloadFileName.safe(s))
        assertEquals("_con.txt", DownloadFileName.safe("con.txt"))
        assertEquals("_LPT1", DownloadFileName.safe("LPT1"))
    }
    @Test fun filenameByteBudgetDoesNotSplitEmojiAndRetainsExtension() {
        val result = DownloadFileName.safe("📄".repeat(1000) + ".pdf")
        assertTrue(result.toByteArray(Charsets.UTF_8).size <= 180)
        assertTrue(result.endsWith(".pdf"))
        assertEquals(result, result.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8))
    }
    @Test fun copyStreamsExactBytesAndDigestWithoutClosingCallers() {
        var closedIn = false; var closedOut = false
        val input = object : ByteArrayInputStream("abc".toByteArray()) { override fun close() { closedIn = true } }
        val out = object : ByteArrayOutputStream() { override fun close() { closedOut = true } }
        val value = BoundedDocumentCopy.copy(input, out, 3, 3)
        assertEquals(3L, value.bytes); assertArrayEquals("abc".toByteArray(), out.toByteArray())
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", value.sha256)
        assertFalse(closedIn); assertFalse(closedOut)
    }
    @Test fun copyRejectsTooLargeBeforeWritingTheOverflowByte() {
        val out = ByteArrayOutputStream()
        assertThrows(IOException::class.java) { BoundedDocumentCopy.copy(ByteArrayInputStream(ByteArray(17)), out, 16) }
        assertTrue(out.size() <= 16)
        assertThrows(IOException::class.java) { BoundedDocumentCopy.copy(ByteArrayInputStream(ByteArray(2)), out, 16, 17) }
    }
    @Test fun copyDetectsIncompleteOrMismatchedContent() {
        assertThrows(IOException::class.java) { BoundedDocumentCopy.copy(ByteArrayInputStream(byteArrayOf(1)), ByteArrayOutputStream(), 16, 2) }
        assertThrows(IOException::class.java) { BoundedDocumentCopy.copy(ByteArrayInputStream(byteArrayOf(1, 2)), ByteArrayOutputStream(), 16, 1) }
    }
    @Test fun cancellationPreventsAnyWriteAndZeroReadMakesProgress() {
        val out = ByteArrayOutputStream()
        assertThrows(IOException::class.java) { BoundedDocumentCopy.copy(ByteArrayInputStream(byteArrayOf(1)), out, 16, active = { false }) }
        assertEquals(0, out.size())
        var consumed = false
        val odd = object : InputStream() {
            override fun read(b: ByteArray, off: Int, len: Int) = if (consumed) -1 else 0
            override fun read(): Int { consumed = true; return 65 }
        }
        assertEquals(1L, BoundedDocumentCopy.copy(odd, out, 16).bytes)
        assertArrayEquals(byteArrayOf(65), out.toByteArray())
    }
    @Test fun completedGetEvidenceIsConsumedOnce() {
        val e = DownloadGetEvidence { 100 }
        e.record(URL, "GET", true); e.navigationStarted(URL)
        assertTrue(e.consume(URL)); assertFalse(e.consume(URL))
    }
    @Test fun postAndIframeCannotBeReinterpretedAsDownloadGet() {
        val e = DownloadGetEvidence { 100 }
        e.record(URL, "POST", true); assertFalse(e.consume(URL))
        e.record(URL, "GET", false); assertFalse(e.consume(URL))
        e.record(URL, "GET", true); e.record(URL, "POST", true); assertFalse(e.consume(URL))
    }
    @Test fun redirectOrNewPageDoesNotInheritOriginalGetEvidence() {
        val e = DownloadGetEvidence { 100 }
        e.record(URL, "GET", true); assertFalse(e.consume(URL + "?new")); assertTrue(e.consume(URL))
        e.record(URL, "GET", true); e.navigationStarted(URL + "?new"); assertFalse(e.consume(URL))
    }
    @Test fun evidenceExpiresOrRejectsClockRollback() {
        var now = 100L; val e = DownloadGetEvidence { now }
        e.record(URL, "GET", true); now += 30_000_000_000L; assertFalse(e.consume(URL))
        e.record(URL, "GET", true); now--; assertFalse(e.consume(URL))
    }
    @Test fun unsafeUrlOrSizeDoesNotBecomeAnOffer() {
        for (url in listOf("http://download.example/file", "https://user@download.example/file", "blob:https://download.example/id", "https://127.0.0.1/file", URL + "#part")) {
            assertNull(plan(url = url))
        }
        assertNull(plan(length = DocumentDownloadPlan.MAX_BYTES + 1)); assertNull(plan(length = -2))
    }
    @Test fun cookiesDoNotCrossAnOriginBoundary() {
        assertEquals("session=synthetic", plan()!!.cookie)
        assertNull(plan(url = "https://other.example/file")!!.cookie)
        assertNull(plan(cookie = "session=x\r\nInjected: yes")!!.cookie)
        assertEquals("https://download.example", plan()!!.targetOrigin)
    }
    @Test fun privateQueryIsPreservedForRequestButNotOriginsOrStringRepresentation() {
        val plan = plan(url = URL + "?token=do-not-display")!!
        assertTrue(plan.url.toASCIIString().endsWith("?token=do-not-display"))
        assertFalse(plan.targetOrigin.contains("token")); assertFalse(plan.toString().contains("token"))
        assertFalse(plan.toString().contains("session=synthetic"))
    }
    @Test fun malformedHeadersCannotCreateAnInjectableRequest() {
        assertNull(DocumentDownloadPlan.create(URL, URL, "a.pdf", "application/pdf", 1, "agent\r\nx", null))
        assertEquals("application/octet-stream", DocumentDownloadPlan.create(URL, URL, "a", "not-a-mime", 1, "agent", null)!!.mimeType)
    }
    @Test fun ticketIsOneShotAndCannotBeReplacedByAnotherOffer() {
        val store = DownloadTicketStore { 100 }; val plan = plan()!!; val token = store.offer(plan)!!
        assertNull(store.offer(plan)); assertNull(store.take("wrong")); assertSame(plan, store.take(token)); assertNull(store.take(token))
    }
    @Test fun expiredOrRevokedTicketCannotReplay() {
        var now = 100L; val store = DownloadTicketStore { now }; val p = plan()!!
        val old = store.offer(p)!!; now += 120_000_000_000L; assertNull(store.take(old))
        val fresh = store.offer(p)!!; store.revoke(old); assertSame(p, store.take(fresh))
        val cancelled = store.offer(p)!!; store.revoke(cancelled); assertNull(store.take(cancelled))
    }
    private fun plan(url: String = URL, length: Long = 10, cookie: String? = "session=synthetic") =
        DocumentDownloadPlan.create(URL, url, "test.pdf", "application/pdf", length, "Synthetic Agent", cookie)
    companion object { private const val URL = "https://download.example/file" }
}

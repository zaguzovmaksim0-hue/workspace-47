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
    @Test fun latestRequestForAnAddressDecidesItsMethodInAnyFrame() {
        val e = DownloadRequestEvidence { 100 }
        e.record(URL, "GET"); assertEquals(ObservedMethod.GET, e.methodOf(URL))
        e.record(URL, "POST"); assertEquals(ObservedMethod.POST, e.methodOf(URL))
        e.record(URL, "GET"); assertEquals(ObservedMethod.GET, e.methodOf(URL))
        e.record(URL, "PUT"); assertEquals(ObservedMethod.OTHER, e.methodOf(URL))
    }
    @Test fun unobservedRedirectTargetHasNoEvidenceAndFragmentsDoNotMatter() {
        val e = DownloadRequestEvidence { 100 }
        e.record(URL, "POST")
        assertNull(e.methodOf(URL + "?redirected"))
        assertEquals(ObservedMethod.POST, e.methodOf(URL + "#page=2"))
    }
    @Test fun evidenceExpiresOrRejectsClockRollback() {
        var now = 100L; val e = DownloadRequestEvidence { now }
        e.record(URL, "POST"); now += 60_000_000_000L; assertNull(e.methodOf(URL))
        e.record(URL, "POST"); now--; assertNull(e.methodOf(URL))
    }
    @Test fun evidenceIsBoundedButKeepsFormSubmissionsLongest() {
        val e = DownloadRequestEvidence { 100 }
        e.record(URL, "POST")
        repeat(200) { e.record("$URL?n=$it", "GET") }
        assertEquals(ObservedMethod.POST, e.methodOf(URL))
        assertNull(e.methodOf("$URL?n=0")); assertEquals(ObservedMethod.GET, e.methodOf("$URL?n=199"))
        repeat(70) { e.record("$URL?p=$it", "POST") }
        assertNull(e.methodOf(URL)); assertEquals(ObservedMethod.POST, e.methodOf("$URL?p=69"))
    }
    @Test fun unsafeUrlOrSizeDoesNotBecomeAnOffer() {
        for (url in listOf("http://download.example/file", "https://user@download.example/file", "blob:https://download.example/id", "https://127.0.0.1/file", "https://download.example:8443/file")) {
            assertNull(plan(url = url))
        }
        assertNull(plan(length = DocumentDownloadPlan.MAX_BYTES + 1)); assertNull(plan(length = -2))
    }
    @Test fun fragmentIsDroppedInsteadOfRejected() {
        assertEquals("https://download.example/file", plan(url = "$URL#page=2")!!.url!!.toASCIIString())
    }
    @Test fun cookiesStayWithTheSameSite() {
        // Registrable-domain cases need the Android public suffix list: DownloadSiteScopeTest.
        assertTrue(plan()!!.sendsSession)
        assertFalse(plan(url = "https://other.example/file")!!.sameSite)
        assertFalse(plan(url = "https://other.example/file")!!.sendsSession)
        assertFalse(plan(cookies = null)!!.sendsSession)
        assertEquals("https://download.example", plan()!!.targetOrigin)
    }
    @Test fun certificateIsBoundOnlyToItsOwnHost() {
        val identity = ClientTlsIdentity("download.example") { null }
        assertNotNull(DocumentDownloadPlan.create(URL, URL, "a.pdf", null, 1, "agent", null, null, identity)!!.clientIdentity)
        assertNull(DocumentDownloadPlan.create(URL, "https://other.example/a", "a.pdf", null, 1, "agent", null, null, identity)!!.clientIdentity)
    }
    @Test fun rejectedPlanDiscardsTheFormItWasGiven() {
        val form = checkNotNull(FormSubmission.encode(null, null, listOf(FormEntry.Text("a", "b"))))
        assertNull(DocumentDownloadPlan.create(URL, "http://download.example/x", "a.pdf", null, 1, "agent", null, form))
        assertThrows(IllegalStateException::class.java) { form.body() }
    }
    @Test fun inlineDocumentNeedsNoRequestAndIsDeletedWithItsOffer() {
        val dir = java.nio.file.Files.createTempDirectory("inline-").toFile()
        try {
            val file = StagedDocument.createFile(dir).apply { writeText("%PDF") }
            val plan = checkNotNull(DocumentDownloadPlan.inline(URL, StagedDocument(file, 4, "x"), "a.pdf", "application/pdf", "agent"))
            assertTrue(plan.isInline); assertNull(plan.url); assertFalse(plan.sendsSession)
            val store = DownloadTicketStore { 100 }; val token = checkNotNull(store.offer(plan))
            store.revoke(token); assertFalse(file.exists())
        } finally { dir.deleteRecursively() }
    }
    @Test fun expiredTicketDeletesItsInlineDocument() {
        val dir = java.nio.file.Files.createTempDirectory("inline-").toFile()
        try {
            var now = 100L; val store = DownloadTicketStore { now }
            val file = StagedDocument.createFile(dir).apply { writeText("%PDF") }
            store.offer(checkNotNull(DocumentDownloadPlan.inline(URL, StagedDocument(file, 4, "x"), "a.pdf", null, "agent")))
            now += 120_000_000_000L
            assertNotNull(store.offer(plan()!!)); assertFalse(file.exists())
        } finally { dir.deleteRecursively() }
    }
    @Test fun dataAddressDecodesBase64AndPercentEncoding() {
        val pdf = DataUrlDocument.parse("data:application/pdf;base64,JVBERi0xLjQ=")!!
        assertEquals("application/pdf", pdf.mimeType); assertEquals("%PDF-1.4", String(pdf.bytes))
        val text = DataUrlDocument.parse("data:,Hola%20mundo%C3%B1#frag")!!
        assertNull(text.mimeType); assertEquals("Hola mundoñ", String(text.bytes, Charsets.UTF_8))
        assertNull(DataUrlDocument.parse("https://download.example/file"))
        assertNull(DataUrlDocument.parse("data:text/plain;base64,%%%"))
        assertNull(DataUrlDocument.parse("data:,abcdef", limit = 3))
    }
    @Test fun privateQueryIsPreservedForRequestButNotOriginsOrStringRepresentation() {
        val plan = plan(url = URL + "?token=do-not-display")!!
        assertTrue(plan.url!!.toASCIIString().endsWith("?token=do-not-display"))
        assertFalse(plan.targetOrigin.contains("token")); assertFalse(plan.toString().contains("token"))
        assertFalse(plan.toString().contains("session"))
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
    private fun plan(url: String = URL, length: Long = 10, cookies: CookieLookup? = CookieLookup { "session=synthetic" }) =
        DocumentDownloadPlan.create(URL, url, "test.pdf", "application/pdf", length, "Synthetic Agent", cookies)
    companion object { private const val URL = "https://download.example/file" }
}

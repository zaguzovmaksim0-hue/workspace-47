package dev.junta.firmamobile.browser.download

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class FormSubmissionTest {
    @Test fun urlEncodedBodyUsesThePageCharsetLikeABrowser() {
        val form = FormSubmission.encode("", "ISO-8859-1", listOf(FormEntry.Text("nombre", "Peña & €"), FormEntry.Text("x", "✓")))!!
        assertEquals("application/x-www-form-urlencoded", form.contentType)
        // windows-1252 has ñ and €; ✓ becomes a numeric reference.
        assertEquals("nombre=Pe%F1a+%26+%80&x=%26%2310003%3B", String(form.body(), Charsets.US_ASCII))
    }
    @Test fun newlinesAreNormalizedToCrLf() {
        val form = FormSubmission.encode(null, "UTF-8", listOf(FormEntry.Text("t", "a\nb\rc")))!!
        assertEquals("t=a%0D%0Ab%0D%0Ac", String(form.body()))
    }
    @Test fun multipartKeepsEmptyFileControlsAndEscapesNames() {
        val form = FormSubmission.encode("multipart/form-data", "UTF-8",
            listOf(FormEntry.Text("a\"b", "valor"), FormEntry.EmptyFile("adjunto")))!!
        val boundary = form.contentType.substringAfter("boundary=")
        val body = String(form.body())
        assertTrue(body.startsWith("--$boundary\r\nContent-Disposition: form-data; name=\"a%22b\"\r\n\r\nvalor\r\n"))
        assertTrue(body.contains("name=\"adjunto\"; filename=\"\"\r\nContent-Type: application/octet-stream\r\n\r\n\r\n"))
        assertTrue(body.endsWith("--$boundary--\r\n"))
    }
    @Test fun textPlainBody() {
        val form = FormSubmission.encode("text/plain", "UTF-8", listOf(FormEntry.Text("a", "b c")))!!
        assertEquals("a=b c\r\n", String(form.body()))
    }
    @Test fun oversizedFormIsNotReplayedAndClosedFormHasNoBody() {
        assertNull(FormSubmission.encode(null, null, listOf(FormEntry.Text("big", "x".repeat(FormSubmission.MAX_BODY)))))
        val form = FormSubmission.encode(null, null, listOf(FormEntry.Text("a", "b")))!!
        form.close(); assertThrows(IllegalStateException::class.java) { form.body() }
    }
    @Test fun pageRecordMustMatchTheDownloadAddress() {
        val record = """{"action":"https://sede.example/tramite.xhtml","enctype":"","charset":"UTF-8","entries":[["a","1"],["f","","file"]],"files":false}"""
        val found = FormSubmission.fromPageRecord(record, "https://sede.example/tramite.xhtml#x")
        assertTrue(found is FormSubmission.Lookup.Found)
        assertEquals("a=1&f=", String((found as FormSubmission.Lookup.Found).submission.body()))
        assertSame(FormSubmission.Lookup.Missing, FormSubmission.fromPageRecord(record, "https://sede.example/otra"))
        assertSame(FormSubmission.Lookup.Missing, FormSubmission.fromPageRecord(null, "https://sede.example/tramite.xhtml"))
        assertSame(FormSubmission.Lookup.Missing, FormSubmission.fromPageRecord("not json", "https://sede.example/tramite.xhtml"))
        assertSame(FormSubmission.Lookup.HasFiles,
            FormSubmission.fromPageRecord(record.replace("\"files\":false", "\"files\":true"), "https://sede.example/tramite.xhtml"))
    }
}

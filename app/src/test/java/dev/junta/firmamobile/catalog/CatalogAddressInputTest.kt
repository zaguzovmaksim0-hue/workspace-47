package dev.junta.firmamobile.catalog

import dev.junta.firmamobile.browser.PublicBrowserAddress
import org.junit.Assert.*
import org.junit.Test

/** Pure input/action tests: no browser, clipboard service or E2E. */
class CatalogAddressInputTest {
    @Test fun blankDraftCannotNavigate() {
        val empty = CatalogAddressInput.empty()
        assertEquals("", empty.text); assertFalse(empty.canSubmit); assertNull(empty.destination)
        assertEquals(CatalogAddressInput.Error.EMPTY, empty.submit().state.error)
        assertNull(empty.submit().destination)
    }

    @Test fun bareDomainGetsHttpsWithoutChangingPathQueryOrFragment() {
        for (text in listOf("portal.example", "portal.example:443/a%2Fb?token=a+b&x=%252F#part", "xn--bcher-kva.example/path", "portal.example/path?return=https://another.example/form#done", "portal.example:443/path#https://another.example/")) {
            val input = CatalogAddressInput.empty().edit(text)
            assertEquals("https://$text", input.submit().destination!!.toASCIIString())
            assertEquals(text, input.text)
        }
    }

    @Test fun explicitHttpsSpellingAndEncodedBytesArePreserved() {
        val raw = "HTTPS://Sub.Example.COM:443/a/../%2f?state=a+b&other=%252F#section"
        val input = CatalogAddressInput.empty().edit("  $raw  ")
        assertEquals(raw, input.submit().destination!!.toString())
        assertEquals("Sub.Example.COM", input.hostPreview)
    }

    @Test fun unsafeAndMalformedAddressesAreNeverSubmitted() {
        for (value in listOf("http://portal.example", "ftp://portal.example", "file:///tmp/a", "javascript:alert(1)",
            "afirma://sign?id=one", "https:portal.example", "//portal.example", "user:secret@portal.example",
            "https://user@portal.example", "127.0.0.1", "localhost", "local.example:444", "printer.local",
            "https://[::1]", "portal.example/%GG", "portal.example/a b", "portal.example/a\\b", "words to search")) {
            val result = CatalogAddressInput.empty().edit(value).submit()
            assertNull(value, result.destination)
            assertNotNull(value, result.state.error)
        }
    }

    @Test fun pasteOnlyEditsAndRequiresAnExplicitOpenAction() {
        val original = CatalogAddressInput.empty()
        val pasted = original.paste(" \tportal.example/path?private=token\n")
        assertEquals("", original.text)
        assertEquals("portal.example/path?private=token", pasted.text)
        assertFalse(pasted.opening); assertTrue(pasted.canSubmit); assertNull(pasted.error)
        assertEquals("https://portal.example/path?private=token", pasted.submit().destination!!.toASCIIString())
    }

    @Test fun emptyClipboardDoesNotAccidentallySubmitThePreviousDraft() {
        for (value in listOf(null, "", " \t\r\n")) {
            val pasted = CatalogAddressInput.empty().edit("old.example/path").paste(value)
            assertEquals("old.example/path", pasted.text)
            assertEquals(CatalogAddressInput.Error.CLIPBOARD_EMPTY, pasted.error)
            assertFalse(pasted.canSubmit); assertNull(pasted.submit().destination)
        }
    }

    @Test fun invalidClipboardShowsAnInlineProblemWithoutGuessingAHost() {
        val input = CatalogAddressInput.empty().paste("First URL: https://portal.example")
        assertEquals(CatalogAddressInput.Error.INVALID, input.error)
        assertNull(input.submit().destination)
        assertNull(input.hostPreview)
    }

    @Test fun oversizedInputIsNotTruncatedToANavigableDifferentUrl() {
        val original = CatalogAddressInput.empty().edit("old.example")
        for (candidate in listOf(original.edit("a".repeat(8193)), original.paste("https://portal.example/" + "a".repeat(8193)))) {
            assertEquals("old.example", candidate.text)
            assertEquals(CatalogAddressInput.Error.TOO_LONG, candidate.error)
            assertFalse(candidate.canSubmit); assertNull(candidate.submit().destination)
        }
        assertEquals("https://correct.example", original.edit("a".repeat(8193)).edit("correct.example").submit().destination!!.toString())
    }

    @Test fun exactUrlLimitIncludesAnAutomaticallyAddedScheme() {
        val prefix = "https://portal.example/"
        val maximum = prefix + "x".repeat(8192 - prefix.length)
        assertEquals(maximum, CatalogAddressInput.empty().edit(maximum).submit().destination!!.toString())
        assertEquals(maximum, CatalogAddressInput.empty().edit(maximum.removePrefix("https://")).submit().destination!!.toString())
        val extra = CatalogAddressInput.empty().edit(maximum.removePrefix("https://") + "x").submit()
        assertNull(extra.destination); assertEquals(CatalogAddressInput.Error.TOO_LONG, extra.state.error)
        val expanded = "https://portal.example/" + "Ж".repeat(2000)
        val rejected = CatalogAddressInput.empty().edit(expanded).submit()
        assertNull(rejected.destination); assertFalse(rejected.state.opening)
        val smallUnicode = CatalogAddressInput.empty().edit("portal.example/Ж?return=https://other.example").submit()
        assertNotNull(smallUnicode.destination)
        assertNotNull(PublicBrowserAddress.parse(smallUnicode.destination!!.toASCIIString()))
    }

    @Test fun buttonAndKeyboardCannotConsumeTheSameSubmissionTwice() {
        val first = CatalogAddressInput.empty().edit("portal.example").submit()
        assertNotNull(first.destination); assertTrue(first.state.opening)
        assertNull(first.state.submit().destination); assertFalse(first.state.canSubmit)
        assertSame(first.state, first.state.edit("different.example"))
        assertSame(first.state, first.state.paste("different.example"))
    }

    @Test fun clearResetsErrorsAndDoesNotRetainADestination() {
        val invalid = CatalogAddressInput.empty().paste("http://portal.example")
        val cleared = invalid.clear()
        assertEquals("", cleared.text); assertNull(cleared.error); assertNull(cleared.destination)
        assertFalse(cleared.opening)
        assertEquals("https://new.example", cleared.edit("new.example").submit().destination!!.toString())
    }

    @Test fun diagnosticDescriptionsExcludeAddressesAndQuerySecrets() {
        val text = "portal.example/private?token=secret-value#one"
        val state = CatalogAddressInput.empty().edit(text)
        for (description in listOf(state.toString(), state.submit().toString(), state.paste(null).toString())) {
            assertFalse(description.contains("secret-value")); assertFalse(description.contains("portal.example"))
        }
        assertEquals("portal.example", state.hostPreview)
        assertSame(state.destination, state.destination)
        assertNotNull(PublicBrowserAddress.parse(checkNotNull(state.destination).toASCIIString()))
    }
}

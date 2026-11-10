package dev.junta.firmamobile.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifiedAfirmaFrameSourceTest {
    private val document = AfirmaFrameDocument(
        "https://portal.example/tramite?atributo=a&atributo=b", 7L, "a".repeat(32),
    )
    private val uri = "afirma://sign?format=CAdES&algorithm=SHA256withRSA&dat=YWJj"

    @Test fun topDocumentWithMatchingOriginCanOfferRequest() {
        assertTrue(VerifiedAfirmaFrameSource.permitsSource("https://PORTAL.example:443", true, document))
        assertEquals(
            AfirmaFrameMessage(document.documentId, uri),
            VerifiedAfirmaFrameSource.parseMessage(document.documentId + "\n" + uri),
        )
    }

    @Test fun subframeCannotBorrowTopDocumentAuthorityEvenWhenSameOrigin() {
        assertFalse(VerifiedAfirmaFrameSource.permitsSource("https://portal.example", false, document))
        assertFalse(VerifiedAfirmaFrameSource.permitsSource("https://foreign.example", false, document))
    }

    @Test fun foreignOpaqueAndInsecureSourcesAreRejected() {
        listOf(
            "https://foreign.example", "null", "file:///tmp/a", "http://portal.example",
            "https://user@portal.example", "https://portal.example/path", "https://portal.example?q=x",
            "https://portal.example#x",
        ).forEach { assertFalse(it, VerifiedAfirmaFrameSource.permitsSource(it, true, document)) }
    }

    @Test fun explicitHttpsPortsRemainSeparateAuthorities() {
        val alternate = document.copy(pageUrl = "https://portal.example:8443/tramite")
        assertTrue(VerifiedAfirmaFrameSource.permitsSource("https://portal.example:8443", true, alternate))
        assertFalse(VerifiedAfirmaFrameSource.permitsSource("https://portal.example", true, alternate))
        assertFalse(VerifiedAfirmaFrameSource.permitsSource("https://portal.example:4443", true, alternate))
    }

    @Test fun changedDocumentEpochOrUrlInvalidatesQueuedRequest() {
        assertTrue(VerifiedAfirmaFrameSource.isCurrent(document, document.copy()))
        assertFalse(VerifiedAfirmaFrameSource.isCurrent(document, document.copy(documentId = "b".repeat(32))))
        assertFalse(VerifiedAfirmaFrameSource.isCurrent(document, document.copy(navigationEpoch = 8L)))
        assertFalse(VerifiedAfirmaFrameSource.isCurrent(document,
            document.copy(pageUrl = "https://portal.example/other")))
        assertFalse(VerifiedAfirmaFrameSource.permitsSource("https://portal.example", true,
            document.copy(navigationEpoch = -1L)))
    }

    @Test fun malformedEnvelopesAndControlCharactersAreRejected() {
        listOf(
            "", "a".repeat(32), "x".repeat(32) + "\n" + uri, document.documentId + ":" + uri,
            document.documentId + "\nhttps://portal.example/", document.documentId + "\n" + uri + "\nmore",
        ).forEach { assertNull(VerifiedAfirmaFrameSource.parseMessage(it)) }
    }

    @Test fun probeAcceptsOnlyExactDocumentToken() {
        assertEquals(document.documentId,
            VerifiedAfirmaFrameSource.documentIdFromJavascript("\"" + document.documentId + "\""))
        listOf(
            null, "null", document.documentId, "\"x\"", "\"" + "A".repeat(32) + "\"",
            "\"" + document.documentId + "\\n\"",
        ).forEach { assertNull(VerifiedAfirmaFrameSource.documentIdFromJavascript(it)) }
    }
}

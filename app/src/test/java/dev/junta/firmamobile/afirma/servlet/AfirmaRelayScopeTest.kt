package dev.junta.firmamobile.afirma.servlet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AfirmaRelayScopeTest {
    @Test fun httpsPageProducesAnEpochBoundScope() {
        assertEquals(
            AfirmaRelayStore.Scope(7L, "https://page.example"),
            relayScopeFor(7L, "https://page.example/path?query=1"),
        )
        assertEquals(
            AfirmaRelayStore.Scope(7L, "https://page.example"),
            relayScopeFor(7L, "https://page.example/path?x=1#frag"),
        )
        assertEquals(
            AfirmaRelayStore.Scope(8L, "https://page.example:8443"),
            relayScopeFor(8L, "https://page.example:8443/"),
        )
    }

    @Test fun nonHttpsOrInvalidPagesProduceNoScope() {
        assertNull(relayScopeFor(1L, "http://page.example/"))
        assertNull(relayScopeFor(2L, ""))
        assertNull(relayScopeFor(3L, "not a url"))
        assertNull(relayScopeFor(4L, "https://user:secret@page.example/"))
    }
}

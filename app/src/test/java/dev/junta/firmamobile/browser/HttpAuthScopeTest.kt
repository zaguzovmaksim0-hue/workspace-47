package dev.junta.firmamobile.browser

import org.junit.Assert.*
import org.junit.Test

class HttpAuthScopeTest {
    @Test fun matchingServerHasNoQueryOrFragmentInItsDescription() {
        val scope = HttpAuthScope.from("https://LOGIN.example:443/private?token=not-for-ui#secret", "login.EXAMPLE", "Área privada")!!
        assertEquals("login.example", scope.server)
        assertEquals("https://login.example", scope.pageOrigin)
        assertEquals("Área privada", scope.realm)
    }
    @Test fun publicPunycodeAndEmptyRealmAreSupported() {
        assertNotNull(HttpAuthScope.from("https://xn--bcher-kva.example/path", "xn--bcher-kva.example", ""))
    }
    @Test fun differentOrMalformedHostCannotBorrowThePageIdentity() {
        for (host in listOf("other.example", "login.example:443", "login.example/path", "user@login.example", "login.example?x", "login.example ", "login.example#x", "localhost", "127.0.0.1")) {
            assertNull(host, HttpAuthScope.from("https://login.example/", host, ""))
        }
    }
    @Test fun insecureAndLocalPagesCannotPrompt() {
        for (url in listOf("http://login.example/", "https://user@login.example/", "file:///private", "https://login.example:444/", "https://localhost/")) {
            assertNull(url, HttpAuthScope.from(url, "login.example", ""))
        }
    }
    @Test fun realmControlsAndBidiDoNotBecomeTrustedDisplayText() {
        for (realm in listOf("a\nb", "a\rb", "a\u0000b", "a\u202eb", "a\u2066b")) {
            assertNull(HttpAuthScope.from("https://login.example", "login.example", realm))
        }
    }
    @Test fun realmBudgetPreservesValidTextInsteadOfTruncatingIt() {
        val valid = "á".repeat(256)
        assertEquals(valid, HttpAuthScope.from("https://login.example", "login.example", valid)!!.realm)
        assertNull(HttpAuthScope.from("https://login.example", "login.example", valid + "x"))
    }
}

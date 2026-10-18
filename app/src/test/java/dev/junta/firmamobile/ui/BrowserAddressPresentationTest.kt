package dev.junta.firmamobile.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@Config(sdk = [28])
class BrowserAddressPresentationTest {
    @Test
    fun httpsPresentationContainsOnlyTheCanonicalHost() {
        assertEquals(
            "example.test",
            BrowserAddressPresentation.hostOf(
                "https://ExAmPlE.test:8443/private/path?token=synthetic#fragment",
                unavailableAddress = "Unavailable",
            ),
        )
    }

    @Test
    fun internationalHostKeepsTheExistingAsciiPresentation() {
        assertEquals(
            "xn--bcher-kva.example",
            BrowserAddressPresentation.hostOf("https://bücher.example/", "Unavailable"),
        )
    }

    @Test
    fun httpsSchemeIsStillCaseInsensitive() {
        assertEquals(
            "example.test",
            BrowserAddressPresentation.hostOf("HTTPS://EXAMPLE.TEST/", "Unavailable"),
        )
    }

    @Test
    fun missingAndMalformedHostsReturnTheExplicitFallback() {
        for (url in listOf("", "not an address", "https://", "https:///missing-host")) {
            assertEquals("Custom unavailable label", BrowserAddressPresentation.hostOf(url, "Custom unavailable label"))
        }
    }

    @Test
    fun opaqueAndNonHttpsUrisReturnTheExplicitFallback() {
        for (url in listOf("about:blank", "javascript:void(0)", "https:opaque", "http://example.test/")) {
            assertEquals("Unavailable", BrowserAddressPresentation.hostOf(url, "Unavailable"))
        }
    }

    @Test
    fun userInfoIsStillRejected() {
        for (url in listOf("https://user:synthetic@example.test/", "https://user%3Aname@example.test/")) {
            assertEquals("Unavailable", BrowserAddressPresentation.hostOf(url, "Unavailable"))
        }
    }

    @Test
    fun std3IdnFailureReturnsTheExplicitFallbackWithoutThrowing() {
        assertEquals(
            "Unavailable",
            BrowserAddressPresentation.hostOf("https://bad_host.example/", "Unavailable"),
        )
    }

    @Test
    fun fallbackIsUsedVerbatimIncludingAnEmptyValue() {
        assertEquals("", BrowserAddressPresentation.hostOf("about:blank", ""))
        assertEquals("  Custom label  ", BrowserAddressPresentation.hostOf("https://", "  Custom label  "))
    }
}

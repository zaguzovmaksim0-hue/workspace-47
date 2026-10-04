package dev.junta.firmamobile.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PublicBrowserAddressTest {
    @Test
    fun acceptsPublicHttpsAddressesAndPunycode() {
        assertAccepted(
            "https://example.com",
            "https://example.com:443/catalog",
            "HTTPS://Sub.Example.COM:443/",
            "https://xn--bcher-kva.example/catalog#details",
            "https://a-b.test/?item=1#section",
            "https://a.b/",
            "https://localhost.example/"
        )
    }

    @Test
    fun preservesOriginalSpellingAndEncodedComponentBytes() {
        val path = "/a/../%E2%82%AC%2fpart%2Fend"
        val query = "q=a+b&escaped=%252F&slash=%2f%2F&space=%20&bytes=%00%09%0A&utf8=%D0%AF"
        val fragment = "section%2F%E2%82%AC"
        val raw = "HTTPS://WWW.Example.COM:443$path?$query#$fragment"
        val uri = PublicBrowserAddress.parse(raw)

        assertNotNull(uri)
        assertEquals(raw, uri!!.toString())
        assertEquals(path, uri.rawPath)
        assertEquals(query, uri.rawQuery)
        assertEquals(fragment, uri.rawFragment)
    }

    @Test
    fun rejectsUnsafeStructureAndMalformedUris() {
        assertRejected(
            "http://example.com/",
            "ftp://example.com/",
            "javascript:alert(1)",
            "//example.com/path",
            "https:example.com/path",
            "https:///path",
            "https://user:password@example.com/",
            "https://@example.com/",
            "https://example.com:80/",
            "https://example.com:444/",
            "https://example.com:abc/",
            "https://example.com/%",
            "https://example.com/%0",
            "https://example.com/?q=%GG",
            "https://example.com/#%",
            "https://[::1"
        )
    }

    @Test
    fun rejectsNumericAddressesAndLocalNames() {
        assertRejected(
            "https://8.8.8.8/",
            "https://192.168.1.10/",
            "https://127.0.0.1/",
            "https://127.1/",
            "https://[::1]/",
            "https://[2001:db8::1]:443/",
            "https://[::ffff:192.168.1.1]/",
            "https://localhost/",
            "https://printer/",
            "https://service.LOCAL/",
            "https://a.service.LocalHost/"
        )
    }

    @Test
    fun enforcesDnsLabelAndHostnameBoundaries() {
        val label = "a".repeat(63)
        val maxHost = listOf(label, label, label, "b".repeat(61)).joinToString(".")
        assertEquals(253, maxHost.length)
        assertAccepted("https://$label.test/", "https://$maxHost/")
        assertRejected(
            "https://${label}a.test/",
            "https://${maxHost}b/",
            "https://-a.example/",
            "https://a-.example/",
            "https://a..example/",
            "https://.example/",
            "https://example.com./",
            "https://bad_name.example/",
            "https://bücher.example/",
            "https://%65xample.com/"
        )
    }

    @Test
    fun rejectsBlankInputRawWhitespaceControlsAndBackslashes() {
        assertRejected("", " ", "\t\n", "https://example.com/a\\b")
        val forbidden = listOf(
            '\u0000', '\t', '\n', '\r', ' ', '\u007f', '\u0085', '\u00a0', '\u2003'
        )
        forbidden.forEach { character ->
            assertRejected("https://example.com/a${character}b")
        }
        assertRejected(" https://example.com/", "https://example.com/ ")
    }

    @Test
    fun acceptsTheExactInputBudgetAndRejectsOneCharacterMore() {
        val prefix = "https://example.com/"
        val maximum = prefix + "a".repeat(8192 - prefix.length)
        assertEquals(8192, maximum.length)
        assertAccepted(maximum.dropLast(1), maximum)
        assertRejected(maximum + "a")
    }

    private fun assertAccepted(vararg addresses: String) {
        addresses.forEach { raw ->
            assertEquals("Rejected or rewritten address: $raw", raw, PublicBrowserAddress.parse(raw)?.toString())
        }
    }

    private fun assertRejected(vararg addresses: String) {
        addresses.forEach { raw ->
            assertNull("Accepted address: $raw", PublicBrowserAddress.parse(raw))
        }
    }
}

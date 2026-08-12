package dev.junta.firmamobile.browser

import dev.junta.firmamobile.network.ValidatedNetworkUrl
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewCookieBridgeTest {
    @Test
    fun compatibilityFacadeIsBoundToTheActiveProfileExactEndpoint() {
        val store = FakeCookieStore(cookie = "SESSION=opaque")
        val profile = profile("unizar-tramitador")
        val bridge = WebViewCookieBridge(profile, store)
        val exact = ValidatedNetworkUrl(profile.endpoints.values.single().url)
        val otherProfile = profile("junta-ofvirtual")
        val foreign = ValidatedNetworkUrl(otherProfile.endpoints.values.single().url)
        val wrongPath = ValidatedNetworkUrl(URI("https://${exact.uri.host}/not-the-endpoint"))

        assertEquals("SESSION=opaque", bridge.cookieHeaderFor(exact))
        assertNull(bridge.cookieHeaderFor(foreign))
        assertNull(bridge.cookieHeaderFor(wrongPath))
        assertEquals(listOf(exact.uri.toASCIIString()), store.readUrls)
    }

    @Test
    fun compatibilityFacadeKeepsTheSameBoundedSetCookieChecks() {
        val store = FakeCookieStore()
        val profile = profile("junta-ofvirtual")
        val bridge = WebViewCookieBridge(profile, store)
        val exact = ValidatedNetworkUrl(profile.endpoints.values.single().url)

        assertTrue(bridge.applySetCookie(exact, "SERVER_SESSION=opaque; Secure; HttpOnly"))
        assertFalse(bridge.applySetCookie(exact, "SESSION=value\r\nInjected: true"))
        assertEquals(1, store.writes.size)
        assertEquals(1, store.flushCalls)
    }

    private fun profile(id: String) = BuiltInSiteProfiles.catalog.profiles.single {
        it.profileId == ProfileId(id)
    }

    private class FakeCookieStore(
        private val cookie: String? = null,
    ) : WebCookieStore {
        val readUrls = mutableListOf<String>()
        val writes = mutableListOf<Pair<String, String>>()
        var flushCalls = 0

        override fun getCookie(url: String): String? {
            readUrls += url
            return cookie
        }

        override fun setCookie(url: String, value: String) {
            writes += url to value
        }

        override fun flush() {
            flushCalls += 1
        }

        override fun removeAllCookies(callback: (Boolean) -> Unit) = callback(true)
    }
}

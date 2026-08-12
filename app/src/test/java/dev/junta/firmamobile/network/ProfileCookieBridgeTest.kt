package dev.junta.firmamobile.network

import dev.junta.firmamobile.browser.WebCookieStore
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileCookieBridgeTest {
    @Test
    fun nativeCookiesNeverCrossProfileOrReachProfilesWithoutNetworkEndpoints() {
        val store = FakeCookieStore(cookie = "SESSION=opaque")
        val unizar = profile("unizar-tramitador")
        val bridge = ProfileCookieBridge(unizar, store)
        val unizarEndpoint = validated(unizar.endpoints.values.single().url)
        val juntaEndpoint = validated(profile("junta-ofvirtual").endpoints.values.single().url)

        assertEquals("SESSION=opaque", bridge.cookieHeaderFor(unizarEndpoint))
        assertNull(bridge.cookieHeaderFor(juntaEndpoint))
        assertEquals(listOf(unizarEndpoint.uri.toASCIIString()), store.readUrls)

        assertThrows(IllegalArgumentException::class.java) {
            ProfileCookieBridge(profile("reg-age-redsara"), store)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProfileCookieBridge(profile("carne-joven-andalucia"), store)
        }
    }

    @Test
    fun readAndWriteRequireTheExactProfileEndpointNotOnlyTheSameOrigin() {
        val store = FakeCookieStore(cookie = "SESSION=opaque")
        val profile = profile("unizar-tramitador")
        val bridge = ProfileCookieBridge(profile, store)
        val endpoint = profile.endpoints.values.single().url
        val exact = validated(endpoint)
        val wrongPath = validated(URI("https://${endpoint.host}/other/path"))

        assertEquals("SESSION=opaque", bridge.cookieHeaderFor(exact))
        assertNull(bridge.cookieHeaderFor(wrongPath))
        assertTrue(bridge.applySetCookie(exact, "SERVER_SESSION=opaque; Secure; HttpOnly; Path=/"))
        assertFalse(bridge.applySetCookie(wrongPath, "SERVER_SESSION=must-not-cross"))

        assertEquals(listOf(exact.uri.toASCIIString()), store.readUrls)
        assertEquals(1, store.writes.size)
        assertEquals(exact.uri.toASCIIString(), store.writes.single().first)
        assertEquals(1, store.flushCalls)
    }

    @Test
    fun malformedOrOversizedSetCookieFailsBeforeTheStoreSeesIt() {
        val store = FakeCookieStore()
        val profile = profile("junta-ofvirtual")
        val bridge = ProfileCookieBridge(profile, store)
        val endpoint = validated(profile.endpoints.values.single().url)

        assertFalse(bridge.applySetCookie(endpoint, "SESSION=value\r\nInjected: true"))
        assertFalse(bridge.applySetCookie(endpoint, "S=" + "x".repeat(8192)))
        assertTrue(store.writes.isEmpty())
        assertEquals(0, store.flushCalls)
    }

    @Test
    fun storeExceptionsFailClosedWithoutEchoingCookieValues() {
        val canary = "cookie-value-must-not-escape"
        val profile = profile("unizar-tramitador")
        val endpoint = validated(profile.endpoints.values.single().url)
        val bridge = ProfileCookieBridge(profile, FakeCookieStore(fail = true))

        assertNull(bridge.cookieHeaderFor(endpoint))
        assertFalse(bridge.applySetCookie(endpoint, "SESSION=$canary"))
    }

    private fun profile(id: String) = BuiltInSiteProfiles.catalog.profiles.single {
        it.profileId == ProfileId(id)
    }

    private fun validated(uri: URI) = ValidatedNetworkUrl(uri)

    private class FakeCookieStore(
        var cookie: String? = null,
        private val fail: Boolean = false,
    ) : WebCookieStore {
        val readUrls = mutableListOf<String>()
        val writes = mutableListOf<Pair<String, String>>()
        var flushCalls = 0

        override fun getCookie(url: String): String? {
            if (fail) error("cookie store unavailable")
            readUrls += url
            return cookie
        }

        override fun setCookie(url: String, value: String) {
            if (fail) error("cookie store unavailable")
            writes += url to value
        }

        override fun flush() {
            if (fail) error("cookie store unavailable")
            flushCalls += 1
        }

        override fun removeAllCookies(callback: (Boolean) -> Unit) = callback(true)
    }
}

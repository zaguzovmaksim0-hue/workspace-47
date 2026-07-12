package dev.junta.firmamobile.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
class WebViewCookieBridgeTest {
    @Test
    fun readsCookiesOnlyForTheExactAllowedDestinationUrl() {
        val store = FakeCookieStore().apply { cookie = "SESSION=secret-cookie" }
        val bridge = WebViewCookieBridge(store)

        assertEquals(
            "SESSION=secret-cookie",
            bridge.cookieHeaderFor("https://www.juntadeandalucia.es/private"),
        )
        assertEquals(
            listOf("https://www.juntadeandalucia.es/private"),
            store.readUrls,
        )

        assertNull(bridge.cookieHeaderFor("https://evil.example/private"))
        assertNull(bridge.cookieHeaderFor("http://www.juntadeandalucia.es/private"))
        assertEquals(1, store.readUrls.size)
    }

    @Test
    fun writesBoundedSetCookieOnlyBackToAnAllowedHostAndFlushes() {
        val store = FakeCookieStore()
        val bridge = WebViewCookieBridge(store)

        assertTrue(
            bridge.applySetCookie(
                "https://ws024.juntadeandalucia.es/sign",
                "SERVER_SESSION=opaque; Secure; HttpOnly; SameSite=Lax",
            ),
        )
        assertEquals(1, store.writes.size)
        assertEquals(1, store.flushCalls)

        assertFalse(
            bridge.applySetCookie(
                "https://evil.example/sign",
                "SESSION=must-not-leak",
            ),
        )
        assertFalse(
            bridge.applySetCookie(
                "https://ws024.juntadeandalucia.es/sign",
                "SESSION=value\r\nInjected: true",
            ),
        )
        assertEquals(1, store.writes.size)
    }

    @Test
    fun clearSessionDelegatesToCookieStoreWithoutInspectingValues() {
        val store = FakeCookieStore()
        val bridge = WebViewCookieBridge(store)
        var callbackValue: Boolean? = null

        bridge.clearSession { callbackValue = it }

        assertEquals(1, store.clearCalls)
        assertEquals(true, callbackValue)
    }

    private class FakeCookieStore : WebCookieStore {
        var cookie: String? = null
        val readUrls = mutableListOf<String>()
        val writes = mutableListOf<Pair<String, String>>()
        var flushCalls = 0
        var clearCalls = 0

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

        override fun removeAllCookies(callback: (Boolean) -> Unit) {
            clearCalls += 1
            callback(true)
        }
    }
}

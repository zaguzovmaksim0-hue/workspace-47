package dev.junta.firmamobile.browser

import android.net.Uri
import android.webkit.CookieManager
import dev.junta.firmamobile.network.JuntaOriginPolicy

interface WebCookieStore {
    fun getCookie(url: String): String?

    fun setCookie(url: String, value: String)

    fun flush()

    fun removeAllCookies(callback: (Boolean) -> Unit)
}

class AndroidWebCookieStore(
    private val cookieManager: CookieManager = CookieManager.getInstance(),
) : WebCookieStore {
    override fun getCookie(url: String): String? = cookieManager.getCookie(url)

    override fun setCookie(url: String, value: String) {
        cookieManager.setCookie(url, value)
    }

    override fun flush() {
        cookieManager.flush()
    }

    override fun removeAllCookies(callback: (Boolean) -> Unit) {
        cookieManager.removeAllCookies(callback)
    }
}

class WebViewCookieBridge(
    private val cookieStore: WebCookieStore = AndroidWebCookieStore(),
) {
    fun cookieHeaderFor(url: String): String? {
        if (!isAllowedUrl(url)) return null
        return try {
            cookieStore.getCookie(url)
        } catch (_: Exception) {
            null
        }
    }

    fun applySetCookie(url: String, setCookieValue: String): Boolean {
        if (!isAllowedUrl(url) ||
            setCookieValue.length > MAX_SET_COOKIE_CHARS ||
            setCookieValue.any { it == '\r' || it == '\n' || it == '\u0000' }
        ) {
            return false
        }
        return try {
            cookieStore.setCookie(url, setCookieValue)
            cookieStore.flush()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun clearSession(callback: (Boolean) -> Unit) {
        try {
            cookieStore.removeAllCookies(callback)
        } catch (_: Exception) {
            callback(false)
        }
    }

    private fun isAllowedUrl(url: String): Boolean = try {
        JuntaOriginPolicy.isAllowed(Uri.parse(url))
    } catch (_: Exception) {
        false
    }

    private companion object {
        const val MAX_SET_COOKIE_CHARS = 8192
    }
}

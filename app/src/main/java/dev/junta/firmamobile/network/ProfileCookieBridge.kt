package dev.junta.firmamobile.network

import dev.junta.firmamobile.browser.AndroidWebCookieStore
import dev.junta.firmamobile.browser.WebCookieStore
import dev.junta.firmamobile.profile.SiteProfile

class ProfileCookieBridge(
    profile: SiteProfile,
    private val cookieStore: WebCookieStore = AndroidWebCookieStore(),
) {
    private val allowedEndpointUrls = profile.endpoints.values
        .mapTo(linkedSetOf()) { it.url.toASCIIString() }
        .also { require(it.isNotEmpty()) }

    fun cookieHeaderFor(url: ValidatedNetworkUrl): String? {
        val canonical = url.uri.toASCIIString()
        if (canonical !in allowedEndpointUrls) return null
        return try {
            cookieStore.getCookie(canonical)
        } catch (_: Exception) {
            null
        }
    }

    fun applySetCookie(url: ValidatedNetworkUrl, value: String): Boolean {
        val canonical = url.uri.toASCIIString()
        if (canonical !in allowedEndpointUrls ||
            value.isEmpty() ||
            value.length > MAX_SET_COOKIE_CHARS ||
            value.any { it == '\r' || it == '\n' || it == '\u0000' }
        ) {
            return false
        }
        return try {
            cookieStore.setCookie(canonical, value)
            cookieStore.flush()
            true
        } catch (_: Exception) {
            false
        }
    }

    private companion object {
        const val MAX_SET_COOKIE_CHARS = 8192
    }
}

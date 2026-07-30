package dev.junta.firmamobile.browser

import android.webkit.CookieManager
import androidx.webkit.CookieManagerCompat
import dev.junta.firmamobile.network.ProfileCookieBridge
import dev.junta.firmamobile.network.ValidatedNetworkUrl
import dev.junta.firmamobile.profile.SiteProfile

interface WebCookieStore {
    fun getCookie(url: String): String?

    fun setCookie(url: String, value: String)

    fun getCookieInfo(url: String): List<String>? = null

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

    override fun getCookieInfo(url: String): List<String> =
        CookieManagerCompat.getCookieInfo(cookieManager, url)

    override fun flush() {
        cookieManager.flush()
    }

    override fun removeAllCookies(callback: (Boolean) -> Unit) {
        cookieManager.removeAllCookies(callback)
    }
}

/**
 * Compatibility facade retained for callers that still use the historical name.
 * The bridge is profile-bound and cannot access a URL outside that profile's exact endpoints.
 */
internal class WebViewCookieBridge(
    profile: SiteProfile,
    cookieStore: WebCookieStore = AndroidWebCookieStore(),
) {
    private val delegate = ProfileCookieBridge(profile, cookieStore)

    fun cookieHeaderFor(url: ValidatedNetworkUrl): String? = delegate.cookieHeaderFor(url)

    fun applySetCookie(url: ValidatedNetworkUrl, value: String): Boolean =
        delegate.applySetCookie(url, value)
}

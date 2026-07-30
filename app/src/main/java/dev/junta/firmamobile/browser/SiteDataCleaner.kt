package dev.junta.firmamobile.browser

import android.webkit.WebStorage
import dev.junta.firmamobile.profile.ExactOrigin
import java.net.URI
import java.util.Locale

enum class SiteClearResult {
    CLEARED_EXACTLY,
    WEB_STORAGE_CLEARED_COOKIE_CLEAR_UNAVAILABLE,
    FAILED,
}

interface SiteWebStorage {
    fun deleteOrigin(origin: String)

    fun deleteAllData()
}

class AndroidSiteWebStorage(
    private val webStorage: WebStorage = WebStorage.getInstance(),
) : SiteWebStorage {
    override fun deleteOrigin(origin: String) {
        webStorage.deleteOrigin(origin)
    }

    override fun deleteAllData() {
        webStorage.deleteAllData()
    }
}

class SiteDataCleaner(
    private val cookieStore: WebCookieStore = AndroidWebCookieStore(),
    private val webStorage: SiteWebStorage = AndroidSiteWebStorage(),
) {
    fun clearOrigin(
        currentUrl: URI,
        capabilities: WebViewProfileCapabilities,
    ): SiteClearResult {
        val site = normalize(currentUrl) ?: return SiteClearResult.FAILED
        try {
            webStorage.deleteOrigin(site.origin.serialized)
        } catch (_: Exception) {
            return SiteClearResult.FAILED
        }
        if (!capabilities.getCookieInfo) {
            return SiteClearResult.WEB_STORAGE_CLEARED_COOKIE_CLEAR_UNAVAILABLE
        }
        val cookieInfo = try {
            cookieStore.getCookieInfo(site.url.toASCIIString())
        } catch (_: Exception) {
            null
        } ?: return SiteClearResult.WEB_STORAGE_CLEARED_COOKIE_CLEAR_UNAVAILABLE
        val expirations = cookieInfo.map { cookie ->
            expirationHeader(cookie, site.origin.host)
                ?: return SiteClearResult.WEB_STORAGE_CLEARED_COOKIE_CLEAR_UNAVAILABLE
        }
        if (expirations.isEmpty()) return SiteClearResult.CLEARED_EXACTLY
        return try {
            expirations.forEach { expiry ->
                cookieStore.setCookie(site.url.toASCIIString(), expiry)
            }
            cookieStore.flush()
            SiteClearResult.CLEARED_EXACTLY
        } catch (_: Exception) {
            SiteClearResult.WEB_STORAGE_CLEARED_COOKIE_CLEAR_UNAVAILABLE
        }
    }

    fun clearAllConfirmed(callback: (Boolean) -> Unit) {
        val storageCleared = try {
            webStorage.deleteAllData()
            true
        } catch (_: Exception) {
            false
        }
        try {
            cookieStore.removeAllCookies { cookiesCleared ->
                val flushed = if (cookiesCleared) {
                    try {
                        cookieStore.flush()
                        true
                    } catch (_: Exception) {
                        false
                    }
                } else {
                    false
                }
                callback(storageCleared && cookiesCleared && flushed)
            }
        } catch (_: Exception) {
            callback(false)
        }
    }

    private fun normalize(uri: URI): NormalizedSite? = runCatching {
        require(!uri.isOpaque && uri.scheme == HTTPS && uri.userInfo == null)
        require(uri.port == -1 || uri.port == HTTPS_PORT)
        require(uri.rawPath == null || uri.rawPath.startsWith('/'))
        val origin = ExactOrigin.parse("https://${requireNotNull(uri.host)}")
        val path = uri.rawPath?.takeIf(String::isNotEmpty) ?: "/"
        require(path.length <= MAX_PATH_CHARS && !path.any(Char::isISOControl))
        NormalizedSite(origin, URI(origin.serialized + path))
    }.getOrNull()

    private fun expirationHeader(raw: String, requestHost: String): String? {
        if (raw.isEmpty() || raw.length > MAX_COOKIE_INFO_CHARS || raw.any(Char::isISOControl)) return null
        val parts = raw.split(';')
        val first = parts.first().trim()
        val separator = first.indexOf('=')
        if (separator <= 0) return null
        val name = first.substring(0, separator).trim()
        if (!COOKIE_NAME.matches(name)) return null

        var domain: String? = null
        var path = "/"
        var secure = false
        for (part in parts.drop(1)) {
            val attribute = part.trim()
            if (attribute.isEmpty()) continue
            val equals = attribute.indexOf('=')
            val key = (if (equals >= 0) attribute.substring(0, equals) else attribute)
                .trim()
                .lowercase(Locale.ROOT)
            val value = if (equals >= 0) attribute.substring(equals + 1).trim() else null
            when (key) {
                "domain" -> {
                    val candidate = value?.trimStart('.')?.lowercase(Locale.ROOT) ?: return null
                    if (candidate.isEmpty() || candidate.endsWith('.') || requestHost != candidate) {
                        return null
                    }
                    domain = candidate
                }
                "path" -> {
                    val candidate = value ?: return null
                    if (!candidate.startsWith('/') || candidate.length > MAX_PATH_CHARS ||
                        candidate.any(Char::isISOControl)
                    ) {
                        return null
                    }
                    path = candidate
                }
                "secure" -> if (value == null) secure = true else return null
            }
        }

        return buildString {
            append(name)
            append("=; Max-Age=0; Expires=")
            append(EXPIRED_AT)
            append("; Path=")
            append(path)
            domain?.let {
                append("; Domain=")
                append(it)
            }
            if (secure) append("; Secure")
        }
    }

    private data class NormalizedSite(
        val origin: ExactOrigin,
        val url: URI,
    )

    private companion object {
        const val HTTPS = "https"
        const val HTTPS_PORT = 443
        const val MAX_COOKIE_INFO_CHARS = 8192
        const val MAX_PATH_CHARS = 2048
        const val EXPIRED_AT = "Thu, 01 Jan 1970 00:00:00 GMT"
        val COOKIE_NAME = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]{1,256}")
    }
}

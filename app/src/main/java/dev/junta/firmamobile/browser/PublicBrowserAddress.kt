package dev.junta.firmamobile.browser

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

object PublicBrowserAddress {
    private const val MAX_LENGTH = 8192
    private const val MAX_HOST_LENGTH = 253
    private const val MAX_PORT = 65535
    private const val HTTPS_PORT = 443
    private const val HTTP_PORT = 80
    private const val HTTP_PREFIX = "http://"
    private const val HTTPS_PREFIX = "https://"
    private val dnsLabel = Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?")
    private val numericHost = Regex("[0-9.]+")

    // Existing download/auth callers use host-only credential scopes.
    fun parse(raw: String): URI? = parseNavigation(raw)?.takeIf { it.port == -1 || it.port == HTTPS_PORT }

    internal fun parseNavigation(raw: String): URI? {
        if (!hasAllowedText(raw)) return null
        return parseUri(raw)?.takeIf { hasAllowedTransport(it) && hasAllowedAuthority(it) }
    }

    private fun hasAllowedText(raw: String): Boolean =
        raw.isNotEmpty() && raw.length <= MAX_LENGTH &&
            raw.none { it.isWhitespace() || Character.isISOControl(it) || it == '\\' }

    private fun parseUri(raw: String): URI? = try {
        URI(raw)
    } catch (_: URISyntaxException) {
        null
    }

    private fun hasAllowedTransport(uri: URI): Boolean =
        !uri.isOpaque && uri.scheme.equals("https", ignoreCase = true) &&
            uri.rawUserInfo == null && (uri.port == -1 || uri.port in 1..MAX_PORT)

    private fun hasAllowedAuthority(uri: URI): Boolean {
        val host = uri.host ?: return false
        val authority = if (uri.port == -1) host else "$host:${uri.port}"
        return uri.rawAuthority == authority && hasPublicHost(host)
    }

    private fun hasPublicHost(host: String): Boolean {
        val lowerHost = host.lowercase(Locale.ROOT)
        return host.length <= MAX_HOST_LENGTH && host.contains('.') && !host.endsWith('.') &&
            !numericHost.matches(host) && host.split('.').all { dnsLabel.matches(it) } &&
            !lowerHost.endsWith(".local") && !lowerHost.endsWith(".localhost")
    }

    internal fun upgradeHttp(raw: String): URI? {
        if (!raw.startsWith(HTTP_PREFIX, ignoreCase = true)) return null
        return parseNavigation(HTTPS_PREFIX + raw.substring(HTTP_PREFIX.length))?.let { upgraded ->
            if (upgraded.port == HTTP_PORT) {
                // HTTP's explicit default port must not become an HTTPS service on port 80.
                parseNavigation(HTTPS_PREFIX + upgraded.host +
                    raw.substring(HTTP_PREFIX.length + upgraded.rawAuthority.length))
            } else upgraded
        }
    }
}

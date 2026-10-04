package dev.junta.firmamobile.browser

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

object PublicBrowserAddress {
    private const val MAX_LENGTH = 8192
    private val dnsLabel = Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?")
    private val numericHost = Regex("[0-9.]+")

    fun parse(raw: String): URI? {
        if (
            raw.isEmpty() || raw.length > MAX_LENGTH ||
            raw.any { it.isWhitespace() || Character.isISOControl(it) || it == '\\' }
        ) return null

        val uri = try {
            URI(raw)
        } catch (_: URISyntaxException) {
            return null
        }

        if (
            uri.isOpaque ||
            !uri.scheme.equals("https", ignoreCase = true) ||
            uri.rawUserInfo != null ||
            (uri.port != -1 && uri.port != 443)
        ) return null

        val host = uri.host ?: return null
        if (
            host.length > 253 ||
            !host.contains('.') ||
            host.endsWith('.') ||
            numericHost.matches(host) ||
            host.split('.').any { !dnsLabel.matches(it) }
        ) return null

        val lowerHost = host.lowercase(Locale.ROOT)
        if (lowerHost.endsWith(".local") || lowerHost.endsWith(".localhost")) return null

        return uri
    }
}

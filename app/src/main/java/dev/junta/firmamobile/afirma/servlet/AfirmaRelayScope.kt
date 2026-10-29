package dev.junta.firmamobile.afirma.servlet

import java.net.URI
import java.util.Locale

/** Relay transports are wired, but remain inactive until a later bridge marks a servlet blocked. */
internal fun relayScopeFor(epoch: Long, pageUrl: String): AfirmaRelayStore.Scope? =
    httpsPageOrigin(pageUrl)?.let { origin ->
        runCatching { AfirmaRelayStore.Scope(epoch, origin) }.getOrNull()
    }

internal fun relayServletOrigin(endpoint: URI): String? = runCatching {
    require(endpoint.scheme.equals("https", ignoreCase = true))
    require(endpoint.host != null)
    require(!endpoint.isOpaque && endpoint.rawUserInfo == null &&
        endpoint.rawQuery == null && endpoint.rawFragment == null)
    require(endpoint.port == -1 || endpoint.port in MIN_PORT..MAX_PORT)
    normalizedHttpsOrigin(endpoint)
}.getOrNull()

private fun httpsPageOrigin(pageUrl: String): String? = runCatching {
    val page = URI(pageUrl)
    require(page.scheme.equals("https", ignoreCase = true))
    require(page.host != null)
    require(!page.isOpaque && page.rawUserInfo == null)
    require(page.port == -1 || page.port in MIN_PORT..MAX_PORT)
    normalizedHttpsOrigin(page)
}.getOrNull()

private fun normalizedHttpsOrigin(uri: URI): String {
    val host = checkNotNull(uri.host).lowercase(Locale.ROOT)
    return when (uri.port) {
        -1, DEFAULT_HTTPS_PORT -> "https://$host"
        else -> "https://$host:${uri.port}"
    }
}

private const val DEFAULT_HTTPS_PORT = 443
private const val MIN_PORT = 1
private const val MAX_PORT = 65535

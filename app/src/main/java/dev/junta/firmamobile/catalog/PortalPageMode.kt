package dev.junta.firmamobile.catalog

import java.net.URI
import java.util.Locale

/**
 * The reviewed portal mode for a top-level page opened in ordinary browsing, or null.
 *
 * The page only selects among bundled catalog bindings whose launch page has the same scheme,
 * host, default port and path; `;` session parameters, query and fragment are ignored. Authority
 * still comes from [PortalCatalogRepository.resolveLaunch]: the result is the registry-validated
 * profile start URL, never the page URL. Bindings without certificate capabilities, inactive
 * profiles and pages shared by different portal modes yield null.
 */
fun PortalCatalogRepository.resolvePortalModeForPage(pageUrl: String): PortalLaunchTarget? {
    val page = runCatching { URI(pageUrl) }.getOrNull()?.let { portalPageKey(it) } ?: return null
    return portals().asSequence()
        .filter { item -> item.capabilities.isNotEmpty() }
        .filter { item -> resolveUniversalOpenTarget(item)?.let { portalPageKey(it.entryUrl) } == page }
        .mapNotNull { item -> resolveLaunch(item) }
        .distinct()
        .singleOrNull()
}

/** Comparable HTTPS page identity: lowercase host, default port and path without `;` parameters. */
internal fun portalPageKey(page: URI): String? {
    val uri = page.takeIf { it.scheme.equals("https", ignoreCase = true) && it.rawUserInfo == null }
        ?.takeIf { it.port == -1 || it.port == HTTPS_PORT }
    val host = uri?.host?.lowercase(Locale.ROOT)
    val path = uri?.rawPath?.ifEmpty { "/" }
        ?.split('/')
        ?.joinToString("/") { segment -> segment.substringBefore(';') }
    return if (host == null || path == null) null else "https://$host$path"
}

private const val HTTPS_PORT = 443

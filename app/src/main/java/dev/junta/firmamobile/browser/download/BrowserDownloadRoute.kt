package dev.junta.firmamobile.browser.download

internal enum class BrowserDownloadRoute { DATA, BLOB, POST, GET, UNSUPPORTED }

/** Chooses capture only; URL validation and document handling belong to the selected path. */
internal fun selectBrowserDownloadRoute(url: String, method: ObservedMethod?): BrowserDownloadRoute = when {
    url.regionMatches(0, "data:", 0, 5, ignoreCase = true) -> BrowserDownloadRoute.DATA
    url.regionMatches(0, "blob:", 0, 5, ignoreCase = true) -> BrowserDownloadRoute.BLOB
    else -> when (method) {
        ObservedMethod.POST -> BrowserDownloadRoute.POST
        ObservedMethod.OTHER -> BrowserDownloadRoute.UNSUPPORTED
        // An unobserved address is the target of a redirect, which is a GET.
        ObservedMethod.GET, null -> BrowserDownloadRoute.GET
    }
}

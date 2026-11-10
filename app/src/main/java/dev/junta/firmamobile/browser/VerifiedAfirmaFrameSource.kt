package dev.junta.firmamobile.browser

import dev.junta.firmamobile.afirma.servlet.NativeAfirmaLimits
import java.net.URI
import java.util.Locale

internal data class AfirmaFrameDocument(
    val pageUrl: String,
    val navigationEpoch: Long,
    val documentId: String,
)

internal data class AfirmaFrameMessage(val documentId: String, val uri: String)

/** Browser-supplied frame metadata is authority; a URI or an iframe's parent is not. */
internal object VerifiedAfirmaFrameSource {
    private val documentIdPattern = Regex("[0-9a-f]{32}")
    private const val DOCUMENT_ID_CHARS = 32
    private const val MESSAGE_PREFIX_CHARS = DOCUMENT_ID_CHARS + 1

    fun parseMessage(raw: String): AfirmaFrameMessage? {
        if (raw.length <= MESSAGE_PREFIX_CHARS ||
            raw.length > NativeAfirmaLimits.URI_CHARS + MESSAGE_PREFIX_CHARS ||
            raw[DOCUMENT_ID_CHARS] != '\n'
        ) return null
        val documentId = raw.substring(0, DOCUMENT_ID_CHARS)
        if (!documentIdPattern.matches(documentId)) return null
        val uri = raw.substring(MESSAGE_PREFIX_CHARS)
        if (!uri.startsWith("afirma://", ignoreCase = true) || uri.any(Char::isISOControl)) return null
        return AfirmaFrameMessage(documentId, uri)
    }

    fun permitsSource(sourceOrigin: String, isMainFrame: Boolean, document: AfirmaFrameDocument): Boolean {
        if (!isMainFrame || document.navigationEpoch < 0 || !documentIdPattern.matches(document.documentId)) return false
        val page = PublicBrowserAddress.parseNavigation(document.pageUrl) ?: return false
        val source = PublicBrowserAddress.parseNavigation(sourceOrigin) ?: return false
        if (source.rawQuery != null || source.rawFragment != null ||
            !source.rawPath.isNullOrEmpty() && source.rawPath != "/"
        ) return false
        return origin(page) == origin(source)
    }

    fun isCurrent(expected: AfirmaFrameDocument, actual: AfirmaFrameDocument): Boolean =
        expected == actual && documentIdPattern.matches(actual.documentId)

    fun documentIdFromJavascript(result: String?): String? {
        if (result == null || result.length != DOCUMENT_ID_CHARS + 2 ||
            result.first() != '"' || result.last() != '"'
        ) return null
        return result.substring(1, result.lastIndex).takeIf(documentIdPattern::matches)
    }

    private fun origin(uri: URI): String {
        val port = if (uri.port == -1) 443 else uri.port
        return "https://${uri.host.lowercase(Locale.ROOT)}:$port"
    }
}

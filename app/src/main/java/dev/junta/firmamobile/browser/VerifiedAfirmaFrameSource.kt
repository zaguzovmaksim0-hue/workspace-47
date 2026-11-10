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
    private const val JSON_QUOTE_CHARS = 2
    private const val HTTPS_DEFAULT_PORT = 443

    fun parseMessage(raw: String): AfirmaFrameMessage? {
        if (!hasMessagePrefix(raw)) return null
        val documentId = raw.substring(0, DOCUMENT_ID_CHARS)
        val uri = raw.substring(MESSAGE_PREFIX_CHARS)
        return if (documentIdPattern.matches(documentId) &&
            uri.startsWith("afirma://", ignoreCase = true) && !uri.any(Char::isISOControl)
        ) AfirmaFrameMessage(documentId, uri) else null
    }

    private fun hasMessagePrefix(raw: String): Boolean =
        raw.length > MESSAGE_PREFIX_CHARS &&
            raw.length <= NativeAfirmaLimits.URI_CHARS + MESSAGE_PREFIX_CHARS &&
            raw[DOCUMENT_ID_CHARS] == '\n'

    fun permitsSource(sourceOrigin: String, isMainFrame: Boolean, document: AfirmaFrameDocument): Boolean {
        if (!isMainFrame || document.navigationEpoch < 0 ||
            !documentIdPattern.matches(document.documentId)
        ) return false
        val page = PublicBrowserAddress.parseNavigation(document.pageUrl)
        val source = PublicBrowserAddress.parseNavigation(sourceOrigin)?.takeIf(::isBareOrigin)
        return page != null && source != null && origin(page) == origin(source)
    }

    private fun isBareOrigin(source: URI): Boolean {
        val originPath = source.rawPath.isNullOrEmpty() || source.rawPath == "/"
        return source.rawQuery == null && source.rawFragment == null && originPath
    }

    fun isCurrent(expected: AfirmaFrameDocument, actual: AfirmaFrameDocument): Boolean =
        expected == actual && documentIdPattern.matches(actual.documentId)

    fun documentIdFromJavascript(result: String?): String? =
        result?.takeIf(::isQuotedDocumentId)?.let { it.substring(1, it.lastIndex) }
            ?.takeIf(documentIdPattern::matches)

    private fun isQuotedDocumentId(result: String): Boolean =
        result.length == DOCUMENT_ID_CHARS + JSON_QUOTE_CHARS && result.first() == '"' && result.last() == '"'

    private fun origin(uri: URI): String {
        val port = if (uri.port == -1) HTTPS_DEFAULT_PORT else uri.port
        return "https://${uri.host.lowercase(Locale.ROOT)}:$port"
    }
}

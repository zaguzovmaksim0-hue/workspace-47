package dev.junta.firmamobile.network

import java.net.URI

enum class CaibBatchOperation { PRESIGN, POSTSIGN }

data class CaibBatchUrlBinding(
    val operation: CaibBatchOperation,
    val requestToken: String,
    val url: URI,
)

/** Exact PortaFIB requestPlugin triphase endpoints observed for CAIB generic instance signing. */
class CaibBatchUrlPolicy {
    fun validate(rawUrl: String, expectedOperation: CaibBatchOperation): CaibBatchUrlBinding? {
        if (rawUrl.isEmpty() || rawUrl.length > MAX_URL_CHARS || rawUrl.any(Char::isISOControl)) return null
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return null
        if (uri.isOpaque || uri.scheme != "https" || uri.host != HOST ||
            (uri.port != -1 && uri.port != 443) || uri.userInfo != null ||
            uri.rawQuery != null || uri.rawFragment != null ||
            (uri.rawAuthority != HOST && uri.rawAuthority != "$HOST:443")
        ) return null
        val match = PATH.matchEntire(uri.rawPath ?: return null) ?: return null
        val operation = when (match.groupValues[2]) {
            "BatchPresigner" -> CaibBatchOperation.PRESIGN
            "BatchPostsigner" -> CaibBatchOperation.POSTSIGN
            else -> return null
        }
        if (operation != expectedOperation) return null
        return CaibBatchUrlBinding(operation, match.groupValues[1], uri)
    }

    fun validatePair(preSignerUrl: String, postSignerUrl: String): Pair<CaibBatchUrlBinding, CaibBatchUrlBinding>? {
        val pre = validate(preSignerUrl, CaibBatchOperation.PRESIGN) ?: return null
        val post = validate(postSignerUrl, CaibBatchOperation.POSTSIGN) ?: return null
        return (pre to post).takeIf { pre.requestToken == post.requestToken }
    }

    companion object {
        const val ORIGIN = "https://intranet.caib.es"
        private const val HOST = "intranet.caib.es"
        private const val MAX_URL_CHARS = 2_048
        private val PATH = Regex(
            "/portafibback/public/signmodule/requestPlugin/([A-Za-z0-9_-]{28})/-1/(BatchPresigner|BatchPostsigner)",
        )
    }
}

package dev.junta.firmamobile.network

import java.net.URI

enum class LugoBatchOperation {
    PRESIGN,
    POSTSIGN,
}

data class LugoBatchUrlBinding(
    val operation: LugoBatchOperation,
    val sessionId: String,
    val url: URI,
)

/** Exact public clientSigner multi-node endpoint contract observed on Deputacion de Lugo. */
class LugoBatchUrlPolicy {
    fun validate(rawUrl: String, expectedOperation: LugoBatchOperation): LugoBatchUrlBinding? {
        if (rawUrl.isEmpty() || rawUrl.length > MAX_URL_CHARS || rawUrl.any(Char::isISOControl)) return null
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return null
        if (uri.isOpaque || uri.scheme != "https" || uri.host != HOST ||
            (uri.port != -1 && uri.port != 443) || uri.userInfo != null ||
            uri.rawQuery != null || uri.rawFragment != null ||
            (uri.rawAuthority != HOST && uri.rawAuthority != "$HOST:443")
        ) return null
        val match = PATH.matchEntire(uri.rawPath ?: return null) ?: return null
        val operation = when (match.groupValues[1]) {
            "BatchPresigner" -> LugoBatchOperation.PRESIGN
            "BatchPostsigner" -> LugoBatchOperation.POSTSIGN
            else -> return null
        }
        if (operation != expectedOperation) return null
        val sessionId = match.groupValues[2]
        return LugoBatchUrlBinding(operation, sessionId, uri)
    }

    fun validatePair(preSignerUrl: String, postSignerUrl: String): Pair<LugoBatchUrlBinding, LugoBatchUrlBinding>? {
        val pre = validate(preSignerUrl, LugoBatchOperation.PRESIGN) ?: return null
        val post = validate(postSignerUrl, LugoBatchOperation.POSTSIGN) ?: return null
        return (pre to post).takeIf { pre.sessionId == post.sessionId }
    }

    companion object {
        const val ORIGIN = "https://sede.deputacionlugo.org"
        private const val HOST = "sede.deputacionlugo.org"
        private const val MAX_URL_CHARS = 2_048
        private val PATH = Regex(
            "/opencms/clientsigner/(BatchPresigner|BatchPostsigner)/service/([A-Fa-f0-9]{32})",
        )
    }
}

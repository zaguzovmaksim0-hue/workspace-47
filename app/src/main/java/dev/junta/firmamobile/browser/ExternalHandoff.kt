package dev.junta.firmamobile.browser

import android.content.Intent
import android.net.Uri
import dev.junta.firmamobile.afirma.AfirmaUriParser
import dev.junta.firmamobile.profile.ExactOrigin
import java.net.URI

/** Reconstructs the minimal external intent; never executes a page-supplied Intent. */
object ExternalHandoff {
    enum class Kind { BROWSER, AUTOFIRMA }

    data class Request(
        val kind: Kind,
        val uri: Uri,
        val navigationEpoch: Long,
        val sourceHost: String?,
    )

    fun intentFor(request: Request): Intent? {
        val raw = request.uri.toString()
        if (raw.isBlank() || raw.length > AfirmaUriParser.MAX_URI_CHARS || raw.any(Char::isISOControl)) return null
        val target = runCatching { URI(raw) }.getOrNull() ?: return null
        if (target.isOpaque || target.rawUserInfo != null || target.host == null) return null
        return when (request.kind) {
            Kind.BROWSER -> {
                if (!target.scheme.equals("https", ignoreCase = true) || target.port !in setOf(-1, 443) ||
                    runCatching { ExactOrigin.parse("https://${target.host}") }.isFailure
                ) return null
                Intent(Intent.ACTION_VIEW, request.uri).addCategory(Intent.CATEGORY_BROWSABLE)
            }
            Kind.AUTOFIRMA -> {
                // Input is an already parsed request or the separately validated
                // official protocol. Never forward it to an arbitrary handler.
                if (!target.scheme.equals("afirma", ignoreCase = true) || target.port != -1 ||
                    target.rawFragment != null || target.host.lowercase() !in OPERATIONS ||
                    target.rawPath !in setOf("", "/") || target.rawQuery.isNullOrBlank()
                ) return null
                Intent(Intent.ACTION_VIEW, request.uri)
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                    .setPackage(JuntaNavigationPolicy.AUTOFIRMA_PACKAGE)
            }
        }
    }

    private val OPERATIONS = setOf("sign", "selectcert", "websocket")
}

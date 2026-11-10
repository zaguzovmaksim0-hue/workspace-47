package dev.junta.firmamobile.browser

import android.content.Intent
import dev.junta.firmamobile.afirma.AfirmaUriParser
import dev.junta.firmamobile.afirma.servlet.NativeAfirmaLimits
import java.net.URI

/** Extract a recognized protocol request for parsing or an explicit refusal.
 * Recognition grants no signing capability. Never execute the site's Intent object. */
internal object NativeAfirmaNavigation {
    fun extract(raw: String): String? {
        if (raw.isBlank() || raw.length > NativeAfirmaLimits.URI_CHARS || raw.any(Char::isISOControl)) return null
        val scheme = raw.substringBefore(':', "")
        val candidate = when {
            scheme.equals("afirma", true) -> raw
            scheme.equals("intent", true) -> {
                // Keep the legacy wrapper budget before Android allocates parsed Intent metadata.
                if (raw.length > AfirmaUriParser.MAX_URI_CHARS) return null
                val intent = runCatching { Intent.parseUri(raw, Intent.URI_INTENT_SCHEME) }.getOrNull() ?: return null
                if (intent.`package` != JuntaNavigationPolicy.AUTOFIRMA_PACKAGE || intent.component != null ||
                    intent.selector != null || intent.getStringExtra("browser_fallback_url") != null
                ) return null
                intent.dataString ?: return null
            }
            else -> return null
        }
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        if (!hasAllowedAfirmaShape(uri) || !hasAllowedAfirmaOperation(uri)) return null
        return candidate
    }

    private fun hasAllowedAfirmaShape(uri: java.net.URI): Boolean =
        hasAllowedAfirmaScheme(uri) && hasAllowedAfirmaAddress(uri)

    private fun hasAllowedAfirmaScheme(uri: java.net.URI): Boolean =
        uri.scheme.equals("afirma", true) && !uri.isOpaque

    private fun hasAllowedAfirmaAddress(uri: java.net.URI): Boolean =
        uri.rawUserInfo == null && uri.port == -1 && uri.rawFragment == null

    private fun hasAllowedAfirmaOperation(uri: java.net.URI): Boolean =
        hasAllowedAfirmaPath(uri) &&
            uri.host?.lowercase(java.util.Locale.ROOT) in RECOGNIZED_AFIRMA_OPERATIONS

    private fun hasAllowedAfirmaPath(uri: java.net.URI): Boolean =
        uri.rawPath == null || uri.rawPath in setOf("", "/")

    private val RECOGNIZED_AFIRMA_OPERATIONS = setOf(
        "sign", "cosign", "countersign", "batch", "selectcert",
        // The native parser refuses this desktop-only protocol before creating an operation.
        "websocket",
    )
}

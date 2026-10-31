package dev.junta.firmamobile.browser

import android.content.Intent
import java.net.URI

/** Extract a protocol request as data. Never execute the site's Intent object. */
internal object NativeAfirmaNavigation {
    fun extract(raw: String): String? {
        if (raw.isBlank() || raw.length > 1024 * 1024 || raw.any(Char::isISOControl)) return null
        val scheme = raw.substringBefore(':', "")
        val candidate = when {
            scheme.equals("afirma", true) -> raw
            scheme.equals("intent", true) -> {
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
            uri.host?.lowercase(java.util.Locale.ROOT) in ALLOWED_AFIRMA_OPERATIONS

    private fun hasAllowedAfirmaPath(uri: java.net.URI): Boolean =
        uri.rawPath == null || uri.rawPath in setOf("", "/")

    private val ALLOWED_AFIRMA_OPERATIONS = setOf(
        "sign", "cosign", "countersign", "batch", "selectcert",
    )
}

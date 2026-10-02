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
        if (!uri.scheme.equals("afirma", true) || uri.isOpaque || uri.rawUserInfo != null ||
            uri.port != -1 || uri.rawFragment != null || uri.rawPath !in setOf("", "/") ||
            uri.host?.lowercase(java.util.Locale.ROOT) !in setOf("sign", "cosign", "selectcert")
        ) return null
        return candidate
    }
}

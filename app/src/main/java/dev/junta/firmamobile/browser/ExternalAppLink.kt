package dev.junta.firmamobile.browser

import android.content.Intent
import android.net.Uri
import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/** Only immutable navigation data is retained, never an executable page-supplied
 * Intent. URI query/fragment can be sensitive and are excluded from toString/UI. */
class ExternalAppLink private constructor(
    val uri: Uri,
    val packageName: String?,
    val webFallback: Uri?,
    val scheme: String,
) {
    fun minimalIntent(): Intent = Intent(Intent.ACTION_VIEW, uri)
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .apply { if (packageName != null) setPackage(packageName) }

    fun fallbackIntent(): Intent? = webFallback?.let {
        Intent(Intent.ACTION_VIEW, it).addCategory(Intent.CATEGORY_BROWSABLE)
    }

    override fun toString(): String = "ExternalAppLink(scheme=$scheme, packaged=${packageName != null}, fallback=${webFallback != null})"

    companion object {
        private const val MAX_CHARS = 16_384
        private const val APP_PACKAGE = "dev.junta.firmamobile"
        private val SCHEME = Regex("[a-z][a-z0-9+.-]{0,63}")
        private val PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
        private val RESERVED = setOf(
            "http", "https", "afirma", "autofirma", "intent", "android-app", "file", "content", "data",
            "javascript", "blob", "about", "view-source", "chrome", "chrome-native", "chrome-search",
            "devtools", "market", "package", "tel", "sms", "smsto", "mms", "mmsto", "sip", "voicemail",
            "mailto", "geo", "settings",
        )

        /** Recognizes a small inert subset of Chrome intent URLs, rebuilding the
         * eventual ACTION_VIEW rather than forwarding flags/components/extras. */
        fun parse(raw: String): ExternalAppLink? {
            if (raw.isEmpty() || raw.length > MAX_CHARS || raw.any(::unsafeCharacter)) return null
            var data = raw
            var targetPackage: String? = null
            var fallback: Uri? = null
            if (raw.startsWith("intent:", ignoreCase = true)) {
                val start = raw.indexOf("#Intent;")
                if (start < 7 || !raw.endsWith(";end") || raw.indexOf("#Intent;", start + 1) >= 0) return null
                val fields = raw.substring(start + 8, raw.length - 4).split(';')
                if (fields.size !in 1..16) return null
                val values = mutableMapOf<String, String>()
                for (field in fields) {
                    val separator = field.indexOf('=')
                    if (separator < 1) return null
                    val key = field.substring(0, separator)
                    if (key !in setOf("scheme", "package", "action", "category", "S.browser_fallback_url") ||
                        values.put(key, field.substring(separator + 1)) != null
                    ) return null
                }
                if (values["action"]?.let { it != Intent.ACTION_VIEW } == true ||
                    values["category"]?.let { it != Intent.CATEGORY_BROWSABLE } == true
                ) return null
                val scheme = values["scheme"]?.lowercase(Locale.ROOT) ?: return null
                if (!SCHEME.matches(scheme) || scheme in RESERVED) return null
                targetPackage = values["package"]
                if (targetPackage != null && (targetPackage.length > 255 || !PACKAGE.matches(targetPackage) ||
                        targetPackage == APP_PACKAGE || targetPackage == "es.gob.afirma")
                ) return null
                values["S.browser_fallback_url"]?.let { encoded ->
                    // Intent extras are percent-encoded, not form-encoded.
                    val decoded = runCatching { URLDecoder.decode(encoded.replace("+", "%2B"), "UTF-8") }.getOrNull()
                        ?: return null
                    val admitted = PublicBrowserAddress.parse(decoded) ?: return null
                    fallback = Uri.parse(admitted.toASCIIString())
                }
                data = scheme + ":" + raw.substring(7, start)
            }
            val parsed = runCatching { URI(data) }.getOrNull() ?: return null
            val scheme = parsed.scheme?.lowercase(Locale.ROOT) ?: return null
            if (!SCHEME.matches(scheme) || scheme in RESERVED || scheme.startsWith("android.") ||
                scheme.startsWith("chrome-") || parsed.rawSchemeSpecificPart.isNullOrEmpty() ||
                parsed.rawUserInfo != null
            ) return null
            // Encoded newlines/bidi in routing fields must not become invisible
            // authority delimiters when another application decodes the URI.
            if (data.contains(Regex("(?i)%0[0ad]"))) return null
            return ExternalAppLink(Uri.parse(data), targetPackage, fallback, scheme)
        }

        private fun unsafeCharacter(char: Char): Boolean = char.isWhitespace() || Character.isISOControl(char) ||
            char == '\\' || char in '\u202a'..'\u202e' || char in '\u2066'..'\u2069'
    }
}

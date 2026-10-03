package dev.junta.firmamobile.browser

import java.util.Locale

/** The callback has no full request URL or main-frame marker. Only a
 * same-host challenge on the visible public HTTPS page is admitted. */
internal data class HttpAuthScope(val server: String, val pageOrigin: String, val realm: String) {
    companion object {
        fun from(pageUrl: String, host: String, realm: String): HttpAuthScope? {
            val page = PublicBrowserAddress.parse(pageUrl) ?: return null
            if (host.isEmpty() || host.any { !it.isLetterOrDigit() && it != '.' && it != '-' }) return null
            val challenge = PublicBrowserAddress.parse("https://$host") ?: return null
            if (!challenge.host.equals(page.host, ignoreCase = true)) return null
            if (realm.length > 256 || realm.any {
                    Character.isISOControl(it) || it in '\u202a'..'\u202e' || it in '\u2066'..'\u2069'
                }) return null
            val server = challenge.host.lowercase(Locale.ROOT)
            return HttpAuthScope(server, "https://$server", realm)
        }
    }
}

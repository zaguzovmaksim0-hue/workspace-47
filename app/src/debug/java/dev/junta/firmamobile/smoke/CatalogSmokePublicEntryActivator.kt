package dev.junta.firmamobile.smoke

import android.webkit.WebView
import dev.junta.firmamobile.profile.ProfileId

/** Fixed QA-only browser action for a reviewed public entry. No caller-controlled DOM input. */
internal object CatalogSmokePublicEntryActivator {
    fun activate(profileId: ProfileId, webView: WebView?): Boolean {
        if (profileId != AEAT_PROFILE || webView?.url != AEAT_SOURCE_URL) return false
        webView.post {
            if (webView.url == AEAT_SOURCE_URL) {
                webView.evaluateJavascript(AEAT_ACTIVATE_SCRIPT, null)
            }
        }
        return true
    }

    private val AEAT_PROFILE = ProfileId("aeat-mis-datos-censales")
    private const val AEAT_SOURCE_URL =
        "https://sede.agenciatributaria.gob.es/Sede/mi-area-personal.html"
    private const val AEAT_TARGET_URL =
        "https://www1.agenciatributaria.gob.es/wlpl/BUGC-JDIT/MdcAcceso"
    private val AEAT_ACTIVATE_SCRIPT =
        """(() => {
            if (location.href !== '$AEAT_SOURCE_URL') return;
            const target = '$AEAT_TARGET_URL';
            const link = Array.from(document.querySelectorAll('a')).find((candidate) =>
                candidate.textContent.trim() === 'Mis datos censales' && candidate.href === target
            );
            if (link) link.click();
        })();""".trimIndent()
}

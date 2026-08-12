package dev.junta.firmamobile.browser

import android.os.Bundle
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SiteProfileRegistry
import dev.junta.firmamobile.profile.TrustMode
import java.net.URI

/**
 * Browser process state is deliberately not restored from WebView.saveState().
 * Only catalog-selected entry URLs may start a fresh browser session.
 */
internal object BrowserSessionStatePolicy {
    internal const val LEGACY_WEBVIEW_HISTORY_KEY = "junta_webview_history"

    fun discardLegacyWebViewState(savedInstanceState: Bundle?) {
        savedInstanceState?.remove(LEGACY_WEBVIEW_HISTORY_KEY)
    }

    fun validatedEntryUrl(
        registry: SiteProfileRegistry,
        profileId: ProfileId,
        entryUrl: URI,
    ): String? {
        val rawUrl = entryUrl.toASCIIString()
        val resolution = runCatching {
            BrowserUrlPolicy(
                registry = registry,
                selectedProfileId = profileId,
            ).resolve(rawUrl, profileId)
        }.getOrNull() ?: return null

        if (resolution.site?.profile?.profileId != profileId) return null
        if (resolution.trustMode !in SAFE_ENTRY_TRUST_MODES) return null
        return resolution.uri?.toASCIIString()
    }

    private val SAFE_ENTRY_TRUST_MODES = setOf(
        TrustMode.TRUSTED_SIGNING,
        TrustMode.TRUSTED_CLIENT_AUTH,
        TrustMode.TRUSTED_BROWSE,
    )
}

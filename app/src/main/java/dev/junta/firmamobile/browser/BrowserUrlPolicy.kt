package dev.junta.firmamobile.browser

import dev.junta.firmamobile.profile.ExactOrigin
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.ResolvedSiteProfile
import dev.junta.firmamobile.profile.SiteProfileRegistry
import dev.junta.firmamobile.profile.TrustMode
import java.net.URI

data class BrowserUrlResolution(
    val uri: URI?,
    val site: ResolvedSiteProfile?,
    val trustMode: TrustMode,
)

class BrowserUrlPolicy(
    private val registry: SiteProfileRegistry,
    private val selectedProfileId: ProfileId?,
    private val externalOnlyOrigins: Set<ExactOrigin> = emptySet(),
) {
    init {
        require(selectedProfileId == null || registry.profile(selectedProfileId) != null) {
            "Selected browser profile is not active: ${selectedProfileId?.value}"
        }
    }

    fun isActiveProfile(profileId: ProfileId): Boolean = registry.profile(profileId) != null

    fun resolve(rawUrl: String, activeProfileId: ProfileId? = null): BrowserUrlResolution {
        if (rawUrl.isBlank() || rawUrl.length > MAX_URL_CHARS || rawUrl.any(Char::isISOControl)) {
            return blocked()
        }
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return blocked()
        if (uri.isOpaque || !uri.scheme.equals("https", ignoreCase = true) || uri.host == null || uri.userInfo != null ||
            uri.port !in setOf(-1, 443)
        ) {
            return blocked()
        }
        if (runCatching { ExactOrigin.parse("https://${uri.host}") }.isFailure) return blocked()

        if (selectedProfileId == null) {
            // A public session never adopts a registered profile, even if the
            // page redirects to a known signing host or supplies an active ID.
            val admitted = PublicBrowserAddress.parse(rawUrl) ?: return blocked()
            val origin = ExactOrigin.parse("https://${admitted.host}")
            return BrowserUrlResolution(admitted, null,
                if (origin in externalOnlyOrigins) TrustMode.EXTERNAL_ONLY else TrustMode.BROWSE_ONLY)
        }
        val activeSelectedProfile = activeProfileId?.takeIf { it == selectedProfileId }
        if (activeProfileId == null) {
            val selectedProfile = registry.profile(selectedProfileId)
            if (selectedProfile != null &&
                uri.toASCIIString() == selectedProfile.startUrl.toASCIIString()
            ) {
                registry.resolveForProfile(selectedProfileId, uri)?.let { selectedStart ->
                    return BrowserUrlResolution(uri, selectedStart, selectedStart.trustMode)
                }
            }
        }
        val transitioned = activeSelectedProfile?.let { registry.resolveRedirect(it, uri) }
        if (transitioned != null) {
            return BrowserUrlResolution(uri, transitioned, transitioned.trustMode)
        }
        val activeResolution = activeSelectedProfile?.let { registry.resolveForProfile(it, uri) }
        if (activeResolution != null) {
            return BrowserUrlResolution(uri, activeResolution, activeResolution.trustMode)
        }

        val direct = registry.resolve(uri)
        if (direct != null && direct.profile.profileId != selectedProfileId) {
            // Crossing a profile is ordinary browsing, never inherited signing trust.
            return BrowserUrlResolution(uri, null, TrustMode.BROWSE_ONLY)
        }
        if (direct != null) {
            return BrowserUrlResolution(uri, direct, direct.trustMode)
        }

        val origin = ExactOrigin.parse("https://${uri.host}")
        return BrowserUrlResolution(
            uri,
            null,
            if (origin in externalOnlyOrigins) TrustMode.EXTERNAL_ONLY else TrustMode.BROWSE_ONLY,
        )
    }

    private fun blocked() = BrowserUrlResolution(null, null, TrustMode.BLOCKED)

    private companion object {
        const val MAX_URL_CHARS = 8_192
    }
}

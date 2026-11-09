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
        val uri = PublicBrowserAddress.parseNavigation(rawUrl) ?: return blocked()

        if (selectedProfileId == null || uri.port !in setOf(-1, 443)) {
            // A public session never adopts a registered profile, even if the
            // page supplies an active ID. Other ports are different origins.
            return browseOnly(uri)
        }
        val activeSelectedProfile = activeProfileId?.takeIf { it == selectedProfileId }
        if (activeProfileId == null) {
            val selectedProfile = registry.profile(selectedProfileId)
            if (selectedProfile != null &&
                uri.toASCIIString() == selectedProfile.startUrl.toASCIIString()
            ) {
                registry.resolveForProfile(selectedProfileId, uri)?.let { selectedStart ->
                    // REAL E2E 42633168933: a start that is also the client-auth source must activate the profile.
                    val start = registry.resolve(uri)?.takeIf {
                        selectedStart.trustMode == TrustMode.TRUSTED_BROWSE && it.profile.profileId == selectedProfileId
                    } ?: selectedStart
                    return BrowserUrlResolution(uri, start, start.trustMode)
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

        return browseOnly(uri)
    }

    private fun blocked() = BrowserUrlResolution(null, null, TrustMode.BLOCKED)

    private fun browseOnly(uri: URI): BrowserUrlResolution {
        val externalOnly = uri.port in setOf(-1, 443) &&
            ExactOrigin.parse("https://${uri.host}") in externalOnlyOrigins
        return BrowserUrlResolution(uri, null,
            if (externalOnly) TrustMode.EXTERNAL_ONLY else TrustMode.BROWSE_ONLY)
    }
}

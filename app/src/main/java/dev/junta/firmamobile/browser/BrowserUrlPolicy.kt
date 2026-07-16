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
    private val externalOnlyOrigins: Set<ExactOrigin> = emptySet(),
) {
    fun isActiveProfile(profileId: ProfileId): Boolean = registry.profile(profileId) != null

    fun resolve(rawUrl: String, activeProfileId: ProfileId? = null): BrowserUrlResolution {
        if (rawUrl.isBlank() || rawUrl.length > MAX_URL_CHARS || rawUrl.any(Char::isISOControl)) {
            return blocked()
        }
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return blocked()
        if (uri.isOpaque || !uri.scheme.equals("https", ignoreCase = true) || uri.host == null || uri.userInfo != null ||
            uri.port !in setOf(-1, 443) || uri.rawFragment != null
        ) {
            return blocked()
        }
        if (runCatching { ExactOrigin.parse("https://${uri.host}") }.isFailure) return blocked()
        val direct = registry.resolve(uri)
        if (direct != null) {
            val transitioned = activeProfileId?.let { registry.resolveRedirect(it, uri) }
            val resolved = transitioned ?: direct
            return BrowserUrlResolution(uri, resolved, resolved.trustMode)
        }
        val origin = ExactOrigin.parse("https://${uri.host}")
        return BrowserUrlResolution(
            uri,
            null,
            if (origin in externalOnlyOrigins) TrustMode.EXTERNAL_ONLY else TrustMode.BROWSE_ONLY,
        )
    }

    private fun blocked() = BrowserUrlResolution(null, null, TrustMode.BLOCKED)

    private companion object { const val MAX_URL_CHARS = 8_192 }
}

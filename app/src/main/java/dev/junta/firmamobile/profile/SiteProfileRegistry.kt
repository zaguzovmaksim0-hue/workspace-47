package dev.junta.firmamobile.profile

import android.net.Uri
import dev.junta.firmamobile.BuildConfig
import dev.junta.firmamobile.network.TrustedOrigin
import java.net.URI

data class ResolvedSiteProfile(
    val profile: SiteProfile,
    val origin: ExactOrigin,
    val trustMode: TrustMode,
)

class SiteProfileRegistry(
    catalog: SiteProfileCatalog,
    private val buildPolicy: BuildTrustPolicy,
) {
    private val profiles = catalog.profiles.toList()

    init {
        require(catalog.schemaVersion == 1)
        require(profiles.map { it.profileId }.toSet().size == profiles.size)
    }

    fun profile(id: ProfileId): SiteProfile? = profiles.singleOrNull {
        it.profileId == id && isActive(it)
    }

    fun profileMetadata(id: ProfileId): SiteProfile? = profiles.singleOrNull { it.profileId == id }

    fun resolve(uri: URI): ResolvedSiteProfile? {
        val origin = exactOrigin(uri) ?: return null
        val matches = resolutions(origin)
        val exactStartMatches = matches.filter { resolution ->
            resolution.profile.startUrl.toASCIIString() == uri.toASCIIString()
        }
        val resolved = when {
            exactStartMatches.size == 1 -> exactStartMatches.single()
            matches.size == 1 -> matches.single()
            else -> null
        } ?: return null
        return resolved.takeIf { acceptsBrowseOnlyUrl(it.profile, uri) }
    }

    fun resolveForProfile(profileId: ProfileId, uri: URI): ResolvedSiteProfile? {
        val profile = profile(profileId) ?: return null
        val origin = exactOrigin(uri) ?: return null
        val trustMode = profile.trustMode(origin) ?: return null
        return ResolvedSiteProfile(profile, origin, trustMode)
            .takeIf { acceptsBrowseOnlyUrl(profile, uri) }
    }

    fun resolve(uri: Uri): ResolvedSiteProfile? = runCatching { URI(uri.toString()) }
        .getOrNull()?.let(::resolve)

    fun resolve(origin: TrustedOrigin): ResolvedSiteProfile? =
        ExactOrigin.fromTrusted(origin)?.let(::resolve)

    fun resolveRedirect(activeProfileId: ProfileId, uri: URI): ResolvedSiteProfile? {
        val direct = resolveForProfile(activeProfileId, uri) ?: return null
        if (direct.origin !in direct.profile.redirectOrigins) return null
        return direct.copy(trustMode = TrustMode.TRUSTED_BROWSE)
    }

    private fun exactOrigin(uri: URI): ExactOrigin? {
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host == null || uri.userInfo != null) return null
        if (uri.port != -1 && uri.port != 443) return null
        return runCatching { ExactOrigin.parse("https://${uri.host}") }.getOrNull()
    }

    private fun acceptsBrowseOnlyUrl(profile: SiteProfile, uri: URI): Boolean =
        profile.compatibilityStatus != CompatibilityStatus.BROWSE_ONLY ||
            uri.toASCIIString() == profile.startUrl.toASCIIString()

    private fun resolve(origin: ExactOrigin): ResolvedSiteProfile? = resolutions(origin).singleOrNull()

    private fun resolutions(origin: ExactOrigin): List<ResolvedSiteProfile> = profiles.asSequence()
        .filter(::isActive)
        .mapNotNull { profile -> profile.trustMode(origin)?.let { ResolvedSiteProfile(profile, origin, it) } }
        .toList()

    private fun isActive(profile: SiteProfile): Boolean = when (profile.activation) {
        ProfileActivation.DISABLED -> false
        ProfileActivation.QA_ONLY -> buildPolicy == BuildTrustPolicy.QA
        ProfileActivation.ENABLED -> buildPolicy == BuildTrustPolicy.QA || profile.isReleaseEligible()
    }

    private fun SiteProfile.isReleaseEligible(): Boolean {
        val hasSensitiveCapability = capabilities.any {
            it == Capability.SIGN ||
                it == Capability.SELECT_CERTIFICATE ||
                it == Capability.CLIENT_TLS_AUTH
        }
        return !hasSensitiveCapability || compatibilityStatus == CompatibilityStatus.VERIFIED_E2E
    }

    private fun SiteProfile.trustMode(origin: ExactOrigin): TrustMode? {
        if (compatibilityStatus == CompatibilityStatus.BROWSE_ONLY && origin in initiatorOrigins) {
            return TrustMode.BROWSE_ONLY
        }
        if (origin in initiatorOrigins) {
            return when {
                Capability.SIGN in capabilities || Capability.SELECT_CERTIFICATE in capabilities ->
                    TrustMode.TRUSTED_SIGNING
                Capability.CLIENT_TLS_AUTH in capabilities -> TrustMode.TRUSTED_CLIENT_AUTH
                else -> TrustMode.TRUSTED_BROWSE
            }
        }
        if (clientAuthPolicy?.requestPort == 443 && origin in clientAuthPolicy.requestOrigins) {
            return TrustMode.BROWSE_ONLY
        }
        if (origin in redirectOrigins) return TrustMode.BROWSE_ONLY
        if (origin in trustedBrowseOrigins) return TrustMode.TRUSTED_BROWSE
        return null
    }
}

object BuiltInSiteProfiles {
    val catalog: SiteProfileCatalog by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SiteProfileCatalogParser.parse(JSON)
    }
    val releaseRegistry: SiteProfileRegistry by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SiteProfileRegistry(catalog, BuildTrustPolicy.RELEASE)
    }
    val qaRegistry: SiteProfileRegistry by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SiteProfileRegistry(catalog, BuildTrustPolicy.QA)
    }
    val runtimeRegistry: SiteProfileRegistry by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        if (BuildConfig.ALLOW_QA_PROFILES) qaRegistry else releaseRegistry
    }

    val JSON: String = BuildConfig.SITE_PROFILE_CATALOG_JSON
}

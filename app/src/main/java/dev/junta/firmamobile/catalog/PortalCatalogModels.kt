package dev.junta.firmamobile.catalog

import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SignatureFormat
import java.net.URI

enum class PortalGovernmentLevel {
    STATE,
    AUTONOMOUS_COMMUNITY,
    LOCAL_ADMINISTRATION,
    UNIVERSITY,
}

enum class PortalServiceCapability {
    CERTIFICATE_ACCESS,
    ELECTRONIC_SIGNATURE,
}

/** Public catalog support state, deliberately separate from the profile evidence lifecycle. */
enum class PortalSupportStatus {
    VERIFIED_E2E,
    IMPLEMENTED_NOT_E2E,
    VERIFIED_CONTRACT,
    BROWSE_ONLY,
    UNSUPPORTED_PROTOCOL,
}

enum class PortalCatalogFilter {
    ALL,
    STATE,
    AUTONOMOUS_COMMUNITIES,
    LOCAL_ADMINISTRATION,
    UNIVERSITIES,
    FAVORITES,
    RECENT,
    CERTIFICATE_ACCESS,
    ELECTRONIC_SIGNATURE,
}

data class PortalCatalogItem(
    val profileId: ProfileId,
    val displayName: String,
    val organization: String,
    val territory: String,
    val governmentLevel: PortalGovernmentLevel,
    val purpose: String,
    val capabilities: Set<PortalServiceCapability>,
    val signatureFormats: Set<SignatureFormat>,
    val supportStatus: PortalSupportStatus,
    val entryUrl: URI,
    val isEnabled: Boolean,
)

/** Canonical, registry-validated launch input. */
data class PortalLaunchTarget(
    val profileId: ProfileId,
    val entryUrl: URI,
)

data class PortalCatalogQuery(
    val searchText: String = "",
    val filter: PortalCatalogFilter = PortalCatalogFilter.ALL,
    val favoriteProfileIds: Set<ProfileId> = emptySet(),
    val recentProfileIds: List<ProfileId> = emptyList(),
)

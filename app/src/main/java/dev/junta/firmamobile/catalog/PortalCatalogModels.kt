package dev.junta.firmamobile.catalog

import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SignatureFormat
import java.net.URI
import java.time.LocalDate

@JvmInline
value class PortalId(val value: String) {
    init { require(ID_PATTERN.matches(value)) }
    private companion object { val ID_PATTERN = Regex("[a-z0-9][a-z0-9-]{0,95}") }
}

enum class PortalGovernmentLevel {
    STATE,
    AUTONOMOUS_COMMUNITY,
    LOCAL_ADMINISTRATION,
    UNIVERSITY,
    PUBLIC_INSTITUTION,
}

enum class PortalServiceCapability {
    CERTIFICATE_ACCESS,
    ELECTRONIC_SIGNATURE,
}

enum class PortalMechanism {
    CERTIFICATE_ACCESS,
    ELECTRONIC_SIGNATURE,
    AUTOFIRMA,
    AUTOSCRIPT,
    MINIAPPLET,
    AFIRMA,
    CLIENT_TLS_AUTH,
}

enum class PublicCatalogStatus {
    DISCOVERED,
    CATALOGED,
    IMPLEMENTED,
    SMOKE_VERIFIED,
    E2E_PENDING,
    E2E_VERIFIED,
    BLOCKED,
    DEPRECATED,
}

enum class PortalInventoryStatus {
    VERIFIED_E2E,
    IMPLEMENTED_NOT_E2E,
    VERIFIED_CONTRACT,
    REQUIRES_AUTHENTICATED_RESEARCH,
    BROWSE_ONLY,
    UNSUPPORTED_PROTOCOL,
    INACCESSIBLE,
    DEPRECATED,
}

enum class PortalDiscoveryState { DISCOVERED, REVIEWED, RECHECK_REQUIRED }

data class PublicPortalCatalog(
    val schemaVersion: Int,
    val catalogVersion: Int,
    val sourceRevision: String,
    val entries: List<PublicPortalEntry>,
)

data class PublicPortalEntry(
    val portalId: PortalId,
    val inventoryId: String?,
    val profileId: ProfileId?,
    val displayName: String,
    val organization: String,
    val governmentLevel: PortalGovernmentLevel,
    val territory: String,
    val purpose: String,
    val entryUrl: URI,
    val observedMechanisms: Set<PortalMechanism>,
    val observedSignatureFormats: Set<SignatureFormat>,
    val protocolFamily: String,
    val catalogStatus: PublicCatalogStatus,
    val inventoryStatus: PortalInventoryStatus,
    val discoveryState: PortalDiscoveryState,
    val evidenceIds: Set<String>,
    val reviewedOn: LocalDate?,
    val limitations: String,
)

/** Public catalog support state, deliberately separate from the profile evidence lifecycle. */
enum class PortalSupportStatus {
    VERIFIED_E2E,
    IMPLEMENTED_NOT_E2E,
    VERIFIED_CONTRACT,
    BROWSE_ONLY,
    UNSUPPORTED_PROTOCOL,
    DISCOVERED,
    CATALOGED,
    INACCESSIBLE,
    DEPRECATED,
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
    val portalId: PortalId,
    val profileId: ProfileId?,
    val displayName: String,
    val organization: String,
    val territory: String,
    val governmentLevel: PortalGovernmentLevel,
    val purpose: String,
    val observedMechanisms: Set<PortalMechanism>,
    val observedSignatureFormats: Set<SignatureFormat>,
    val capabilities: Set<PortalServiceCapability>,
    val signatureFormats: Set<SignatureFormat>,
    val catalogStatus: PublicCatalogStatus,
    val inventoryStatus: PortalInventoryStatus,
    val limitations: String,
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
    val favoritePortalIds: Set<PortalId> = emptySet(),
    val recentPortalIds: List<PortalId> = emptyList(),
)

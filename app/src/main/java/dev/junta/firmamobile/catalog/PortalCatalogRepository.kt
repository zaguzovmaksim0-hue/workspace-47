package dev.junta.firmamobile.catalog

import dev.junta.firmamobile.profile.Capability
import dev.junta.firmamobile.profile.CompatibilityStatus
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.ProtocolOperation
import dev.junta.firmamobile.profile.SiteProfile
import dev.junta.firmamobile.profile.SiteProfileCatalog
import dev.junta.firmamobile.profile.SiteProfileRegistry
import dev.junta.firmamobile.signing.BuiltInProtocolAdapterRegistry
import java.text.Normalizer
import java.util.Locale

/**
 * Public, non-security metadata for the native portal picker.
 *
 * Security-sensitive fields (origins, endpoints, algorithms and trust modes) intentionally remain
 * in [SiteProfileRegistry]. This catalog only enriches a known profile with presentation copy.
 */
class PortalCatalogRepository(
    private val registry: SiteProfileRegistry,
    private val profileCatalog: SiteProfileCatalog,
    private val publicCatalog: PublicPortalCatalog,
) {
    val bundledCatalogVersion: Int = publicCatalog.catalogVersion

    fun portals(query: PortalCatalogQuery = PortalCatalogQuery()): List<PortalCatalogItem> {
        val items = publicCatalog.entries.map(::resolve)
        val filtered = items.filter { it.matches(query) }
        if (query.filter != PortalCatalogFilter.RECENT) return filtered

        val recentOrder = query.recentPortalIds.withIndex().associate { (index, id) -> id to index }
        return filtered.sortedBy { recentOrder.getValue(it.portalId) }
    }

    /**
     * Resolves only an exact bundled profile/start-URL pair that is currently active and openable.
     * Callers must use the returned target rather than constructing a browser destination themselves.
     */
    fun resolveLaunch(profileId: ProfileId, entryUrl: java.net.URI): PortalLaunchTarget? {
        val portal = publicCatalog.entries.singleOrNull {
            it.profileId == profileId && it.entryUrl.toASCIIString() == entryUrl.toASCIIString()
        } ?: return null
        return resolveLaunch(portal.portalId, entryUrl)
    }

    fun resolveLaunch(portalId: PortalId, entryUrl: java.net.URI): PortalLaunchTarget? {
        val metadata = publicCatalog.entries.singleOrNull { it.portalId == portalId } ?: return null
        val item = resolve(metadata)
        if (!item.isEnabled || item.entryUrl.toASCIIString() != entryUrl.toASCIIString()) return null

        val profileId = metadata.profileId ?: return null
        val activeProfile = registry.profile(profileId) ?: return null
        if (activeProfile.startUrl.toASCIIString() != entryUrl.toASCIIString()) return null
        val resolved = registry.resolve(entryUrl) ?: return null
        if (resolved.profile.profileId != profileId) return null

        return PortalLaunchTarget(profileId = profileId, entryUrl = activeProfile.startUrl)
    }

    fun resolveLaunch(item: PortalCatalogItem): PortalLaunchTarget? =
        resolveLaunch(item.portalId, item.entryUrl)

    private fun resolve(metadata: PublicPortalEntry): PortalCatalogItem {
        val profile = metadata.profileId?.let { profileId ->
            profileCatalog.profiles.singleOrNull { it.profileId == profileId }
        }
        val bindingMatches = profile != null &&
            registry.profileMetadata(profile.profileId) == profile &&
            profile.startUrl.toASCIIString() == metadata.entryUrl.toASCIIString()
        val isImplemented = bindingMatches && profile.isImplementedAndActive()
        val supportStatus = if (bindingMatches) {
            resolvePortalSupportStatus(
                profileStatus = checkNotNull(profile).compatibilityStatus,
                isImplemented = isImplemented,
            )
        } else {
            metadata.metadataSupportStatus()
        }
        val isOpenable = bindingMatches && isImplemented && supportStatus in OPENABLE_SUPPORT_STATUSES

        return PortalCatalogItem(
            portalId = metadata.portalId,
            profileId = metadata.profileId,
            displayName = profile?.displayName ?: metadata.displayName,
            organization = metadata.organization,
            territory = metadata.territory,
            governmentLevel = metadata.governmentLevel,
            purpose = metadata.purpose,
            observedMechanisms = metadata.observedMechanisms,
            observedSignatureFormats = metadata.observedSignatureFormats,
            capabilities = profile?.takeIf { bindingMatches }?.toPublicCapabilities().orEmpty(),
            signatureFormats = profile?.takeIf { bindingMatches }?.operationPolicies?.values
                .orEmpty()
                .asSequence()
                .filter { it.operation == ProtocolOperation.SIGN }
                .mapNotNull { it.format }
                .toSet(),
            catalogStatus = metadata.catalogStatus,
            inventoryStatus = metadata.inventoryStatus,
            limitations = metadata.limitations,
            supportStatus = supportStatus,
            entryUrl = metadata.entryUrl,
            isEnabled = isOpenable,
        )
    }

    private fun PortalCatalogItem.matches(query: PortalCatalogQuery): Boolean {
        val matchesFilter = when (query.filter) {
            PortalCatalogFilter.ALL -> true
            PortalCatalogFilter.STATE -> governmentLevel == PortalGovernmentLevel.STATE
            PortalCatalogFilter.AUTONOMOUS_COMMUNITIES ->
                governmentLevel == PortalGovernmentLevel.AUTONOMOUS_COMMUNITY
            PortalCatalogFilter.LOCAL_ADMINISTRATION ->
                governmentLevel == PortalGovernmentLevel.LOCAL_ADMINISTRATION
            PortalCatalogFilter.UNIVERSITIES -> governmentLevel == PortalGovernmentLevel.UNIVERSITY
            PortalCatalogFilter.FAVORITES -> portalId in query.favoritePortalIds
            PortalCatalogFilter.RECENT -> portalId in query.recentPortalIds
            PortalCatalogFilter.CERTIFICATE_ACCESS ->
                PortalMechanism.CERTIFICATE_ACCESS in observedMechanisms
            PortalCatalogFilter.ELECTRONIC_SIGNATURE ->
                PortalMechanism.ELECTRONIC_SIGNATURE in observedMechanisms
        }
        if (!matchesFilter) return false

        val needle = query.searchText.searchKey()
        if (needle.isEmpty()) return true
        return sequenceOf(displayName, organization, territory, purpose)
            .any { needle in it.searchKey() }
    }

    private fun SiteProfile.toPublicCapabilities(): Set<PortalServiceCapability> = buildSet {
        if (Capability.SELECT_CERTIFICATE in capabilities || Capability.CLIENT_TLS_AUTH in capabilities) {
            add(PortalServiceCapability.CERTIFICATE_ACCESS)
        }
        if (Capability.SIGN in capabilities) add(PortalServiceCapability.ELECTRONIC_SIGNATURE)
    }

    private fun SiteProfile.isImplementedAndActive(): Boolean {
        if (registry.profile(profileId) == null) return false
        if (Capability.CLIENT_TLS_AUTH in capabilities && clientAuthPolicy == null) return false
        if (Capability.SIGN in capabilities) {
            val operation = operationPolicies[ProtocolOperation.SIGN] ?: return false
            val binding = BuiltInProtocolAdapterRegistry.registry
                .resolve(profileId, ProtocolOperation.SIGN)
                ?: return false
            if (binding.inputAdapterId != operation.inputAdapterId ||
                binding.callbackContractId != operation.callbackContractId
            ) {
                return false
            }
        }
        return true
    }

    private fun String.searchKey(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)
        .trim()

    private companion object {
        val COMBINING_MARKS = Regex("\\p{M}+")
        val OPENABLE_SUPPORT_STATUSES = setOf(
            PortalSupportStatus.VERIFIED_E2E,
            PortalSupportStatus.IMPLEMENTED_NOT_E2E,
            PortalSupportStatus.BROWSE_ONLY,
        )
    }
}

private fun PublicPortalEntry.metadataSupportStatus(): PortalSupportStatus = when (inventoryStatus) {
    PortalInventoryStatus.VERIFIED_CONTRACT -> PortalSupportStatus.VERIFIED_CONTRACT
    PortalInventoryStatus.UNSUPPORTED_PROTOCOL -> PortalSupportStatus.UNSUPPORTED_PROTOCOL
    PortalInventoryStatus.INACCESSIBLE -> PortalSupportStatus.INACCESSIBLE
    PortalInventoryStatus.DEPRECATED -> PortalSupportStatus.DEPRECATED
    PortalInventoryStatus.BROWSE_ONLY,
    PortalInventoryStatus.REQUIRES_AUTHENTICATED_RESEARCH,
    -> if (catalogStatus == PublicCatalogStatus.DISCOVERED) {
        PortalSupportStatus.DISCOVERED
    } else {
        PortalSupportStatus.CATALOGED
    }
    PortalInventoryStatus.IMPLEMENTED_NOT_E2E -> PortalSupportStatus.CATALOGED
    PortalInventoryStatus.VERIFIED_E2E -> PortalSupportStatus.CATALOGED
}

internal fun resolvePortalSupportStatus(
    profileStatus: CompatibilityStatus,
    isImplemented: Boolean,
): PortalSupportStatus = when (profileStatus) {
    CompatibilityStatus.VERIFIED_E2E -> PortalSupportStatus.VERIFIED_E2E
    CompatibilityStatus.VERIFIED_CONTRACT -> if (isImplemented) {
        PortalSupportStatus.IMPLEMENTED_NOT_E2E
    } else {
        PortalSupportStatus.VERIFIED_CONTRACT
    }
    CompatibilityStatus.EXPERIMENTAL -> if (isImplemented) {
        PortalSupportStatus.IMPLEMENTED_NOT_E2E
    } else {
        PortalSupportStatus.BROWSE_ONLY
    }
    CompatibilityStatus.BROWSE_ONLY -> PortalSupportStatus.BROWSE_ONLY
    CompatibilityStatus.UNSUPPORTED -> PortalSupportStatus.UNSUPPORTED_PROTOCOL
}

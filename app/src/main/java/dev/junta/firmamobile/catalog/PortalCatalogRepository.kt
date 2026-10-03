package dev.junta.firmamobile.catalog

import dev.junta.firmamobile.profile.Capability
import dev.junta.firmamobile.browser.PublicBrowserAddress
import dev.junta.firmamobile.profile.CompatibilityStatus
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.ProtocolOperation
import dev.junta.firmamobile.profile.SiteProfile
import dev.junta.firmamobile.profile.SiteProfileCatalog
import dev.junta.firmamobile.profile.SiteProfileRegistry
import dev.junta.firmamobile.signing.BuiltInProtocolAdapterRegistry
import java.net.URI

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
    val portalIds: Set<PortalId> = publicCatalog.entries.mapTo(linkedSetOf()) { it.portalId }
    // Precomputation contains only bundled public metadata, not user queries.
    // Synchronised lazy initialization also permits the view-model worker path.
    private val resolvedItems by lazy { publicCatalog.entries.map(::resolve) }
    private val searchIndex by lazy {
        val aliases = publicCatalog.entries.associate { it.portalId to it.displayName }
        CatalogSearchIndex(resolvedItems.map { item ->
            CatalogSearchIndex.Row(item.portalId.value, item.regionCode.ordinal, item.displayName,
                listOf(aliases[item.portalId].orEmpty(), item.displayName, item.organization, item.territory, item.purpose), item)
        })
    }

    fun portals(query: PortalCatalogQuery = PortalCatalogQuery()): List<PortalCatalogItem> {
        val filtered = searchIndex.find(query.searchText, query.selectedRegion?.ordinal) { it.matchesFilter(query) }
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
        if (!item.isEnabled || item.opensWithoutProfile || item.entryUrl.toASCIIString() != entryUrl.toASCIIString()) return null

        val profileId = metadata.profileId ?: return null
        val activeProfile = registry.profile(profileId) ?: return null
        val effectiveLaunchUrl = metadata.launchUrl ?: metadata.entryUrl
        if (activeProfile.startUrl.toASCIIString() != effectiveLaunchUrl.toASCIIString()) return null
        val resolved = registry.resolveForProfile(profileId, effectiveLaunchUrl) ?: return null
        if (resolved.profile.profileId != profileId) return null

        return PortalLaunchTarget(profileId = profileId, entryUrl = activeProfile.startUrl)
    }

    fun resolveLaunch(item: PortalCatalogItem): PortalLaunchTarget? =
        resolveLaunch(item.portalId, item.entryUrl)

    /**
     * Prefer the exact active profile binding. Otherwise open the bundled public
     * entry in ordinary browsing mode, without inheriting any profile authority.
     */
    fun resolveOpenTarget(item: PortalCatalogItem): PortalOpenTarget? {
        val metadata = publicCatalog.entries.singleOrNull { it.portalId == item.portalId }
            ?: return null
        if (item.entryUrl.toASCIIString() != metadata.entryUrl.toASCIIString()) return null
        resolveLaunch(item)?.let { return PortalOpenTarget.InApp(it) }
        return PublicBrowserAddress.parse(metadata.entryUrl.toASCIIString())?.let(PortalOpenTarget::PublicWeb)
    }

    /** The primary catalog route uses the same ordinary AutoFirma engine for
     * every entry, independently of technical-profile availability or build.
     * A reviewed profile remains an explicit compatibility action. */
    fun resolveUniversalOpenTarget(item: PortalCatalogItem): PortalOpenTarget.PublicWeb? {
        val metadata = publicCatalog.entries.singleOrNull { it.portalId == item.portalId } ?: return null
        if (item.entryUrl.toASCIIString() != metadata.entryUrl.toASCIIString()) return null
        val destination = metadata.launchUrl ?: metadata.entryUrl
        return PublicBrowserAddress.parse(destination.toASCIIString())?.let(PortalOpenTarget::PublicWeb)
    }

    private fun resolve(metadata: PublicPortalEntry): PortalCatalogItem {
        val profile = metadata.profileId?.let { profileId ->
            profileCatalog.profiles.singleOrNull { it.profileId == profileId }
        }
        val effectiveLaunchUrl = metadata.launchUrl ?: metadata.entryUrl
        val bindingMatches = profile != null &&
            registry.profileMetadata(profile.profileId) == profile &&
            effectiveLaunchUrl.toASCIIString() == profile.startUrl.toASCIIString()
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
        val isPubliclyOpenable = PublicBrowserAddress.parse(metadata.entryUrl.toASCIIString()) != null

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
            regionCode = metadata.regionCode,
            opensWithoutProfile = !isOpenable && isPubliclyOpenable,
        )
    }

    private fun PortalCatalogItem.matchesFilter(query: PortalCatalogQuery): Boolean {
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

        if (query.regionScope == PortalCatalogRegionScope.SELECTED_AND_NATIONAL) {
            val selected = query.selectedRegion ?: return false
            val allowedRegions = if (selected == PortalRegionCode.SPAIN) {
                setOf(PortalRegionCode.SPAIN)
            } else {
                setOf(PortalRegionCode.SPAIN, selected)
            }
            if (regionCode !in allowedRegions) return false
        }

        return true
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
        setOf(
            Capability.SIGN to ProtocolOperation.SIGN,
            Capability.SELECT_CERTIFICATE to ProtocolOperation.SELECT_CERTIFICATE,
        ).forEach { (capability, operationType) ->
            if (capability in capabilities) {
                val operation = operationPolicies[operationType] ?: return false
                val binding = BuiltInProtocolAdapterRegistry.registry
                    .resolve(profileId, operationType) ?: return false
                if (binding.inputAdapterId != operation.inputAdapterId ||
                    binding.callbackContractId != operation.callbackContractId
                ) {
                    return false
                }
            }
        }
        return true
    }

    private companion object {
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

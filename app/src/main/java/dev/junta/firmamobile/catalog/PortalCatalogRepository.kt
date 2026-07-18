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
) {
    val bundledCatalogVersion: Int = BundledPortalCatalog.VERSION

    fun portals(query: PortalCatalogQuery = PortalCatalogQuery()): List<PortalCatalogItem> {
        val items = BundledPortalCatalog.entries.mapNotNull(::resolve)
        val filtered = items.filter { it.matches(query) }
        if (query.filter != PortalCatalogFilter.RECENT) return filtered

        val recentOrder = query.recentProfileIds.withIndex().associate { (index, id) -> id to index }
        return filtered.sortedBy { recentOrder.getValue(it.profileId) }
    }

    /**
     * Resolves only an exact bundled profile/start-URL pair that is currently active and openable.
     * Callers must use the returned target rather than constructing a browser destination themselves.
     */
    fun resolveLaunch(profileId: ProfileId, entryUrl: java.net.URI): PortalLaunchTarget? {
        val metadata = BundledPortalCatalog.entries.singleOrNull { it.profileId == profileId }
            ?: return null
        val item = resolve(metadata) ?: return null
        if (!item.isEnabled || item.entryUrl.toASCIIString() != entryUrl.toASCIIString()) return null

        val activeProfile = registry.profile(profileId) ?: return null
        if (activeProfile.startUrl.toASCIIString() != entryUrl.toASCIIString()) return null
        val resolved = registry.resolve(entryUrl) ?: return null
        if (resolved.profile.profileId != profileId) return null

        return PortalLaunchTarget(profileId = profileId, entryUrl = activeProfile.startUrl)
    }

    fun resolveLaunch(item: PortalCatalogItem): PortalLaunchTarget? =
        resolveLaunch(item.profileId, item.entryUrl)

    private fun resolve(metadata: PortalPresentationMetadata): PortalCatalogItem? {
        val profile = profileCatalog.profiles.singleOrNull { it.profileId == metadata.profileId }
            ?: return null
        // A mismatched registry/catalog pair must never produce a selectable catalog entry.
        if (registry.profileMetadata(metadata.profileId) != profile) return null

        val isImplemented = profile.isImplementedAndActive()
        val supportStatus = resolvePortalSupportStatus(
            profileStatus = profile.compatibilityStatus,
            isImplemented = isImplemented,
        )
        val isOpenable = isImplemented && supportStatus in OPENABLE_SUPPORT_STATUSES

        return PortalCatalogItem(
            profileId = profile.profileId,
            displayName = profile.displayName,
            organization = metadata.organization,
            territory = metadata.territory,
            governmentLevel = metadata.governmentLevel,
            purpose = metadata.purpose,
            capabilities = profile.toPublicCapabilities(),
            signatureFormats = profile.operationPolicies.values
                .asSequence()
                .filter { it.operation == ProtocolOperation.SIGN }
                .mapNotNull { it.format }
                .toSet(),
            supportStatus = supportStatus,
            entryUrl = profile.startUrl,
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
            PortalCatalogFilter.FAVORITES -> profileId in query.favoriteProfileIds
            PortalCatalogFilter.RECENT -> profileId in query.recentProfileIds
            PortalCatalogFilter.CERTIFICATE_ACCESS ->
                PortalServiceCapability.CERTIFICATE_ACCESS in capabilities
            PortalCatalogFilter.ELECTRONIC_SIGNATURE ->
                PortalServiceCapability.ELECTRONIC_SIGNATURE in capabilities
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

private data class PortalPresentationMetadata(
    val profileId: ProfileId,
    val organization: String,
    val territory: String,
    val governmentLevel: PortalGovernmentLevel,
    val purpose: String,
)

private object BundledPortalCatalog {
    const val VERSION = 1

    val entries = listOf(
        PortalPresentationMetadata(
            profileId = ProfileId("junta-andalucia"),
            organization = "Junta de Andalucía",
            territory = "Andalucía",
            governmentLevel = PortalGovernmentLevel.AUTONOMOUS_COMMUNITY,
            purpose = "Acceso a trámites de la Junta de Andalucía",
        ),
        PortalPresentationMetadata(
            profileId = ProfileId("reg-age-redsara"),
            organization = "Administración General del Estado",
            territory = "España",
            governmentLevel = PortalGovernmentLevel.STATE,
            purpose = "Presentación en el Registro Electrónico General",
        ),
        PortalPresentationMetadata(
            profileId = ProfileId("unizar-tramitador"),
            organization = "Universidad de Zaragoza",
            territory = "Aragón",
            governmentLevel = PortalGovernmentLevel.UNIVERSITY,
            purpose = "Acceso a la Oficina Virtual universitaria",
        ),
        PortalPresentationMetadata(
            profileId = ProfileId("carne-joven-andalucia"),
            organization = "Instituto Andaluz de la Juventud",
            territory = "Andalucía",
            governmentLevel = PortalGovernmentLevel.AUTONOMOUS_COMMUNITY,
            purpose = "Gestión del Carné Joven Europeo de Andalucía",
        ),
    )
}

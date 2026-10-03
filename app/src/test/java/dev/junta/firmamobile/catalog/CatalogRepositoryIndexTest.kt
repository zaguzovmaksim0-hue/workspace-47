package dev.junta.firmamobile.catalog

import dev.junta.firmamobile.profile.BuildTrustPolicy
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.SiteProfileRegistry
import java.text.Collator
import java.text.Normalizer
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class CatalogRepositoryIndexTest {
    private val catalog = loadBundledPublicPortalCatalog()
    private val profiles = BuiltInSiteProfiles.catalog
    private fun repository(mode: BuildTrustPolicy) = PortalCatalogRepository(SiteProfileRegistry(profiles, mode), profiles, catalog)

    @Test fun everyRegionAndAccentSearchKeepsThePreviousOrderingAndMembership() {
        for (mode in listOf(BuildTrustPolicy.QA, BuildTrustPolicy.RELEASE)) {
            val repo = repository(mode); val all = repo.portals()
            assertEquals(183, all.size)
            for (region in listOf(null) + PortalRegionCode.entries) for (text in listOf("", "sede", "CÁDIZ", "registro", "universidad", "xxyy-not-found")) {
                val query = PortalCatalogQuery(searchText = text, selectedRegion = region)
                assertEquals("$mode/$region/$text", previous(all, query), repo.portals(query))
            }
        }
    }

    @Test fun allFiltersFavoritesRecentsAndRegionScopesKeepTheirSemantics() {
        val repo = repository(BuildTrustPolicy.QA); val all = repo.portals()
        val favorites = all.take(5).map { it.portalId }.toSet()
        val recent = all.takeLast(6).map { it.portalId }.reversed()
        for (filter in PortalCatalogFilter.entries) for (scope in PortalCatalogRegionScope.entries) for (region in listOf(null, PortalRegionCode.SPAIN, PortalRegionCode.ANDALUSIA)) {
            val q = PortalCatalogQuery(filter = filter, regionScope = scope, selectedRegion = region,
                favoritePortalIds = favorites, recentPortalIds = recent)
            assertEquals("$filter/$scope/$region", previous(all, q), repo.portals(q))
        }
    }

    @Test fun publicAliasesStaySearchableAndLaunchAuthorityIsNotAPropertyOfTheIndex() {
        val repo = repository(BuildTrustPolicy.QA)
        for (entry in catalog.entries.filter { it.launchUrl != null }) {
            val item = repo.portals(PortalCatalogQuery(searchText = entry.displayName)).single { it.portalId == entry.portalId }
            assertEquals(entry.launchUrl, repo.resolveUniversalOpenTarget(item)!!.entryUrl)
            assertNull(repo.resolveUniversalOpenTarget(item.copy(entryUrl = java.net.URI("https://forged.example/"))))
        }
    }

    @Test fun changedPreferenceCollectionsDoNotReuseAnOutdatedQueryResult() {
        val repo = repository(BuildTrustPolicy.QA); val all = repo.portals()
        val favorites = mutableSetOf(all.first().portalId)
        val q = PortalCatalogQuery(filter = PortalCatalogFilter.FAVORITES, favoritePortalIds = favorites)
        assertEquals(listOf(all.first()), repo.portals(q))
        favorites.clear(); favorites += all.last().portalId
        assertEquals(listOf(all.last()), repo.portals(q))
        assertEquals(183, repo.portals().size)
    }

    /** Independent prior search/filter/sort behavior, not a call into the new index. */
    private fun previous(all: List<PortalCatalogItem>, q: PortalCatalogQuery): List<PortalCatalogItem> {
        val aliases = catalog.entries.associate { it.portalId to key(it.displayName) }
        val needle = key(q.searchText)
        val found = all.filter { item ->
            val category = when (q.filter) {
                PortalCatalogFilter.ALL -> true
                PortalCatalogFilter.STATE -> item.governmentLevel == PortalGovernmentLevel.STATE
                PortalCatalogFilter.AUTONOMOUS_COMMUNITIES -> item.governmentLevel == PortalGovernmentLevel.AUTONOMOUS_COMMUNITY
                PortalCatalogFilter.LOCAL_ADMINISTRATION -> item.governmentLevel == PortalGovernmentLevel.LOCAL_ADMINISTRATION
                PortalCatalogFilter.UNIVERSITIES -> item.governmentLevel == PortalGovernmentLevel.UNIVERSITY
                PortalCatalogFilter.FAVORITES -> item.portalId in q.favoritePortalIds
                PortalCatalogFilter.RECENT -> item.portalId in q.recentPortalIds
                PortalCatalogFilter.CERTIFICATE_ACCESS -> PortalMechanism.CERTIFICATE_ACCESS in item.observedMechanisms
                PortalCatalogFilter.ELECTRONIC_SIGNATURE -> PortalMechanism.ELECTRONIC_SIGNATURE in item.observedMechanisms
            }
            val region = q.regionScope == PortalCatalogRegionScope.ALL ||
                q.selectedRegion != null && item.regionCode in setOf(PortalRegionCode.SPAIN, q.selectedRegion)
            category && region && (needle.isEmpty() || needle in aliases.getValue(item.portalId) ||
                listOf(item.displayName, item.organization, item.territory, item.purpose).any { needle in key(it) })
        }
        if (q.filter == PortalCatalogFilter.RECENT) {
            val order = q.recentPortalIds.withIndex().associate { it.value to it.index }
            return found.sortedBy { order.getValue(it.portalId) }
        }
        val collator = Collator.getInstance(Locale.forLanguageTag("es-ES")).apply { strength = Collator.PRIMARY }
        fun rank(region: PortalRegionCode) = when {
            q.selectedRegion != null && q.selectedRegion != PortalRegionCode.SPAIN && region == q.selectedRegion -> 0
            region == PortalRegionCode.SPAIN -> if (q.selectedRegion == null) 0 else 1
            else -> 2 + region.ordinal
        }
        return found.sortedWith { a, b ->
            val region = rank(a.regionCode).compareTo(rank(b.regionCode))
            if (region != 0) region else {
                val name = collator.compare(a.displayName, b.displayName)
                if (name != 0) name else a.portalId.value.compareTo(b.portalId.value)
            }
        }
    }
    private fun key(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).trim()
}

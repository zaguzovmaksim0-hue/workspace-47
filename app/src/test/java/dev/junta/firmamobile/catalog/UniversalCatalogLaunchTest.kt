package dev.junta.firmamobile.catalog

import dev.junta.firmamobile.profile.BuildTrustPolicy
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SiteProfileRegistry
import java.net.URI
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
class UniversalCatalogLaunchTest {
    private val profiles = BuiltInSiteProfiles.catalog
    private val catalog = loadBundledPublicPortalCatalog()
    private fun repository(build: BuildTrustPolicy, data: PublicPortalCatalog = catalog) =
        PortalCatalogRepository(SiteProfileRegistry(profiles, build), profiles, data)

    @Test fun everyEntryHasTheSameUnprofiledPrimaryRouteInQaAndRelease() {
        assertTrue(catalog.entries.size >= 180)
        for (build in listOf(BuildTrustPolicy.QA, BuildTrustPolicy.RELEASE)) {
            val repo = repository(build); val items = repo.portals()
            assertEquals(catalog.entries.size, items.size)
            assertEquals(catalog.entries.size, items.map { it.portalId }.toSet().size)
            for (item in items) {
                val source = catalog.entries.single { it.portalId == item.portalId }
                val target = repo.resolveUniversalOpenTarget(item)
                assertNotNull("${item.portalId.value}/$build", target)
                assertEquals(source.launchUrl ?: source.entryUrl, target!!.entryUrl)
                assertTrue(item.canOpen)
            }
        }
    }

    @Test fun differentBuildProfileAvailabilityCannotChangeTheUniversalTarget() {
        val qa = repository(BuildTrustPolicy.QA); val release = repository(BuildTrustPolicy.RELEASE)
        for (item in qa.portals()) {
            val other = release.portals().single { it.portalId == item.portalId }
            val before = item.copy(); val strict = qa.resolveLaunch(item)
            assertEquals(qa.resolveUniversalOpenTarget(item), release.resolveUniversalOpenTarget(other))
            assertEquals(before, qa.portals().single { it.portalId == item.portalId })
            assertEquals(strict, qa.resolveLaunch(item))
        }
    }

    @Test fun forgedUrlsOrIdsCannotBecomeACatalogLaunchAndProfileFlagsCannotGrantAuthority() {
        val repo = repository(BuildTrustPolicy.QA); val item = repo.portals().first()
        assertNull(repo.resolveUniversalOpenTarget(item.copy(portalId = PortalId("unknown-synthetic-entry"))))
        assertNull(repo.resolveUniversalOpenTarget(item.copy(entryUrl = URI("https://forged.synthetic.example/"))))
        val altered = item.copy(profileId = ProfileId("forged-profile"), isEnabled = true,
            opensWithoutProfile = false, capabilities = emptySet(), signatureFormats = emptySet())
        assertEquals(repo.resolveUniversalOpenTarget(item), repo.resolveUniversalOpenTarget(altered))
    }

    @Test fun reviewedCompatibilityRemainsAvailableButIsNotTheUniversalPath() {
        val qa = repository(BuildTrustPolicy.QA)
        val enabled = qa.portals().filter { qa.resolveLaunch(it) != null }
        assertTrue(enabled.isNotEmpty())
        for (item in enabled) {
            assertEquals(PortalOpenTarget.InApp(checkNotNull(qa.resolveLaunch(item))), qa.resolveOpenTarget(item))
            assertNotNull(qa.resolveUniversalOpenTarget(item))
        }
        val release = repository(BuildTrustPolicy.RELEASE)
        for (item in release.portals().filter { release.resolveLaunch(it) == null }) {
            assertEquals(PortalOpenTarget.PublicWeb(item.entryUrl), release.resolveOpenTarget(item))
        }
    }

    @Test fun aliasesUseTheBundledPublicDestinationAndNeverAnUnsafeAlternate() {
        val repo = repository(BuildTrustPolicy.QA)
        val aliases = catalog.entries.filter { it.launchUrl != null }
        assertTrue(aliases.isNotEmpty())
        for (entry in aliases) {
            val item = repo.portals().single { it.portalId == entry.portalId }
            assertEquals(entry.launchUrl, repo.resolveUniversalOpenTarget(item)!!.entryUrl)
        }
        val original = catalog.entries.first()
        for (unsafe in listOf("http://source.synthetic.example/", "https://source.synthetic.example:444/", "https://127.0.0.1/")) {
            val data = catalog.copy(entries = listOf(original.copy(launchUrl = URI(unsafe))))
            val constrained = repository(BuildTrustPolicy.QA, data)
            assertNull(constrained.resolveUniversalOpenTarget(constrained.portals().single()))
        }
    }
}

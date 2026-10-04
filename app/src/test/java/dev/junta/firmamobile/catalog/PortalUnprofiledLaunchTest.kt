package dev.junta.firmamobile.catalog

import dev.junta.firmamobile.browser.PublicBrowserAddress
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
class PortalUnprofiledLaunchTest {
    private val catalog = BuiltInSiteProfiles.catalog
    private val publicCatalog = loadBundledPublicPortalCatalog()
    private fun repo(build: BuildTrustPolicy) =
        PortalCatalogRepository(SiteProfileRegistry(catalog, build), catalog, publicCatalog)

    @Test fun publicItemsOpenInBothBuilds() {
        for (build in listOf(BuildTrustPolicy.QA, BuildTrustPolicy.RELEASE)) {
            val repository = repo(build)
            val items = repository.portals().filter { PublicBrowserAddress.parse(it.entryUrl.toString()) != null }
            assertTrue(items.isNotEmpty())
            for (item in items) {
                assertTrue(item.canOpen)
                val strict = repository.resolveLaunch(item)
                val open = repository.resolveOpenTarget(item)
                assertNotNull(open)
                if (strict != null) {
                    assertEquals(PortalOpenTarget.InApp(strict), open)
                } else {
                    assertEquals(PortalOpenTarget.PublicWeb(item.entryUrl), open)
                    assertTrue(item.opensWithoutProfile)
                }
            }
        }
    }

    @Test fun releasePublicItemCannotAdoptQaProfile() {
        val release = repo(BuildTrustPolicy.RELEASE)
        val item = release.portals().first { it.opensWithoutProfile }
        val qa = repo(BuildTrustPolicy.QA)
        val known = qa.portals().first { qa.resolveLaunch(it) != null }
        val profileId: ProfileId = requireNotNull(known.profileId)
        val altered = item.copy(
            profileId = profileId, isEnabled = known.isEnabled, opensWithoutProfile = known.opensWithoutProfile,
            capabilities = known.capabilities, signatureFormats = known.signatureFormats,
            supportStatus = known.supportStatus
        )
        assertNull(release.resolveLaunch(item))
        assertNull(release.resolveLaunch(altered))
        assertEquals(PortalOpenTarget.PublicWeb(item.entryUrl), release.resolveOpenTarget(altered))
    }

    @Test fun forgedUrlOrPortalIdIsRejected() {
        val repository = repo(BuildTrustPolicy.RELEASE)
        val item = repository.portals().first { it.opensWithoutProfile }
        assertNull(repository.resolveOpenTarget(item.copy(entryUrl = URI("https://forged.synthetic.example/"))))
        assertNull(repository.resolveOpenTarget(item.copy(portalId = PortalId("unknown-synthetic-entry"))))
    }

    @Test fun unboundKnownHostRemainsPublicOnly() {
        val entry = publicCatalog.entries.first { it.launchUrl == null }.copy(
            profileId = null, portalId = PortalId("unbound-synthetic")
        )
        val repository = PortalCatalogRepository(
            SiteProfileRegistry(catalog, BuildTrustPolicy.QA), catalog, publicCatalog.copy(entries = listOf(entry))
        )
        val item = repository.portals().single()
        assertNull(repository.resolveLaunch(item))
        assertEquals(PortalOpenTarget.PublicWeb(entry.entryUrl), repository.resolveOpenTarget(item))
        assertTrue(item.opensWithoutProfile)
        assertTrue(item.capabilities.isEmpty())
        assertTrue(item.signatureFormats.isEmpty())
    }

    @Test fun resolvingOpenTargetDoesNotMutateCatalogItems() {
        for (build in listOf(BuildTrustPolicy.QA, BuildTrustPolicy.RELEASE)) {
            val repository = repo(build)
            for (item in repository.portals()) {
                val before = item.copy()
                val capabilities = item.capabilities.toList()
                val formats = item.signatureFormats.toList()
                repository.resolveOpenTarget(item)
                val after = repository.portals().single { it.portalId == before.portalId }
                assertEquals(before, after)
                assertEquals(capabilities, after.capabilities.toList())
                assertEquals(formats, after.signatureFormats.toList())
            }
        }
    }
}

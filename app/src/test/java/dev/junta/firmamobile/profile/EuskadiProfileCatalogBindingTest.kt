package dev.junta.firmamobile.profile

import dev.junta.firmamobile.catalog.PortalCatalogRepository
import dev.junta.firmamobile.catalog.PortalId
import dev.junta.firmamobile.catalog.PortalInventoryStatus
import dev.junta.firmamobile.catalog.PortalLaunchTarget
import dev.junta.firmamobile.catalog.PortalSupportStatus
import dev.junta.firmamobile.catalog.PublicCatalogStatus
import dev.junta.firmamobile.catalog.loadBundledPublicPortalCatalog
import dev.junta.firmamobile.signing.BuiltInProtocolAdapterRegistry
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
class EuskadiProfileCatalogBindingTest {
    private val profileId = ProfileId("euskadi-sede-electronica")
    private val portalId = PortalId("euskadi-sede-electronica")
    private val startUrl = URI("https://www.euskadi.eus/sede-electronica/")

    @Test
    fun `profile exposes only the exact reviewed browse seed`() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }
        assertEquals(CompatibilityStatus.BROWSE_ONLY, profile.compatibilityStatus)
        assertEquals(ProfileActivation.ENABLED, profile.activation)
        assertEquals(startUrl, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://www.euskadi.eus")), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertEquals(TrustMode.BROWSE_ONLY, BuiltInSiteProfiles.qaRegistry.resolve(startUrl)?.trustMode)
        assertEquals(TrustMode.BROWSE_ONLY, BuiltInSiteProfiles.releaseRegistry.resolve(startUrl)?.trustMode)
        listOf(
            "http://www.euskadi.eus/sede-electronica/",
            "https://user@www.euskadi.eus/sede-electronica/",
            "https://www.euskadi.eus.evil.example/sede-electronica/",
            "https://www.euskadi.eus:8443/sede-electronica/",
            "https://www.euskadi.eus/sede-electronica/otra-ruta",
            "https://www.euskadi.eus/medios-de-identificacion-electronica-admitidos/web01-sede/eu/",
        ).forEach { rejected ->
            assertNull(rejected, BuiltInSiteProfiles.qaRegistry.resolve(URI(rejected)))
            assertNull(rejected, BuiltInSiteProfiles.releaseRegistry.resolve(URI(rejected)))
        }
    }

    @Test
    fun `catalog binds Euskadi without promoting generic signature documentation`() {
        assertNull(BuiltInProtocolAdapterRegistry.registry.resolve(profileId, ProtocolOperation.SIGN))
        assertNull(BuiltInProtocolAdapterRegistry.registry.resolve(profileId, ProtocolOperation.SELECT_CERTIFICATE))
        val catalog = loadBundledPublicPortalCatalog()
        val entry = catalog.entries.single { it.portalId == portalId }
        assertEquals(profileId, entry.profileId)
        assertEquals("ES-PUB-0115", entry.inventoryId)
        assertEquals(startUrl, entry.entryUrl)
        assertEquals(PortalInventoryStatus.BROWSE_ONLY, entry.inventoryStatus)
        assertEquals(PublicCatalogStatus.CATALOGED, entry.catalogStatus)
        assertTrue(entry.observedSignatureFormats.isEmpty())

        val qa = PortalCatalogRepository(BuiltInSiteProfiles.qaRegistry, BuiltInSiteProfiles.catalog, catalog)
        val release = PortalCatalogRepository(BuiltInSiteProfiles.releaseRegistry, BuiltInSiteProfiles.catalog, catalog)
        for (repo in listOf(qa, release)) {
            val portal = repo.portals().single { it.portalId == portalId }
            assertEquals(PortalSupportStatus.BROWSE_ONLY, portal.supportStatus)
            assertTrue(portal.isEnabled)
            assertTrue(portal.capabilities.isEmpty())
            assertTrue(portal.signatureFormats.isEmpty())
            assertEquals(PortalLaunchTarget(profileId, startUrl), repo.resolveLaunch(portal))
        }
    }
}

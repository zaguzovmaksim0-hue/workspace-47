package dev.junta.firmamobile.catalog

import dev.junta.firmamobile.profile.BuildTrustPolicy
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class DgtVerificationCatalogBindingTest {
    private val profileId = ProfileId("dgt-verificacion-equipo")
    private val entryUrl = URI(
        "https://sede.dgt.gob.es/es/otros-tramites/verificacion-de-equipos-firmas-y-certificados/verificacion-de-mi-equipo/",
    )

    @Test
    fun generatedCatalogBindsTheExactDgtEntryAndDoesNotOverstateIt() {
        val catalog = loadBundledPublicPortalCatalog()
        val entry = catalog.entries.single { it.portalId == PortalId("dgt-sede") }

        assertEquals(profileId, entry.profileId)
        assertEquals("ES-PUB-0008", entry.inventoryId)
        assertEquals(entryUrl, entry.entryUrl)
        assertEquals(PublicCatalogStatus.E2E_PENDING, entry.catalogStatus)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, entry.inventoryStatus)
        assertEquals(PortalDiscoveryState.REVIEWED, entry.discoveryState)
        assertEquals(setOf(PortalMechanism.CERTIFICATE_ACCESS, PortalMechanism.ELECTRONIC_SIGNATURE, PortalMechanism.MINIAPPLET), entry.observedMechanisms)
        assertEquals(setOf(dev.junta.firmamobile.profile.SignatureFormat.CADES), entry.observedSignatureFormats)
        assertEquals("MINIAPPLET_LOCAL_CADES", entry.protocolFamily)
        assertEquals("2026-08-09", entry.reviewedOn.toString())
    }

    @Test
    fun qaCatalogIsImplementedButReleaseRemainsClosed() {
        val publicCatalog = loadBundledPublicPortalCatalog()
        val qa = PortalCatalogRepository(
            BuiltInSiteProfiles.qaRegistry,
            BuiltInSiteProfiles.catalog,
            publicCatalog,
        )
        val release = PortalCatalogRepository(
            BuiltInSiteProfiles.releaseRegistry,
            BuiltInSiteProfiles.catalog,
            publicCatalog,
        )

        val qaPortal = qa.portals().single { it.portalId == PortalId("dgt-sede") }
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertEquals(
            setOf(PortalServiceCapability.ELECTRONIC_SIGNATURE),
            qaPortal.capabilities,
        )
        assertEquals(
            setOf(dev.junta.firmamobile.profile.SignatureFormat.CADES),
            qaPortal.signatureFormats,
        )
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, entryUrl), qa.resolveLaunch(qaPortal))

        val releasePortal = release.portals().single { it.portalId == PortalId("dgt-sede") }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertEquals(
            setOf(PortalServiceCapability.ELECTRONIC_SIGNATURE),
            releasePortal.capabilities,
        )
        assertEquals(
            setOf(dev.junta.firmamobile.profile.SignatureFormat.CADES),
            releasePortal.signatureFormats,
        )
        assertFalse(releasePortal.isEnabled)
        assertEquals(null, release.resolveLaunch(releasePortal))
    }
}

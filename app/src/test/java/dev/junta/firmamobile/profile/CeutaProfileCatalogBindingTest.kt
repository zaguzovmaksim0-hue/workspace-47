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
import org.junit.Assert.assertFalse
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
class CeutaProfileCatalogBindingTest {
    private val profileId = ProfileId("ceuta-sede")
    private val portalId = PortalId("ceuta-sede")
    private val startUrl = URI("https://sede.ceuta.es/controlador/controlador?modulo=tramites&funcion=applet&tramite=ANI")

    @Test
    fun `profile exposes only the exact ANI boundary in QA without signer capability`() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }
        assertEquals(2, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(startUrl, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://sede.ceuta.es")), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(3, profile.evidence.size)

        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(startUrl)?.trustMode)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(startUrl))
        listOf(
            "http://sede.ceuta.es/controlador/controlador?modulo=tramites&funcion=applet&tramite=ANI",
            "https://user@sede.ceuta.es/controlador/controlador?modulo=tramites&funcion=applet&tramite=ANI",
            "https://sede.ceuta.es.evil.example/controlador/controlador?modulo=tramites&funcion=applet&tramite=ANI",
            "https://sede.ceuta.es:8443/controlador/controlador?modulo=tramites&funcion=applet&tramite=ANI",
            "https://sede.ceuta.es/controlador/controlador?cmd=info&modulo=info",
            "https://sede.ceuta.es/controlador/controlador?cmd=tramite&modulo=tramites&tramite=ANI",
            "https://sede.ceuta.es/controlador/controlador",
        ).forEach { rejected ->
            assertNull(rejected, BuiltInSiteProfiles.qaRegistry.resolve(URI(rejected)))
            assertNull(rejected, BuiltInSiteProfiles.releaseRegistry.resolve(URI(rejected)))
        }
    }

    @Test
    fun `catalog binds Ceuta ANI as QA pending and keeps signing fail closed`() {
        assertNull(BuiltInProtocolAdapterRegistry.registry.resolve(profileId, ProtocolOperation.SIGN))
        assertNull(BuiltInProtocolAdapterRegistry.registry.resolve(profileId, ProtocolOperation.SELECT_CERTIFICATE))
        val catalog = loadBundledPublicPortalCatalog()
        val entry = catalog.entries.single { it.portalId == portalId }
        assertEquals(profileId, entry.profileId)
        assertEquals("ES-PUB-0106", entry.inventoryId)
        assertEquals(startUrl, entry.entryUrl)
        assertEquals("CEUTA_AUTHENTICATED_FORM_BOUNDARY", entry.protocolFamily)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, entry.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, entry.catalogStatus)
        assertEquals(setOf("CERTIFICATE_ACCESS", "ELECTRONIC_SIGNATURE"), entry.observedMechanisms.map { it.name }.toSet())
        assertTrue(entry.observedSignatureFormats.isEmpty())

        val qa = PortalCatalogRepository(BuiltInSiteProfiles.qaRegistry, BuiltInSiteProfiles.catalog, catalog)
        val qaPortal = qa.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertTrue(qaPortal.capabilities.isEmpty())
        assertTrue(qaPortal.signatureFormats.isEmpty())
        assertEquals(PortalLaunchTarget(profileId, startUrl), qa.resolveLaunch(qaPortal))

        val release = PortalCatalogRepository(BuiltInSiteProfiles.releaseRegistry, BuiltInSiteProfiles.catalog, catalog)
        val releasePortal = release.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertNull(release.resolveLaunch(releasePortal))
    }
}

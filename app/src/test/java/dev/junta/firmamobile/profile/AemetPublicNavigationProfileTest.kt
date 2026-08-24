package dev.junta.firmamobile.profile

import dev.junta.firmamobile.catalog.PortalCatalogRepository
import dev.junta.firmamobile.catalog.PortalId
import dev.junta.firmamobile.catalog.PortalInventoryStatus
import dev.junta.firmamobile.catalog.PortalLaunchTarget
import dev.junta.firmamobile.catalog.PortalMechanism
import dev.junta.firmamobile.catalog.PortalSupportStatus
import dev.junta.firmamobile.catalog.PublicCatalogStatus
import dev.junta.firmamobile.catalog.loadBundledPublicPortalCatalog
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
class AemetPublicNavigationProfileTest {
    private val profileId = ProfileId("aemet-public-solicitud-navigation")
    private val portalId = PortalId("age-agencia-estatal-de-meteorologia-aemet")
    private val startUrl = URI("https://sede.aemet.gob.es/AEMET/es/GestionPeticiones/home")

    @Test
    fun qaProfilePinsStableAemetSedeEntryWithoutCryptographicCapabilities() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }
        assertEquals(1, profile.profileVersion)
        assertEquals("AEMET — Solicitud certificados y datos", profile.displayName)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(startUrl, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://sede.aemet.gob.es")), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertFalse(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(6, profile.evidence.size)
        assertTrue(profile.evidence.all { it.reviewedOn.toString() == "2026-08-23" })
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(startUrl)?.trustMode)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(startUrl))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.aemet.gob.es.evil.example/")))
    }

    @Test
    fun publicCatalogRecordsObservedCertificateAutofirmaBoundaryWithoutEnablingSigning() {
        val publicCatalog = loadBundledPublicPortalCatalog()
        val entry = publicCatalog.entries.single { it.inventoryId == "ES-PUB-0024" }
        assertEquals(portalId, entry.portalId)
        assertEquals(profileId, entry.profileId)
        assertEquals(startUrl, entry.entryUrl)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, entry.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, entry.catalogStatus)
        assertEquals("AEMET_SEDE_CERTIFICATE_LOGIN_PUBLIC_BOUNDARY", entry.protocolFamily)
        assertEquals(
            setOf(
                PortalMechanism.CERTIFICATE_ACCESS,
                PortalMechanism.ELECTRONIC_SIGNATURE,
                PortalMechanism.AUTOFIRMA,
                PortalMechanism.MINIAPPLET,
            ),
            entry.observedMechanisms,
        )
        assertTrue(entry.observedSignatureFormats.isEmpty())
        assertEquals("2026-08-23", entry.reviewedOn.toString())
        assertTrue(entry.limitations.contains("capabilities vacío", ignoreCase = true))

        val qa = PortalCatalogRepository(BuiltInSiteProfiles.qaRegistry, BuiltInSiteProfiles.catalog, publicCatalog)
        val release = PortalCatalogRepository(BuiltInSiteProfiles.releaseRegistry, BuiltInSiteProfiles.catalog, publicCatalog)
        val qaPortal = qa.portals().single { it.portalId == portalId }
        val releasePortal = release.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.capabilities.isEmpty())
        assertTrue(qaPortal.signatureFormats.isEmpty())
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, startUrl), qa.resolveLaunch(qaPortal))
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertNull(release.resolveLaunch(releasePortal))
    }
}

package dev.junta.firmamobile.profile

import dev.junta.firmamobile.catalog.PortalCatalogRepository
import dev.junta.firmamobile.catalog.PortalId
import dev.junta.firmamobile.catalog.PortalInventoryStatus
import dev.junta.firmamobile.catalog.PortalLaunchTarget
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
class DiputacionBizkaiaProfileCatalogBindingTest {
    private val profileId = ProfileId("diputacion-bizkaia-instancia-generica")
    private val portalId = PortalId("diputacion-bizkaia-sede")
    private val startUrl = URI(
        "https://appsec.ebizkaia.eus/JXSS001C/?procedimiento=1664&formulario=4912&idioma=C&sede=S",
    )

    @Test
    fun qaProfileExposesOnlyTheObservedBizkaiaNavigationBoundary() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(startUrl, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://appsec.ebizkaia.eus")), profile.initiatorOrigins)
        assertEquals(setOf(ExactOrigin.parse("https://eidasbiz.izenpe.com")), profile.redirectOrigins)
        assertEquals(setOf(ExactOrigin.parse("https://appstac.ebizkaia.eus")), profile.trustedBrowseOrigins)
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertFalse(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(startUrl)?.trustMode)
        assertEquals(
            TrustMode.TRUSTED_BROWSE,
            BuiltInSiteProfiles.qaRegistry.resolveForProfile(
                profileId,
                URI("https://appstac.ebizkaia.eus/EWEC000W/"),
            )?.trustMode,
        )
        assertEquals(
            TrustMode.BROWSE_ONLY,
            BuiltInSiteProfiles.qaRegistry.resolveForProfile(
                profileId,
                URI("https://eidasbiz.izenpe.com/trustedx-authserver/izenpe/flowSelector.xhtml"),
            )?.trustMode,
        )
    }

    @Test
    fun publicCatalogBindsOnlyThePendingBizkaiaInstanciaGenericaContract() {
        val publicCatalog = loadBundledPublicPortalCatalog()
        val entry = publicCatalog.entries.single { it.inventoryId == "ES-PUB-0176" }

        assertEquals(portalId, entry.portalId)
        assertEquals(profileId, entry.profileId)
        assertEquals(startUrl, entry.entryUrl)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, entry.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, entry.catalogStatus)
        assertTrue(entry.observedSignatureFormats.isEmpty())

        val qa = PortalCatalogRepository(BuiltInSiteProfiles.qaRegistry, BuiltInSiteProfiles.catalog, publicCatalog)
        val release = PortalCatalogRepository(BuiltInSiteProfiles.releaseRegistry, BuiltInSiteProfiles.catalog, publicCatalog)
        val qaPortal = qa.portals().single { it.portalId == portalId }
        val releasePortal = release.portals().single { it.portalId == portalId }
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, startUrl), qa.resolveLaunch(qaPortal))
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertNull(release.resolveLaunch(releasePortal))
    }
}

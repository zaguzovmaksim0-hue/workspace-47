package dev.junta.firmamobile.profile

import dev.junta.firmamobile.catalog.PortalCatalogRepository
import dev.junta.firmamobile.catalog.PortalId
import dev.junta.firmamobile.catalog.PortalInventoryStatus
import dev.junta.firmamobile.catalog.PortalLaunchTarget
import dev.junta.firmamobile.catalog.PortalSupportStatus
import dev.junta.firmamobile.catalog.PublicCatalogStatus
import dev.junta.firmamobile.catalog.loadBundledPublicPortalCatalog
import dev.junta.firmamobile.signing.BuiltInProtocolAdapterRegistry
import dev.junta.firmamobile.signing.XuntaPadesTriPhaseAdapter
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
class XuntaProfileCatalogBindingTest {
    private val profileId = ProfileId(XuntaPadesTriPhaseAdapter.PROFILE_ID)
    private val portalId = PortalId("galicia-sede")
    private val startUrl = URI(XuntaPadesTriPhaseAdapter.PUBLIC_START_URL)

    @Test
    fun qaProfilePinsOnlyTheObservedPr004aMainApplicationContract() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }
        assertEquals(1, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(startUrl, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse(XuntaPadesTriPhaseAdapter.INITIATOR_ORIGIN)), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertEquals(setOf(Capability.SIGN, Capability.SELECT_CERTIFICATE, Capability.LEGACY_SHA1), profile.capabilities)
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA"), profile.certificateRules.allowedKeyAlgorithms)
        assertFalse(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(1, profile.endpoints.size)

        val sign = profile.operationPolicies.getValue(ProtocolOperation.SIGN)
        assertEquals(setOf(SignatureAlgorithm.SHA1_WITH_RSA), sign.algorithms)
        assertEquals(SignatureFormat.PADES, sign.format)
        assertEquals(SignaturePackaging.ATTACHED, sign.packaging)
        assertEquals(XuntaPadesTriPhaseAdapter.FIXED_EXTRA_PROPERTIES, sign.fixedExtraProperties)
        assertEquals(XuntaPadesTriPhaseAdapter.ALLOWED_EXTRA_PROPERTIES, sign.allowedExtraProperties)
        val select = profile.operationPolicies.getValue(ProtocolOperation.SELECT_CERTIFICATE)
        assertEquals(mapOf("filters" to "nonexpired"), select.fixedExtraProperties)
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_SIGNING, BuiltInSiteProfiles.qaRegistry.resolve(startUrl)?.trustMode)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
    }

    @Test
    fun registryAndPublicCatalogExposeXuntaOnlyAsPendingE2e() {
        assertEquals(
            XuntaPadesTriPhaseAdapter.ID,
            BuiltInProtocolAdapterRegistry.registry.resolve(profileId, ProtocolOperation.SIGN)?.signingProtocolId,
        )
        assertEquals(
            XuntaPadesTriPhaseAdapter.SELECT_CERTIFICATE_PROTOCOL_ID,
            BuiltInProtocolAdapterRegistry.registry.resolve(profileId, ProtocolOperation.SELECT_CERTIFICATE)?.signingProtocolId?.value,
        )
        val publicCatalog = loadBundledPublicPortalCatalog()
        val entry = publicCatalog.entries.single { it.inventoryId == "ES-PUB-0112" }
        assertEquals(portalId, entry.portalId)
        assertEquals(profileId, entry.profileId)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, entry.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, entry.catalogStatus)
        assertEquals(setOf(SignatureFormat.PADES), entry.observedSignatureFormats)
        assertEquals("2026-08-18", entry.reviewedOn.toString())

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

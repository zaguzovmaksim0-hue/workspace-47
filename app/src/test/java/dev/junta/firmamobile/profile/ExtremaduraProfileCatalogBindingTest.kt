package dev.junta.firmamobile.profile

import dev.junta.firmamobile.catalog.PortalCatalogRepository
import dev.junta.firmamobile.catalog.PortalInventoryStatus
import dev.junta.firmamobile.catalog.PortalLaunchTarget
import dev.junta.firmamobile.catalog.PortalSupportStatus
import dev.junta.firmamobile.catalog.PublicCatalogStatus
import dev.junta.firmamobile.catalog.loadBundledPublicPortalCatalog
import dev.junta.firmamobile.signing.BuiltInProtocolAdapterRegistry
import dev.junta.firmamobile.signing.ExtremaduraBatchProtocolAdapter
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class ExtremaduraProfileCatalogBindingTest {
    @Test
    fun qaProfilePreservesExactExtremaduraBatchContractAndReleaseStaysDisabled() {
        val id = ProfileId("extremadura-tramites")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == id }

        assertEquals(1, profile.profileVersion)
        assertEquals("Junta de Extremadura — Trámites", profile.displayName)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(URI("https://tramites.juntaex.es/"), profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://tramites.juntaex.es")), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertEquals(setOf(Capability.SIGN), profile.capabilities)
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertTrue(profile.evidence.isNotEmpty())

        val operation = profile.operationPolicies.getValue(ProtocolOperation.SIGN)
        assertEquals(ProtocolInputAdapterId("extremadura-batch-autoscript-v1"), operation.inputAdapterId)
        assertEquals(CallbackContractId("extremadura-batch-result-v1"), operation.callbackContractId)
        assertEquals(setOf(Capability.SIGN), operation.capabilities)
        assertEquals(setOf(SignatureAlgorithm.SHA256_WITH_RSA), operation.algorithms)
        assertEquals(SignatureFormat.CADES, operation.format)
        assertEquals(SignaturePackaging.DETACHED, operation.packaging)
        assertNull(operation.mode)
        assertNull(operation.endpointId)
        assertTrue(operation.fixedExtraProperties.isEmpty())
        assertTrue(operation.allowedExtraProperties.isEmpty())

        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(id))
        assertEquals(TrustMode.TRUSTED_SIGNING, BuiltInSiteProfiles.qaRegistry.resolve(profile.startUrl)?.trustMode)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(id))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(profile.startUrl))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://evil.tramites.juntaex.es/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://tramites.juntaex.es:444/")))
    }

    @Test
    fun protocolRegistryBindsOnlyTheExtremaduraSignContract() {
        val id = ProfileId("extremadura-tramites")
        val binding = BuiltInProtocolAdapterRegistry.registry.resolve(id, ProtocolOperation.SIGN)

        assertNotNull(binding)
        assertEquals(ProtocolInputAdapterId("extremadura-batch-autoscript-v1"), binding?.inputAdapterId)
        assertEquals(CallbackContractId("extremadura-batch-result-v1"), binding?.callbackContractId)
        assertEquals(ExtremaduraBatchProtocolAdapter.ID, binding?.signingProtocolId)
        assertNull(BuiltInProtocolAdapterRegistry.registry.resolve(id, ProtocolOperation.SELECT_CERTIFICATE))
    }

    @Test
    fun publicCatalogBindsExtremaduraPendingWithoutE2ePromotion() {
        val profileId = ProfileId("extremadura-tramites")
        val portalId = dev.junta.firmamobile.catalog.PortalId("extremadura-tramites")
        val expectedUrl = URI("https://tramites.juntaex.es/")
        val publicCatalog = loadBundledPublicPortalCatalog()
        val entry = publicCatalog.entries.single { it.inventoryId == "ES-PUB-0109" }

        assertEquals(portalId, entry.portalId)
        assertEquals(profileId, entry.profileId)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, entry.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, entry.catalogStatus)
        assertTrue(SignatureFormat.CADES in entry.observedSignatureFormats)
        assertTrue(entry.limitations.contains("E2E", ignoreCase = true))

        val qa = PortalCatalogRepository(BuiltInSiteProfiles.qaRegistry, BuiltInSiteProfiles.catalog, publicCatalog)
        val release = PortalCatalogRepository(BuiltInSiteProfiles.releaseRegistry, BuiltInSiteProfiles.catalog, publicCatalog)
        val qaPortal = qa.portals().single { it.portalId == portalId }
        val releasePortal = release.portals().single { it.portalId == portalId }

        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, expectedUrl), qa.resolveLaunch(qaPortal))
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertNull(release.resolveLaunch(releasePortal))
    }
}

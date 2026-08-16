package dev.junta.firmamobile.profile

import dev.junta.firmamobile.catalog.PortalCatalogRepository
import dev.junta.firmamobile.catalog.PortalId
import dev.junta.firmamobile.catalog.PortalInventoryStatus
import dev.junta.firmamobile.catalog.PortalLaunchTarget
import dev.junta.firmamobile.catalog.PortalSupportStatus
import dev.junta.firmamobile.catalog.PublicCatalogStatus
import dev.junta.firmamobile.catalog.loadBundledPublicPortalCatalog
import dev.junta.firmamobile.signing.BuiltInProtocolAdapterRegistry
import dev.junta.firmamobile.signing.LugoBatchProtocolAdapter
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
class LugoProfileCatalogBindingTest {
    private val portalId = PortalId("diputacion-lugo-sede")
    private val profileId = ProfileId("diputacion-lugo-sede")
    private val startUrl = URI("https://sede.deputacionlugo.org/opencms/system/modules/gsede/elements/secciones/autenticacion/autenticacion.jsp")

    @Test
    fun qaProfilePreservesOnlyTheExactPublicLugoXmlBatchContract() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }
        assertEquals(1, profile.profileVersion)
        assertEquals("Deputación de Lugo — Sede electrónica", profile.displayName)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(startUrl, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://sede.deputacionlugo.org")), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertEquals(setOf(Capability.SIGN), profile.capabilities)
        assertEquals(setOf("RSA"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)

        val op = profile.operationPolicies.getValue(ProtocolOperation.SIGN)
        assertEquals(ProtocolInputAdapterId("lugo-clientsigner-xml-batch-v1"), op.inputAdapterId)
        assertEquals(CallbackContractId("lugo-clientsigner-batch-result-v1"), op.callbackContractId)
        assertEquals(setOf(SignatureAlgorithm.SHA256_WITH_RSA), op.algorithms)
        assertEquals(SignatureFormat.CADES, op.format)
        assertEquals(SignaturePackaging.DETACHED, op.packaging)
        assertEquals(SignatureMode.EXPLICIT, op.mode)
        assertEquals(mapOf("precalculatedHashAlgorithm" to "SHA-256"), op.fixedExtraProperties)
        assertTrue(op.allowedExtraProperties.isEmpty())

        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_SIGNING, BuiltInSiteProfiles.qaRegistry.resolve(startUrl)?.trustMode)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://evil.sede.deputacionlugo.org/")))
    }

    @Test
    fun protocolAndPublicCatalogRemainQaOnlyAndNotE2e() {
        val binding = BuiltInProtocolAdapterRegistry.registry.resolve(profileId, ProtocolOperation.SIGN)
        assertNotNull(binding)
        assertEquals(LugoBatchProtocolAdapter.ID, binding?.signingProtocolId)

        val publicCatalog = loadBundledPublicPortalCatalog()
        val entry = publicCatalog.entries.single { it.inventoryId == "ES-PUB-0163" }
        assertEquals(portalId, entry.portalId)
        assertEquals(profileId, entry.profileId)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, entry.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, entry.catalogStatus)
        assertEquals(setOf(SignatureFormat.CADES), entry.observedSignatureFormats)
        assertEquals("2026-08-16", entry.reviewedOn.toString())

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

package dev.junta.firmamobile.profile

import dev.junta.firmamobile.catalog.PortalCatalogRepository
import dev.junta.firmamobile.catalog.PortalId
import dev.junta.firmamobile.catalog.PortalInventoryStatus
import dev.junta.firmamobile.catalog.PortalLaunchTarget
import dev.junta.firmamobile.catalog.PortalSupportStatus
import dev.junta.firmamobile.catalog.PublicCatalogStatus
import dev.junta.firmamobile.catalog.loadBundledPublicPortalCatalog
import dev.junta.firmamobile.signing.BuiltInProtocolAdapterRegistry
import dev.junta.firmamobile.signing.BurgosBatchProtocolAdapter
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
class BurgosProfileCatalogBindingTest {
    private val profileId = ProfileId("diputacion-burgos-portal")
    private val startUrl = URI("https://registro.diputaciondeburgos.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&DETALLE=6269000968832920507194&PAGE_CODE=CATALOGO")

    @Test
    fun qaProfilePreservesExactBurgosBatchContractAndReleaseStaysDisabled() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals("Diputación Provincial de Burgos — Registro electrónico", profile.displayName)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(startUrl, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://registro.diputaciondeburgos.es")), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertEquals(setOf(Capability.SIGN), profile.capabilities)
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertTrue(profile.evidence.all { it.reviewedOn.toString() == "2026-08-16" })

        val operation = profile.operationPolicies.getValue(ProtocolOperation.SIGN)
        assertEquals(ProtocolInputAdapterId("burgos-batch-autoscript-v1"), operation.inputAdapterId)
        assertEquals(CallbackContractId("burgos-batch-result-v1"), operation.callbackContractId)
        assertEquals(setOf(Capability.SIGN), operation.capabilities)
        assertEquals(setOf(SignatureAlgorithm.SHA256_WITH_RSA), operation.algorithms)
        assertEquals(SignatureFormat.CADES, operation.format)
        assertEquals(SignaturePackaging.DETACHED, operation.packaging)
        assertNull(operation.mode)
        assertNull(operation.endpointId)
        assertTrue(operation.fixedExtraProperties.isEmpty())
        assertTrue(operation.allowedExtraProperties.isEmpty())

        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_SIGNING, BuiltInSiteProfiles.qaRegistry.resolve(startUrl)?.trustMode)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(startUrl))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://evil.registro.diputaciondeburgos.es/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://registro.diputaciondeburgos.es:444/")))
    }

    @Test
    fun protocolRegistryBindsOnlyTheBurgosSignContract() {
        val binding = BuiltInProtocolAdapterRegistry.registry.resolve(profileId, ProtocolOperation.SIGN)

        assertNotNull(binding)
        assertEquals(ProtocolInputAdapterId("burgos-batch-autoscript-v1"), binding?.inputAdapterId)
        assertEquals(CallbackContractId("burgos-batch-result-v1"), binding?.callbackContractId)
        assertEquals(BurgosBatchProtocolAdapter.ID, binding?.signingProtocolId)
        assertNull(BuiltInProtocolAdapterRegistry.registry.resolve(profileId, ProtocolOperation.SELECT_CERTIFICATE))
    }

    @Test
    fun publicCatalogBindsBurgosPendingWithoutE2ePromotion() {
        val portalId = PortalId("diputacion-burgos-portal")
        val publicCatalog = loadBundledPublicPortalCatalog()
        val entry = publicCatalog.entries.single { it.inventoryId == "ES-PUB-0146" }

        assertEquals(portalId, entry.portalId)
        assertEquals(profileId, entry.profileId)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, entry.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, entry.catalogStatus)
        assertEquals(
            setOf(SignatureFormat.CADES, SignatureFormat.PADES, SignatureFormat.XADES),
            entry.observedSignatureFormats,
        )
        assertEquals("2026-08-16", entry.reviewedOn.toString())
        assertTrue(entry.limitations.contains("E2E", ignoreCase = true))

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

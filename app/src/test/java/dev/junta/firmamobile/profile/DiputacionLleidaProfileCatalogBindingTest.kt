package dev.junta.firmamobile.profile

import dev.junta.firmamobile.catalog.PortalCatalogRepository
import dev.junta.firmamobile.catalog.PortalId
import dev.junta.firmamobile.catalog.PortalInventoryStatus
import dev.junta.firmamobile.catalog.PortalLaunchTarget
import dev.junta.firmamobile.catalog.PortalSupportStatus
import dev.junta.firmamobile.catalog.PublicCatalogStatus
import dev.junta.firmamobile.catalog.loadBundledPublicPortalCatalog
import dev.junta.firmamobile.signing.BuiltInProtocolAdapterRegistry
import dev.junta.firmamobile.signing.DiputacionLleidaCadesAdapter
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
class DiputacionLleidaProfileCatalogBindingTest {
    private val profileId = ProfileId(DiputacionLleidaCadesAdapter.PROFILE_ID)
    private val portalId = PortalId("diputacion-lleida-sede")
    private val startUrl = URI("https://seu.diputaciolleida.cat")

    @Test
    fun qaProfilePreservesExactDiputacionLleidaSignerContractAndReleaseStaysDisabled() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals("Diputació de Lleida — Sede electrónica", profile.displayName)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(startUrl, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://seu.diputaciolleida.cat")), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertEquals(setOf(Capability.SIGN), profile.capabilities)
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertTrue(profile.evidence.all { it.reviewedOn.toString() == "2026-08-16" })

        val operation = profile.operationPolicies.getValue(ProtocolOperation.SIGN)
        assertEquals(ProtocolInputAdapterId("miniapplet-autoscript-v1"), operation.inputAdapterId)
        assertEquals(CallbackContractId("miniapplet-sign-callback-v1"), operation.callbackContractId)
        assertEquals(setOf(SignatureAlgorithm.SHA256_WITH_RSA), operation.algorithms)
        assertEquals(SignatureFormat.CADES, operation.format)
        assertEquals(SignaturePackaging.DETACHED, operation.packaging)
        assertEquals(SignatureMode.EXPLICIT, operation.mode)
        assertEquals(
            mapOf(
                "policy" to "FirmaAGE",
                "headless" to "true",
                "filters" to "nonexpired:true;authCert:true",
            ),
            operation.fixedExtraProperties,
        )
        assertTrue(operation.allowedExtraProperties.isEmpty())

        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_SIGNING, BuiltInSiteProfiles.qaRegistry.resolve(startUrl)?.trustMode)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(startUrl))
        listOf(
            "http://seu.diputaciolleida.cat",
            "https://user@seu.diputaciolleida.cat",
            "https://seu.diputaciolleida.cat.evil.example",
            "https://seu.diputaciolleida.cat:8443",
            "https://www.diputaciolleida.cat",
            "https://www.diputaciolleida.es",
            "https://idcat.aoc.cat",
            "https://clave.gob.es",
            "https://giltza.euskadi.eus",
        ).forEach { rejected ->
            assertNull(rejected, BuiltInSiteProfiles.qaRegistry.resolve(URI(rejected)))
            assertNull(rejected, BuiltInSiteProfiles.releaseRegistry.resolve(URI(rejected)))
        }
    }

    @Test
    fun protocolAndPublicCatalogBindOnlyThePendingDiputacionLleidaContract() {
        val binding = BuiltInProtocolAdapterRegistry.registry.resolve(profileId, ProtocolOperation.SIGN)
        assertEquals(DiputacionLleidaCadesAdapter.ID, binding?.signingProtocolId)
        assertEquals(ProtocolInputAdapterId("miniapplet-autoscript-v1"), binding?.inputAdapterId)
        assertEquals(CallbackContractId("miniapplet-sign-callback-v1"), binding?.callbackContractId)
        assertNull(BuiltInProtocolAdapterRegistry.registry.resolve(profileId, ProtocolOperation.SELECT_CERTIFICATE))

        val publicCatalog = loadBundledPublicPortalCatalog()
        val entry = publicCatalog.entries.single { it.inventoryId == "ES-PUB-0162" }
        assertEquals(portalId, entry.portalId)
        assertEquals(profileId, entry.profileId)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, entry.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, entry.catalogStatus)
        assertEquals(setOf(SignatureFormat.CADES), entry.observedSignatureFormats)
        assertEquals("2026-07-16", entry.reviewedOn.toString())
        assertTrue(entry.limitations.contains("E2E", ignoreCase = true))

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

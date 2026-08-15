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
class ValenciaProfileCatalogBindingTest {
    private val profileId = ProfileId("diputacion-valencia-sede")
    private val portalId = PortalId("diputacion-valencia-sede")
    private val startUrl = URI("https://portafirmas.dival.es/signingpad/xhtml/login.xhtml")

    @Test
    fun qaProfilePreservesExactCertificateSelectionContractWithoutSigningCapability() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals("Diputació de València — selección de certificado", profile.displayName)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(startUrl, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://portafirmas.dival.es")), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertEquals(setOf(Capability.SELECT_CERTIFICATE), profile.capabilities)
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertFalse(profile.certificateRules.requireDigitalSignatureKeyUsage)

        val operation = profile.operationPolicies.getValue(ProtocolOperation.SELECT_CERTIFICATE)
        assertEquals(ProtocolInputAdapterId("autoscript-select-certificate-v1"), operation.inputAdapterId)
        assertEquals(
            CallbackContractId("autoscript-select-certificate-callback-v1"),
            operation.callbackContractId,
        )
        assertEquals(setOf(Capability.SELECT_CERTIFICATE), operation.capabilities)
        assertNull(operation.endpointId)
        assertTrue(operation.algorithms.isEmpty())
        assertNull(operation.format)
        assertNull(operation.packaging)
        assertNull(operation.mode)
        assertEquals(
            mapOf(
                "filters" to "keyusage.nonrepudiation:true;nonexpired:true",
                "headless" to "true",
            ),
            operation.fixedExtraProperties,
        )
        assertTrue(operation.allowedExtraProperties.isEmpty())
        assertTrue(profile.evidence.all { it.reviewedOn.toString() == "2026-08-15" })
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_SIGNING, BuiltInSiteProfiles.qaRegistry.resolve(startUrl)?.trustMode)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(startUrl))
    }

    @Test
    fun protocolAndPublicCatalogBindOnlyTheQaCertificateSelectionImplementation() {
        val binding = BuiltInProtocolAdapterRegistry.registry.resolve(
            profileId,
            ProtocolOperation.SELECT_CERTIFICATE,
        )
        assertEquals("valencia-select-certificate-v1", binding?.signingProtocolId?.value)
        assertEquals(ProtocolInputAdapterId("autoscript-select-certificate-v1"), binding?.inputAdapterId)
        assertEquals(
            CallbackContractId("autoscript-select-certificate-callback-v1"),
            binding?.callbackContractId,
        )
        assertNull(BuiltInProtocolAdapterRegistry.registry.resolve(profileId, ProtocolOperation.SIGN))

        val publicCatalog = loadBundledPublicPortalCatalog()
        val entry = publicCatalog.entries.single { it.portalId == portalId }
        assertEquals(profileId, entry.profileId)
        assertEquals(startUrl, entry.entryUrl)
        assertNull(entry.launchUrl)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, entry.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, entry.catalogStatus)
        assertEquals("2026-08-15", entry.reviewedOn.toString())
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

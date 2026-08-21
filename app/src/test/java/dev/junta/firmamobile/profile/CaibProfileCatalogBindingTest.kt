package dev.junta.firmamobile.profile

import dev.junta.firmamobile.catalog.PortalCatalogRepository
import dev.junta.firmamobile.catalog.PortalId
import dev.junta.firmamobile.catalog.PortalInventoryStatus
import dev.junta.firmamobile.catalog.PortalLaunchTarget
import dev.junta.firmamobile.catalog.PortalSupportStatus
import dev.junta.firmamobile.catalog.PublicCatalogStatus
import dev.junta.firmamobile.catalog.loadBundledPublicPortalCatalog
import dev.junta.firmamobile.signing.BuiltInProtocolAdapterRegistry
import dev.junta.firmamobile.signing.CaibBatchProtocolAdapter
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
class CaibProfileCatalogBindingTest {
    private val portalId = PortalId("caib-seu-electronica")
    private val profileId = ProfileId("caib-portafib")
    private val startUrl = URI("https://www.caib.es/sistramitfront/asistente/iniciarTramite.html?tramite=CAIB.SIMPL_DOC.INSTANCIA_GENERICA_SR&version=1&idioma=es&servicioCatalogo=false&idTramiteCatalogo=4213963&parametros=")

    @Test
    fun qaProfileContainsOnlyObservedCaibPadesContract() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(startUrl, profile.startUrl)
        assertEquals(
            setOf(ExactOrigin.parse("https://www.caib.es"), ExactOrigin.parse("https://intranet.caib.es")),
            profile.initiatorOrigins,
        )
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertNull(profile.clientAuthPolicy)
        val op = profile.operationPolicies.getValue(ProtocolOperation.SIGN)
        assertEquals(ProtocolInputAdapterId("caib-portafib-batch-v1"), op.inputAdapterId)
        assertEquals(CallbackContractId("caib-portafib-batch-result-v1"), op.callbackContractId)
        assertEquals(setOf(SignatureAlgorithm.SHA256_WITH_RSA), op.algorithms)
        assertEquals(SignatureFormat.PADES, op.format)
        assertEquals(SignatureMode.IMPLICIT, op.mode)
        assertTrue(op.fixedExtraProperties.isEmpty())
        assertTrue(op.allowedExtraProperties.isEmpty())
        assertEquals(TrustMode.TRUSTED_SIGNING, BuiltInSiteProfiles.qaRegistry.resolve(startUrl)?.trustMode)
        assertEquals(
            TrustMode.TRUSTED_SIGNING,
            BuiltInSiteProfiles.qaRegistry.resolve(URI("https://intranet.caib.es/portafibback/public/signmodule/requestPlugin/ABCDEFGHIJKLMNOPQRSTUVWX1234/-1/index"))?.trustMode,
        )
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
    }

    @Test
    fun protocolAndPublicCatalogRemainQaOnlyAndE2ePending() {
        val binding = BuiltInProtocolAdapterRegistry.registry.resolve(profileId, ProtocolOperation.SIGN)
        assertNotNull(binding)
        assertEquals(CaibBatchProtocolAdapter.ID, binding?.signingProtocolId)
        val publicCatalog = loadBundledPublicPortalCatalog()
        val entry = publicCatalog.entries.single { it.inventoryId == "ES-PUB-0097" }
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

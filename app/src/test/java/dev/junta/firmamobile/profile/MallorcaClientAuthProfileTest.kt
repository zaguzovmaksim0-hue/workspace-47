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
class MallorcaClientAuthProfileTest {
    private val profileId = ProfileId("consell-mallorca-sede")
    private val portalId = PortalId("mallorca-sede-electronica")
    private val startUrl = URI("https://cim.secimallorca.net/segex/tramite.aspx?idtramite=12082")
    private val sourceUrl = URI("https://cim.secimallorca.net/segex/identificacion_opciones.aspx")

    @Test
    fun qaProfilePinsObservedSedipualbaClientTlsTransitionAndReleaseStaysDisabled() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }
        val policy = checkNotNull(profile.clientAuthPolicy)

        assertEquals(1, profile.profileVersion)
        assertEquals("Consell de Mallorca — acceso con certificado", profile.displayName)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(startUrl, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://cim.secimallorca.net")), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertEquals(ClientAuthTransitionMode.DIRECT_FROM_SOURCE, policy.transitionMode)
        assertEquals(
            setOf(ExactOrigin.parse("https://identificacionssl.sedipualba.es")),
            policy.requestOrigins,
        )
        assertEquals(setOf(sourceUrl), policy.sourceUrls)
        assertEquals(mapOf("idioma" to "ca"), policy.sourceFixedQueryParameters)
        assertEquals(setOf("idtoken"), policy.sourceRequiredEphemeralQueryParameters)
        assertEquals(setOf("idtoken"), policy.linkedEphemeralQueryParameters)
        assertEquals("/", policy.requestPath)
        assertEquals(mapOf("idioma" to "ca", "entidad" to "07700"), policy.fixedQueryParameters)
        assertEquals(setOf("idtoken"), policy.requiredEphemeralQueryParameters)
        assertEquals(443, policy.requestPort)
        assertTrue(policy.allowEmptyIssuerList)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertTrue(profile.evidence.all { it.reviewedOn.toString() == "2026-08-18" })

        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_CLIENT_AUTH, BuiltInSiteProfiles.qaRegistry.resolve(startUrl)?.trustMode)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(startUrl))
    }

    @Test
    fun publicCatalogPromotesOnlyCertificateAccessAndKeepsDocumentSigningUnimplemented() {
        val publicCatalog = loadBundledPublicPortalCatalog()
        val entry = publicCatalog.entries.single { it.inventoryId == "ES-PUB-0120" }

        assertEquals(portalId, entry.portalId)
        assertEquals(profileId, entry.profileId)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, entry.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, entry.catalogStatus)
        assertEquals("CLIENT_TLS_AUTH", entry.protocolFamily)
        assertTrue(PortalMechanism.CLIENT_TLS_AUTH in entry.observedMechanisms)
        assertTrue(PortalMechanism.CERTIFICATE_ACCESS in entry.observedMechanisms)
        assertTrue(PortalMechanism.ELECTRONIC_SIGNATURE in entry.observedMechanisms)
        assertTrue(entry.observedSignatureFormats.isEmpty())
        assertEquals("2026-08-18", entry.reviewedOn.toString())
        assertTrue(entry.limitations.contains("firma", ignoreCase = true))
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

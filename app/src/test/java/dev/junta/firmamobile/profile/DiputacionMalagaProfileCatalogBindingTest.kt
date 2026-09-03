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
import org.junit.Assert.assertThrows
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
class DiputacionMalagaProfileCatalogBindingTest {
    private val profileId = ProfileId("diputacion-malaga-instancia-general")
    private val portalId = PortalId("diputacion-malaga-sede")
    private val startUrl = URI("https://sede.malaga.es/instancia-general/nueva-instancia-general/")

    @Test
    fun qaProfilePinsTheReviewedMalagaClaveClientTlsTransition() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }
        val policy = checkNotNull(profile.clientAuthPolicy)
        val sourceUrl = URI("https://pasarela.clave.gob.es/Proxy2/ServiceRedirect")
        val targetUrl = URI("https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen")

        assertEquals(3, profile.profileVersion)
        assertEquals("Diputación de Málaga — Instancia general — acceso con certificado", profile.displayName)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(startUrl, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://sede.malaga.es")), profile.initiatorOrigins)
        assertEquals(
            setOf(
                ExactOrigin.parse("https://clave.malaga.es"),
                ExactOrigin.parse("https://pasarela.clave.gob.es"),
            ),
            profile.redirectOrigins,
        )
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        assertEquals(ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE, policy.transitionMode)
        assertEquals(setOf(ExactOrigin.parse("https://pasarela-ident.clave.gob.es")), policy.requestOrigins)
        assertEquals(setOf(sourceUrl), policy.sourceUrls)
        assertEquals("/IdP2/AuthenticateCitizen", policy.requestPath)
        assertEquals(HttpMethod.POST, policy.requestMethod)
        assertTrue(policy.fixedQueryParameters.isEmpty())
        assertTrue(policy.requiredEphemeralQueryParameters.isEmpty())
        assertEquals(443, policy.requestPort)
        assertTrue(policy.allowEmptyIssuerList)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_CLIENT_AUTH, BuiltInSiteProfiles.qaRegistry.resolve(startUrl)?.trustMode)
        assertEquals(
            TrustMode.TRUSTED_BROWSE,
            BuiltInSiteProfiles.qaRegistry.resolveForProfile(profileId, sourceUrl)?.trustMode,
        )
        assertEquals(
            TrustMode.TRUSTED_CLIENT_AUTH,
            BuiltInSiteProfiles.qaRegistry.resolveForProfile(profileId, targetUrl)?.trustMode,
        )
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
    }

    @Test
    fun unparameterizedPostExceptionIsScopedToTheExactReviewedMalagaContract() {
        val json = BuiltInSiteProfiles.JSON
        val start = json.indexOf("\"profileId\": \"diputacion-malaga-instancia-general\"")
        assertTrue(start >= 0)
        val end = json.indexOf("\"profileId\":", start + 1).let { if (it >= 0) it else json.length }
        val block = json.substring(start, end)
        val mutations = listOf(
            block.replace(
                "\"profileId\": \"diputacion-malaga-instancia-general\"",
                "\"profileId\": \"unreviewed-malaga-client-auth\"",
            ),
            block.replace(
                "https://pasarela.clave.gob.es/Proxy2/ServiceRedirect",
                "https://pasarela.clave.gob.es/Proxy2/ServiceRedirectOther",
            ),
            block.replace(
                "\"requestPath\": \"/IdP2/AuthenticateCitizen\"",
                "\"requestPath\": \"/IdP2/AuthenticateCitizen/other\"",
            ),
        )
        mutations.forEach { mutatedBlock ->
            assertThrows(IllegalArgumentException::class.java) {
                SiteProfileCatalogParser.parse(json.substring(0, start) + mutatedBlock + json.substring(end))
            }
        }
    }

    @Test
    fun publicCatalogExposesTheReviewedClientTlsMechanismButKeepsReleaseDisabled() {
        val publicCatalog = loadBundledPublicPortalCatalog()
        val entry = publicCatalog.entries.single { it.inventoryId == "ES-PUB-0164" }
        assertEquals(portalId, entry.portalId)
        assertEquals(profileId, entry.profileId)
        assertEquals(startUrl, entry.entryUrl)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, entry.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, entry.catalogStatus)
        assertEquals("CLIENT_TLS_AUTH_CLAVE", entry.protocolFamily)
        assertTrue(PortalMechanism.CLIENT_TLS_AUTH in entry.observedMechanisms)
        assertTrue(PortalMechanism.CERTIFICATE_ACCESS in entry.observedMechanisms)
        assertTrue(PortalMechanism.ELECTRONIC_SIGNATURE in entry.observedMechanisms)
        assertEquals("2026-09-03", entry.reviewedOn.toString())
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

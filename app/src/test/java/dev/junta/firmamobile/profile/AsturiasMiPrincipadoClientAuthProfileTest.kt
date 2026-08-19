package dev.junta.firmamobile.profile

import dev.junta.firmamobile.browser.BrowserTrustController
import dev.junta.firmamobile.browser.BrowserUrlPolicy
import dev.junta.firmamobile.browser.ClientAuthNavigationAuthorizer
import dev.junta.firmamobile.browser.SensitiveFlowInvalidator
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
class AsturiasMiPrincipadoClientAuthProfileTest {
    private val profileId = ProfileId("asturias-miprincipado")
    private val portalId = PortalId("asturias-miprincipado-sede")
    private val startUrl = URI(
        "https://miprincipado.asturias.es/-/dboid-6269000102616541907573" +
            "?redirect=%2Fweb%2Fsede%2Ftodos-los-servicios-y-tramites",
    )
    private val claveSource = "https://pasarela.clave.gob.es/Proxy2/ServiceProvider"
    private val claveTarget = "https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen"

    @Test
    fun qaProfilePinsOnlyTheObservedAsturiasClaveClientTlsBoundary() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }
        val policy = checkNotNull(profile.clientAuthPolicy)

        assertEquals(1, profile.profileVersion)
        assertEquals("Principado de Asturias — MiPrincipado / Solicitud Genérica", profile.displayName)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(startUrl, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://miprincipado.asturias.es")), profile.initiatorOrigins)
        assertEquals(
            setOf(
                ExactOrigin.parse("https://tramita.asturias.es"),
                ExactOrigin.parse("https://rhsso.asturias.es"),
                ExactOrigin.parse("https://pasarela.clave.gob.es"),
            ),
            profile.redirectOrigins,
        )
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)

        assertEquals(ClientAuthTransitionMode.DIRECT_FROM_SOURCE, policy.transitionMode)
        assertEquals(setOf(ExactOrigin.parse("https://pasarela-ident.clave.gob.es")), policy.requestOrigins)
        assertEquals(setOf(URI(claveSource)), policy.sourceUrls)
        assertTrue(policy.sourceFixedQueryParameters.isEmpty())
        assertTrue(policy.sourceRequiredEphemeralQueryParameters.isEmpty())
        assertTrue(policy.linkedEphemeralQueryParameters.isEmpty())
        assertTrue(policy.linkedEphemeralQueryParameterMappings.isEmpty())
        assertEquals("/IdP2/AuthenticateCitizen", policy.requestPath)
        assertTrue(policy.fixedQueryParameters.isEmpty())
        assertTrue(policy.requiredEphemeralQueryParameters.isEmpty())
        assertTrue(policy.allowEmptyIssuerList)
        assertEquals(15, policy.grantTtlSeconds)
        assertEquals(443, policy.requestPort)
        assertEquals(setOf("RSA"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertTrue(profile.evidence.all { it.reviewedOn.toString() == "2026-08-19" })

        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_CLIENT_AUTH, BuiltInSiteProfiles.qaRegistry.resolve(startUrl)?.trustMode)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(startUrl))
    }

    @Test
    fun observedMultihopBrowseChainRetainsOnlyTheSelectedAsturiasProfileAndExactClientAuthTarget() {
        val controller = BrowserTrustController(
            BrowserUrlPolicy(BuiltInSiteProfiles.qaRegistry, profileId),
            SensitiveFlowInvalidator {},
        )
        assertEquals(profileId, controller.navigate(startUrl.toASCIIString()).activeProfileId)

        listOf(
            "https://tramita.asturias.es/sta/Relec/STARhssoManager",
            "https://rhsso.asturias.es/auth/realms/princast-asturias/protocol/openid-connect/auth",
            claveSource,
        ).forEach { url ->
            val state = controller.navigate(url)
            assertEquals(url, TrustMode.TRUSTED_BROWSE, state.resolution.trustMode)
            assertEquals(url, profileId, state.activeProfileId)
        }

        val authorizer = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry) { 1_000_000_000L }
        val authorized = authorizer.observeTopLevelNavigation(
            profileId,
            claveSource,
            claveTarget,
            controller.current().epoch,
            true,
        )
        assertNotNull(authorized)
        assertEquals(profileId, authorized?.profileId)

        listOf(
            claveTarget.replace("/AuthenticateCitizen", "/AuthenticateCitizen/other"),
            "$claveTarget?extra=1",
            claveTarget.replace("pasarela-ident.clave.gob.es", "pasarela-ident.clave.gob.es.evil.example"),
            claveTarget.replace("pasarela-ident.clave.gob.es", "pasarela-ident.clave.gob.es:8443"),
        ).forEachIndexed { index, invalidTarget ->
            val fresh = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry) { 2_000_000_000L + index }
            assertNull(
                invalidTarget,
                fresh.observeTopLevelNavigation(profileId, claveSource, invalidTarget, 20L + index, true),
            )
        }

        val freshPolicy = BrowserUrlPolicy(BuiltInSiteProfiles.qaRegistry, profileId)
        assertEquals(TrustMode.BROWSE_ONLY, freshPolicy.resolve(claveSource).trustMode)
    }

    @Test
    fun publicCatalogPromotesAuthenticationButKeepsDocumentSigningUnimplemented() {
        val publicCatalog = loadBundledPublicPortalCatalog()
        val entry = publicCatalog.entries.single { it.inventoryId == "ES-PUB-0095" }

        assertEquals(portalId, entry.portalId)
        assertEquals(profileId, entry.profileId)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, entry.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, entry.catalogStatus)
        assertEquals("CLIENT_TLS_AUTH", entry.protocolFamily)
        assertTrue(PortalMechanism.CLIENT_TLS_AUTH in entry.observedMechanisms)
        assertTrue(PortalMechanism.CERTIFICATE_ACCESS in entry.observedMechanisms)
        assertTrue(PortalMechanism.ELECTRONIC_SIGNATURE in entry.observedMechanisms)
        assertTrue(entry.observedSignatureFormats.isEmpty())
        assertEquals("2026-08-19", entry.reviewedOn.toString())
        assertTrue(entry.limitations.contains("firma sigue NO_VERIFICADO", ignoreCase = true))
        assertTrue(entry.limitations.contains("clave privada", ignoreCase = true))

        val qa = PortalCatalogRepository(BuiltInSiteProfiles.qaRegistry, BuiltInSiteProfiles.catalog, publicCatalog)
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

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
class NavarraClientAuthProfileTest {
    private val profileId = ProfileId("navarra-sede-registro-general")
    private val portalId = PortalId("navarra-sede-registro-general")
    private val startUrl = URI(
        "https://www.navarra.es/es/tramites/on/-/line/registro-general-electronico",
    )
    private val sourceUrl = URI("https://ateka.navarra.es/ateka/router")

    @Test
    fun qaProfilePinsTheObservedAtekaClientTlsTransitionAndReleaseStaysDisabled() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }
        val policy = checkNotNull(profile.clientAuthPolicy)

        assertEquals(1, profile.profileVersion)
        assertEquals("Gobierno de Navarra — Registro General con certificado", profile.displayName)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(startUrl, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://www.navarra.es")), profile.initiatorOrigins)
        assertEquals(
            setOf(
                ExactOrigin.parse("https://administracionelectronica.navarra.es"),
                ExactOrigin.parse("https://ateka.navarra.es"),
            ),
            profile.redirectOrigins,
        )
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertEquals(ClientAuthTransitionMode.DIRECT_FROM_SOURCE, policy.transitionMode)
        assertEquals(setOf(ExactOrigin.parse("https://ateka.navarra.es")), policy.requestOrigins)
        assertEquals(setOf(sourceUrl), policy.sourceUrls)
        assertTrue(policy.sourceFixedQueryParameters.isEmpty())
        assertEquals(setOf("ReturnUrl"), policy.sourceRequiredEphemeralQueryParameters)
        assertTrue(policy.linkedEphemeralQueryParameters.isEmpty())
        assertEquals(mapOf("ReturnUrl" to "returnUrl"), policy.linkedEphemeralQueryParameterMappings)
        assertEquals("/ateka/Certificate/login", policy.requestPath)
        assertTrue(policy.fixedQueryParameters.isEmpty())
        assertEquals(setOf("returnUrl"), policy.requiredEphemeralQueryParameters)
        assertEquals(443, policy.requestPort)
        assertTrue(policy.allowEmptyIssuerList)
        assertEquals(setOf("RSA"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertTrue(profile.evidence.all { it.reviewedOn.toString() == "2026-08-18" })

        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_CLIENT_AUTH, BuiltInSiteProfiles.qaRegistry.resolve(startUrl)?.trustMode)
        assertEquals(
            TrustMode.BROWSE_ONLY,
            BuiltInSiteProfiles.qaRegistry.resolve(URI("https://ateka.navarra.es/ateka/router"))?.trustMode,
        )
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(startUrl))
    }

    @Test
    fun publicCatalogPromotesOnlyTheCertificateAuthenticationSeam() {
        val publicCatalog = loadBundledPublicPortalCatalog()
        val entry = publicCatalog.entries.single { it.inventoryId == "ES-PUB-0114" }

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
        assertTrue(entry.limitations.contains("QA", ignoreCase = true))
        assertTrue(entry.limitations.contains("E2E", ignoreCase = true))
        assertTrue(entry.limitations.contains("firma", ignoreCase = true))

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

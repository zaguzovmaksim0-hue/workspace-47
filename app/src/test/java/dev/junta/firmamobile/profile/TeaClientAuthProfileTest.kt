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
class TeaClientAuthProfileTest {
    private val profileId = ProfileId("tea-alegaciones-certificado")
    private val portalId = PortalId("age-sede-electronica-de-los-tribunales-economico-administrativos-tea")
    private val entryUrl = URI("https://sede.tea.hacienda.gob.es/TEA/alegaciones.html")
    private val oldTargetUrl = URI("https://www1.tea.hacienda.gob.es/wlpl/TEAC-TRAM/SedeTRAM?tram=0")
    private val startUrl = URI(
        "https://sede.tea.hacienda.gob.es/static_files/common/html/selector_acceso/" +
            "SelectorAccesos.html?rep=S&ref=%2Fwlpl%2FTEAC-TRAM%2FSedeTRAM%3Ftram%3D0&aut=CPE",
    )
    private val targetUrl = URI(
        "https://www1.tea.hacienda.gob.es/wlpl/OVCT-CXEW/DialogoRepresentacion" +
            "?ref=%2Fwlpl%2FTEAC-TRAM%2FSedeTRAM%3Ftram%3D0",
    )

    @Test
    fun qaProfilePinsTheObservedCrossOriginTls12AlegacionesTransitionAndReleaseStaysDisabled() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }
        val policy = checkNotNull(profile.clientAuthPolicy)

        assertEquals(1, profile.profileVersion)
        assertEquals("TEA — Alegaciones con certificado", profile.displayName)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(startUrl, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://sede.tea.hacienda.gob.es")), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertEquals(ClientAuthTransitionMode.DIRECT_FROM_SOURCE, policy.transitionMode)
        assertEquals(setOf(ExactOrigin.parse("https://www1.tea.hacienda.gob.es")), policy.requestOrigins)
        assertEquals(443, policy.requestPort)
        assertEquals(setOf(startUrl), policy.sourceUrls)
        assertEquals("/wlpl/OVCT-CXEW/DialogoRepresentacion", policy.requestPath)
        assertEquals(linkedMapOf("ref" to "/wlpl/TEAC-TRAM/SedeTRAM?tram=0"), policy.fixedQueryParameters)
        assertEquals(HttpMethod.GET, policy.requestMethod)
        assertTrue(policy.requiredEphemeralQueryParameters.isEmpty())
        assertTrue(policy.sourceFixedQueryParameters.isEmpty())
        assertTrue(policy.sourceRequiredEphemeralQueryParameters.isEmpty())
        assertTrue(policy.linkedEphemeralQueryParameters.isEmpty())
        assertTrue(policy.linkedEphemeralQueryParameterMappings.isEmpty())
        assertTrue(policy.sourceBase64UrlConstraints.isEmpty())
        assertTrue(policy.requestContinuationUrlConstraints.isEmpty())
        assertTrue(policy.returnUrlConstraints.isEmpty())
        assertTrue(policy.requireOfferedKeyTypeMatch)
        assertTrue(policy.requireTlsClientAuthExtendedKeyUsage)
        assertFalse(policy.allowEmptyIssuerList)
        assertEquals(15, policy.grantTtlSeconds)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(4, profile.evidence.size)
        assertEquals(
            mapOf(entryUrl to "2026-08-14", oldTargetUrl to "2026-08-14",
                startUrl to "2026-11-08", targetUrl to "2026-11-08"),
            profile.evidence.associate { it.url to it.reviewedOn.toString() },
        )
        assertTrue(policy.matchesSourceUrl(startUrl))
        assertFalse(policy.matchesSourceUrl(entryUrl))
        assertTrue(policy.matchesRequestUrl(targetUrl))
        assertFalse(policy.matchesRequestUrl(oldTargetUrl))

        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_CLIENT_AUTH, BuiltInSiteProfiles.qaRegistry.resolve(startUrl)?.trustMode)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(startUrl))
    }

    @Test
    fun publicCatalogPromotesOnlyCertificateAccessAndKeepsSignatureAndReleaseDisabled() {
        val publicCatalog = loadBundledPublicPortalCatalog()
        val entry = publicCatalog.entries.single { it.inventoryId == "ES-PUB-0090" }

        assertEquals(portalId, entry.portalId)
        assertEquals(entryUrl, entry.entryUrl)
        assertEquals(startUrl, entry.launchUrl)
        assertEquals(profileId, entry.profileId)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, entry.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, entry.catalogStatus)
        assertEquals("CLIENT_TLS_AUTH", entry.protocolFamily)
        assertTrue(PortalMechanism.CLIENT_TLS_AUTH in entry.observedMechanisms)
        assertTrue(PortalMechanism.CERTIFICATE_ACCESS in entry.observedMechanisms)
        assertFalse(PortalMechanism.ELECTRONIC_SIGNATURE in entry.observedMechanisms)
        assertTrue(entry.observedSignatureFormats.isEmpty())
        assertEquals("2026-11-08", entry.reviewedOn.toString())
        assertTrue(entry.limitations.contains("TLS 1.2", ignoreCase = true))
        assertTrue(entry.limitations.contains("E2E", ignoreCase = true))
        assertTrue(entry.limitations.contains("ref=/wlpl/TEAC-TRAM/SedeTRAM?tram=0"))
        assertTrue(entry.limitations.contains("selector", ignoreCase = true))

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
        assertNull(qaPortal.lastVerifiedOn)
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertNull(release.resolveLaunch(releasePortal))
    }
}

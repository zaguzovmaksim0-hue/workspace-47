package dev.junta.firmamobile.profile

import android.net.Uri
import dev.junta.firmamobile.catalog.PortalCatalogRepository
import dev.junta.firmamobile.catalog.PortalId
import dev.junta.firmamobile.catalog.PortalInventoryStatus
import dev.junta.firmamobile.catalog.PortalLaunchTarget
import dev.junta.firmamobile.catalog.PortalSupportStatus
import dev.junta.firmamobile.catalog.PortalServiceCapability
import dev.junta.firmamobile.catalog.PublicCatalogStatus
import dev.junta.firmamobile.catalog.loadBundledPublicPortalCatalog
import dev.junta.firmamobile.network.JuntaOriginPolicy
import dev.junta.firmamobile.signing.BuiltInProtocolAdapterRegistry
import java.net.URI
import java.time.LocalDate
import org.junit.Assert.assertEquals
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
class RedEsProfileCatalogBindingTest {
    private val profileId = ProfileId("red-es-sede-public-navigation")
    private val portalId = PortalId("age-red-es")
    private val startUrl = URI("https://sede.red.gob.es/")

    @Test
    fun profileConfigPreservesExactClaveClientAuthContract() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(2, profile.profileVersion)
        assertEquals("Sede electrónica pública de Red.es", profile.displayName)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(startUrl, profile.startUrl)
        assertEquals(
            setOf(ExactOrigin.parse("https://sede.red.gob.es")),
            profile.initiatorOrigins,
        )
        assertEquals(
            setOf(ExactOrigin.parse("https://pasarela.clave.gob.es")),
            profile.redirectOrigins,
        )
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        assertEquals(ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE, profile.clientAuthPolicy?.transitionMode)
        assertEquals(HttpMethod.POST, profile.clientAuthPolicy?.requestMethod)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(
            listOf(
                "https://sede.red.gob.es/" to "2026-08-25",
                "https://sede.red.gob.es/es/carpeta-del-ciudadano/mis-tramites" to "2026-11-07",
                "https://sede.red.gob.es/oficina/solicitudes/solicitudes.do" to "2026-11-07",
                "https://sede.red.gob.es/oficina/autenticacion/autenticacion.do" to "2026-11-07",
                "https://pasarela.clave.gob.es/Proxy2/ServiceProvider" to "2026-11-07",
                "https://pasarela.clave.gob.es/Proxy2/ServiceRedirect" to "2026-11-07",
                "https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen" to "2026-11-07",
            ).map { (url, date) -> EvidenceReference(URI(url), java.time.LocalDate.parse(date)) },
            profile.evidence,
        )
    }

    @Test
    fun registryResolvesSameOriginClientAuthPathsOnlyInQa() {
        val releaseProfile = BuiltInSiteProfiles.releaseRegistry.profile(profileId)
        val qaProfile = BuiltInSiteProfiles.qaRegistry.profile(profileId)
        assertNull(releaseProfile)
        assertNotNull(qaProfile)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, qaProfile?.compatibilityStatus)

        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(startUrl))
        assertEquals(
            TrustMode.TRUSTED_CLIENT_AUTH,
            BuiltInSiteProfiles.qaRegistry.resolve(startUrl)?.trustMode,
        )
        assertEquals(
            TrustMode.TRUSTED_CLIENT_AUTH,
            BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.red.gob.es/procedimientos"))?.trustMode,
        )

        listOf(
            "http://sede.red.gob.es/",
            "https://sede.red.gob.es:8443/",
            "https://evil.sede.red.gob.es/",
            "https://red.gob.es/",
            "https://user@sede.red.gob.es/",
        ).forEach { rejectedUrl ->
            assertNull(rejectedUrl, BuiltInSiteProfiles.qaRegistry.resolve(URI(rejectedUrl)))
        }
    }

    @Test
    fun securityAndProtocolIsolationEnforcesFailClosedNavigation() {
        assertNull(BuiltInProtocolAdapterRegistry.registry.resolve(profileId, ProtocolOperation.SIGN))
        assertNull(
            BuiltInProtocolAdapterRegistry.registry.resolve(
                profileId,
                ProtocolOperation.SELECT_CERTIFICATE,
            ),
        )
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(profileId).isEmpty())
        assertNull(JuntaOriginPolicy.signingOriginFor(Uri.parse(startUrl.toString()), profileId))
        assertEquals(
            setOf(ExactOrigin.parse("https://sede.red.gob.es"), ExactOrigin.parse("https://pasarela.clave.gob.es")),
            JuntaOriginPolicy.browserOrigins(profileId),
        )
        assertEquals(
            setOf("sede.red.gob.es", "pasarela.clave.gob.es"),
            JuntaOriginPolicy.browserAllowedHosts(profileId),
        )
    }

    @Test
    fun catalogBindsRedEsAsPendingNavigationAndResolvesLaunchOnlyForExactSeed() {
        val publicCatalog = loadBundledPublicPortalCatalog()
        val entry = publicCatalog.entries.single { it.inventoryId == "ES-PUB-0086" }

        assertEquals(portalId, entry.portalId)
        assertEquals(profileId, entry.profileId)
        assertEquals("https://sede.red.gob.es/", entry.entryUrl.toString())
        assertEquals("AGE_PUBLIC_SEDE_NAVIGATION", entry.protocolFamily)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, entry.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, entry.catalogStatus)
        assertTrue(entry.observedMechanisms.isEmpty())
        assertTrue(entry.observedSignatureFormats.isEmpty())
        assertEquals("2026-08-25", entry.reviewedOn.toString())

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
        assertEquals(setOf(PortalServiceCapability.CERTIFICATE_ACCESS), qaPortal.capabilities)
        assertTrue(qaPortal.signatureFormats.isEmpty())
        assertEquals(PortalLaunchTarget(profileId, startUrl), qa.resolveLaunch(qaPortal))
        assertEquals(PortalLaunchTarget(profileId, startUrl), qa.resolveLaunch(profileId, startUrl))

        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertTrue(!releasePortal.isEnabled)
        assertNull(release.resolveLaunch(releasePortal))
        assertNull(release.resolveLaunch(profileId, startUrl))

        listOf(
            "http://sede.red.gob.es/",
            "https://user@sede.red.gob.es/",
            "https://sede.red.gob.es:8443/",
            "https://evil.sede.red.gob.es/",
            "https://sede.red.gob.es/procedimientos",
        ).forEach { rejectedUrl ->
            assertNull(rejectedUrl, qa.resolveLaunch(profileId, URI(rejectedUrl)))
        }
    }
}

package dev.junta.firmamobile.profile

import android.net.Uri
import dev.junta.firmamobile.catalog.PortalCatalogRepository
import dev.junta.firmamobile.catalog.PortalMechanism
import dev.junta.firmamobile.catalog.PortalServiceCapability
import dev.junta.firmamobile.catalog.PortalId
import dev.junta.firmamobile.catalog.PortalInventoryStatus
import dev.junta.firmamobile.catalog.PortalLaunchTarget
import dev.junta.firmamobile.catalog.PortalSupportStatus
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
class ImsersoProfileCatalogBindingTest {
    private val profileId = ProfileId("imserso-sede-public-navigation")
    private val portalId = PortalId("age-instituto-de-mayores-y-servicios-sociales-imserso")
    private val entryUrl = URI("https://sede.imserso.gob.es/inicio")
    private val startUrl = URI("https://sede.imserso.gob.es/sedecdi?locale=es&action=consulta&representante=no&aut=si")

    @Test
    fun profileConfigPinsTheExactQaClaveLoginContract() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(2, profile.profileVersion)
        assertEquals(
            "IMSERSO — consulta de solicitudes",
            profile.displayName,
        )
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(startUrl, profile.startUrl)
        assertEquals(
            setOf(ExactOrigin.parse("https://sede.imserso.gob.es")),
            profile.initiatorOrigins,
        )
        assertEquals(setOf(ExactOrigin.parse("https://pasarela.clave.gob.es")), profile.redirectOrigins)
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        assertEquals(
            ClientAuthPolicy(
                transitionMode = ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE,
                requestOrigins = setOf(ExactOrigin.parse("https://pasarela-ident.clave.gob.es")),
                sourceUrls = setOf(URI("https://pasarela.clave.gob.es/Proxy2/ServiceRedirect")),
                requestMethod = HttpMethod.POST,
                requestPath = "/IdP2/AuthenticateCitizen",
                fixedQueryParameters = emptyMap(),
                requiredEphemeralQueryParameters = emptySet(),
                allowEmptyIssuerList = true,
                grantTtlSeconds = 15,
            ),
            profile.clientAuthPolicy,
        )
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(
            listOf(
                EvidenceReference(URI("https://sede.imserso.gob.es/inicio"), LocalDate.parse("2026-08-25")),
                EvidenceReference(
                    URI("https://sede.imserso.gob.es/sedecdi?locale=es&action=consulta&representante=no&aut=si"),
                    LocalDate.parse("2026-11-08"),
                ),
                EvidenceReference(
                    URI("https://pasarela.clave.gob.es/Proxy2/ServiceProvider"),
                    LocalDate.parse("2026-11-08"),
                ),
                EvidenceReference(
                    URI("https://pasarela.clave.gob.es/Proxy2/ServiceRedirect"),
                    LocalDate.parse("2026-11-08"),
                ),
                EvidenceReference(
                    URI("https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen"),
                    LocalDate.parse("2026-11-08"),
                ),
            ),
            profile.evidence,
        )
    }

    @Test
    fun registryResolvesExactEntryAndSameOriginBrowsePathsInQa() {
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
            BuiltInSiteProfiles.qaRegistry.resolve(
                URI("https://sede.imserso.gob.es/es/procedimientos-servicios/centros"),
            )?.trustMode,
        )

        listOf(
            "http://sede.imserso.gob.es/inicio",
            "https://sede.imserso.gob.es:8443/inicio",
            "https://evil.sede.imserso.gob.es/inicio",
            "https://gob.es/inicio",
            "https://user@sede.imserso.gob.es/inicio",
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
            setOf(ExactOrigin.parse("https://sede.imserso.gob.es"), ExactOrigin.parse("https://pasarela.clave.gob.es")),
            JuntaOriginPolicy.browserOrigins(profileId),
        )
        assertEquals(
            setOf("sede.imserso.gob.es", "pasarela.clave.gob.es"),
            JuntaOriginPolicy.browserAllowedHosts(profileId),
        )
    }

    @Test
    fun catalogBindsImsersoAsPendingNavigationAndResolvesLaunchOnlyForExactEntry() {
        val publicCatalog = loadBundledPublicPortalCatalog()
        val entry = publicCatalog.entries.single { it.inventoryId == "ES-PUB-0053" }

        assertEquals(portalId, entry.portalId)
        assertEquals(profileId, entry.profileId)
        assertEquals(entryUrl, entry.entryUrl)
        assertEquals(startUrl, entry.launchUrl)
        assertEquals("CLIENT_TLS_AUTH", entry.protocolFamily)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, entry.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, entry.catalogStatus)
        assertEquals(
            setOf(PortalMechanism.CERTIFICATE_ACCESS, PortalMechanism.CLIENT_TLS_AUTH), entry.observedMechanisms,
        )
        assertTrue(entry.observedSignatureFormats.isEmpty())
        assertEquals("2026-11-08", entry.reviewedOn.toString())

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
        assertEquals(PortalLaunchTarget(profileId, startUrl), qa.resolveLaunch(profileId, entryUrl))

        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertTrue(!releasePortal.isEnabled)
        assertNull(release.resolveLaunch(releasePortal))
        assertNull(release.resolveLaunch(profileId, startUrl))

        listOf(
            "http://sede.imserso.gob.es/inicio",
            "https://user@sede.imserso.gob.es/inicio",
            "https://sede.imserso.gob.es:8443/inicio",
            "https://evil.sede.imserso.gob.es/inicio",
            "https://sede.imserso.gob.es/",
        ).forEach { rejectedUrl ->
            assertNull(rejectedUrl, qa.resolveLaunch(profileId, URI(rejectedUrl)))
        }
    }
}

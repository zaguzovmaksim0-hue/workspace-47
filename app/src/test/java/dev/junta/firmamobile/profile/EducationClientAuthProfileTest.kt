package dev.junta.firmamobile.profile

import dev.junta.firmamobile.catalog.PortalCatalogRepository
import dev.junta.firmamobile.catalog.PortalInventoryStatus
import dev.junta.firmamobile.catalog.PortalId
import dev.junta.firmamobile.catalog.PortalMechanism
import dev.junta.firmamobile.catalog.PortalSupportStatus
import dev.junta.firmamobile.catalog.PublicCatalogStatus
import dev.junta.firmamobile.catalog.loadBundledPublicPortalCatalog
import dev.junta.firmamobile.network.JuntaOriginPolicy
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
class EducationClientAuthProfileTest {
    private val profileId = ProfileId("educacion-convocatoria")
    private val startUrl = URI(
        "https://sede.educacion.gob.es/sede/login/loginConv.jjsp?iA=no&idConvocatoria=46",
    )

    @Test
    fun qaProfilePinsOnlyTheObservedEducationClaveClientTlsContract() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }
        val policy = checkNotNull(profile.clientAuthPolicy)

        assertEquals(2, profile.profileVersion)
        assertEquals("Ministerio de Educación — Convocatoria 46 — acceso con certificado", profile.displayName)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(startUrl, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://sede.educacion.gob.es")), profile.initiatorOrigins)
        assertEquals(
            setOf(
                ExactOrigin.parse("https://www.educacion.gob.es"),
                ExactOrigin.parse("https://pasarela.clave.gob.es"),
            ),
            profile.redirectOrigins,
        )
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        assertEquals(ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE, policy.transitionMode)
        assertEquals(
            setOf(ExactOrigin.parse("https://pasarela-ident.clave.gob.es")),
            policy.requestOrigins,
        )
        assertEquals(
            setOf(URI("https://pasarela.clave.gob.es/Proxy2/ServiceRedirect")),
            policy.sourceUrls,
        )
        assertEquals("/IdP2/AuthenticateCitizen", policy.requestPath)
        assertTrue(policy.fixedQueryParameters.isEmpty())
        assertTrue(policy.requiredEphemeralQueryParameters.isEmpty())
        assertTrue(policy.sourceFixedQueryParameters.isEmpty())
        assertTrue(policy.sourceRequiredEphemeralQueryParameters.isEmpty())
        assertTrue(policy.linkedEphemeralQueryParameters.isEmpty())
        assertTrue(policy.linkedEphemeralQueryParameterMappings.isEmpty())
        assertEquals(15, policy.grantTtlSeconds)
        assertEquals(443, policy.requestPort)
        assertEquals(HttpMethod.POST, policy.requestMethod)
        assertTrue(policy.allowEmptyIssuerList)
        assertEquals(setOf("RSA"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(5, profile.evidence.size)
        assertTrue(profile.evidence.all { it.reviewedOn.toString() == "2026-08-19" })

        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_CLIENT_AUTH, BuiltInSiteProfiles.qaRegistry.resolve(startUrl)?.trustMode)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(startUrl))
    }

    @Test
    fun browserOriginsKeepTheClientCertificateRequestOriginOutOfNormalNavigation() {
        val browserOrigins = JuntaOriginPolicy.browserOrigins(profileId)

        assertEquals(
            setOf(
                ExactOrigin.parse("https://sede.educacion.gob.es"),
                ExactOrigin.parse("https://www.educacion.gob.es"),
                ExactOrigin.parse("https://pasarela.clave.gob.es"),
            ),
            browserOrigins,
        )
        assertFalse(ExactOrigin.parse("https://pasarela-ident.clave.gob.es") in browserOrigins)
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(profileId).isEmpty())
    }

    @Test
    fun publicCatalogMarksOnlyClientTlsAsImplementedAndReleaseRemainsDisabled() {
        val publicCatalog = loadBundledPublicPortalCatalog()
        val entry = publicCatalog.entries.single { it.inventoryId == "ES-PUB-0182" }

        assertEquals(profileId, entry.profileId)
        assertEquals(startUrl, entry.entryUrl)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, entry.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, entry.catalogStatus)
        assertEquals("CLIENT_TLS_AUTH", entry.protocolFamily)
        assertTrue(PortalMechanism.CLIENT_TLS_AUTH in entry.observedMechanisms)
        assertTrue(PortalMechanism.CERTIFICATE_ACCESS in entry.observedMechanisms)
        assertFalse(PortalMechanism.ELECTRONIC_SIGNATURE in entry.observedMechanisms)
        assertTrue(entry.observedSignatureFormats.isEmpty())
        assertTrue(entry.limitations.contains("firma", ignoreCase = true))

        val qa = PortalCatalogRepository(BuiltInSiteProfiles.qaRegistry, BuiltInSiteProfiles.catalog, publicCatalog)
        val release = PortalCatalogRepository(BuiltInSiteProfiles.releaseRegistry, BuiltInSiteProfiles.catalog, publicCatalog)
        val qaPortal = qa.portals().single { it.portalId == PortalId("educacion-convocatoria-46") }
        val releasePortal = release.portals().single { it.portalId == PortalId("educacion-convocatoria-46") }
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(profileId, qa.resolveLaunch(qaPortal)?.profileId)
        assertEquals(startUrl, qa.resolveLaunch(qaPortal)?.entryUrl)
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertNull(release.resolveLaunch(releasePortal))
    }
}

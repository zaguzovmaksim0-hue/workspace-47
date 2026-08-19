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
class GvaClientAuthProfileTest {
    private val profileId = ProfileId("generalitat-valenciana-client-auth")
    private val startUrl = URI(
        "https://www.tramita.gva.es/ctt-att-atr/asistente/iniciarTramite.html?" +
            "tramite=DGM_GEN&version=4&idioma=es&idProcGuc=15602&" +
            "idSubfaseGuc=SOLICITUD&idCatGuc=PR",
    )

    @Test
    fun qaProfilePinsOnlyTheObservedGvaClientTlsAuthenticationContract() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }
        val policy = checkNotNull(profile.clientAuthPolicy)

        assertEquals(1, profile.profileVersion)
        assertEquals("Generalitat Valenciana — acceso con certificado", profile.displayName)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(startUrl, profile.startUrl)
        assertEquals(
            setOf(
                ExactOrigin.parse("https://www.tramita.gva.es"),
                ExactOrigin.parse("https://ptt-clave.gva.es"),
            ),
            profile.initiatorOrigins,
        )
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        assertEquals(ClientAuthTransitionMode.DIRECT_FROM_SOURCE, policy.transitionMode)
        assertEquals(
            setOf(ExactOrigin.parse("https://ptt-clave-clientcert.gva.es")),
            policy.requestOrigins,
        )
        assertEquals(
            setOf(URI("https://ptt-clave.gva.es/pttclave/redirigirClave.html")),
            policy.sourceUrls,
        )
        assertEquals(setOf("idSesion"), policy.sourceRequiredEphemeralQueryParameters)
        assertEquals(setOf("idSesion"), policy.linkedEphemeralQueryParameters)
        assertEquals("/pttclave/retornoClientCert.html", policy.requestPath)
        assertEquals(mapOf("idioma" to "es"), policy.fixedQueryParameters)
        assertEquals(setOf("idSesion"), policy.requiredEphemeralQueryParameters)
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
    fun browserOriginsExcludeTheClientCertificateRequestOrigin() {
        val browserOrigins = JuntaOriginPolicy.browserOrigins(profileId)

        assertTrue(ExactOrigin.parse("https://www.tramita.gva.es") in browserOrigins)
        assertTrue(ExactOrigin.parse("https://ptt-clave.gva.es") in browserOrigins)
        assertFalse(ExactOrigin.parse("https://ptt-clave-clientcert.gva.es") in browserOrigins)
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(profileId).isEmpty())
    }

    @Test
    fun publicCatalogMarksOnlyClientTlsAsImplementedAndReleaseRemainsDisabled() {
        val publicCatalog = loadBundledPublicPortalCatalog()
        val entry = publicCatalog.entries.single { it.inventoryId == "ES-PUB-0108" }

        assertEquals(profileId, entry.profileId)
        assertEquals(startUrl, entry.entryUrl)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, entry.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, entry.catalogStatus)
        assertEquals("CLIENT_TLS_AUTH", entry.protocolFamily)
        assertTrue(PortalMechanism.CLIENT_TLS_AUTH in entry.observedMechanisms)
        assertTrue(PortalMechanism.CERTIFICATE_ACCESS in entry.observedMechanisms)
        assertTrue(PortalMechanism.ELECTRONIC_SIGNATURE in entry.observedMechanisms)
        assertTrue(entry.observedSignatureFormats.isEmpty())
        assertTrue(entry.limitations.contains("firma", ignoreCase = true))
        assertTrue(entry.limitations.contains("sin E2E", ignoreCase = true))

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
        val qaPortal = qa.portals().single { it.portalId == PortalId("gva-sede") }
        val releasePortal = release.portals().single { it.portalId == PortalId("gva-sede") }
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(profileId, qa.resolveLaunch(qaPortal)?.profileId)
        assertEquals(startUrl, qa.resolveLaunch(qaPortal)?.entryUrl)
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertNull(release.resolveLaunch(releasePortal))
    }
}

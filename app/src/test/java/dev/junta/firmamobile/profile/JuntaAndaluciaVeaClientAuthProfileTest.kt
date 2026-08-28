package dev.junta.firmamobile.profile

import dev.junta.firmamobile.browser.BrowserTrustController
import dev.junta.firmamobile.browser.BrowserUrlPolicy
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
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
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
class JuntaAndaluciaVeaClientAuthProfileTest {
    private val profileId = ProfileId("junta-andalucia-vea-peg")
    private val portalId = PortalId("junta-andalucia-sede")
    private val startUrl = URI(VEA_START)

    @Test
    fun qaProfilePinsOnlyTheObservedVeaCertificateAuthenticationContract() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }
        val policy = checkNotNull(profile.clientAuthPolicy)

        assertEquals(2, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(startUrl, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse(VEA_ORIGIN)), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.endpoints.isEmpty())

        assertEquals(ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE, policy.transitionMode)
        assertEquals(HttpMethod.GET, policy.requestMethod)
        assertEquals(setOf(ExactOrigin.parse(WS235_ORIGIN)), policy.requestOrigins)
        assertEquals(setOf(URI(API_LOGIN)), policy.sourceUrls)
        assertEquals("/authenticationFacade", policy.requestPath)
        assertEquals(
            mapOf(
                "action" to "validateCert",
                "appId" to "CHIE.VEA",
                "comeBackURL" to base64(API_RETURN),
            ),
            policy.fixedQueryParameters,
        )
        assertEquals(setOf("ticketId", "webSessionId"), policy.requiredEphemeralQueryParameters)
        assertEquals(
            mapOf(
                "modoAcceso" to "afirma",
                "codigoProcedimiento" to "PEG_VEA",
                "comeBackUrl" to base64(VEA_AUTH_FACADE),
            ),
            policy.sourceFixedQueryParameters,
        )
        assertEquals(setOf("redirectUrl"), policy.sourceRequiredEphemeralQueryParameters)
        val redirect = policy.sourceBase64UrlConstraints.getValue("redirectUrl")
        assertEquals(ExactOrigin.parse(VEA_ORIGIN), redirect.origin)
        assertEquals("/inicio/procedimiento-detalle/PEG_VEA", redirect.path)
        assertEquals(mapOf("iniciarSolicitud" to "true"), redirect.fixedQueryParameters)
        assertEquals(setOf("procedureId", "versionId"), redirect.requiredEphemeralQueryParameters)
        assertEquals(
            setOf(
                ClientAuthUrlConstraint(
                    ExactOrigin.parse(API_ORIGIN),
                    "/auth/returnLogin",
                    emptyMap(),
                    setOf("resCode"),
                ),
                ClientAuthUrlConstraint(
                    ExactOrigin.parse(API_ORIGIN),
                    "/auth/returnLogin",
                    mapOf("appId" to "CHIE.VEA"),
                    setOf("resCode", "ticketId", "webSessionId"),
                ),
                ClientAuthUrlConstraint(
                    ExactOrigin.parse(API_ORIGIN),
                    "/auth/endLogin",
                    emptyMap(),
                    emptySet(),
                ),
                ClientAuthUrlConstraint(
                    ExactOrigin.parse(VEA_ORIGIN),
                    "/authFacade",
                    emptyMap(),
                    setOf("error", "redirectUrl"),
                ),
                ClientAuthUrlConstraint(
                    ExactOrigin.parse(VEA_ORIGIN),
                    "/authFacade",
                    emptyMap(),
                    setOf("token", "redirectUrl"),
                ),
            ),
            policy.returnUrlConstraints,
        )
        assertTrue(policy.allowEmptyIssuerList)
        assertEquals(60, policy.grantTtlSeconds)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertFalse(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertTrue(profile.evidence.all { it.reviewedOn.toString() == "2026-08-28" })

        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_CLIENT_AUTH, BuiltInSiteProfiles.qaRegistry.resolve(startUrl)?.trustMode)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(startUrl))
    }

    @Test
    fun observedReturnLoginShapeIsBoundToTheConfirmedTlsRequest() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }
        val policy = checkNotNull(profile.clientAuthPolicy)
        val tlsTarget = URI(
            "$WS235_ORIGIN/authenticationFacade?action=validateCert&appId=CHIE.VEA" +
                "&comeBackURL=${urlEncode(base64(API_RETURN))}" +
                "&ticketId=synthetic-ticket&webSessionId=synthetic-session",
        )
        val exactReturn = URI(
            "$API_RETURN?appId=CHIE.VEA&resCode=synthetic-result" +
                "&ticketId=synthetic-ticket&webSessionId=synthetic-session",
        )

        assertFalse(policy.matchesReturnUrl(exactReturn))
        assertTrue(policy.matchesReturnUrl(exactReturn, tlsTarget))
        assertFalse(
            policy.matchesReturnUrl(
                URI(exactReturn.toString().replace("ticketId=synthetic-ticket", "ticketId=other-ticket")),
                tlsTarget,
            ),
        )
        assertFalse(
            policy.matchesReturnUrl(
                URI(exactReturn.toString().replace("appId=CHIE.VEA", "appId=OTHER.APP")),
                tlsTarget,
            ),
        )
    }

    @Test
    fun onlyExactApiSourceAndReturnPathsKeepTheSelectedVeaProfileActive() {
        val controller = BrowserTrustController(
            BrowserUrlPolicy(BuiltInSiteProfiles.qaRegistry, profileId),
            SensitiveFlowInvalidator {},
        )
        assertEquals(profileId, controller.navigate(VEA_START).activeProfileId)

        val source = controller.navigate(sourceUrl())
        assertEquals(TrustMode.TRUSTED_BROWSE, source.resolution.trustMode)
        assertEquals(profileId, source.activeProfileId)

        val errorReturn = controller.navigate("$API_RETURN?resCode=1")
        assertEquals(TrustMode.TRUSTED_BROWSE, errorReturn.resolution.trustMode)
        assertEquals(profileId, errorReturn.activeProfileId)

        val end = controller.navigate(API_END)
        assertEquals(TrustMode.TRUSTED_BROWSE, end.resolution.trustMode)
        assertEquals(profileId, end.activeProfileId)

        val fresh = BrowserUrlPolicy(BuiltInSiteProfiles.qaRegistry, profileId)
        listOf(
            "$API_ORIGIN/auth/other",
            "$API_RETURN?resCode=1&extra=1",
            "$API_END?extra=1",
            sourceUrl(redirectUrl = "https://evil.example/?iniciarSolicitud=true&procedureId=1&versionId=2"),
            sourceUrl(redirectUrl = "$VEA_START?iniciarSolicitud=false&procedureId=1&versionId=2"),
            sourceUrl(redirectUrl = "$VEA_START?iniciarSolicitud=true&procedureId=1&versionId=2&extra=1"),
        ).forEach { raw ->
            val resolution = fresh.resolve(raw)
            assertEquals(raw, TrustMode.BROWSE_ONLY, resolution.trustMode)
            assertNull(raw, resolution.site)
        }
    }

    @Test
    fun publicCatalogExposesOnlyClientTlsAuthAndKeepsSigningOutOfScope() {
        val publicCatalog = loadBundledPublicPortalCatalog()
        val entry = publicCatalog.entries.single { it.inventoryId == "ES-PUB-0093" }

        assertEquals(portalId, entry.portalId)
        assertEquals(profileId, entry.profileId)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, entry.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, entry.catalogStatus)
        assertTrue(PortalMechanism.CLIENT_TLS_AUTH in entry.observedMechanisms)
        assertTrue(PortalMechanism.CERTIFICATE_ACCESS in entry.observedMechanisms)
        assertTrue(entry.observedSignatureFormats.isEmpty())
        assertEquals("2026-08-28", entry.reviewedOn.toString())

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
        assertTrue(qaPortal.signatureFormats.isEmpty())
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, startUrl), qa.resolveLaunch(qaPortal))
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertNull(release.resolveLaunch(releasePortal))
    }

    private fun sourceUrl(
        redirectUrl: String = "$VEA_START?iniciarSolicitud=true&procedureId=123&versionId=456",
    ): String = buildString {
        append(API_LOGIN)
        append("?modoAcceso=afirma")
        append("&comeBackUrl=").append(urlEncode(base64(VEA_AUTH_FACADE)))
        append("&redirectUrl=").append(urlEncode(base64(redirectUrl)))
        append("&codigoProcedimiento=PEG_VEA")
    }

    private fun base64(value: String): String = Base64.getEncoder().encodeToString(value.toByteArray())

    private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private companion object {
        const val VEA_ORIGIN = "https://veaja.cloud.juntadeandalucia.es"
        const val API_ORIGIN = "https://api-veaja.cloud.juntadeandalucia.es"
        const val WS235_ORIGIN = "https://ws235.juntadeandalucia.es"
        const val VEA_START = "$VEA_ORIGIN/inicio/procedimiento-detalle/PEG_VEA"
        const val VEA_AUTH_FACADE = "$VEA_ORIGIN/authFacade"
        const val API_LOGIN = "$API_ORIGIN/auth/login"
        const val API_RETURN = "$API_ORIGIN/auth/returnLogin"
        const val API_END = "$API_ORIGIN/auth/endLogin"
    }
}

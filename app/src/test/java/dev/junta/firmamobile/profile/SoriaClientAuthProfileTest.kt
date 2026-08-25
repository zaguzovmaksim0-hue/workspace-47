package dev.junta.firmamobile.profile

import android.net.Uri
import dev.junta.firmamobile.browser.ClientAuthNavigationAuthorizer
import dev.junta.firmamobile.catalog.PortalCatalogRepository
import dev.junta.firmamobile.catalog.PortalId
import dev.junta.firmamobile.catalog.PortalInventoryStatus
import dev.junta.firmamobile.catalog.PortalLaunchTarget
import dev.junta.firmamobile.catalog.PortalMechanism
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
class SoriaClientAuthProfileTest {
    private val profileId = ProfileId("diputacion-soria-sede-client-auth")
    private val portalId = PortalId("diputacion-soria-sede")
    private val startUrl = URI(
        "https://portaltramitador.dipsoria.es/web/inicioWebc.do?opcion=cargar&redirige=L2NhcmdhTWVudVdlYi5kbz9vcGNpb249bm9yZWc%3D&entidad=SORIA&idioma=1",
    )
    private val sourceUrl = startUrl
    private val targetUrl = URI(
        "https://portaltramitador.dipsoria.es/web/inicioWebcCert.do?opcion=ssl&entidad=SORIA&redirige=L2NhcmdhTWVudVdlYi5kbz9vcGNpb249bm9yZWc%253D&idioma=1",
    )

    @Test
    fun qaProfilePinsTheObservedDirectClientTlsTransition() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }
        val policy = checkNotNull(profile.clientAuthPolicy)

        assertEquals(1, profile.profileVersion)
        assertEquals(
            "Sede electrónica de la Diputación de Soria — acceso con certificado",
            profile.displayName,
        )
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(startUrl, profile.startUrl)
        assertEquals(
            setOf(ExactOrigin.parse("https://portaltramitador.dipsoria.es")),
            profile.initiatorOrigins,
        )
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        assertEquals(ClientAuthTransitionMode.DIRECT_FROM_SOURCE, policy.transitionMode)
        assertEquals(
            setOf(ExactOrigin.parse("https://portaltramitador.dipsoria.es")),
            policy.requestOrigins,
        )
        assertEquals(setOf(sourceUrl), policy.sourceUrls)
        assertEquals("/web/inicioWebcCert.do", policy.requestPath)
        assertEquals(
            mapOf(
                "opcion" to "ssl",
                "entidad" to "SORIA",
                "redirige" to "L2NhcmdhTWVudVdlYi5kbz9vcGNpb249bm9yZWc%3D",
                "idioma" to "1",
            ),
            policy.fixedQueryParameters,
        )
        assertTrue(policy.requiredEphemeralQueryParameters.isEmpty())
        assertTrue(policy.sourceFixedQueryParameters.isEmpty())
        assertTrue(policy.sourceRequiredEphemeralQueryParameters.isEmpty())
        assertTrue(policy.linkedEphemeralQueryParameters.isEmpty())
        assertTrue(policy.linkedEphemeralQueryParameterMappings.isEmpty())
        assertEquals(443, policy.requestPort)
        assertTrue(policy.allowEmptyIssuerList)
        assertEquals(15, policy.grantTtlSeconds)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(3, profile.evidence.size)
        assertTrue(profile.evidence.all { it.reviewedOn == LocalDate.parse("2026-08-25") })

        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_CLIENT_AUTH, BuiltInSiteProfiles.qaRegistry.resolve(startUrl)?.trustMode)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(startUrl))
    }

    @Test
    fun authorizerAcceptsOnlyTheExactSoriaSourceAndCertificateTarget() {
        val authorizer = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry)
        val grant = authorizer.observeTopLevelNavigation(
            profileId,
            startUrl.toString(),
            targetUrl.toString(),
            currentEpoch = 11L,
            isModernMainFrameRequest = true,
        )

        assertNotNull(grant)
        assertEquals(profileId, grant?.profileId)
        assertEquals(targetUrl, grant?.target)
        assertNull(
            authorizer.observeTopLevelNavigation(
                profileId,
                startUrl.toString(),
                targetUrl.toString(),
                currentEpoch = 11L,
                isModernMainFrameRequest = true,
            ),
        )

        listOf(
            targetUrl.toString().replace("entidad=SORIA", "entidad=EVIL"),
            targetUrl.toString().replace("%253D", "%253D&extra=1"),
            targetUrl.toString().replace("inicioWebcCert.do", "other.do"),
            targetUrl.toString().replace("portaltramitador.dipsoria.es", "evil.example"),
        ).forEachIndexed { index, invalidTarget ->
            assertNull(
                invalidTarget,
                ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry)
                    .observeTopLevelNavigation(
                        profileId,
                        startUrl.toString(),
                        invalidTarget,
                        currentEpoch = 20L + index,
                        isModernMainFrameRequest = true,
                    ),
            )
        }
    }

    @Test
    fun catalogExposesOnlyQaClientTlsAndKeepsSigningOutOfScope() {
        val publicCatalog = loadBundledPublicPortalCatalog()
        val entry = publicCatalog.entries.single { it.inventoryId == "ES-PUB-0171" }

        assertEquals(portalId, entry.portalId)
        assertEquals(profileId, entry.profileId)
        assertEquals(startUrl, entry.entryUrl)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, entry.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, entry.catalogStatus)
        assertEquals("CLIENT_TLS_AUTH", entry.protocolFamily)
        assertEquals(
            setOf(
                PortalMechanism.CERTIFICATE_ACCESS,
                PortalMechanism.CLIENT_TLS_AUTH,
                PortalMechanism.ELECTRONIC_SIGNATURE,
            ),
            entry.observedMechanisms,
        )
        assertTrue(entry.observedSignatureFormats.isEmpty())
        assertEquals("2026-08-25", entry.reviewedOn.toString())
        assertTrue(entry.limitations.contains("QA-only", ignoreCase = true))
        assertTrue(entry.limitations.contains("firma", ignoreCase = true))

        assertNull(BuiltInProtocolAdapterRegistry.registry.resolve(profileId, ProtocolOperation.SIGN))
        assertNull(
            BuiltInProtocolAdapterRegistry.registry.resolve(
                profileId,
                ProtocolOperation.SELECT_CERTIFICATE,
            ),
        )
        assertTrue(JuntaOriginPolicy.webMessageOriginRules(profileId).isEmpty())
        assertEquals(
            setOf("portaltramitador.dipsoria.es"),
            JuntaOriginPolicy.browserAllowedHosts(profileId),
        )

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
        assertTrue(!releasePortal.isEnabled)
        assertNull(release.resolveLaunch(releasePortal))
    }
}

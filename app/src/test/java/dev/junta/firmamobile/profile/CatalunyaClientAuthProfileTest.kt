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
class CatalunyaClientAuthProfileTest {
    private val profileId = ProfileId("catalunya-peticio-generica-client-auth")
    private val portalId = PortalId("catalunya-tramits-peticio-generica")
    private val startUrl = URI(
        "https://tramits.gencat.cat/ca/tramits/tramits-temes/Peticio-generica?" +
            "category=72461610-a82c-11e3-a972-000c29052e2c",
    )
    private val sourceUrl = URI("https://pasarela.clave.gob.es/Proxy2/ServiceProvider")

    @Test
    fun qaProfilePinsOnlyTheObservedClaveIdentifierClientTlsBoundary() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }
        val policy = checkNotNull(profile.clientAuthPolicy)

        assertEquals(1, profile.profileVersion)
        assertEquals("Generalitat de Catalunya — Petició genèrica — acceso con certificado", profile.displayName)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(startUrl, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://tramits.gencat.cat")), profile.initiatorOrigins)
        assertEquals(
            setOf(
                ExactOrigin.parse("https://ovt.gencat.cat"),
                ExactOrigin.parse("https://valid.aoc.cat"),
                ExactOrigin.parse("https://pasarela.clave.gob.es"),
            ),
            profile.redirectOrigins,
        )
        val sharedAocOrigin = ExactOrigin.parse("https://valid.aoc.cat")
        val sharedAocOwners = BuiltInSiteProfiles.catalog.profiles.filter { candidate ->
            sharedAocOrigin in candidate.initiatorOrigins ||
                sharedAocOrigin in candidate.redirectOrigins ||
                sharedAocOrigin in candidate.trustedBrowseOrigins ||
                sharedAocOrigin in (candidate.clientAuthPolicy?.requestOrigins ?: emptySet())
        }.map { it.profileId }.toSet()
        assertEquals(
            setOf(
                ProfileId("diputacion-barcelona-solicitud-generica-2057"),
                profileId,
                ProfileId("catalunya-seu-registre-client-auth"),
            ),
            sharedAocOwners,
        )
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        assertEquals(ClientAuthTransitionMode.DIRECT_FROM_SOURCE, policy.transitionMode)
        assertEquals(setOf(ExactOrigin.parse("https://pasarela-ident.clave.gob.es")), policy.requestOrigins)
        assertEquals(setOf(sourceUrl), policy.sourceUrls)
        assertEquals("/IdP2/AuthenticateCitizen", policy.requestPath)
        assertTrue(policy.fixedQueryParameters.isEmpty())
        assertTrue(policy.requiredEphemeralQueryParameters.isEmpty())
        assertTrue(policy.sourceFixedQueryParameters.isEmpty())
        assertTrue(policy.sourceRequiredEphemeralQueryParameters.isEmpty())
        assertEquals(443, policy.requestPort)
        assertTrue(policy.allowEmptyIssuerList)
        assertEquals(15, policy.grantTtlSeconds)
        assertEquals(setOf("RSA"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(4, profile.evidence.size)
        assertTrue(profile.evidence.all { it.reviewedOn.toString() == "2026-08-19" })

        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_CLIENT_AUTH, BuiltInSiteProfiles.qaRegistry.resolve(startUrl)?.trustMode)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(startUrl))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://tramits.gencat.cat.evil.example/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://tramits.gencat.cat:444/")))
    }

    @Test
    fun publicCatalogPromotesClientTlsOnlyAndKeepsSigningUnknown() {
        val publicCatalog = loadBundledPublicPortalCatalog()
        val entry = publicCatalog.entries.single { it.inventoryId == "ES-PUB-0105" }

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
        assertTrue(entry.limitations.contains("GSIT", ignoreCase = true))
        assertTrue(entry.limitations.contains("firma", ignoreCase = true))
        assertTrue(entry.limitations.contains("formato", ignoreCase = true))
        assertTrue(entry.limitations.contains("algoritmo", ignoreCase = true))
        assertTrue(entry.limitations.contains("callback", ignoreCase = true))
        assertTrue(entry.limitations.contains("E2E", ignoreCase = true))

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

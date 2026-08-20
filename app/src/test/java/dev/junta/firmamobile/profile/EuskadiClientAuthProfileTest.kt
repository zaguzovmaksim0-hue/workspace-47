package dev.junta.firmamobile.profile

import dev.junta.firmamobile.catalog.PortalCatalogRepository
import dev.junta.firmamobile.catalog.PortalInventoryStatus
import dev.junta.firmamobile.catalog.PortalId
import dev.junta.firmamobile.catalog.PortalMechanism
import dev.junta.firmamobile.catalog.PortalSupportStatus
import dev.junta.firmamobile.catalog.PublicCatalogStatus
import dev.junta.firmamobile.catalog.loadBundledPublicPortalCatalog
import dev.junta.firmamobile.browser.EuskadiClientAuthPostBridgeAdapter
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
class EuskadiClientAuthProfileTest {
    private val profileId = ProfileId(EuskadiClientAuthPostBridgeAdapter.PROFILE_ID)
    private val startUrl = URI(EuskadiClientAuthPostBridgeAdapter.START_URL)

    @Test
    fun qaProfilePinsOnlyObservedIzenpeClientTlsPostContract() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }
        val policy = checkNotNull(profile.clientAuthPolicy)

        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(startUrl, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://www.euskadi.eus")), profile.initiatorOrigins)
        assertEquals(setOf(ExactOrigin.parse("https://eidas.izenpe.com")), profile.redirectOrigins)
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertEquals(ClientAuthTransitionMode.DIRECT_FROM_SOURCE, policy.transitionMode)
        assertEquals(setOf(ExactOrigin.parse("https://eidas2.izenpe.com")), policy.requestOrigins)
        assertEquals(setOf(URI(EuskadiClientAuthPostBridgeAdapter.SOURCE_PAGE)), policy.sourceUrls)
        assertEquals(EuskadiClientAuthPostBridgeAdapter.TARGET_PATH, policy.requestPath)
        assertFalse(policy.allowEmptyIssuerList)
        assertEquals(15, policy.grantTtlSeconds)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertTrue(profile.evidence.all { it.reviewedOn.toString() == "2026-08-19" })
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
    }

    @Test
    fun bridgeAndBrowserOriginsStaySeparatedFromClientCertificateHost() {
        val browserOrigins = JuntaOriginPolicy.browserOrigins(profileId)
        assertTrue(ExactOrigin.parse("https://www.euskadi.eus") in browserOrigins)
        assertTrue(ExactOrigin.parse("https://eidas.izenpe.com") in browserOrigins)
        assertFalse(ExactOrigin.parse("https://eidas2.izenpe.com") in browserOrigins)
        assertEquals(setOf("https://eidas.izenpe.com"), JuntaOriginPolicy.webMessageOriginRules(profileId))
    }

    @Test
    fun publicCatalogClaimsOnlyClientTlsAndKeepsReleaseDisabled() {
        val publicCatalog = loadBundledPublicPortalCatalog()
        val entry = publicCatalog.entries.single { it.inventoryId == "ES-PUB-0115" }
        assertEquals(profileId, entry.profileId)
        assertEquals(startUrl, entry.entryUrl)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, entry.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, entry.catalogStatus)
        assertEquals("CLIENT_TLS_AUTH", entry.protocolFamily)
        assertTrue(PortalMechanism.CLIENT_TLS_AUTH in entry.observedMechanisms)
        assertTrue(entry.observedSignatureFormats.isEmpty())
        assertTrue(entry.limitations.contains("algoritmo", ignoreCase = true))

        val qa = PortalCatalogRepository(BuiltInSiteProfiles.qaRegistry, BuiltInSiteProfiles.catalog, publicCatalog)
        val release = PortalCatalogRepository(BuiltInSiteProfiles.releaseRegistry, BuiltInSiteProfiles.catalog, publicCatalog)
        val qaPortal = qa.portals().single { it.portalId == PortalId("euskadi-sede-electronica") }
        val releasePortal = release.portals().single { it.portalId == PortalId("euskadi-sede-electronica") }
        assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(profileId, qa.resolveLaunch(qaPortal)?.profileId)
        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertFalse(releasePortal.isEnabled)
        assertNull(release.resolveLaunch(releasePortal))
    }
}

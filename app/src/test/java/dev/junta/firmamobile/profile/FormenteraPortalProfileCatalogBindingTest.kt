package dev.junta.firmamobile.profile

import android.net.Uri
import dev.junta.firmamobile.catalog.PortalCatalogRepository
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
class FormenteraPortalProfileCatalogBindingTest {
    private val profileId = ProfileId("formentera-portal-institucional-navigation")
    private val portalId = PortalId("formentera-portal-institucional")
    private val startUrl = URI("https://www.consellinsulardeformentera.cat/")

    @Test
    fun profileConfigPreservesExactFailClosedNavigationContract() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals(
            "Portal institucional público del Consell Insular de Formentera",
            profile.displayName,
        )
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(startUrl, profile.startUrl)
        assertEquals(
            setOf(ExactOrigin.parse("https://www.consellinsulardeformentera.cat")),
            profile.initiatorOrigins,
        )
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(!profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(
            listOf(EvidenceReference(startUrl, LocalDate.parse("2026-08-25"))),
            profile.evidence,
        )
    }

    @Test
    fun registryResolvesOnlySameOriginBrowsePathsInQa() {
        val releaseProfile = BuiltInSiteProfiles.releaseRegistry.profile(profileId)
        val qaProfile = BuiltInSiteProfiles.qaRegistry.profile(profileId)
        assertNull(releaseProfile)
        assertNotNull(qaProfile)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, qaProfile?.compatibilityStatus)

        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(startUrl))
        assertEquals(
            TrustMode.TRUSTED_BROWSE,
            BuiltInSiteProfiles.qaRegistry.resolve(startUrl)?.trustMode,
        )
        assertEquals(
            TrustMode.TRUSTED_BROWSE,
            BuiltInSiteProfiles.qaRegistry.resolve(URI("https://www.consellinsulardeformentera.cat/servicios"))?.trustMode,
        )

        listOf(
            "http://www.consellinsulardeformentera.cat/",
            "https://www.consellinsulardeformentera.cat:8443/",
            "https://evil.www.consellinsulardeformentera.cat/",
            "https://consellinsulardeformentera.cat/",
            "https://user@www.consellinsulardeformentera.cat/",
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
            setOf(ExactOrigin.parse("https://www.consellinsulardeformentera.cat")),
            JuntaOriginPolicy.browserOrigins(profileId),
        )
        assertEquals(
            setOf("www.consellinsulardeformentera.cat"),
            JuntaOriginPolicy.browserAllowedHosts(profileId),
        )
    }

    @Test
    fun catalogBindsFormenteraInstitutionalAsPendingNavigationAndResolvesLaunchOnlyForExactSeed() {
        val publicCatalog = loadBundledPublicPortalCatalog()
        val entry = publicCatalog.entries.single { it.inventoryId == "ES-PUB-0123" }

        assertEquals(portalId, entry.portalId)
        assertEquals(profileId, entry.profileId)
        assertEquals("https://www.consellinsulardeformentera.cat/", entry.entryUrl.toString())
        assertEquals("FORMENTERA_PUBLIC_PORTAL_NAVIGATION", entry.protocolFamily)
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
        assertTrue(qaPortal.capabilities.isEmpty())
        assertTrue(qaPortal.signatureFormats.isEmpty())
        assertEquals(PortalLaunchTarget(profileId, startUrl), qa.resolveLaunch(qaPortal))
        assertEquals(PortalLaunchTarget(profileId, startUrl), qa.resolveLaunch(profileId, startUrl))

        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertTrue(!releasePortal.isEnabled)
        assertNull(release.resolveLaunch(releasePortal))
        assertNull(release.resolveLaunch(profileId, startUrl))

        listOf(
            "http://www.consellinsulardeformentera.cat/",
            "https://user@www.consellinsulardeformentera.cat/",
            "https://www.consellinsulardeformentera.cat:8443/",
            "https://evil.www.consellinsulardeformentera.cat/",
            "https://www.consellinsulardeformentera.cat/servicios",
        ).forEach { rejectedUrl ->
            assertNull(rejectedUrl, qa.resolveLaunch(profileId, URI(rejectedUrl)))
        }
    }
}

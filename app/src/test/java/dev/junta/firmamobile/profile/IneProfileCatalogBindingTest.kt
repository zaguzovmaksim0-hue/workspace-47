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
class IneProfileCatalogBindingTest {
    private val profileId = ProfileId("ine-sede-public-navigation")
    private val portalId = PortalId("age-instituto-nacional-de-estadistica-ine")
    private val startUrl = URI("https://sede.ine.gob.es/es/index.htm")

    @Test
    fun profileConfigPreservesExactFailClosedNavigationContract() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals(
            "Sede electrónica pública del Instituto Nacional de Estadística",
            profile.displayName,
        )
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(startUrl, profile.startUrl)
        assertEquals(
            setOf(ExactOrigin.parse("https://sede.ine.gob.es")),
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
    fun registryResolvesExactEntryAndSameOriginBrowsePathsInQa() {
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
            BuiltInSiteProfiles.qaRegistry.resolve(
                URI("https://sede.ine.gob.es/es/index.htm?lang=es"),
            )?.trustMode,
        )

        listOf(
            "http://sede.ine.gob.es/es/index.htm",
            "https://sede.ine.gob.es:8443/es/index.htm",
            "https://evil.sede.ine.gob.es/es/index.htm",
            "https://ine.gob.es/es/index.htm",
            "https://user@sede.ine.gob.es/es/index.htm",
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
            setOf(ExactOrigin.parse("https://sede.ine.gob.es")),
            JuntaOriginPolicy.browserOrigins(profileId),
        )
        assertEquals(
            setOf("sede.ine.gob.es"),
            JuntaOriginPolicy.browserAllowedHosts(profileId),
        )
    }

    @Test
    fun catalogBindsIneAsPendingNavigationAndResolvesLaunchOnlyForExactEntry() {
        val publicCatalog = loadBundledPublicPortalCatalog()
        val entry = publicCatalog.entries.single { it.inventoryId == "ES-PUB-0056" }

        assertEquals(portalId, entry.portalId)
        assertEquals(profileId, entry.profileId)
        assertEquals(startUrl, entry.entryUrl)
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
        assertTrue(qaPortal.capabilities.isEmpty())
        assertTrue(qaPortal.signatureFormats.isEmpty())
        assertEquals(PortalLaunchTarget(profileId, startUrl), qa.resolveLaunch(qaPortal))
        assertEquals(PortalLaunchTarget(profileId, startUrl), qa.resolveLaunch(profileId, startUrl))

        assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, releasePortal.supportStatus)
        assertTrue(!releasePortal.isEnabled)
        assertNull(release.resolveLaunch(releasePortal))
        assertNull(release.resolveLaunch(profileId, startUrl))

        listOf(
            "http://sede.ine.gob.es/es/index.htm",
            "https://user@sede.ine.gob.es/es/index.htm",
            "https://sede.ine.gob.es:8443/es/index.htm",
            "https://evil.sede.ine.gob.es/es/index.htm",
            "https://sede.ine.gob.es/",
        ).forEach { rejectedUrl ->
            assertNull(rejectedUrl, qa.resolveLaunch(profileId, URI(rejectedUrl)))
        }
    }
}

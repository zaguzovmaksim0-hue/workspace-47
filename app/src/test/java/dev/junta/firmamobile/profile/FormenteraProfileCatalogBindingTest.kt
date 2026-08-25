package dev.junta.firmamobile.profile

import android.net.Uri
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
class FormenteraProfileCatalogBindingTest {
    private val profileId = ProfileId("formentera-sede-electronica")
    private val portalId = PortalId("formentera-sede-electronica")
    private val startUrl = URI("https://ovac.conselldeformentera.cat/")

    @Test
    fun profileConfigPreservesExactFailClosedNavigationContract() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals(
            "Sede electrónica / OVAC del Consell Insular de Formentera",
            profile.displayName,
        )
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(startUrl, profile.startUrl)
        assertEquals(
            setOf(ExactOrigin.parse("https://ovac.conselldeformentera.cat")),
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
        assertTrue(profile.evidence.all { it.reviewedOn.toString() == "2026-08-16" })
        assertEquals(
            listOf(
                EvidenceReference(startUrl, LocalDate.parse("2026-08-16")),
                EvidenceReference(
                    URI("https://ovac.conselldeformentera.cat/ovac/catala/emiservicio/41E6BF9D755E4825AF8E6B49E85B5079.asp"),
                    LocalDate.parse("2026-08-16"),
                ),
            ),
            profile.evidence,
        )
    }

    @Test
    fun registryResolvesSeedAndSameOriginBrowsePathsInQa() {
        val releaseProfile = BuiltInSiteProfiles.releaseRegistry.profile(profileId)
        val qaProfile = BuiltInSiteProfiles.qaRegistry.profile(profileId)
        assertNull(releaseProfile)
        assertNotNull(qaProfile)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, qaProfile?.compatibilityStatus)

        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(startUrl))
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(startUrl)?.trustMode)

        listOf(
            "http://ovac.conselldeformentera.cat/",
            "https://ovac.conselldeformentera.cat:8443/",
            "https://evil.ovac.conselldeformentera.cat/",
            "https://conselldeformentera.cat/",
            "https://www.consellinsulardeformentera.cat/",
            "https://user@ovac.conselldeformentera.cat/",
        ).forEach { nonExactUrl ->
            val uri = URI(nonExactUrl)
            assertNull(nonExactUrl, BuiltInSiteProfiles.qaRegistry.resolve(uri))
        }

        listOf(
            "https://ovac.conselldeformentera.cat/ovac/catala/emiservicio/41E6BF9D755E4825AF8E6B49E85B5079.asp",
            "https://ovac.conselldeformentera.cat/ovac/",
        ).forEach { sameOriginBrowseUrl ->
            assertEquals(
                TrustMode.TRUSTED_BROWSE,
                BuiltInSiteProfiles.qaRegistry.resolve(URI(sameOriginBrowseUrl))?.trustMode,
            )
        }

        val observedMetaRefresh = URI("https://ovac.conselldeformentera.cat/ovac/catala/emiservicio/41E6BF9D755E4825AF8E6B49E85B5079.asp")
        assertNull(BuiltInSiteProfiles.qaRegistry.resolveRedirect(profileId, observedMetaRefresh))
    }

    @Test
    fun securityAndProtocolIsolationEnforcesFailClosedNavigation() {
        assertNull(BuiltInProtocolAdapterRegistry.registry.resolve(profileId, ProtocolOperation.SIGN))
        assertNull(BuiltInProtocolAdapterRegistry.registry.resolve(profileId, ProtocolOperation.SELECT_CERTIFICATE))

        assertTrue(JuntaOriginPolicy.webMessageOriginRules(profileId).isEmpty())
        assertNull(JuntaOriginPolicy.signingOriginFor(Uri.parse("https://ovac.conselldeformentera.cat/"), profileId))
        assertEquals(
            setOf(ExactOrigin.parse("https://ovac.conselldeformentera.cat")),
            JuntaOriginPolicy.browserOrigins(profileId),
        )
        assertEquals(
            setOf("ovac.conselldeformentera.cat"),
            JuntaOriginPolicy.browserAllowedHosts(profileId),
        )
    }

    @Test
    fun catalogBindsFormenteraAsPendingNavigationAndResolvesLaunchOnlyForExactSeed() {
        val publicCatalog = loadBundledPublicPortalCatalog()
        val entry = publicCatalog.entries.single { it.inventoryId == "ES-PUB-0124" }
        assertEquals(portalId, entry.portalId)
        assertEquals(profileId, entry.profileId)
        assertEquals(PortalInventoryStatus.IMPLEMENTED_NOT_E2E, entry.inventoryStatus)
        assertEquals(PublicCatalogStatus.E2E_PENDING, entry.catalogStatus)
        assertEquals(
            setOf(PortalMechanism.CERTIFICATE_ACCESS, PortalMechanism.ELECTRONIC_SIGNATURE),
            entry.observedMechanisms,
        )
        assertTrue(entry.observedSignatureFormats.isEmpty())
        assertEquals("2026-08-16", entry.reviewedOn.toString())

        val qa = PortalCatalogRepository(BuiltInSiteProfiles.qaRegistry, BuiltInSiteProfiles.catalog, publicCatalog)
        val release = PortalCatalogRepository(BuiltInSiteProfiles.releaseRegistry, BuiltInSiteProfiles.catalog, publicCatalog)
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
        assertTrue(releasePortal.capabilities.isEmpty())
        assertTrue(releasePortal.signatureFormats.isEmpty())
        assertNull(release.resolveLaunch(releasePortal))
        assertNull(release.resolveLaunch(profileId, startUrl))

        listOf(
            "http://ovac.conselldeformentera.cat/",
            "https://user@ovac.conselldeformentera.cat/",
            "https://evil.ovac.conselldeformentera.cat/",
            "https://ovac.conselldeformentera.cat/ovac/",
            "https://ovac.conselldeformentera.cat/ovac/catala/emiservicio/41E6BF9D755E4825AF8E6B49E85B5079.asp",
        ).forEach { mutatedUrl ->
            assertNull(mutatedUrl, qa.resolveLaunch(profileId, URI(mutatedUrl)))
        }
    }
}

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
import dev.junta.firmamobile.signing.SignatureFormat
import java.net.URI
import org.junit.Assert.assertEquals
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
class AccedaProfileCatalogBindingTest {
    private val profileId = ProfileId("age-acceda")
    private val portalId = PortalId("age-acceda")
    private val startUrl = URI("https://sede.administracionespublicas.gob.es/certificado/info/idp/82/ida/0/language/es_ES")

    @Test
    fun profilePreservesExactBrowseOnlyContractAndExposesNoSensitiveCapabilities() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals("Plataforma ACCEDA — Sede electrónica", profile.displayName)
        assertEquals(CompatibilityStatus.BROWSE_ONLY, profile.compatibilityStatus)
        assertEquals(ProfileActivation.ENABLED, profile.activation)
        assertEquals(startUrl, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://sede.administracionespublicas.gob.es")), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertTrue(profile.evidence.isNotEmpty())

        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(profile, BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertEquals(TrustMode.BROWSE_ONLY, BuiltInSiteProfiles.qaRegistry.resolve(startUrl)?.trustMode)
        assertEquals(TrustMode.BROWSE_ONLY, BuiltInSiteProfiles.releaseRegistry.resolve(startUrl)?.trustMode)
    }

    @Test
    fun securityPolicyRejectsNonMatchingSeedsAndInjectsNoNativeBridge() {
        listOf(
            "http://sede.administracionespublicas.gob.es/certificado/info/idp/82/ida/0/language/es_ES",
            "https://user@sede.administracionespublicas.gob.es/certificado/info/idp/82/ida/0/language/es_ES",
            "https://sede.administracionespublicas.gob.es:8443/certificado/info/idp/82/ida/0/language/es_ES",
            "https://sede.administracionespublicas.gob.es/certificado/valida",
            "https://evil.sede.administracionespublicas.gob.es/",
            "https://sede.administracionespublicas.gob.es.evil.example/",
        ).forEach { rejected ->
            assertNull(rejected, BuiltInSiteProfiles.releaseRegistry.resolve(URI(rejected)))
            assertNull(rejected, BuiltInSiteProfiles.qaRegistry.resolve(URI(rejected)))
        }

        assertNull(BuiltInProtocolAdapterRegistry.registry.resolve(profileId, ProtocolOperation.SIGN))
        assertNull(BuiltInProtocolAdapterRegistry.registry.resolve(profileId, ProtocolOperation.SELECT_CERTIFICATE))

        assertTrue(JuntaOriginPolicy.webMessageOriginRules(profileId).isEmpty())
        assertNull(JuntaOriginPolicy.signingOriginFor(Uri.parse("https://sede.administracionespublicas.gob.es/certificado/info/idp/82/ida/0/language/es_ES"), profileId))
        assertEquals(setOf("sede.administracionespublicas.gob.es"), JuntaOriginPolicy.browserAllowedHosts(profileId))
    }

    @Test
    fun publicCatalogBindsTheExactBrowseOnlyEntry() {
        val publicCatalog = loadBundledPublicPortalCatalog()
        val entry = publicCatalog.entries.single { it.portalId == portalId }
        assertEquals(profileId, entry.profileId)
        assertEquals("ES-PUB-0003", entry.inventoryId)
        assertEquals(startUrl, entry.entryUrl)
        assertNull(entry.launchUrl)
        assertEquals(PortalInventoryStatus.VERIFIED_CONTRACT, entry.inventoryStatus)
        assertEquals(PublicCatalogStatus.CATALOGED, entry.catalogStatus)
        assertEquals("2026-07-15", entry.reviewedOn.toString())
        assertEquals(
            setOf(PortalMechanism.AUTOSCRIPT, PortalMechanism.CERTIFICATE_ACCESS, PortalMechanism.ELECTRONIC_SIGNATURE),
            entry.observedMechanisms,
        )
        assertEquals(
            setOf(SignatureFormat.PADES, SignatureFormat.XADES),
            entry.observedSignatureFormats,
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

        assertEquals(PortalSupportStatus.BROWSE_ONLY, qaPortal.supportStatus)
        assertTrue(qaPortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, startUrl), qa.resolveLaunch(qaPortal))

        assertEquals(PortalSupportStatus.BROWSE_ONLY, releasePortal.supportStatus)
        assertTrue(releasePortal.isEnabled)
        assertEquals(PortalLaunchTarget(profileId, startUrl), release.resolveLaunch(releasePortal))
    }
}

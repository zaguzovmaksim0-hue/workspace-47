package dev.junta.firmamobile.catalog

import dev.junta.firmamobile.profile.BuildTrustPolicy
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.Capability
import dev.junta.firmamobile.profile.CompatibilityStatus
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SignatureFormat
import dev.junta.firmamobile.profile.SiteProfileCatalog
import dev.junta.firmamobile.profile.SiteProfileRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortalCatalogRepositoryTest {
    private val catalog = BuiltInSiteProfiles.catalog
    private val releaseRepository = PortalCatalogRepository(
        SiteProfileRegistry(catalog, BuildTrustPolicy.RELEASE),
        catalog,
    )
    private val qaRepository = PortalCatalogRepository(
        SiteProfileRegistry(catalog, BuildTrustPolicy.QA),
        catalog,
    )

    @Test
    fun `qa publishes all typed portal entries but release opens only verified Junta and Carne Joven`() {
        val qaPortals = qaRepository.portals()
        val releasePortals = releaseRepository.portals()

        assertEquals(1, qaRepository.bundledCatalogVersion)
        assertEquals(
            setOf(
                "junta-andalucia",
                "reg-age-redsara",
                "unizar-tramitador",
                "carne-joven-andalucia",
            ),
            qaPortals.map { it.profileId.value }.toSet(),
        )
        val verifiedIds = setOf(ProfileId("junta-andalucia"), ProfileId("carne-joven-andalucia"))
        verifiedIds.forEach { profileId ->
            assertEquals(PortalSupportStatus.VERIFIED_E2E, qaPortals.single { it.profileId == profileId }.supportStatus)
        }
        qaPortals.filterNot { it.profileId in verifiedIds }.forEach { portal ->
            assertEquals(PortalSupportStatus.IMPLEMENTED_NOT_E2E, portal.supportStatus)
            assertTrue(portal.isEnabled)
        }

        verifiedIds.forEach { profileId ->
            val releasePortal = releasePortals.single { it.profileId == profileId }
            assertEquals(PortalSupportStatus.VERIFIED_E2E, releasePortal.supportStatus)
            assertTrue(releasePortal.isEnabled)
        }
        releasePortals.filterNot { it.profileId in verifiedIds }.forEach { portal ->
            assertEquals(PortalSupportStatus.VERIFIED_CONTRACT, portal.supportStatus)
            assertFalse(portal.isEnabled)
            assertEquals(null, releaseRepository.resolveLaunch(portal))
        }
    }

    @Test
    fun `does not elevate capabilities or signature formats beyond the profile`() {
        qaRepository.portals().forEach { portal ->
            val profile = catalog.profiles.single { it.profileId == portal.profileId }
            assertEquals(
                Capability.SIGN in profile.capabilities,
                PortalServiceCapability.ELECTRONIC_SIGNATURE in portal.capabilities,
            )
            assertEquals(
                Capability.SELECT_CERTIFICATE in profile.capabilities ||
                    Capability.CLIENT_TLS_AUTH in profile.capabilities,
                PortalServiceCapability.CERTIFICATE_ACCESS in portal.capabilities,
            )
            assertEquals(
                profile.operationPolicies.values.mapNotNull { it.format }.toSet(),
                portal.signatureFormats,
            )
        }

        val carneJoven = qaRepository.portals().single {
            it.profileId == ProfileId("carne-joven-andalucia")
        }
        assertEquals(PortalSupportStatus.VERIFIED_E2E, carneJoven.supportStatus)
        assertTrue(PortalServiceCapability.CERTIFICATE_ACCESS in carneJoven.capabilities)
        assertFalse(PortalServiceCapability.ELECTRONIC_SIGNATURE in carneJoven.capabilities)
        assertTrue(carneJoven.signatureFormats.isEmpty())

        val redSara = qaRepository.portals().single { it.profileId == ProfileId("reg-age-redsara") }
        assertEquals(setOf(SignatureFormat.XADES), redSara.signatureFormats)
    }

    @Test
    fun `supports accent insensitive search and public filters`() {
        assertEquals(
            listOf("junta-andalucia", "carne-joven-andalucia"),
            qaRepository.portals(
                PortalCatalogQuery(filter = PortalCatalogFilter.AUTONOMOUS_COMMUNITIES),
            ).map { it.profileId.value },
        )
        assertEquals(
            listOf("unizar-tramitador"),
            qaRepository.portals(PortalCatalogQuery(searchText = "zaragoza"))
                .map { it.profileId.value },
        )
        assertEquals(
            listOf("carne-joven-andalucia"),
            qaRepository.portals(
                PortalCatalogQuery(
                    searchText = "carne joven",
                    filter = PortalCatalogFilter.CERTIFICATE_ACCESS,
                ),
            ).map { it.profileId.value },
        )
    }

    @Test
    fun `favorites and recents are caller supplied and recent order is preserved`() {
        assertEquals(
            listOf("reg-age-redsara"),
            qaRepository.portals(
                PortalCatalogQuery(
                    filter = PortalCatalogFilter.FAVORITES,
                    favoriteProfileIds = setOf(ProfileId("reg-age-redsara")),
                ),
            ).map { it.profileId.value },
        )

        val recentIds = listOf(ProfileId("unizar-tramitador"), ProfileId("junta-andalucia"))
        assertEquals(
            recentIds,
            qaRepository.portals(
                PortalCatalogQuery(
                    filter = PortalCatalogFilter.RECENT,
                    recentProfileIds = recentIds,
                ),
            ).map { it.profileId },
        )
    }

    @Test
    fun `fails closed when registry and catalog do not describe the same profiles`() {
        val emptyCatalog = SiteProfileCatalog(
            schemaVersion = catalog.schemaVersion,
            catalogVersion = catalog.catalogVersion,
            profiles = emptyList(),
        )
        val mismatched = PortalCatalogRepository(SiteProfileRegistry(catalog, BuildTrustPolicy.RELEASE), emptyCatalog)

        assertTrue(mismatched.portals().isEmpty())
    }

    @Test
    fun `launch resolution accepts only canonical active profile and exact entry URL`() {
        val item = qaRepository.portals().single { it.profileId == ProfileId("reg-age-redsara") }

        assertEquals(
            PortalLaunchTarget(item.profileId, item.entryUrl),
            qaRepository.resolveLaunch(item),
        )
        assertEquals(null, qaRepository.resolveLaunch(item.profileId, java.net.URI("https://reg.redsara.es/")))
        assertEquals(
            null,
            qaRepository.resolveLaunch(ProfileId("unknown-profile"), item.entryUrl),
        )
    }

    @Test
    fun `public support status never treats contract evidence as implementation`() {
        assertEquals(
            PortalSupportStatus.VERIFIED_CONTRACT,
            resolvePortalSupportStatus(CompatibilityStatus.VERIFIED_CONTRACT, isImplemented = false),
        )
        assertEquals(
            PortalSupportStatus.IMPLEMENTED_NOT_E2E,
            resolvePortalSupportStatus(CompatibilityStatus.VERIFIED_CONTRACT, isImplemented = true),
        )
        assertEquals(
            PortalSupportStatus.BROWSE_ONLY,
            resolvePortalSupportStatus(CompatibilityStatus.BROWSE_ONLY, isImplemented = true),
        )
        assertEquals(
            PortalSupportStatus.UNSUPPORTED_PROTOCOL,
            resolvePortalSupportStatus(CompatibilityStatus.UNSUPPORTED, isImplemented = false),
        )
    }
}

package dev.junta.firmamobile.smoke

import dev.junta.firmamobile.catalog.PortalCatalogRepository
import dev.junta.firmamobile.catalog.PortalId
import dev.junta.firmamobile.catalog.PortalLaunchTarget
import dev.junta.firmamobile.catalog.loadBundledPublicPortalCatalog
import dev.junta.firmamobile.profile.BuildTrustPolicy
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SiteProfileRegistry
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
class CatalogSmokeControllerTest {
    private val profileCatalog = BuiltInSiteProfiles.catalog
    private val repository by lazy {
        PortalCatalogRepository(
            registry = SiteProfileRegistry(profileCatalog, BuildTrustPolicy.QA),
            profileCatalog = profileCatalog,
            publicCatalog = loadBundledPublicPortalCatalog(),
        )
    }

    @Test
    fun `rejects missing unsafe and unknown identifiers without opening`() {
        val opened = mutableListOf<PortalLaunchTarget>()
        val controller = controller(opened = opened)

        listOf(
            CatalogSmokeRequest(null, "age-reg-redsara", "OPEN"),
            CatalogSmokeRequest("bad run id", "age-reg-redsara", "OPEN"),
            CatalogSmokeRequest("run-1", "../redsara", "OPEN"),
            CatalogSmokeRequest("run-1", "age-reg-redsara", "SHELL"),
        ).forEach { request ->
            assertEquals(CatalogSmokeResultCode.INVALID_REQUEST, controller.execute(request).result)
        }
        assertEquals(
            CatalogSmokeResultCode.UNKNOWN_PORTAL,
            controller.execute(CatalogSmokeRequest("run-1", "unknown-portal", "OPEN")).result,
        )
        assertTrue(opened.isEmpty())
    }

    @Test
    fun `metadata only entries stay catalog only even when certificate is unlocked`() {
        val opened = mutableListOf<PortalLaunchTarget>()
        val outcome = controller(unlocked = true, opened = opened).execute(
            CatalogSmokeRequest("run-2", "comunidad-madrid-gestiona2", "OPEN"),
        )

        assertEquals(CatalogSmokeResultCode.CATALOG_ONLY, outcome.result)
        assertEquals(PortalId("comunidad-madrid-gestiona2"), outcome.portalId)
        assertNull(outcome.profileId)
        assertTrue(opened.isEmpty())
    }

    @Test
    fun `locked session proves exact resolution but never opens browser`() {
        val opened = mutableListOf<PortalLaunchTarget>()
        val outcome = controller(unlocked = false, opened = opened).execute(
            CatalogSmokeRequest("run-3", "age-reg-redsara", "OPEN"),
        )

        assertEquals(CatalogSmokeResultCode.PROFILE_RESOLVED, outcome.result)
        assertEquals(ProfileId("reg-age-redsara"), outcome.profileId)
        assertEquals("https://reg.redsara.es/es/", outcome.entryUrl)
        assertTrue(opened.isEmpty())
    }

    @Test
    fun `unlocked session requests only the repository validated target`() {
        val opened = mutableListOf<PortalLaunchTarget>()
        val outcome = controller(unlocked = true, opened = opened).execute(
            CatalogSmokeRequest("run-4", "age-reg-redsara", "OPEN"),
        )

        assertEquals(CatalogSmokeResultCode.OPEN_REQUESTED, outcome.result)
        assertEquals(
            listOf(
                PortalLaunchTarget(
                    profileId = ProfileId("reg-age-redsara"),
                    entryUrl = java.net.URI("https://reg.redsara.es/es/"),
                ),
            ),
            opened,
        )
    }

    @Test
    fun `inspect reports active webview only for the requested profile`() {
        val active = ProfileId("junta-ofvirtual")
        val controller = controller(activeProfile = active)

        assertEquals(
            CatalogSmokeResultCode.WEBVIEW_ACTIVE,
            controller.execute(
                CatalogSmokeRequest("run-5", "junta-andalucia-ofvirtual", "INSPECT"),
            ).result,
        )
        assertEquals(
            CatalogSmokeResultCode.WEBVIEW_NOT_ACTIVE,
            controller.execute(
                CatalogSmokeRequest("run-6", "age-reg-redsara", "INSPECT"),
            ).result,
        )
    }

    @Test
    fun `ordered broadcast result fails closed for invalid disabled and inactive outcomes`() {
        assertEquals(
            setOf(
                CatalogSmokeResultCode.INVALID_REQUEST,
                CatalogSmokeResultCode.UNKNOWN_PORTAL,
                CatalogSmokeResultCode.PROFILE_DISABLED,
                CatalogSmokeResultCode.WEBVIEW_NOT_ACTIVE,
            ),
            CatalogSmokeResultCode.entries.filterTo(mutableSetOf()) {
                it.isOrderedBroadcastFailure()
            },
        )
    }

    private fun controller(
        unlocked: Boolean = false,
        opened: MutableList<PortalLaunchTarget> = mutableListOf(),
        activeProfile: ProfileId? = null,
    ) = CatalogSmokeController(
        repository = repository,
        certificateUnlocked = { unlocked },
        openProfile = opened::add,
        activeWebViewMatches = { it == activeProfile },
        adapterIdForProfile = { "test-adapter" },
    )
}

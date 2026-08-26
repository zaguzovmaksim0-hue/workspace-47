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
import dev.junta.firmamobile.diagnostics.RuntimeDiagnosticEvent
import java.util.UUID
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

    private val metadataOnlyRepository by lazy {
        val catalog = loadBundledPublicPortalCatalog()
        val metadataOnly = catalog.entries.first().copy(
            portalId = PortalId("catalog-only-fixture"),
            inventoryId = null,
            profileId = null,
            displayName = "Catalog-only fixture",
            entryUrl = java.net.URI("https://catalog-only.example.test/"),
            launchUrl = null,
            observedMechanisms = emptySet(),
            observedSignatureFormats = emptySet(),
            protocolFamily = "CATALOG_ONLY_FIXTURE",
            catalogStatus = dev.junta.firmamobile.catalog.PublicCatalogStatus.CATALOGED,
            inventoryStatus = dev.junta.firmamobile.catalog.PortalInventoryStatus.BROWSE_ONLY,
            discoveryState = dev.junta.firmamobile.catalog.PortalDiscoveryState.REVIEWED,
            evidenceIds = setOf("test-catalog-only"),
            reviewedOn = null,
            limitations = "Test fixture only",
        )
        PortalCatalogRepository(
            registry = SiteProfileRegistry(profileCatalog, BuildTrustPolicy.QA),
            profileCatalog = profileCatalog,
            publicCatalog = catalog.copy(entries = catalog.entries + metadataOnly),
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
        val metadataOnly = metadataOnlyRepository.portals().single { it.profileId == null }
        val outcome = controller(
            catalogRepository = metadataOnlyRepository,
            unlocked = true,
            opened = opened,
        ).execute(
            CatalogSmokeRequest("run-2", metadataOnly.portalId.value, "OPEN"),
        )

        assertEquals(CatalogSmokeResultCode.CATALOG_ONLY, outcome.result)
        assertEquals(metadataOnly.portalId, outcome.portalId)
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
    fun `inspect reports active webview only for the exact run profile and browser session`() {
        val active = ProfileId("junta-ofvirtual")
        val runtime = CatalogSmokeRuntime()
        runtime.beginRun("run-5", active)
        val sessionId = UUID.randomUUID()
        runtime.observe(
            RuntimeDiagnosticEvent.WebViewState(active, sessionId, 0L, active = true),
        )
        runtime.observe(
            RuntimeDiagnosticEvent.NavigationChanged(
                active,
                sessionId,
                1L,
                "https://ws072.juntadeandalucia.es/ofvirtual/auth/signInAutcertjs",
            ),
        )
        val controller = controller(activeProfile = active, runtime = runtime)

        assertEquals(
            CatalogSmokeResultCode.WEBVIEW_ACTIVE,
            controller.execute(
                CatalogSmokeRequest("run-5", "junta-andalucia-ofvirtual", "INSPECT"),
            ).result,
        )
        assertEquals(
            CatalogSmokeResultCode.RUN_NOT_ACTIVE,
            controller.execute(
                CatalogSmokeRequest("other-run", "junta-andalucia-ofvirtual", "INSPECT"),
            ).result,
        )
    }

    @Test
    fun `inspect still fails closed when live webview no longer matches profile`() {
        val active = ProfileId("junta-ofvirtual")
        val runtime = CatalogSmokeRuntime()
        runtime.beginRun("run-live-mismatch", active)
        val sessionId = UUID.randomUUID()
        runtime.observe(RuntimeDiagnosticEvent.WebViewState(active, sessionId, 0L, active = true))
        runtime.observe(
            RuntimeDiagnosticEvent.NavigationChanged(
                active,
                sessionId,
                1L,
                "https://ws072.juntadeandalucia.es/ofvirtual/auth/signInAutcertjs",
            ),
        )

        val result = controller(activeProfile = null, runtime = runtime).execute(
            CatalogSmokeRequest(
                "run-live-mismatch",
                "junta-andalucia-ofvirtual",
                "INSPECT",
            ),
        )

        assertEquals(CatalogSmokeResultCode.WEBVIEW_NOT_ACTIVE, result.result)
    }

    @Test
    fun `profile identifier is accepted only when it maps to one catalog portal`() {
        val opened = mutableListOf<PortalLaunchTarget>()
        val runtime = CatalogSmokeRuntime()
        val unique = repository.portals()
            .groupBy { it.profileId }
            .entries
            .first { (profileId, portals) -> profileId != null && portals.size == 1 }
            .value
            .single()
        val uniqueProfileId = requireNotNull(unique.profileId)

        val uniqueResult = controller(unlocked = true, opened = opened, runtime = runtime).execute(
            CatalogSmokeRequest(
                runId = "profile-run",
                operation = "OPEN",
                profileId = uniqueProfileId.value,
            ),
        )
        assertEquals(CatalogSmokeResultCode.OPEN_REQUESTED, uniqueResult.result)
        assertEquals(uniqueProfileId, uniqueResult.profileId)

        val duplicateProfile = repository.portals()
            .groupBy { it.profileId }
            .entries
            .first { (profileId, portals) -> profileId != null && portals.size > 1 }
            .key
        val ambiguousResult = controller(unlocked = true, runtime = CatalogSmokeRuntime()).execute(
            CatalogSmokeRequest(
                runId = "ambiguous-run",
                operation = "OPEN",
                profileId = requireNotNull(duplicateProfile).value,
            ),
        )
        assertEquals(CatalogSmokeResultCode.AMBIGUOUS_PROFILE, ambiguousResult.result)
    }

    @Test
    fun `close requires the exact active run and profile before leaving browser`() {
        val active = ProfileId("junta-ofvirtual")
        val runtime = CatalogSmokeRuntime()
        runtime.beginRun("run-close", active)
        val sessionId = UUID.randomUUID()
        runtime.observe(RuntimeDiagnosticEvent.WebViewState(active, sessionId, 0L, active = true))
        runtime.observe(
            RuntimeDiagnosticEvent.NavigationChanged(
                active,
                sessionId,
                1L,
                "https://ws072.juntadeandalucia.es/ofvirtual/auth/signInAutcertjs",
            ),
        )
        val controller = controller(activeProfile = active, runtime = runtime)

        val closed = controller.execute(
            CatalogSmokeRequest("run-close", "junta-andalucia-ofvirtual", "CLOSE"),
        )
        assertEquals(CatalogSmokeResultCode.PORTAL_CLOSED, closed.result)
        assertEquals(
            CatalogSmokeResultCode.RUN_NOT_ACTIVE,
            controller.execute(
                CatalogSmokeRequest("run-close", "junta-andalucia-ofvirtual", "INSPECT"),
            ).result,
        )
    }

    @Test
    fun `ordered broadcast result fails closed for invalid disabled and inactive outcomes`() {
        assertEquals(
            setOf(
                CatalogSmokeResultCode.INVALID_REQUEST,
                CatalogSmokeResultCode.UNKNOWN_PORTAL,
                CatalogSmokeResultCode.UNKNOWN_PROFILE,
                CatalogSmokeResultCode.AMBIGUOUS_PROFILE,
                CatalogSmokeResultCode.PROFILE_DISABLED,
                CatalogSmokeResultCode.WEBVIEW_NOT_ACTIVE,
                CatalogSmokeResultCode.RUN_NOT_ACTIVE,
            ),
            CatalogSmokeResultCode.entries.filterTo(mutableSetOf()) {
                it.isOrderedBroadcastFailure()
            },
        )
    }

    private fun controller(
        catalogRepository: PortalCatalogRepository = repository,
        unlocked: Boolean = false,
        opened: MutableList<PortalLaunchTarget> = mutableListOf(),
        activeProfile: ProfileId? = null,
        runtime: CatalogSmokeRuntime = CatalogSmokeRuntime(),
    ) = CatalogSmokeController(
        repository = catalogRepository,
        certificateUnlocked = { unlocked },
        openProfile = opened::add,
        activeWebViewMatches = { it == activeProfile },
        adapterIdForProfile = { "test-adapter" },
        closeProfile = { it == activeProfile },
        runtime = runtime,
    )
}

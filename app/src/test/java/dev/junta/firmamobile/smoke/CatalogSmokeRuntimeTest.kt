package dev.junta.firmamobile.smoke

import dev.junta.firmamobile.browser.NavigationBlockReason
import dev.junta.firmamobile.diagnostics.RuntimeDiagnosticEvent
import dev.junta.firmamobile.diagnostics.observeSafely
import dev.junta.firmamobile.profile.ProfileId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSmokeRuntimeTest {
    @Test
    fun `diagnostic callback failures are isolated`() {
        val observer: (RuntimeDiagnosticEvent) -> Unit = { error("boom") }
        observer.observeSafely(
            RuntimeDiagnosticEvent.WebViewState(
                ProfileId("reg-age-redsara"),
                UUID.randomUUID(),
                0L,
                active = true,
            ),
        )
    }

    private val profileId = ProfileId("reg-age-redsara")

    @Test
    fun `run binds only to a fresh browser session and ignores stale session events`() {
        val runtime = CatalogSmokeRuntime()
        val staleSession = UUID.randomUUID()
        val freshSession = UUID.randomUUID()

        runtime.observe(
            RuntimeDiagnosticEvent.WebViewState(profileId, staleSession, 7L, active = true),
        )
        runtime.beginRun("run-fresh", profileId)
        runtime.observe(
            RuntimeDiagnosticEvent.WebViewState(profileId, staleSession, 8L, active = true),
        )

        var snapshot = requireNotNull(runtime.snapshot("run-fresh", profileId))
        assertFalse(snapshot.browserSessionBound)
        assertTrue(snapshot.events.isEmpty())

        runtime.observe(
            RuntimeDiagnosticEvent.WebViewState(profileId, freshSession, 0L, active = true),
        )
        runtime.observe(
            RuntimeDiagnosticEvent.NavigationChanged(
                profileId,
                freshSession,
                1L,
                "https://reg.redsara.es/es/",
            ),
        )

        snapshot = requireNotNull(runtime.snapshot("run-fresh", profileId))
        assertTrue(snapshot.browserSessionBound)
        assertTrue(snapshot.webViewActive)
        assertTrue(snapshot.currentUrlAllowed)
        assertEquals("reg.redsara.es", snapshot.currentHost)
        assertEquals("/es/", snapshot.currentPath)
    }

    @Test
    fun `wrong profile and decreasing navigation epoch cannot mutate current run`() {
        val runtime = CatalogSmokeRuntime()
        val session = UUID.randomUUID()
        runtime.beginRun("run-epoch", profileId)
        runtime.observe(RuntimeDiagnosticEvent.WebViewState(profileId, session, 0L, true))
        runtime.observe(
            RuntimeDiagnosticEvent.NavigationChanged(
                profileId,
                session,
                3L,
                "https://reg.redsara.es/es/",
            ),
        )
        runtime.observe(
            RuntimeDiagnosticEvent.NavigationBlocked(
                profileId,
                session,
                2L,
                NavigationBlockReason.INVALID_URL,
            ),
        )
        runtime.observe(
            RuntimeDiagnosticEvent.BrowserError(
                ProfileId("junta-ofvirtual"),
                session,
                4L,
                dev.junta.firmamobile.browser.BrowserErrorCode.NETWORK_ERROR,
            ),
        )

        val snapshot = requireNotNull(runtime.snapshot("run-epoch", profileId))
        assertEquals(3L, snapshot.navigationEpoch)
        assertNull(snapshot.failureCode)
    }

    @Test
    fun `runtime report keeps only public host path and bounded event codes`() {
        val runtime = CatalogSmokeRuntime()
        val session = UUID.randomUUID()
        runtime.beginRun("run-sanitize", profileId)
        runtime.observe(RuntimeDiagnosticEvent.WebViewState(profileId, session, 0L, true))
        runtime.observe(
            RuntimeDiagnosticEvent.NavigationChanged(
                profileId,
                session,
                1L,
                "https://reg.redsara.es/es/?token=do-not-export#private",
            ),
        )
        runtime.observe(
            RuntimeDiagnosticEvent.ClientCertRequestAccepted(
                profileId,
                session,
                1L,
                "reg.redsara.es",
                443,
            ),
        )

        val snapshot = requireNotNull(runtime.snapshot("run-sanitize", profileId))
        assertEquals("reg.redsara.es", snapshot.currentHost)
        assertEquals("/es/", snapshot.currentPath)
        assertTrue(snapshot.clientCertAcceptedObserved)
        assertTrue(snapshot.events.none { event ->
            event.host?.contains("token") == true ||
                event.path?.contains("do-not-export") == true ||
                event.detail?.contains("do-not-export") == true
        })
    }
}

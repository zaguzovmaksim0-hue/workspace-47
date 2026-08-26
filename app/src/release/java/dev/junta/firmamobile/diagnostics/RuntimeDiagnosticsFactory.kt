package dev.junta.firmamobile.diagnostics

import android.app.Activity
import dev.junta.firmamobile.catalog.PortalCatalogRepository
import dev.junta.firmamobile.catalog.PortalLaunchTarget
import dev.junta.firmamobile.profile.ProfileId

/** Release implementation deliberately has no receiver, action string, command parser or journal. */
internal object RuntimeDiagnosticsFactory {
    fun create(
        activity: Activity,
        repository: PortalCatalogRepository,
        certificateUnlocked: () -> Boolean,
        openProfile: (PortalLaunchTarget) -> Unit,
        activeWebViewMatches: (ProfileId) -> Boolean,
        adapterIdForProfile: (ProfileId) -> String?,
    ): RuntimeDiagnosticsObserver = NoOpRuntimeDiagnosticsObserver

    private object NoOpRuntimeDiagnosticsObserver : RuntimeDiagnosticsObserver {
        override fun start() = Unit
        override fun stop() = Unit
        override fun observe(event: RuntimeDiagnosticEvent) = Unit
    }
}

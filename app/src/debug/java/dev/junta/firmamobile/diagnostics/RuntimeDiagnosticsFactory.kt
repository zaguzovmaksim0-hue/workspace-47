package dev.junta.firmamobile.diagnostics

import android.app.Activity
import dev.junta.firmamobile.catalog.PortalCatalogRepository
import dev.junta.firmamobile.catalog.PortalLaunchTarget
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.smoke.CatalogSmokeController
import dev.junta.firmamobile.smoke.CatalogSmokeHook
import dev.junta.firmamobile.smoke.CatalogSmokeRuntime

internal object RuntimeDiagnosticsFactory {
    fun create(
        activity: Activity,
        repository: PortalCatalogRepository,
        certificateUnlocked: () -> Boolean,
        openProfile: (PortalLaunchTarget) -> Unit,
        activeWebViewMatches: (ProfileId) -> Boolean,
        adapterIdForProfile: (ProfileId) -> String?,
    ): RuntimeDiagnosticsObserver {
        val runtime = CatalogSmokeRuntime()
        val controller = CatalogSmokeController(
            repository = repository,
            certificateUnlocked = certificateUnlocked,
            openProfile = openProfile,
            activeWebViewMatches = activeWebViewMatches,
            adapterIdForProfile = adapterIdForProfile,
            runtime = runtime,
        )
        val hook = CatalogSmokeHook(activity, controller::execute)
        return object : RuntimeDiagnosticsObserver {
            override fun start() = hook.start()
            override fun stop() = hook.stop()
            override fun observe(event: RuntimeDiagnosticEvent) = runtime.observe(event)
        }
    }
}

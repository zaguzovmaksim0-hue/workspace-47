package dev.junta.firmamobile.diagnostics

import android.app.Activity
import android.net.Uri
import dev.junta.firmamobile.catalog.PortalCatalogRepository
import dev.junta.firmamobile.catalog.PortalLaunchTarget
import dev.junta.firmamobile.control.E2eControlController
import dev.junta.firmamobile.control.E2eControlHook
import dev.junta.firmamobile.control.E2eSecretInbox
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.signing.SigningUiState
import dev.junta.firmamobile.smoke.CatalogSmokeController
import dev.junta.firmamobile.smoke.CatalogSmokeHook
import dev.junta.firmamobile.smoke.CatalogSmokeRuntime
import dev.junta.firmamobile.ui.CertificateUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

internal object RuntimeDiagnosticsFactory {
    fun create(
        activity: Activity,
        scope: CoroutineScope,
        repository: PortalCatalogRepository,
        certificateState: StateFlow<CertificateUiState>,
        certificateUnlocked: () -> Boolean,
        selectCertificate: (Uri) -> Unit,
        unlockCertificate: (CharArray) -> Unit,
        lockCertificate: () -> Unit,
        forgetCertificate: () -> Unit,
        openProfile: (PortalLaunchTarget) -> Unit,
        closeProfile: (ProfileId) -> Boolean,
        activeWebViewMatches: (ProfileId) -> Boolean,
        adapterIdForProfile: (ProfileId) -> String?,
        signingState: () -> SigningUiState,
        confirmCurrentSigning: () -> Boolean,
        cancelCurrentSigning: () -> Boolean,
        dismissCurrentSigning: () -> Boolean,
    ): RuntimeDiagnosticsObserver {
        val runtime = CatalogSmokeRuntime()
        val portalController = CatalogSmokeController(
            repository = repository,
            certificateUnlocked = certificateUnlocked,
            openProfile = openProfile,
            activeWebViewMatches = activeWebViewMatches,
            adapterIdForProfile = adapterIdForProfile,
            closeProfile = closeProfile,
            runtime = runtime,
        )
        val smokeHook = CatalogSmokeHook(activity, portalController::execute)
        var interactiveControl: BrowserInteractiveControl? = null
        fun interactiveAction(
            profileId: ProfileId,
            action: (BrowserInteractiveControl) -> Boolean,
        ): Boolean {
            val control = interactiveControl ?: return false
            if (control.profileId != profileId) return false
            return action(control)
        }
        val secretInbox = E2eSecretInbox(
            activity.cacheDir.resolve(E2eSecretInbox.RELATIVE_DIRECTORY),
        )
        val controlHook = E2eControlHook(
            activity = activity,
            scope = scope,
            controller = E2eControlController(
                certificateState = certificateState,
                selectCertificate = selectCertificate,
                unlockCertificate = unlockCertificate,
                lockCertificate = lockCertificate,
                forgetCertificate = forgetCertificate,
                consumeSecret = secretInbox::consume,
                executePortal = portalController::execute,
                signingState = signingState,
                confirmClientAuth = { profileId ->
                    interactiveAction(profileId) { it.confirmClientAuth() }
                },
                cancelClientAuth = { profileId ->
                    interactiveAction(profileId) { it.cancelClientAuth() }
                },
                confirmPortalCertificate = { profileId ->
                    interactiveAction(profileId) { it.confirmCertificateSelection() }
                },
                cancelPortalCertificate = { profileId ->
                    interactiveAction(profileId) { it.cancelCertificateSelection() }
                },
                confirmCurrentSigning = confirmCurrentSigning,
                cancelCurrentSigning = cancelCurrentSigning,
                dismissCurrentSigning = dismissCurrentSigning,
            ),
        )
        return object : RuntimeDiagnosticsObserver {
            override fun start() {
                smokeHook.start()
                controlHook.start()
            }

            override fun stop() {
                controlHook.stop()
                smokeHook.stop()
            }

            override fun observe(event: RuntimeDiagnosticEvent) = runtime.observe(event)

            override fun updateInteractiveControl(control: BrowserInteractiveControl?) {
                interactiveControl = control
            }
        }
    }
}

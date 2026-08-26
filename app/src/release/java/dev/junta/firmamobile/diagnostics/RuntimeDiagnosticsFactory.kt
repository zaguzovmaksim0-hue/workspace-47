package dev.junta.firmamobile.diagnostics

import android.app.Activity
import android.net.Uri
import dev.junta.firmamobile.catalog.PortalCatalogRepository
import dev.junta.firmamobile.catalog.PortalLaunchTarget
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.signing.SigningUiState
import dev.junta.firmamobile.ui.CertificateUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/** Release implementation deliberately has no receiver, action string, command parser or journal. */
internal object RuntimeDiagnosticsFactory {
    @Suppress("UNUSED_PARAMETER")
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
    ): RuntimeDiagnosticsObserver = NoOpRuntimeDiagnosticsObserver

    private object NoOpRuntimeDiagnosticsObserver : RuntimeDiagnosticsObserver {
        override fun start() = Unit
        override fun stop() = Unit
        override fun observe(event: RuntimeDiagnosticEvent) = Unit
        override fun updateInteractiveControl(control: BrowserInteractiveControl?) = Unit
    }
}

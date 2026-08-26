package dev.junta.firmamobile.diagnostics

import dev.junta.firmamobile.browser.BrowserErrorCode
import dev.junta.firmamobile.browser.NavigationBlockReason
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.signing.SigningErrorCode
import java.util.UUID

/**
 * Internal runtime observations. Values are converted to a bounded, sanitised QA report only by
 * the debug/QA observer. Release uses a no-op observer and has no shell command surface.
 */
internal sealed interface RuntimeDiagnosticEvent {
    val profileId: ProfileId
    val browserSessionId: UUID
    val navigationEpoch: Long

    data class WebViewState(
        override val profileId: ProfileId,
        override val browserSessionId: UUID,
        override val navigationEpoch: Long,
        val active: Boolean,
    ) : RuntimeDiagnosticEvent

    data class NavigationStarted(
        override val profileId: ProfileId,
        override val browserSessionId: UUID,
        override val navigationEpoch: Long,
        val url: String,
    ) : RuntimeDiagnosticEvent

    data class NavigationChanged(
        override val profileId: ProfileId,
        override val browserSessionId: UUID,
        override val navigationEpoch: Long,
        val url: String,
    ) : RuntimeDiagnosticEvent

    data class NavigationBlocked(
        override val profileId: ProfileId,
        override val browserSessionId: UUID,
        override val navigationEpoch: Long,
        val reason: NavigationBlockReason,
    ) : RuntimeDiagnosticEvent

    data class BrowserError(
        override val profileId: ProfileId,
        override val browserSessionId: UUID,
        override val navigationEpoch: Long,
        val error: BrowserErrorCode,
    ) : RuntimeDiagnosticEvent

    data class RenderProcessGone(
        override val profileId: ProfileId,
        override val browserSessionId: UUID,
        override val navigationEpoch: Long,
    ) : RuntimeDiagnosticEvent

    data class ClientCertRequestObserved(
        override val profileId: ProfileId,
        override val browserSessionId: UUID,
        override val navigationEpoch: Long,
        val host: String,
        val port: Int,
    ) : RuntimeDiagnosticEvent

    data class ClientCertRequestAccepted(
        override val profileId: ProfileId,
        override val browserSessionId: UUID,
        override val navigationEpoch: Long,
        val host: String,
        val port: Int,
    ) : RuntimeDiagnosticEvent

    data class ClientAuthConfirmationRequired(
        override val profileId: ProfileId,
        override val browserSessionId: UUID,
        override val navigationEpoch: Long,
        val host: String,
    ) : RuntimeDiagnosticEvent

    data class CertificateSelectionRequired(
        override val profileId: ProfileId,
        override val browserSessionId: UUID,
        override val navigationEpoch: Long,
        val host: String,
    ) : RuntimeDiagnosticEvent

    data class AfirmaRequestObserved(
        override val profileId: ProfileId,
        override val browserSessionId: UUID,
        override val navigationEpoch: Long,
        val host: String,
    ) : RuntimeDiagnosticEvent

    data class AutoFirmaIntentObserved(
        override val profileId: ProfileId,
        override val browserSessionId: UUID,
        override val navigationEpoch: Long,
    ) : RuntimeDiagnosticEvent

    data class PortalCallbackObserved(
        override val profileId: ProfileId,
        override val browserSessionId: UUID,
        override val navigationEpoch: Long,
        val stage: String,
        val host: String,
    ) : RuntimeDiagnosticEvent

    data class SigningStateObserved(
        override val profileId: ProfileId,
        override val browserSessionId: UUID,
        override val navigationEpoch: Long,
        val state: SigningDiagnosticState,
        val error: SigningErrorCode? = null,
    ) : RuntimeDiagnosticEvent
}

internal enum class SigningDiagnosticState {
    IDLE,
    AWAITING_CONFIRMATION,
    CONNECTING_SECURELY,
    SIGNING,
    COMPLETED,
    FAILED,
}

internal interface RuntimeDiagnosticsObserver {
    fun start()
    fun stop()
    fun observe(event: RuntimeDiagnosticEvent)
}

internal fun ((RuntimeDiagnosticEvent) -> Unit).observeSafely(event: RuntimeDiagnosticEvent) {
    try {
        invoke(event)
    } catch (_: Exception) {
        // Diagnostics must never alter navigation, client-auth or signing behavior.
    }
}

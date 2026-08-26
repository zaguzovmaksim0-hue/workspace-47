package dev.junta.firmamobile.control

import android.net.Uri
import dev.junta.firmamobile.certificate.E2eStagedCertificateDocumentAccess
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.signing.SigningUiState
import dev.junta.firmamobile.smoke.CatalogSmokeOutcome
import dev.junta.firmamobile.smoke.CatalogSmokeResultCode
import dev.junta.firmamobile.smoke.CatalogSmokeRequest
import dev.junta.firmamobile.smoke.isOrderedBroadcastFailure
import dev.junta.firmamobile.ui.CertificateUiState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URI

internal enum class E2eControlCommand {
    STATE,
    CERT_SELECT,
    CERT_UNLOCK,
    CERT_LOCK,
    CERT_FORGET,
    PORTAL_OPEN,
    PORTAL_INSPECT,
    PORTAL_CLOSE,
    PORTAL_LOGIN,
    CLIENT_AUTH_CONFIRM,
    CLIENT_AUTH_CANCEL,
    PORTAL_CERT_CONFIRM,
    PORTAL_CERT_CANCEL,
    SIGN_CONFIRM,
    SIGN_CANCEL,
    SIGN_DISMISS,
}

internal data class E2eControlRequest(
    val runId: String?,
    val command: String?,
    val portalId: String? = null,
    val profileId: String? = null,
    val secretHandle: String? = null,
    val certificateHandle: String? = null,
)

internal data class E2eCertificateStateSnapshot(
    val state: String,
    val error: String? = null,
)

internal data class E2eControlOutcome(
    val runId: String?,
    val command: String?,
    val result: String,
    val success: Boolean,
    val certificate: E2eCertificateStateSnapshot,
    val signingState: String,
    val portal: CatalogSmokeOutcome? = null,
)

internal class E2eControlController(
    private val certificateState: StateFlow<CertificateUiState>,
    private val selectCertificate: (Uri) -> Unit,
    private val unlockCertificate: (CharArray) -> Unit,
    private val lockCertificate: () -> Unit,
    private val forgetCertificate: () -> Unit,
    private val consumeSecret: suspend (String) -> E2eSecretReadResult,
    private val executePortal: (CatalogSmokeRequest) -> CatalogSmokeOutcome,
    private val navigateReviewedUrl: (ProfileId, URI) -> Boolean,
    private val signingState: () -> SigningUiState,
    private val confirmClientAuth: (ProfileId) -> Boolean,
    private val cancelClientAuth: (ProfileId) -> Boolean,
    private val confirmPortalCertificate: (ProfileId) -> Boolean,
    private val cancelPortalCertificate: (ProfileId) -> Boolean,
    private val confirmCurrentSigning: () -> Boolean,
    private val cancelCurrentSigning: () -> Boolean,
    private val dismissCurrentSigning: () -> Boolean,
) {
    private val commandMutex = Mutex()

    suspend fun execute(request: E2eControlRequest): E2eControlOutcome = commandMutex.withLock {
        val runId = request.runId?.takeIf(RUN_ID::matches)
            ?: return@withLock outcome(request, "INVALID_REQUEST", success = false)
        val command = request.command
            ?.uppercase()
            ?.let { raw -> runCatching { E2eControlCommand.valueOf(raw) }.getOrNull() }
            ?: return@withLock outcome(request.copy(runId = runId), "INVALID_REQUEST", success = false)
        val normalized = request.copy(runId = runId, command = command.name)
        if (!hasValidShape(normalized, command)) {
            return@withLock outcome(normalized, "INVALID_REQUEST", success = false)
        }
        when (command) {
            E2eControlCommand.STATE -> outcome(normalized, "STATE", success = true)
            E2eControlCommand.CERT_SELECT -> select(normalized)
            E2eControlCommand.CERT_UNLOCK -> unlock(normalized)
            E2eControlCommand.CERT_LOCK -> {
                lockCertificate()
                val locked = certificateState.value !is CertificateUiState.Unlocked &&
                    certificateState.value !is CertificateUiState.Unlocking
                outcome(normalized, if (locked) "CERTIFICATE_LOCKED" else "CERTIFICATE_LOCK_FAILED", locked)
            }
            E2eControlCommand.CERT_FORGET -> forget(normalized)
            E2eControlCommand.PORTAL_OPEN -> portal(normalized, "OPEN")
            E2eControlCommand.PORTAL_INSPECT -> portal(normalized, "INSPECT")
            E2eControlCommand.PORTAL_CLOSE -> portal(normalized, "CLOSE")
            E2eControlCommand.PORTAL_LOGIN -> redSaraLogin(normalized)
            E2eControlCommand.CLIENT_AUTH_CONFIRM ->
                browserConfirmationAction(normalized, BrowserConfirmationAction.CLIENT_AUTH_CONFIRM)
            E2eControlCommand.CLIENT_AUTH_CANCEL ->
                browserConfirmationAction(normalized, BrowserConfirmationAction.CLIENT_AUTH_CANCEL)
            E2eControlCommand.PORTAL_CERT_CONFIRM ->
                browserConfirmationAction(normalized, BrowserConfirmationAction.PORTAL_CERT_CONFIRM)
            E2eControlCommand.PORTAL_CERT_CANCEL ->
                browserConfirmationAction(normalized, BrowserConfirmationAction.PORTAL_CERT_CANCEL)
            E2eControlCommand.SIGN_CONFIRM -> signingAction(normalized, SigningAction.CONFIRM)
            E2eControlCommand.SIGN_CANCEL -> signingAction(normalized, SigningAction.CANCEL)
            E2eControlCommand.SIGN_DISMISS -> signingAction(normalized, SigningAction.DISMISS)
        }
    }

    private suspend fun select(request: E2eControlRequest): E2eControlOutcome {
        val handle = requireNotNull(request.certificateHandle)
        val uri = E2eStagedCertificateDocumentAccess.uriForHandle(handle)
        selectCertificate(uri)
        val terminal = awaitCertificateTerminal()
            ?: return outcome(request, "CERTIFICATE_OPERATION_TIMEOUT", success = false)
        val success = terminal is CertificateUiState.Locked && terminal.error == null
        return outcome(
            request,
            if (success) "CERTIFICATE_SELECTED" else "CERTIFICATE_SELECTION_FAILED",
            success,
        )
    }

    private suspend fun unlock(request: E2eControlRequest): E2eControlOutcome {
        if (certificateState.value !is CertificateUiState.Locked) {
            return outcome(request, "CERTIFICATE_NOT_LOCKED", success = false)
        }
        val secretResult = consumeSecret(requireNotNull(request.secretHandle))
        val owned = when (secretResult) {
            is E2eSecretReadResult.Success -> secretResult.secret
            E2eSecretReadResult.Missing -> return outcome(request, "SECRET_NOT_FOUND", success = false)
            E2eSecretReadResult.Invalid -> return outcome(request, "SECRET_INVALID", success = false)
        }
        owned.use {
            val password = it.take()
            var handedOff = false
            try {
                unlockCertificate(password)
                handedOff = true
            } finally {
                if (!handedOff) password.fill('\u0000')
            }
        }
        val terminal = awaitCertificateTerminal()
            ?: return outcome(request, "CERTIFICATE_OPERATION_TIMEOUT", success = false)
        val success = terminal is CertificateUiState.Unlocked
        return outcome(
            request,
            if (success) "CERTIFICATE_UNLOCKED" else "CERTIFICATE_UNLOCK_FAILED",
            success,
        )
    }

    private suspend fun forget(request: E2eControlRequest): E2eControlOutcome {
        forgetCertificate()
        val terminal = withTimeoutOrNull(OPERATION_TIMEOUT_MILLIS) {
            certificateState.first { it is CertificateUiState.NoCertificate }
        } ?: return outcome(request, "CERTIFICATE_OPERATION_TIMEOUT", success = false)
        return outcome(
            request,
            "CERTIFICATE_FORGOTTEN",
            terminal is CertificateUiState.NoCertificate,
        )
    }

    private fun portal(request: E2eControlRequest, operation: String): E2eControlOutcome {
        val portal = executePortal(
            CatalogSmokeRequest(
                runId = request.runId,
                portalId = request.portalId,
                profileId = request.profileId,
                operation = operation,
            ),
        )
        return outcome(
            request,
            portal.result.name,
            success = !portal.result.isOrderedBroadcastFailure(),
            portal = portal,
        )
    }

    private fun redSaraLogin(request: E2eControlRequest): E2eControlOutcome {
        val portal = inspectPortal(request)
        val profileId = portal.profileId
        val runtime = portal.runtime
        if (portal.portalId?.value != REDSARA_PORTAL_ID ||
            profileId != REDSARA_PROFILE_ID ||
            portal.result != CatalogSmokeResultCode.WEBVIEW_ACTIVE ||
            !runtimeContextIsBound(runtime)
        ) {
            return outcome(request, "PORTAL_LOGIN_NOT_AVAILABLE", success = false, portal = portal)
        }
        val accepted = navigateReviewedUrl(profileId, REDSARA_LOGIN_URI)
        return outcome(
            request,
            if (accepted) "PORTAL_LOGIN_REQUESTED" else "PORTAL_LOGIN_REJECTED",
            accepted,
            portal,
        )
    }

    private fun browserConfirmationAction(
        request: E2eControlRequest,
        action: BrowserConfirmationAction,
    ): E2eControlOutcome {
        val portal = inspectPortal(request)
        val runtime = portal.runtime
        val requiredObserved = when (action) {
            BrowserConfirmationAction.CLIENT_AUTH_CONFIRM,
            BrowserConfirmationAction.CLIENT_AUTH_CANCEL,
            -> runtime?.clientAuthConfirmationRequired == true
            BrowserConfirmationAction.PORTAL_CERT_CONFIRM,
            BrowserConfirmationAction.PORTAL_CERT_CANCEL,
            -> runtime?.certificateSelectionRequired == true
        }
        val profileId = portal.profileId
        if (portal.result != CatalogSmokeResultCode.WEBVIEW_ACTIVE ||
            profileId == null ||
            !runtimeContextIsBound(runtime) ||
            !requiredObserved
        ) {
            return outcome(request, "BROWSER_CONFIRMATION_NOT_PENDING", success = false, portal = portal)
        }
        val accepted = when (action) {
            BrowserConfirmationAction.CLIENT_AUTH_CONFIRM -> confirmClientAuth(profileId)
            BrowserConfirmationAction.CLIENT_AUTH_CANCEL -> cancelClientAuth(profileId)
            BrowserConfirmationAction.PORTAL_CERT_CONFIRM -> confirmPortalCertificate(profileId)
            BrowserConfirmationAction.PORTAL_CERT_CANCEL -> cancelPortalCertificate(profileId)
        }
        val result = when (action) {
            BrowserConfirmationAction.CLIENT_AUTH_CONFIRM ->
                if (accepted) "CLIENT_AUTH_CONFIRM_REQUESTED" else "CLIENT_AUTH_NOT_PENDING"
            BrowserConfirmationAction.CLIENT_AUTH_CANCEL ->
                if (accepted) "CLIENT_AUTH_CANCEL_REQUESTED" else "CLIENT_AUTH_NOT_PENDING"
            BrowserConfirmationAction.PORTAL_CERT_CONFIRM ->
                if (accepted) "PORTAL_CERTIFICATE_CONFIRM_REQUESTED" else "PORTAL_CERTIFICATE_NOT_PENDING"
            BrowserConfirmationAction.PORTAL_CERT_CANCEL ->
                if (accepted) "PORTAL_CERTIFICATE_CANCELLED" else "PORTAL_CERTIFICATE_NOT_PENDING"
        }
        return outcome(request, result, accepted, portal)
    }

    private fun signingAction(request: E2eControlRequest, action: SigningAction): E2eControlOutcome {
        val portal = inspectPortal(request)
        val runtime = portal.runtime
        val requiredObserved = when (action) {
            SigningAction.CONFIRM -> runtime?.signingConfirmationRequired == true
            SigningAction.CANCEL -> runtime?.let {
                it.signingConfirmationRequired || it.signingStartedObserved
            } == true
            SigningAction.DISMISS -> runtime?.let {
                it.signingCompletedObserved || it.signingFailedObserved
            } == true
        }
        if (portal.result != CatalogSmokeResultCode.WEBVIEW_ACTIVE ||
            !runtimeContextIsBound(runtime) ||
            !requiredObserved
        ) {
            return outcome(request, "SIGNING_CONTEXT_NOT_ACTIVE", success = false, portal = portal)
        }
        val state = signingState()
        val accepted = when (action) {
            SigningAction.CONFIRM -> state is SigningUiState.AwaitingConfirmation && confirmCurrentSigning()
            SigningAction.CANCEL -> state !is SigningUiState.Idle && cancelCurrentSigning()
            SigningAction.DISMISS ->
                (state is SigningUiState.Completed || state is SigningUiState.Failed) && dismissCurrentSigning()
        }
        val result = when (action) {
            SigningAction.CONFIRM -> if (accepted) {
                "SIGNING_CONFIRM_REQUESTED"
            } else {
                "SIGNING_NOT_AWAITING_CONFIRMATION"
            }
            SigningAction.CANCEL -> if (accepted) "SIGNING_CANCEL_REQUESTED" else "SIGNING_NOT_ACTIVE"
            SigningAction.DISMISS -> if (accepted) "SIGNING_DISMISSED" else "SIGNING_NOT_TERMINAL"
        }
        return outcome(request, result, accepted, portal)
    }

    private fun runtimeContextIsBound(
        runtime: dev.junta.firmamobile.smoke.CatalogSmokeRuntimeSnapshot?,
    ): Boolean = runtime?.browserSessionBound == true &&
        runtime.webViewActive &&
        runtime.currentUrlAllowed &&
        !runtime.renderProcessGone

    private fun inspectPortal(request: E2eControlRequest): CatalogSmokeOutcome = executePortal(
        CatalogSmokeRequest(
            runId = request.runId,
            portalId = request.portalId,
            profileId = request.profileId,
            operation = "INSPECT",
        ),
    )

    private enum class BrowserConfirmationAction {
        CLIENT_AUTH_CONFIRM,
        CLIENT_AUTH_CANCEL,
        PORTAL_CERT_CONFIRM,
        PORTAL_CERT_CANCEL,
    }

    private enum class SigningAction { CONFIRM, CANCEL, DISMISS }

    private suspend fun awaitCertificateTerminal(): CertificateUiState? =
        withTimeoutOrNull(OPERATION_TIMEOUT_MILLIS) {
            certificateState.first {
                it !is CertificateUiState.LoadingReference && it !is CertificateUiState.Unlocking
            }
        }

    private fun hasValidShape(request: E2eControlRequest, command: E2eControlCommand): Boolean {
        val hasPortal = !request.portalId.isNullOrBlank()
        val hasProfile = !request.profileId.isNullOrBlank()
        val hasTarget = hasPortal.xor(hasProfile)
        return when (command) {
            E2eControlCommand.STATE,
            E2eControlCommand.CERT_LOCK,
            E2eControlCommand.CERT_FORGET,
            -> !hasPortal && !hasProfile && request.secretHandle == null && request.certificateHandle == null
            E2eControlCommand.CERT_SELECT ->
                !hasPortal && !hasProfile && request.secretHandle == null &&
                    request.certificateHandle?.let(E2eStagedCertificateDocumentAccess.HANDLE::matches) == true
            E2eControlCommand.CERT_UNLOCK ->
                !hasPortal && !hasProfile && request.certificateHandle == null &&
                    request.secretHandle?.let(E2eSecretInbox.HANDLE::matches) == true
            E2eControlCommand.PORTAL_OPEN,
            E2eControlCommand.PORTAL_INSPECT,
            E2eControlCommand.PORTAL_CLOSE,
            E2eControlCommand.PORTAL_LOGIN,
            E2eControlCommand.CLIENT_AUTH_CONFIRM,
            E2eControlCommand.CLIENT_AUTH_CANCEL,
            E2eControlCommand.PORTAL_CERT_CONFIRM,
            E2eControlCommand.PORTAL_CERT_CANCEL,
            E2eControlCommand.SIGN_CONFIRM,
            E2eControlCommand.SIGN_CANCEL,
            E2eControlCommand.SIGN_DISMISS,
            -> hasTarget && request.secretHandle == null && request.certificateHandle == null
        }
    }

    private fun outcome(
        request: E2eControlRequest,
        result: String,
        success: Boolean,
        portal: CatalogSmokeOutcome? = null,
    ) = E2eControlOutcome(
        runId = request.runId?.takeIf(RUN_ID::matches),
        command = request.command?.uppercase()?.takeIf(COMMAND_TOKEN::matches),
        result = result,
        success = success,
        certificate = certificateState.value.toSnapshot(),
        signingState = signingState().sanitizedName(),
        portal = portal,
    )

    private fun CertificateUiState.toSnapshot(): E2eCertificateStateSnapshot = when (this) {
        CertificateUiState.LoadingReference -> E2eCertificateStateSnapshot("LOADING_REFERENCE")
        is CertificateUiState.NoCertificate -> E2eCertificateStateSnapshot("NO_CERTIFICATE", error?.name)
        is CertificateUiState.Locked -> E2eCertificateStateSnapshot("LOCKED", error?.name)
        is CertificateUiState.Unlocking -> E2eCertificateStateSnapshot("UNLOCKING")
        is CertificateUiState.Unlocked -> E2eCertificateStateSnapshot("UNLOCKED")
    }

    private fun SigningUiState.sanitizedName(): String = when (this) {
        SigningUiState.Idle -> "IDLE"
        is SigningUiState.AwaitingConfirmation -> "AWAITING_CONFIRMATION"
        is SigningUiState.ConnectingSecurely -> "CONNECTING_SECURELY"
        is SigningUiState.Signing -> "SIGNING"
        is SigningUiState.Completed -> "COMPLETED"
        is SigningUiState.Failed -> "FAILED"
    }

    private companion object {
        const val OPERATION_TIMEOUT_MILLIS = 10_000L
        const val REDSARA_PORTAL_ID = "age-reg-redsara"
        val REDSARA_PROFILE_ID = ProfileId("reg-age-redsara")
        val REDSARA_LOGIN_URI = URI("https://reg.redsara.es/es/login")
        val RUN_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
        val COMMAND_TOKEN = Regex("[A-Z_]{1,64}")
    }
}

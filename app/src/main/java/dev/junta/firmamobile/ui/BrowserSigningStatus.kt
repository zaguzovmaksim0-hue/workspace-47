package dev.junta.firmamobile.ui

import androidx.compose.runtime.Composable
import dev.junta.firmamobile.signing.SigningUiState

/** Status presentation only; signing confirmation remains owned by BrowserScreen. */
@Composable
internal fun browserSigningStatus(
    signingState: SigningUiState,
    onDismiss: () -> Unit,
) {
    when (signingState) {
        is SigningUiState.Signing,
        is SigningUiState.ConnectingSecurely,
        is SigningUiState.Completed,
        is SigningUiState.Failed,
        -> SigningStatusDialog(
            state = signingState,
            onDismiss = onDismiss,
        )

        SigningUiState.Idle,
        is SigningUiState.AwaitingConfirmation,
        -> Unit
    }
}

package dev.junta.firmamobile.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.junta.firmamobile.R
import dev.junta.firmamobile.signing.SigningUiState

@Composable
internal fun SigningStatusDialog(
    state: SigningUiState,
    onDismiss: () -> Unit,
) {
    when (state) {
        is SigningUiState.Signing -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.signing_in_progress_title)) },
            text = { Text(stringResource(R.string.signing_in_progress_copy)) },
            confirmButton = {},
        )

        is SigningUiState.Completed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.signing_completed_title)) },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close))
                }
            },
        )

        is SigningUiState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.signing_failed_title)) },
            text = {
                Text(stringResource(R.string.signing_closed_error_code, state.code.name))
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close))
                }
            },
        )

        SigningUiState.Idle,
        is SigningUiState.AwaitingConfirmation,
        -> Unit
    }
}

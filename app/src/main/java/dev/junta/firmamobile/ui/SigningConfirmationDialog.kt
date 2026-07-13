package dev.junta.firmamobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.signing.SigningUiState
import androidx.compose.ui.res.stringResource

@Composable
internal fun SigningConfirmationDialog(
    state: SigningUiState.AwaitingConfirmation,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.signing_confirmation_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.signing_site, state.siteHost))
                Text(stringResource(R.string.signing_profile, state.profileName))
                Text(stringResource(R.string.signing_support_level, state.supportLevel))
                Text(stringResource(R.string.signing_operation, state.safeDescription))
                Text(stringResource(R.string.signing_certificate, state.certificateOwner))
                Text(stringResource(R.string.signing_format, state.format))
                Text(stringResource(R.string.signing_algorithm, state.algorithm))
                if (state.requiresLegacySha1Warning) {
                    Text(
                        text = stringResource(R.string.signing_sha1_warning),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.signing_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

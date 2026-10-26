package dev.junta.firmamobile.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.junta.firmamobile.R

/** A key deletion is irreversible; opening or dismissing this dialog never removes a record. */
@Composable
internal fun savedCertificateKeyDeletionDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.keystore_key_delete_title)) },
        text = { Text(stringResource(R.string.keystore_key_delete_warning)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm-key-deletion")) {
                Text(stringResource(R.string.saved_certificate_remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("cancel-key-deletion")) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

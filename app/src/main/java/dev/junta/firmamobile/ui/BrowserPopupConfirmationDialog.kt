package dev.junta.firmamobile.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.junta.firmamobile.R

/** The callback has no authenticated frame origin or destination; the prompt must not invent either. */
@Suppress("FunctionNaming")
@Composable
internal fun BrowserPopupConfirmationDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.browser_popup_confirmation_title)) },
        text = {
            FilterObscuredTouches()
            Text(stringResource(R.string.browser_popup_confirmation_message))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.browser_popup_confirmation_open)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.browser_popup_confirmation_cancel)) }
        },
        modifier = Modifier.testTag("browser-popup-confirmation"),
    )
}

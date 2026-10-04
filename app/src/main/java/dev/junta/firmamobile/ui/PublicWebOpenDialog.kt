package dev.junta.firmamobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import dev.junta.firmamobile.R
import dev.junta.firmamobile.browser.PublicBrowserAddress
import java.net.URI

/** The address is transient UI input, not a persisted catalog/trust entry. */
@Composable
internal fun PublicWebOpenDialog(onOpen: (URI) -> Unit, onDismiss: () -> Unit) {
    var address by remember { mutableStateOf("https://") }
    val admitted = remember(address) { PublicBrowserAddress.parse(address.trim()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("public-web-open-dialog"),
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(stringResource(R.string.public_web_open_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = address, onValueChange = { address = it },
                    modifier = Modifier.fillMaxWidth().testTag("public-web-address"),
                    label = { Text(stringResource(R.string.public_web_address)) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Text(stringResource(R.string.public_web_open_hint))
            }
        },
        confirmButton = {
            TextButton(enabled = admitted != null, onClick = { admitted?.let(onOpen) },
                modifier = Modifier.testTag("public-web-open-confirm")) {
                Text(stringResource(R.string.public_browsing_open))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

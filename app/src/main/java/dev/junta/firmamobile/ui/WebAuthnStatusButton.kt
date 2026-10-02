package dev.junta.firmamobile.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
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
import dev.junta.firmamobile.R
import dev.junta.firmamobile.browser.WebAuthnEngineState

/** Engine availability is not credential-provider approval or a successful
 * relying-party authentication. Never replace navigator.credentials with a shim. */
@Composable
internal fun WebAuthnStatusButton(state: WebAuthnEngineState, onOpenBrowser: (() -> Unit)?) {
    var show by remember { mutableStateOf(false) }
    TextButton(onClick = { show = true }, modifier = Modifier.testTag("webauthn-status")) {
        Text(stringResource(R.string.webauthn_status_label))
    }
    if (show) AlertDialog(
        onDismissRequest = { show = false }, modifier = Modifier.testTag("webauthn-status-dialog"),
        title = { Text(stringResource(R.string.webauthn_status_label)) },
        text = { Column(Modifier.fillMaxWidth()) {
            Text(stringResource(when (state) {
                WebAuthnEngineState.UNSUPPORTED -> R.string.webauthn_unsupported
                WebAuthnEngineState.CONFIGURATION_FAILED -> R.string.webauthn_configuration_failed
                WebAuthnEngineState.ENABLED_PROVIDER_AUTHORIZATION_UNKNOWN -> R.string.webauthn_provider_unknown
            }))
            Text(stringResource(R.string.webauthn_no_success_claim))
            if (onOpenBrowser != null) TextButton(onClick = { show = false; onOpenBrowser() }, modifier = Modifier.testTag("webauthn-open-browser")) {
                Text(stringResource(R.string.webauthn_external_browser))
            }
        } },
        confirmButton = { TextButton(onClick = { show = false }) { Text(stringResource(R.string.webauthn_close)) } },
    )
}

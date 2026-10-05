package dev.junta.firmamobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import dev.junta.firmamobile.R
import dev.junta.firmamobile.browser.InteractiveClientAuthProblem
import dev.junta.firmamobile.browser.InteractiveClientAuthPrompt

@Composable
internal fun InteractiveClientAuthDialog(
    prompt: InteractiveClientAuthPrompt,
    onConfirm: () -> Unit,
    onUnlock: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier.testTag("interactive-client-auth"),
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(stringResource(R.string.interactive_tls_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.interactive_tls_server, prompt.server))
                Text(stringResource(R.string.interactive_tls_page, prompt.pageOrigin))
                Text(stringResource(R.string.interactive_tls_operation))
                Text(stringResource(R.string.compatibility_operation_notice))
                prompt.certificateOwner?.let { Text(stringResource(R.string.interactive_tls_certificate, it)) }
                when (prompt.problem) {
                    InteractiveClientAuthProblem.NO_CERTIFICATE -> Text(stringResource(R.string.interactive_tls_unlock_copy))
                    InteractiveClientAuthProblem.INCOMPATIBLE_CERTIFICATE -> Text(stringResource(R.string.interactive_tls_incompatible))
                    else -> Unit
                }
                Text(stringResource(R.string.interactive_tls_cache_notice))
                if (prompt.canConfirm) {
                    TextButton(onClick = onUnlock, modifier = Modifier.testTag("interactive-client-auth-change")) {
                        Text(stringResource(R.string.interactive_tls_change))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = if (prompt.canConfirm) onConfirm else onUnlock,
                modifier = Modifier.testTag(if (prompt.canConfirm) "interactive-client-auth-confirm" else "interactive-client-auth-unlock"),
            ) {
                Text(stringResource(if (prompt.canConfirm) R.string.interactive_tls_confirm else R.string.interactive_tls_unlock))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("interactive-client-auth-cancel")) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

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

@Composable
internal fun ExternalAppConsentDialog(
    sourceHost: String,
    scheme: String,
    requestedPackage: String?,
    fallbackHost: String?,
    onOpenApp: () -> Unit,
    onOpenWeb: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier.testTag("external-app-dialog"),
        title = { Text(stringResource(R.string.external_app_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.external_app_source, sourceHost))
                Text(stringResource(R.string.external_app_scheme, scheme))
                requestedPackage?.let { packageName ->
                    Text(stringResource(R.string.external_app_package, packageName))
                }
                Text(stringResource(R.string.external_app_copy))
                fallbackHost?.let { host ->
                    TextButton(
                        onClick = onOpenWeb,
                        modifier = Modifier.testTag("external-app-web"),
                    ) {
                        Text(stringResource(R.string.external_app_fallback, host))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onOpenApp,
                modifier = Modifier.testTag("external-app-open"),
            ) {
                Text(stringResource(R.string.external_app_open))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.testTag("external-app-cancel"),
            ) {
                Text(stringResource(R.string.external_app_cancel))
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            securePolicy = SecureFlagPolicy.SecureOn,
        ),
    )
}

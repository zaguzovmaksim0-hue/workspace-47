package dev.junta.firmamobile.ui.offlineverification

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import dev.junta.firmamobile.R

@Suppress("FunctionNaming") // Unit-returning Compose UI follows the framework naming convention.
@Composable
internal fun OfflineVerificationDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val controller = remember(scope) { OfflineVerificationController(scope) }
    val pickers = rememberOfflineVerificationPickers(controller)
    OfflineVerificationDialog(controller, pickers.cms, pickers.original, onDismiss)
}

@Suppress("FunctionNaming") // Unit-returning Compose UI follows the framework naming convention.
@Composable
internal fun OfflineVerificationDialog(
    controller: OfflineVerificationController,
    onPickCms: () -> Unit,
    onPickOriginal: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state by controller.state.collectAsState()
    DisposableEffect(controller) { onDispose { controller.close() } }
    val close = { controller.close(); onDismiss() }
    AlertDialog(
        modifier = Modifier.testTag("offline-verification-dialog"),
        onDismissRequest = close,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(stringResource(R.string.offline_verification_title)) },
        text = { OfflineVerificationContent(state, controller, onPickCms, onPickOriginal) },
        confirmButton = {
            TextButton(
                onClick = controller::verify,
                enabled = state.canVerify,
                modifier = Modifier.testTag("offline-verification-verify"),
            ) { Text(stringResource(R.string.offline_verification_verify)) }
        },
        dismissButton = {
            TextButton(onClick = close, modifier = Modifier.testTag("offline-verification-close")) {
                Text(stringResource(R.string.offline_verification_close))
            }
        },
    )
}

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
import dev.junta.firmamobile.afirma.servlet.AfirmaConsentPhase
import dev.junta.firmamobile.afirma.servlet.AfirmaConsentProblem
import dev.junta.firmamobile.afirma.servlet.AfirmaConsentPrompt

@Composable
internal fun NativeAfirmaConsentDialog(
    prompt: AfirmaConsentPrompt,
    onConfirm: () -> Unit,
    onUnlock: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val finished = prompt.phase == AfirmaConsentPhase.FINISHED
    val review = prompt.phase == AfirmaConsentPhase.REVIEW
    AlertDialog(
        onDismissRequest = if (finished) onDismiss else onCancel,
        modifier = Modifier.testTag("native-afirma-consent"),
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(stringResource(R.string.native_afirma_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.native_afirma_source, prompt.details.sourceOrigin))
                Text(stringResource(R.string.native_afirma_destination, prompt.details.destination))
                Text(stringResource(if (prompt.details.operation == "selectcert") R.string.native_afirma_selectcert else R.string.native_afirma_sign))
                prompt.details.format?.let { Text(stringResource(R.string.native_afirma_format, it)) }
                prompt.details.algorithm?.let { Text(stringResource(R.string.native_afirma_algorithm, it)) }
                if (prompt.details.operation == "sign") {
                    Text(stringResource(R.string.native_afirma_size, prompt.details.payloadBytes))
                    prompt.details.payloadSha256?.let { Text(stringResource(R.string.native_afirma_digest, it)) }
                }
                prompt.certificateOwner?.let { Text(stringResource(R.string.native_afirma_certificate, it)) }
                when (prompt.phase) {
                    AfirmaConsentPhase.WORKING -> Text(stringResource(R.string.native_afirma_working))
                    AfirmaConsentPhase.SENDING -> Text(stringResource(R.string.native_afirma_sending))
                    else -> Unit
                }
                when (prompt.problem) {
                    AfirmaConsentProblem.LOCKED -> Text(stringResource(R.string.native_afirma_locked))
                    AfirmaConsentProblem.INCOMPATIBLE -> Text(stringResource(R.string.native_afirma_incompatible))
                    AfirmaConsentProblem.ACKNOWLEDGED -> Text(stringResource(R.string.native_afirma_stored))
                    AfirmaConsentProblem.UNCERTAIN -> Text(stringResource(R.string.native_afirma_uncertain))
                    AfirmaConsentProblem.NOT_SENT -> Text(stringResource(R.string.native_afirma_not_sent))
                    AfirmaConsentProblem.REJECTED -> Text(stringResource(R.string.native_afirma_rejected))
                    AfirmaConsentProblem.CANCELLED -> Text(stringResource(R.string.native_afirma_cancelled))
                    AfirmaConsentProblem.EXPIRED -> Text(stringResource(R.string.native_afirma_expired))
                    AfirmaConsentProblem.FAILED -> Text(stringResource(R.string.native_afirma_failed))
                    null -> if (review) Text(stringResource(R.string.native_afirma_review_notice))
                }
            }
        },
        confirmButton = {
            when {
                finished -> TextButton(onClick = onDismiss, modifier = Modifier.testTag("native-afirma-close")) {
                    Text(stringResource(R.string.native_afirma_close))
                }
                review && prompt.canConfirm -> TextButton(onClick = onConfirm, modifier = Modifier.testTag("native-afirma-confirm")) {
                    Text(stringResource(if (prompt.details.operation == "selectcert") R.string.native_afirma_send_certificate else R.string.native_afirma_sign_and_send))
                }
                review -> TextButton(onClick = onUnlock, modifier = Modifier.testTag("native-afirma-unlock")) {
                    Text(stringResource(R.string.interactive_tls_unlock))
                }
            }
        },
        dismissButton = {
            if (!finished) TextButton(onClick = onCancel, modifier = Modifier.testTag("native-afirma-cancel")) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

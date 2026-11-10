package dev.junta.firmamobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.afirma.servlet.AfirmaFallbackPrompt

/** An AutoFirma request refused before certificate use. The selected browser mode is preserved. */
@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
internal fun NativeAfirmaFallbackDialog(
    prompt: AfirmaFallbackPrompt,
    onClose: () -> Unit,
    onOpenOfficial: () -> Unit,
) {
    val closeButton: @Composable () -> Unit = {
        TextButton(onClick = onClose) { Text(stringResource(R.string.native_afirma_close)) }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.native_afirma_title)) },
        text = {
            FilterObscuredTouches()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        if (prompt.invalid) R.string.native_afirma_invalid else R.string.native_afirma_unsupported,
                    ),
                )
                prompt.reason?.let { reason ->
                    Text(
                        stringResource(R.string.native_afirma_reason, reason),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = closeButton,
        dismissButton = {
            if (!prompt.invalid) {
                TextButton(onClick = onOpenOfficial) { Text(stringResource(R.string.native_afirma_official)) }
            }
        },
    )
}

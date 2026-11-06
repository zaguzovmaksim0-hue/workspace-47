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

/** Ordinary-browsing AutoFirma request that the native engine refused before using the
 * certificate. When the catalog has a reviewed mode for the page, that mode is the main action. */
@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
internal fun NativeAfirmaFallbackDialog(
    prompt: AfirmaFallbackPrompt,
    onClose: () -> Unit,
    onOpenOfficial: () -> Unit,
    onOpenPortalMode: () -> Unit,
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
                if (prompt.portalMode) Text(stringResource(R.string.native_afirma_portal_mode_hint))
                prompt.reason?.let { reason ->
                    Text(
                        stringResource(R.string.native_afirma_reason, reason),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (prompt.portalMode) {
                TextButton(onClick = onOpenPortalMode) {
                    Text(stringResource(R.string.catalog_open_profile_compatibility))
                }
            } else {
                closeButton()
            }
        },
        dismissButton = {
            if (prompt.portalMode) closeButton()
            if (!prompt.invalid) {
                TextButton(onClick = onOpenOfficial) { Text(stringResource(R.string.native_afirma_official)) }
            }
        },
    )
}

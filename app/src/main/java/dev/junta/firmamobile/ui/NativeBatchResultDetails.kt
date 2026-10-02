package dev.junta.firmamobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.afirma.servlet.NativeBatchReceipt

@Composable
internal fun NativeBatchResultDetails(receipt: NativeBatchReceipt) {
    var expanded by remember(receipt) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.testTag("native-batch-details-toggle"),
        ) {
            Text(
                text = stringResource(
                    if (expanded) R.string.native_batch_details_hide
                    else R.string.native_batch_details_show,
                ),
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier.fillMaxWidth().testTag("native-batch-details"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val originResource = when (receipt.origin) {
                    NativeBatchReceipt.Origin.LOCAL -> R.string.native_batch_origin_local
                    NativeBatchReceipt.Origin.SERVICE -> R.string.native_batch_origin_service
                }
                Text(text = stringResource(originResource))
                receipt.entries.forEachIndexed { index, entry ->
                    Column(
                        modifier = Modifier.fillMaxWidth().testTag("native-batch-result-$index"),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(text = stringResource(R.string.native_batch_id, index + 1, entry.documentLabel))
                        Text(text = stringResource(R.string.native_batch_status, entry.statusCode))
                        Text(text = stringResource(nativeBatchStatusText(entry.statusCode)))
                        entry.description?.let { description ->
                            Text(text = stringResource(R.string.native_batch_description, description))
                        }
                    }
                }
            }
        }
    }
}

private fun nativeBatchStatusText(code: String): Int = when (code) {
    "DONE_AND_SAVED", "OK" -> R.string.native_batch_status_done
    "DONE_BUT_NOT_SAVED_YET" -> R.string.native_batch_status_pending_save
    "DONE_BUT_SAVED_SKIPPED" -> R.string.native_batch_status_save_skipped
    "DONE_BUT_ERROR_SAVING" -> R.string.native_batch_status_save_failed
    "ERROR_PRE" -> R.string.native_batch_status_pre_failed
    "ERROR_POST" -> R.string.native_batch_status_post_failed
    "SKIPPED" -> R.string.native_batch_status_skipped
    "NOT_STARTED", "NP" -> R.string.native_batch_status_not_started
    "SAVE_ROLLBACKED" -> R.string.native_batch_status_rollback
    "KO" -> R.string.native_batch_status_failed
    else -> R.string.native_batch_status_other
}

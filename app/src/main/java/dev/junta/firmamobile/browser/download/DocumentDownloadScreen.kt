package dev.junta.firmamobile.browser.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.R

@Composable
internal fun DocumentDownloadScreen(
    plan: DocumentDownloadPlan?, state: DocumentDownloadState, received: Long,
    onStart: () -> Unit, onSave: () -> Unit, onClose: () -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.document_download_title), style = MaterialTheme.typography.headlineSmall)
            if (plan != null) {
                Text(plan.fileName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("download-file-name"))
                Text(stringResource(R.string.document_download_server, plan.targetOrigin))
                if (plan.sourceOrigin != plan.targetOrigin) Text(stringResource(R.string.document_download_foreign))
            }
            Text(stringResource(when (state) {
                DocumentDownloadState.OFFER -> R.string.document_download_offer
                DocumentDownloadState.FETCHING -> R.string.document_download_fetching
                DocumentDownloadState.READY -> R.string.document_download_ready
                DocumentDownloadState.PICKING -> R.string.document_download_picking
                DocumentDownloadState.COPYING -> R.string.document_download_copying
                DocumentDownloadState.SAVED -> R.string.document_download_saved
                DocumentDownloadState.SAVE_FAILED -> R.string.document_download_save_failed
                DocumentDownloadState.FAILED -> R.string.document_download_failed
                DocumentDownloadState.EXPIRED -> R.string.document_download_expired
            }), modifier = Modifier.testTag("download-status"))
            if (state == DocumentDownloadState.OFFER && plan?.cookie != null) {
                Text(stringResource(R.string.document_download_session))
            }
            if (state in setOf(DocumentDownloadState.FETCHING, DocumentDownloadState.COPYING)) {
                CircularProgressIndicator(Modifier.testTag("download-progress"))
            }
            if (received > 0) Text(stringResource(R.string.document_download_bytes, received))
            Spacer(Modifier.height(8.dp))
            if (state == DocumentDownloadState.OFFER && plan != null) Button(
                onClick = onStart, modifier = Modifier.fillMaxWidth().testTag("download-start"),
            ) { Text(stringResource(R.string.document_download_start)) }
            if (state in setOf(DocumentDownloadState.READY, DocumentDownloadState.SAVE_FAILED)) Button(
                onClick = onSave, modifier = Modifier.fillMaxWidth().testTag("download-save"),
            ) { Text(stringResource(R.string.document_download_save)) }
            if (state != DocumentDownloadState.PICKING) OutlinedButton(
                onClick = onClose, modifier = Modifier.fillMaxWidth().testTag("download-close"),
            ) { Text(stringResource(if (state == DocumentDownloadState.SAVED) R.string.document_download_done else R.string.document_download_cancel)) }
        }
    }
}

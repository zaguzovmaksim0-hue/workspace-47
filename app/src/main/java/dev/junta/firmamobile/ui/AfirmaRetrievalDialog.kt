package dev.junta.firmamobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
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
import dev.junta.firmamobile.afirma.servlet.AfirmaRetrievalProblem
import dev.junta.firmamobile.afirma.servlet.AfirmaRetrievalPrompt

@Composable
internal fun AfirmaRetrievalDialog(prompt: AfirmaRetrievalPrompt, onCancel: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = if (prompt.loading) onCancel else onClose,
        modifier = Modifier.testTag("afirma-retrieval-dialog"),
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(stringResource(R.string.afirma_retrieval_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.afirma_retrieval_source, prompt.sourceOrigin))
                Text(stringResource(R.string.afirma_retrieval_server, prompt.retrievalEndpoint))
                if (prompt.loading) {
                    LinearProgressIndicator(modifier = Modifier.testTag("afirma-retrieval-progress"))
                    Text(stringResource(R.string.afirma_retrieval_loading))
                } else {
                    Text(stringResource(when (prompt.problem) {
                        AfirmaRetrievalProblem.UNSUPPORTED -> R.string.afirma_retrieval_unsupported
                        AfirmaRetrievalProblem.CANCELLED -> R.string.afirma_retrieval_cancelled
                        AfirmaRetrievalProblem.EXPIRED -> R.string.afirma_retrieval_expired
                        else -> R.string.afirma_retrieval_failed
                    }))
                    Text(stringResource(R.string.afirma_retrieval_restart))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = if (prompt.loading) onCancel else onClose,
                modifier = Modifier.testTag(if (prompt.loading) "afirma-retrieval-cancel" else "afirma-retrieval-close"),
            ) { Text(stringResource(if (prompt.loading) R.string.cancel else R.string.native_afirma_close)) }
        },
    )
}

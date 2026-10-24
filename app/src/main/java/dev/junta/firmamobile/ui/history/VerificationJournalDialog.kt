package dev.junta.firmamobile.ui.history

import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import dev.junta.firmamobile.R
import dev.junta.firmamobile.history.InMemoryOperationJournal

@Composable
internal fun verificationJournalDialog(journal: InMemoryOperationJournal, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val controller = remember(journal, scope) { VerificationJournalController(journal, scope) }
    verificationJournalDialog(controller, onDismiss)
}

@Composable
internal fun verificationJournalDialog(controller: VerificationJournalController, onDismiss: () -> Unit) {
    val state by controller.state.collectAsState()
    DisposableEffect(controller) { onDispose { controller.close() } }
    state.exportTicket?.let { journalCreateDocumentPicker(controller, it) }
    val close = { controller.close(); onDismiss() }
    AlertDialog(
        modifier = Modifier.testTag("verification-journal-dialog"),
        onDismissRequest = close,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(stringResource(R.string.verification_journal_title)) },
        text = {
            verificationJournalContent(state, onExport = { controller.beginExport() }, onClear = controller::clear)
        },
        confirmButton = {
            TextButton(
                onClick = close,
                modifier = Modifier.heightIn(min = 48.dp).testTag("verification-journal-close"),
            ) { Text(stringResource(R.string.verification_journal_close)) }
        },
    )
}

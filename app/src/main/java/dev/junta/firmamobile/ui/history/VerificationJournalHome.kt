package dev.junta.firmamobile.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.junta.firmamobile.history.InMemoryOperationJournal
import dev.junta.firmamobile.history.OperationKind
import dev.junta.firmamobile.ui.offlineverification.OfflineVerificationDialog
import dev.junta.firmamobile.ui.offlineverification.OfflineVerificationEntry
import java.time.Instant

/** Independent home actions; AppRoot supplies its single ordinary-remember session journal. */
@Composable
internal fun verificationJournalHome(journal: InMemoryOperationJournal) {
    var verificationVisible by remember { mutableStateOf(false) }
    var journalVisible by remember { mutableStateOf(false) }
    if (verificationVisible) OfflineVerificationDialog(
        onTerminal = { journal.append(OperationKind.VERIFY, it, Instant.now()) },
        onDismiss = { verificationVisible = false },
    )
    if (journalVisible) verificationJournalDialog(journal, onDismiss = { journalVisible = false })
    OfflineVerificationEntry(onClick = { verificationVisible = true })
    verificationJournalEntry(onClick = { journalVisible = true })
}

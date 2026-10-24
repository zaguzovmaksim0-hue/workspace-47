package dev.junta.firmamobile.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.history.LocalOutcome

@Composable
internal fun verificationJournalContent(state: VerificationJournalState, onExport: () -> Unit, onClear: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.verification_journal_scope))
        Text(stringResource(R.string.verification_journal_integrity))
        Text(stringResource(R.string.verification_journal_copies))
        Text(stringResource(R.string.verification_journal_provider_warning))
        TextButton(
            onClick = onExport,
            enabled = state.canExport,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("verification-journal-export"),
        ) { Text(stringResource(R.string.verification_journal_export)) }
        TextButton(
            onClick = onClear,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("verification-journal-clear"),
        ) { Text(stringResource(R.string.verification_journal_clear)) }
        journalExportStatusResource(state.exportStatus)?.let { resource ->
            Text(
                stringResource(resource),
                modifier = Modifier.testTag("verification-journal-export-status")
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        HorizontalDivider()
        if (state.snapshot.entries.isEmpty()) Text(stringResource(R.string.verification_journal_empty))
        state.snapshot.entries.forEach { entry ->
            val outcome = when (entry.outcome) {
                LocalOutcome.COMPLETED -> R.string.verification_journal_completed
                LocalOutcome.CANCELLED -> R.string.verification_journal_cancelled
                LocalOutcome.FAILED -> R.string.verification_journal_failed
            }
            Text(
                stringResource(
                    R.string.verification_journal_row, entry.sequence, entry.occurredAt.toString(),
                    stringResource(R.string.verification_journal_kind_verify), stringResource(outcome),
                ),
                modifier = Modifier.testTag("verification-journal-row-${entry.sequence}"),
            )
        }
    }
}

private fun journalExportStatusResource(status: JournalExportStatus): Int? = when (status) {
    JournalExportStatus.IDLE -> null
    JournalExportStatus.CHOOSING -> R.string.verification_journal_choosing
    JournalExportStatus.WRITING -> R.string.verification_journal_writing
    JournalExportStatus.EXPORTED -> R.string.verification_journal_exported
    JournalExportStatus.CANCELLED -> R.string.verification_journal_export_cancelled
    JournalExportStatus.FAILED -> R.string.verification_journal_export_failed
}

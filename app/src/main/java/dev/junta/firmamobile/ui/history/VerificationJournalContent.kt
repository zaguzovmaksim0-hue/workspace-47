package dev.junta.firmamobile.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.history.LocalOutcome
import dev.junta.firmamobile.ui.JuntaFieldShape
import dev.junta.firmamobile.ui.theme.JuntaHairline
import dev.junta.firmamobile.ui.theme.JuntaInk
import dev.junta.firmamobile.ui.theme.JuntaMutedInk
import dev.junta.firmamobile.ui.theme.JuntaTeal
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun verificationJournalContent(state: VerificationJournalState, onExport: () -> Unit, onClear: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        journalEntries(state)
        Text(
            stringResource(R.string.verification_journal_scope),
            color = JuntaMutedInk,
            style = MaterialTheme.typography.bodySmall,
        )
        journalExportStatusResource(state.exportStatus)?.let { resource ->
            Text(
                stringResource(resource),
                color = JuntaMutedInk,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("verification-journal-export-status")
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        OutlinedButton(
            onClick = onExport,
            enabled = state.canExport,
            shape = JuntaFieldShape,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("verification-journal-export"),
        ) { Text(stringResource(R.string.verification_journal_export)) }
        TextButton(
            onClick = onClear,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("verification-journal-clear"),
        ) { Text(stringResource(R.string.verification_journal_clear)) }
        journalLimits()
    }
}

@Composable
private fun journalEntries(state: VerificationJournalState) {
    val entries = state.snapshot.entries
    if (entries.isEmpty()) {
        Text(
            stringResource(R.string.verification_journal_empty),
            color = JuntaMutedInk,
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale)
            .withZone(ZoneId.systemDefault())
    }
    Column(Modifier.fillMaxWidth().border(1.dp, JuntaHairline, JuntaFieldShape)) {
        entries.forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider(color = JuntaHairline)
            val outcome = when (entry.outcome) {
                LocalOutcome.COMPLETED -> R.string.verification_journal_completed
                LocalOutcome.CANCELLED -> R.string.verification_journal_cancelled
                LocalOutcome.FAILED -> R.string.verification_journal_failed
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp).testTag("verification-journal-row-${entry.sequence}"),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                journalOutcomeIcon(entry.outcome)
                Text(
                    stringResource(
                        R.string.verification_journal_row, entry.sequence, formatter.format(entry.occurredAt),
                        stringResource(R.string.verification_journal_kind_verify), stringResource(outcome),
                    ),
                    color = JuntaInk,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun journalOutcomeIcon(outcome: LocalOutcome) {
    val (icon, tint) = when (outcome) {
        LocalOutcome.COMPLETED -> R.drawable.ic_ui_check_circle to JuntaTeal
        LocalOutcome.CANCELLED -> R.drawable.ic_ui_close to JuntaMutedInk
        LocalOutcome.FAILED -> R.drawable.ic_browser_warning to MaterialTheme.colorScheme.error
    }
    Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
}

/** The session scope stays visible above; the remaining honest limits open on request. */
@Composable
private fun journalLimits() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    TextButton(
        onClick = { expanded = !expanded },
        modifier = Modifier.heightIn(min = 48.dp).testTag("verification-journal-details"),
    ) {
        Text(stringResource(if (expanded) R.string.more_information_hide else R.string.more_information_show))
    }
    if (!expanded) return
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, JuntaFieldShape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (resource in listOf(
            R.string.verification_journal_integrity,
            R.string.verification_journal_copies,
            R.string.verification_journal_provider_warning,
        )) {
            Text(
                stringResource(resource),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
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

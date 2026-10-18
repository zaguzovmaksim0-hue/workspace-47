package dev.junta.firmamobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.feedback.CompatibilityFeedbackReport

@Composable
internal fun CompatibilityFeedbackPane(
    report: CompatibilityFeedbackReport,
    onCopy: (CompatibilityFeedbackReport) -> Boolean,
    onOpenIssue: (CompatibilityFeedbackReport) -> Boolean,
    modifier: Modifier = Modifier,
) {
    var confirmOpen by remember(report) { mutableStateOf(false) }
    var statusResource by remember(report) { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.compatibility_feedback_intro))
        Text(
            text = stringResource(R.string.compatibility_feedback_preview),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = report.body,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.fillMaxWidth().testTag("compatibility-feedback-report"),
        )
        Text(stringResource(R.string.compatibility_feedback_privacy))
        OutlinedButton(
            onClick = {
                statusResource = if (onCopy(report)) R.string.compatibility_feedback_copied
                else R.string.compatibility_feedback_copy_failed
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("compatibility-feedback-copy"),
        ) {
            Text(stringResource(R.string.compatibility_feedback_copy))
        }
        Button(
            onClick = { confirmOpen = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("compatibility-feedback-open"),
        ) {
            Text(stringResource(R.string.compatibility_feedback_open))
        }
        statusResource?.let { message ->
            Text(
                text = stringResource(message),
                modifier = Modifier.testTag("compatibility-feedback-status")
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }

    if (confirmOpen) {
        AlertDialog(
            onDismissRequest = { confirmOpen = false },
            modifier = Modifier.testTag("compatibility-feedback-confirmation"),
            title = { Text(stringResource(R.string.compatibility_feedback_confirm_title)) },
            text = {
                Text(
                    text = stringResource(R.string.compatibility_feedback_confirm_message),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmOpen = false
                        statusResource = if (onOpenIssue(report)) null else R.string.compatibility_feedback_open_failed
                    },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("compatibility-feedback-confirm-open"),
                ) {
                    Text(stringResource(R.string.compatibility_feedback_confirm_open))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmOpen = false },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("compatibility-feedback-cancel"),
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

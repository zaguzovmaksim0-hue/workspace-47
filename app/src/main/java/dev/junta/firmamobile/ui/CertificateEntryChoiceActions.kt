package dev.junta.firmamobile.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.certificate.CertificateEntryChoice
import dev.junta.firmamobile.ui.theme.JuntaInk
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Choice actions for a multi-key file; absent where the panel cannot choose. */
internal class CertificateEntryChoiceActions(
    val onChoose: (CertificateEntryChoice) -> Unit,
    val onCancel: () -> Unit,
)

internal val LocalCertificateEntryChoice = staticCompositionLocalOf<CertificateEntryChoiceActions?> { null }

@Composable
internal fun certificateEntryChooser(state: CertificateUiState.Locked) {
    val actions = LocalCertificateEntryChoice.current
    Column(modifier = Modifier.fillMaxWidth().testTag("certificate-entry-chooser")) {
        Text(
            stringResource(R.string.certificate_entry_choose_title, state.reference.displayName),
            color = JuntaInk,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(8.dp))
        state.choices.forEach { choice ->
            val summary = choice.summary
            OutlinedButton(
                onClick = { actions?.onChoose?.invoke(choice) },
                enabled = actions != null,
                modifier = Modifier.fillMaxWidth().testTag("certificate-entry-${choice.number}"),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(summary.ownerName, style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.certificate_issuer, summary.issuerName))
                    Text(
                        stringResource(
                            R.string.certificate_validity,
                            CHOICE_DATE.format(summary.validFrom),
                            CHOICE_DATE.format(summary.validUntil),
                        ),
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
        JuntaOutlinedAction(
            text = stringResource(R.string.certificate_entry_cancel),
            onClick = { actions?.onCancel?.invoke() },
        )
    }
}

private val CHOICE_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.systemDefault())

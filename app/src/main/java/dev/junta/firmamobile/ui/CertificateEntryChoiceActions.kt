package dev.junta.firmamobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.certificate.CertificateEntryChoice
import dev.junta.firmamobile.ui.theme.JuntaInk
import dev.junta.firmamobile.ui.theme.JuntaMutedInk
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
            stringResource(
                R.string.certificate_entry_choose_title,
                certificateDisplayNameText(state.reference.displayName),
            ),
            color = JuntaInk,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(12.dp))
        state.choices.forEach { choice ->
            key(choice.number) {
                certificateEntryChoiceRow(choice, enabled = actions != null) { actions?.onChoose?.invoke(choice) }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        JuntaOutlinedAction(
            text = stringResource(R.string.certificate_entry_cancel),
            onClick = { actions?.onCancel?.invoke() },
        )
    }
}

/** One identity inside the file: a light key with the holder badge, issuer and validity. */
@Composable
private fun certificateEntryChoiceRow(choice: CertificateEntryChoice, enabled: Boolean, onClick: () -> Unit) {
    val summary = choice.summary
    val owner = certificateDisplayNameText(summary.ownerName)
    FirmaKey(
        onClick = onClick,
        style = FirmaKeyStyle.LIGHT,
        shape = FirmaWideKeyShape,
        modifier = Modifier.fillMaxWidth().testTag("certificate-entry-${choice.number}"),
        enabled = enabled,
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JuntaMonogram(name = owner, size = 40.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(owner, color = JuntaInk, style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.certificate_issuer, certificateDisplayNameText(summary.issuerName)),
                    color = JuntaMutedInk,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    stringResource(
                        R.string.certificate_validity,
                        CHOICE_DATE.format(summary.validFrom),
                        CHOICE_DATE.format(summary.validUntil),
                    ),
                    color = JuntaMutedInk,
                    style = firmaMono(12.sp),
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_ui_chevron_right),
                contentDescription = null,
                tint = JuntaMutedInk,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private val CHOICE_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.systemDefault())

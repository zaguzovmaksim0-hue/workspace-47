package dev.junta.firmamobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.certificate.CertificateUnlockRetention
import dev.junta.firmamobile.ui.theme.JuntaHairline
import dev.junta.firmamobile.ui.theme.JuntaInk
import dev.junta.firmamobile.ui.theme.JuntaPaperElevated

// Compose UI entry points use PascalCase by convention.
@Suppress("FunctionNaming")
@Composable
internal fun CertificateUnlockRetentionSelector(
    selected: CertificateUnlockRetention,
    enabled: Boolean,
    onSelected: (CertificateUnlockRetention) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("certificate-retention-menu"),
            shape = JuntaFieldShape,
            border = BorderStroke(1.dp, JuntaHairline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = JuntaInk),
            contentPadding = PaddingValues(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_ui_history),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.certificate_retention_label, stringResource(selected.labelResource())),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                painter = painterResource(R.drawable.ic_ui_expand_more),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
            shape = JuntaFieldShape,
            containerColor = JuntaPaperElevated,
        ) {
            CertificateUnlockRetention.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelResource())) },
                    modifier = Modifier.testTag("certificate-retention-${option.name}"),
                    onClick = { onSelected(option); expanded = false },
                )
            }
        }
    }
}

private fun CertificateUnlockRetention.labelResource(): Int = when (this) {
    CertificateUnlockRetention.FIFTEEN_MINUTES -> R.string.certificate_retention_fifteen_minutes
    CertificateUnlockRetention.ONE_HOUR -> R.string.certificate_retention_one_hour
    CertificateUnlockRetention.ONE_DAY -> R.string.certificate_retention_one_day
}

package dev.junta.firmamobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import dev.junta.firmamobile.R
import dev.junta.firmamobile.certificate.CertificateUnlockRetention

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
        TextButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.testTag("certificate-retention-menu"),
        ) {
            Text(stringResource(R.string.certificate_retention_label, stringResource(selected.labelResource())))
        }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
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

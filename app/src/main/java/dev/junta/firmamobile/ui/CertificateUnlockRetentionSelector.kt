package dev.junta.firmamobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.certificate.CertificateUnlockRetention
import dev.junta.firmamobile.ui.theme.JuntaInk
import dev.junta.firmamobile.ui.theme.JuntaMutedInk
import dev.junta.firmamobile.ui.theme.JuntaPaperElevated

private const val RETENTION_TINT_ALPHA = 0.12f
private const val RETENTION_DISABLED_ALPHA = 0.5f

/** Unlock period as a sky-blue capsule with a clock badge; the list opens below it. */
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
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .alpha(if (enabled) 1f else RETENTION_DISABLED_ALPHA)
                .clip(CircleShape)
                .background(JuntaTone.SKY.accent.copy(alpha = RETENTION_TINT_ALPHA))
                .clickable(enabled = enabled, role = Role.Button) { expanded = true }
                .padding(start = 7.dp, end = 14.dp)
                .testTag("certificate-retention-menu"),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { RetentionCapsuleContent(selected) }
        DropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
            shape = JuntaFieldShape,
            containerColor = JuntaPaperElevated,
        ) {
            CertificateUnlockRetention.entries.forEach { option ->
                RetentionMenuItem(option = option, selected = option == selected) {
                    onSelected(option)
                    expanded = false
                }
            }
        }
    }
}

/** Clock badge, current period and chevron, laid out by the capsule row. */
@Suppress("FunctionNaming")
@Composable
private fun RetentionCapsuleContent(selected: CertificateUnlockRetention) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(JuntaTone.SKY.brush, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_ui_history),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(17.dp),
        )
    }
    Text(
        text = stringResource(R.string.certificate_retention_label, stringResource(selected.labelResource())),
        color = JuntaInk,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
    )
    Icon(
        painter = painterResource(R.drawable.ic_ui_expand_more),
        contentDescription = null,
        tint = JuntaMutedInk,
        modifier = Modifier.size(18.dp),
    )
}

@Suppress("FunctionNaming")
@Composable
private fun RetentionMenuItem(option: CertificateUnlockRetention, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                text = stringResource(option.labelResource()),
                color = JuntaInk,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        },
        trailingIcon = if (selected) {
            {
                Icon(
                    painter = painterResource(R.drawable.ic_ui_check_circle),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            null
        },
        modifier = Modifier.testTag("certificate-retention-${option.name}"),
        onClick = onClick,
    )
}

private fun CertificateUnlockRetention.labelResource(): Int = when (this) {
    CertificateUnlockRetention.FIFTEEN_MINUTES -> R.string.certificate_retention_fifteen_minutes
    CertificateUnlockRetention.ONE_HOUR -> R.string.certificate_retention_one_hour
    CertificateUnlockRetention.ONE_DAY -> R.string.certificate_retention_one_day
}

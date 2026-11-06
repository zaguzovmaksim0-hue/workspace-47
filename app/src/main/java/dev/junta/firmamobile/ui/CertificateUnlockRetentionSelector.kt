package dev.junta.firmamobile.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.certificate.CertificateUnlockRetention
import dev.junta.firmamobile.ui.theme.JuntaInk
import dev.junta.firmamobile.ui.theme.JuntaMutedInk
import kotlin.math.roundToInt

private const val RETENTION_DISABLED_ALPHA = 0.5f
private const val THUMB_DAMPING = 0.78f
private val TrackShape = RoundedCornerShape(15.dp)
private val ThumbShape = RoundedCornerShape(11.dp)

/**
 * Unlock period as a three-position slide switch moulded into the body. Each position is a
 * radio button; the light thumb slides to the chosen one and the caption repeats the choice.
 */
// Compose UI entry points use PascalCase by convention.
@Suppress("FunctionNaming")
@Composable
internal fun CertificateUnlockRetentionSelector(
    selected: CertificateUnlockRetention,
    enabled: Boolean,
    onSelected: (CertificateUnlockRetention) -> Unit,
) {
    val choices = CertificateUnlockRetention.entries
    val position by animateFloatAsState(
        targetValue = choices.indexOf(selected).toFloat(),
        animationSpec = spring(dampingRatio = THUMB_DAMPING, stiffness = Spring.StiffnessMediumLow),
        label = "retention-thumb",
    )
    val haptics = LocalHapticFeedback.current
    Column(modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else RETENTION_DISABLED_ALPHA)) {
        Text(
            text = stringResource(R.string.certificate_retention_label, stringResource(selected.labelResource())),
            color = JuntaMutedInk,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .firmaBodyRecess(TrackShape, depth = 3.dp)
                .padding(4.dp)
                .selectableGroup()
                .testTag("certificate-retention-menu"),
        ) {
            RetentionThumb(position = position, cells = choices.size, enabled = enabled)
            Row(modifier = Modifier.fillMaxSize()) {
                choices.forEach { option ->
                    RetentionPosition(option = option, chosen = option == selected, enabled = enabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelected(option)
                    }
                }
            }
        }
    }
}

private fun CertificateUnlockRetention.labelResource(): Int = when (this) {
    CertificateUnlockRetention.FIFTEEN_MINUTES -> R.string.certificate_retention_fifteen_minutes
    CertificateUnlockRetention.ONE_HOUR -> R.string.certificate_retention_one_hour
    CertificateUnlockRetention.ONE_DAY -> R.string.certificate_retention_one_day
}

/** The light thumb, one cell wide, slid to [position] (in cells) along the track. */
@Suppress("FunctionNaming")
@Composable
private fun RetentionThumb(position: Float, cells: Int, enabled: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val cell = constraints.maxWidth / cells
                val thumb = measurable.measure(Constraints.fixed(cell, constraints.maxHeight))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    thumb.placeRelative((position * cell).roundToInt(), 0)
                }
            },
    ) {
        FirmaKey(
            onClick = null,
            style = FirmaKeyStyle.LIGHT,
            shape = ThumbShape,
            modifier = Modifier.fillMaxSize(),
            depth = 2.dp,
            enabled = enabled,
            role = null,
        ) {}
    }
}

/** One switch position: a radio button printed with its period; [onPick] runs only on a change. */
@Suppress("FunctionNaming")
@Composable
private fun RowScope.RetentionPosition(
    option: CertificateUnlockRetention,
    chosen: Boolean,
    enabled: Boolean,
    onPick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(
                selected = chosen,
                enabled = enabled,
                role = Role.RadioButton,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { if (!chosen) onPick() }
            .padding(bottom = 2.dp)
            .testTag("certificate-retention-${option.name}"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(option.labelResource()),
            color = if (chosen) JuntaInk else JuntaMutedInk,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (chosen) FontWeight.Medium else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

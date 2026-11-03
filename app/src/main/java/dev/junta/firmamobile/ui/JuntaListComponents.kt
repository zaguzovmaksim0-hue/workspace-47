package dev.junta.firmamobile.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.ui.theme.Firma
import dev.junta.firmamobile.ui.theme.JuntaHairline
import dev.junta.firmamobile.ui.theme.JuntaInk
import dev.junta.firmamobile.ui.theme.JuntaMutedInk
import dev.junta.firmamobile.ui.theme.JuntaPaperElevated
import kotlin.math.roundToInt

private const val DISABLED_ROW_ALPHA = 0.5f
private const val KEYPAD_COLUMNS = 4
private const val NARROW_KEYPAD_COLUMNS = 2
private val KeypadKeySize = 60.dp
private val KeypadMinColumn = 68.dp

/** Grouped list plate; rows inside are separated by [JuntaListDivider]. */
@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
internal fun JuntaListPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(JuntaPanelShape)
            .background(JuntaPaperElevated)
            .border(1.dp, JuntaHairline, JuntaPanelShape)
            .padding(vertical = 4.dp),
        content = content,
    )
}

@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
internal fun JuntaListDivider() {
    FirmaDivider(modifier = Modifier.padding(start = 70.dp, end = 16.dp))
}

/** One full-width, tappable action with an engraved glyph, optional explanation and chevron. */
@Suppress("LongParameterList", "FunctionNaming")
@Composable
internal fun JuntaToolRow(
    @DrawableRes iconRes: Int,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    tone: JuntaTone = JuntaTone.VIOLET,
) {
    val actionAlpha = if (enabled) 1f else DISABLED_ROW_ALPHA
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        JuntaIconBubble(iconRes = iconRes, tone = tone, modifier = Modifier.alpha(actionAlpha), size = 40.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                modifier = Modifier.alpha(actionAlpha),
                color = JuntaInk,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = JuntaMutedInk,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_ui_chevron_right),
            contentDescription = null,
            tint = JuntaMutedInk,
            modifier = Modifier
                .size(20.dp)
                .alpha(actionAlpha),
        )
    }
}

/**
 * A round graphite key of the home keypad with its short printed caption. The full [title]
 * and the optional [subtitle] are what screen readers announce; the caption is only printed.
 */
@Suppress("LongParameterList", "FunctionNaming")
@Composable
internal fun JuntaToolTile(
    @DrawableRes iconRes: Int,
    title: String,
    caption: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val ink = if (enabled) Firma.keyGraphiteInk else JuntaMutedInk
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                contentDescription = title
                if (subtitle != null) stateDescription = subtitle
            }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FirmaKey(
            onClick = null,
            style = FirmaKeyStyle.GRAPHITE,
            shape = CircleShape,
            modifier = Modifier.size(width = KeypadKeySize, height = KeypadKeySize + 3.dp),
            depth = 3.dp,
            enabled = enabled,
            interactionSource = interaction,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        ToolCaption(caption, enabled)
    }
}

/** The caption printed under a keypad key; a small lock marks a key that is not available yet. */
@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
private fun ToolCaption(caption: String, enabled: Boolean) {
    Row(
        modifier = Modifier.clearAndSetSemantics {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!enabled) {
            Icon(
                painter = painterResource(R.drawable.ic_ui_lock),
                contentDescription = null,
                tint = JuntaMutedInk,
                modifier = Modifier.padding(end = 4.dp).size(13.dp),
            )
        }
        Text(
            text = caption,
            color = if (enabled) Firma.keyCaption else JuntaMutedInk,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/**
 * The keypad: four equal columns on ordinary phones, two when the width cannot hold four keys.
 * Each row takes the height of its tallest key so captions stay on one baseline.
 */
@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
internal fun JuntaTileGrid(
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier.fillMaxWidth()) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
        // Larger text needs wider columns for the printed captions, so it falls back to two sooner.
        val minColumn = (KeypadMinColumn.toPx() * fontScale.coerceAtLeast(1f)).roundToInt()
        val columns = if (width >= minColumn * KEYPAD_COLUMNS + gap * (KEYPAD_COLUMNS - 1)) {
            KEYPAD_COLUMNS
        } else {
            NARROW_KEYPAD_COLUMNS
        }
        val column = ((width - gap * (columns - 1)) / columns).coerceAtLeast(0)
        val rows = measurables.chunked(columns)
        val placeables = rows.map { row ->
            val height = row.maxOf { it.maxIntrinsicHeight(column) }
            row.map { it.measure(Constraints.fixed(column, height)) }
        }
        val rowGap = gap * 2
        val total = placeables.sumOf { row -> row.first().height } + rowGap * (placeables.size - 1).coerceAtLeast(0)
        layout(width, total) {
            var y = 0
            placeables.forEach { row ->
                row.forEachIndexed { index, placeable -> placeable.placeRelative(index * (column + gap), y) }
                y += row.first().height + rowGap
            }
        }
    }
}

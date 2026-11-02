package dev.junta.firmamobile.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.ui.theme.JuntaHairline
import dev.junta.firmamobile.ui.theme.JuntaInk
import dev.junta.firmamobile.ui.theme.JuntaMutedInk
import dev.junta.firmamobile.ui.theme.JuntaPaperElevated

private const val DISABLED_ROW_ALPHA = 0.5f
private const val TILE_GLOW_ALPHA = 0.16f
private const val TILE_GLOW_RADIUS_RATIO = 0.55f
private val TileShape = RoundedCornerShape(26.dp)

/** Heading above a group of home actions, marked by a short mosaic-ribbon bar. */
@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
internal fun JuntaSectionLabel(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 24.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 5.dp, height = 18.dp)
                .background(Brush.verticalGradient(JuntaGradients.Ribbon), CircleShape),
        )
        Text(
            text = text,
            modifier = Modifier.semantics { heading() },
            color = JuntaInk,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** White grouped list surface; rows inside are separated by [JuntaListDivider]. */
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
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp, end = 16.dp),
        color = JuntaHairline,
        thickness = 1.dp,
    )
}

/** One full-width, tappable action with a gradient glyph, optional explanation and chevron. */
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
        JuntaIconBubble(iconRes = iconRes, tone = tone, modifier = Modifier.alpha(actionAlpha), size = 42.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                modifier = Modifier.alpha(actionAlpha),
                color = JuntaInk,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
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

/** Square-ish home tile: gradient glyph, title, optional hint and a soft pool of its own colour. */
@Suppress("LongParameterList", "FunctionNaming")
@Composable
internal fun JuntaToolTile(
    @DrawableRes iconRes: Int,
    title: String,
    onClick: () -> Unit,
    tone: JuntaTone,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val actionAlpha = if (enabled) 1f else DISABLED_ROW_ALPHA
    val glow = tone.light
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 128.dp)
            .juntaPressScale(interaction)
            .clip(TileShape)
            .background(JuntaPaperElevated)
            .drawBehind {
                drawGlow(
                    color = glow,
                    center = Offset(size.width, 0f),
                    radius = size.width * TILE_GLOW_RADIUS_RATIO,
                    alpha = TILE_GLOW_ALPHA,
                )
            }
            .border(1.dp, JuntaHairline, TileShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(14.dp),
    ) {
        JuntaIconBubble(iconRes = iconRes, tone = tone, modifier = Modifier.alpha(actionAlpha))
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            modifier = Modifier.alpha(actionAlpha),
            color = JuntaInk,
            style = MaterialTheme.typography.titleSmall,
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = JuntaMutedInk,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Two equal columns; each row takes the height of its tallest tile so the mosaic stays aligned. */
@Suppress("FunctionNaming") // Compose UI naming convention.
@Composable
internal fun JuntaTileGrid(
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier.fillMaxWidth()) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
        val column = ((width - gap) / 2).coerceAtLeast(0)
        val rows = measurables.chunked(2)
        val placeables = rows.map { row ->
            val height = row.maxOf { it.maxIntrinsicHeight(column) }
            row.map { it.measure(Constraints.fixed(column, height)) }
        }
        val total = placeables.sumOf { row -> row.first().height } + gap * (placeables.size - 1).coerceAtLeast(0)
        layout(width, total) {
            var y = 0
            placeables.forEach { row ->
                row.forEachIndexed { index, placeable -> placeable.placeRelative(index * (column + gap), y) }
                y += row.first().height + gap
            }
        }
    }
}

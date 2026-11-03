package dev.junta.firmamobile.catalog

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.ui.JuntaCountPill
import dev.junta.firmamobile.ui.JuntaIconBubble
import dev.junta.firmamobile.ui.JuntaMonogram
import dev.junta.firmamobile.ui.JuntaPanelShape
import dev.junta.firmamobile.ui.JuntaTone
import dev.junta.firmamobile.ui.accent
import dev.junta.firmamobile.ui.drawGlow
import dev.junta.firmamobile.ui.drawSparkle
import dev.junta.firmamobile.ui.juntaPressScale
import dev.junta.firmamobile.ui.light
import dev.junta.firmamobile.ui.theme.JuntaHairline
import dev.junta.firmamobile.ui.theme.JuntaInk
import dev.junta.firmamobile.ui.theme.JuntaMutedInk
import dev.junta.firmamobile.ui.theme.JuntaPaperElevated

private val SectionCardShape = RoundedCornerShape(24.dp)
private const val EXPANDED_DEGREES = 180f
private const val SECTION_SHADOW_ALPHA = 0.14f
private const val TIP_TINT_ALPHA = 0.08f
private const val TIP_EDGE_ALPHA = 0.2f
private const val LOADING_PANEL_ALPHA = 0.72f
private const val GHOST_ALPHA = 0.55f
private const val GHOST_CARDS = 2

/** Section heading: a tappable card for the long territorial lists, a quiet labelled row otherwise. */
@Suppress("FunctionNaming")
@Composable
internal fun CatalogSectionHeader(
    section: PortalCatalogSection,
    title: String,
    expanded: Boolean,
    onToggle: (() -> Unit)?,
) {
    val serviceCount = section.items.size
    val count = pluralStringResource(R.plurals.catalog_section_service_count, serviceCount, serviceCount)
    if (onToggle == null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, top = 14.dp, end = 2.dp)
                .semantics(mergeDescendants = true) { heading() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CatalogSectionBadge(section, title, size = 32.dp, iconSize = 17.dp)
            Text(
                text = title,
                modifier = Modifier.weight(1f, fill = false),
                color = JuntaInk,
                style = MaterialTheme.typography.titleMedium,
            )
            JuntaCountPill(text = count)
        }
    } else {
        CatalogCollapsibleHeader(section, title, count, expanded, onToggle)
    }
}

@Suppress("FunctionNaming")
@Composable
private fun CatalogCollapsibleHeader(
    section: PortalCatalogSection,
    title: String,
    count: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val expandedState = stringResource(R.string.catalog_section_expanded)
    val collapsedState = stringResource(R.string.catalog_section_collapsed)
    val rotation by animateFloatAsState(if (expanded) EXPANDED_DEGREES else 0f, label = "section-chevron")
    val chevronBackground by animateColorAsState(
        if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
        label = "section-chevron-background",
    )
    val chevronTint by animateColorAsState(
        if (expanded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
        label = "section-chevron-tint",
    )
    val interaction = remember { MutableInteractionSource() }
    val shadow = JuntaTone.VIOLET.accent.copy(alpha = SECTION_SHADOW_ALPHA)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .juntaPressScale(interaction)
            .shadow(elevation = 6.dp, shape = SectionCardShape, ambientColor = shadow, spotColor = shadow)
            .clip(SectionCardShape)
            .background(JuntaPaperElevated)
            .border(1.dp, JuntaHairline, SectionCardShape)
            .semantics(mergeDescendants = true) {
                heading()
                stateDescription = if (expanded) expandedState else collapsedState
            }
            .clickable(interactionSource = interaction, indication = ripple(), role = Role.Button, onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CatalogSectionBadge(section, title, size = 44.dp, iconSize = 22.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = title,
                color = JuntaInk,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            JuntaCountPill(text = count)
        }
        Box(
            modifier = Modifier.size(34.dp).background(chevronBackground, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_ui_expand_more),
                contentDescription = null,
                tint = chevronTint,
                modifier = Modifier.size(22.dp).rotate(rotation),
            )
        }
    }
}

/** Each kind of section owns a colour; regions get their own initials tile. */
@Suppress("FunctionNaming")
@Composable
private fun CatalogSectionBadge(section: PortalCatalogSection, title: String, size: Dp, iconSize: Dp) {
    val (iconRes, tone) = when (section.kind) {
        PortalCatalogSectionKind.FAVORITES -> R.drawable.ic_ui_star to JuntaTone.SAFFRON
        PortalCatalogSectionKind.RECENT -> R.drawable.ic_ui_history to JuntaTone.SKY
        PortalCatalogSectionKind.SELECTED_REGION -> R.drawable.ic_ui_location to JuntaTone.CORAL
        PortalCatalogSectionKind.NATIONAL -> R.drawable.ic_ui_shield to JuntaTone.VIOLET
        PortalCatalogSectionKind.OTHER_REGIONS -> R.drawable.ic_ui_globe to JuntaTone.MINT
        PortalCatalogSectionKind.REGION -> {
            JuntaMonogram(name = title, size = size)
            return
        }
    }
    JuntaIconBubble(iconRes = iconRes, tone = tone, size = size, iconSize = iconSize)
}

/** Loading row plus two ghost cards, so the list does not jump when the catalog arrives. */
@Suppress("FunctionNaming")
@Composable
internal fun CatalogLoadingNotice() {
    val ghost = JuntaPaperElevated.copy(alpha = GHOST_ALPHA)
    val bar = JuntaHairline
    Column(
        modifier = Modifier.fillMaxWidth().testTag("catalog-loading"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SectionCardShape)
                .background(JuntaPaperElevated.copy(alpha = LOADING_PANEL_ALPHA))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer,
            )
            Text(
                text = stringResource(R.string.catalog_loading),
                color = JuntaMutedInk,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        repeat(GHOST_CARDS) {
            Canvas(modifier = Modifier.fillMaxWidth().height(112.dp).clearAndSetSemantics {}) {
                drawGhostCard(ghost, bar)
            }
        }
    }
}

@Suppress("MagicNumber") // Placeholder geometry in dp.
private fun DrawScope.drawGhostCard(card: Color, bar: Color) {
    val corner = CornerRadius(28.dp.toPx())
    drawRoundRect(card, cornerRadius = corner)
    val pad = 18.dp.toPx()
    val tile = 46.dp.toPx()
    drawRoundRect(bar, topLeft = Offset(pad, pad), size = Size(tile, tile), cornerRadius = CornerRadius(tile * 0.32f))
    val textStart = pad + tile + 14.dp.toPx()
    val lineHeight = 12.dp.toPx()
    val radius = CornerRadius(lineHeight / 2)
    val widths = listOf(0.72f, 0.46f, 0.3f)
    widths.forEachIndexed { index, fraction ->
        drawRoundRect(
            bar,
            topLeft = Offset(textStart, pad + index * 24.dp.toPx()),
            size = Size((size.width - textStart - pad) * fraction, lineHeight),
            cornerRadius = radius,
        )
    }
}

/** Friendly empty state: a magnifier tile with a few sparkles. */
@Suppress("FunctionNaming")
@Composable
internal fun CatalogEmptyNotice(title: String, text: String) {
    val sparkle = JuntaTone.SAFFRON.light
    val glow = JuntaTone.SKY.light
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(JuntaPanelShape)
            .background(JuntaPaperElevated)
            .border(1.dp, JuntaHairline, JuntaPanelShape)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .drawBehind { drawEmptyDecor(glow, sparkle) },
            contentAlignment = Alignment.Center,
        ) {
            JuntaIconBubble(iconRes = R.drawable.ic_ui_search, tone = JuntaTone.SKY, size = 58.dp, iconSize = 28.dp)
        }
        Text(text = title, color = JuntaInk, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            text = text,
            color = JuntaMutedInk,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Suppress("MagicNumber") // Decorative geometry as fractions of the tile box.
private fun DrawScope.drawEmptyDecor(glow: Color, sparkle: Color) {
    drawGlow(glow, center, size.minDimension * 0.6f, alpha = 0.35f)
    drawSparkle(Offset(size.width * 0.86f, size.height * 0.18f), 7.dp.toPx(), sparkle)
    drawSparkle(Offset(size.width * 0.1f, size.height * 0.72f), 5.dp.toPx(), JuntaTone.ROSE.light)
    drawSparkle(Offset(size.width * 0.92f, size.height * 0.82f), 3.5.dp.toPx(), JuntaTone.MINT.light)
}

/** Closing tip card: universal AutoFirma support and the long-press hint. */
@Suppress("FunctionNaming")
@Composable
internal fun CatalogFooterTips() {
    val tint = JuntaTone.SKY.accent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SectionCardShape)
            .background(tint.copy(alpha = TIP_TINT_ALPHA))
            .border(1.dp, tint.copy(alpha = TIP_EDGE_ALPHA), SectionCardShape)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        JuntaIconBubble(iconRes = R.drawable.ic_ui_info, tone = JuntaTone.SKY, size = 34.dp, iconSize = 18.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.catalog_universal_afirma_copy),
                modifier = Modifier.fillMaxWidth().testTag("catalog-universal-afirma"),
                color = JuntaInk,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(R.string.catalog_options_hint),
                color = JuntaMutedInk,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

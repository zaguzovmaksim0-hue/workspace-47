package dev.junta.firmamobile.catalog

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.ui.FirmaDivider
import dev.junta.firmamobile.ui.FirmaEngraved
import dev.junta.firmamobile.ui.FirmaKey
import dev.junta.firmamobile.ui.FirmaKeyStyle
import dev.junta.firmamobile.ui.FirmaLed
import dev.junta.firmamobile.ui.FirmaLedState
import dev.junta.firmamobile.ui.FirmaRuleLayout
import dev.junta.firmamobile.ui.FirmaSectionRule
import dev.junta.firmamobile.ui.firmaBodyRecess
import dev.junta.firmamobile.ui.firmaMono
import dev.junta.firmamobile.ui.theme.Firma
import dev.junta.firmamobile.ui.theme.JuntaHairline
import dev.junta.firmamobile.ui.theme.JuntaInk
import dev.junta.firmamobile.ui.theme.JuntaMutedInk

private const val EXPANDED_DEGREES = 180f
private const val GHOST_ROWS = 3
private val NoticeShape = RoundedCornerShape(16.dp)

/**
 * Section rule of the list: the title, a hairline to the edge and the number of services.
 * The long territorial lists fold; their whole rule is the target and the small key shows it.
 */
@Suppress("FunctionNaming")
@Composable
internal fun CatalogSectionHeader(
    section: PortalCatalogSection,
    title: String,
    expanded: Boolean,
    onToggle: (() -> Unit)?,
) {
    val serviceCount = section.items.size
    val spokenCount = pluralStringResource(R.plurals.catalog_section_service_count, serviceCount, serviceCount)
    if (onToggle == null) {
        FirmaSectionRule(
            label = title,
            count = serviceCount.toString(),
            countSpoken = spokenCount,
            modifier = Modifier.padding(top = 24.dp, bottom = 2.dp).heightIn(min = 32.dp),
        )
    } else {
        CatalogCollapsibleHeader(title, serviceCount, spokenCount, expanded, onToggle)
    }
}

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun CatalogCollapsibleHeader(
    title: String,
    serviceCount: Int,
    spokenCount: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val expandedState = stringResource(R.string.catalog_section_expanded)
    val collapsedState = stringResource(R.string.catalog_section_collapsed)
    val rotation by animateFloatAsState(if (expanded) EXPANDED_DEGREES else 0f, label = "section-chevron")
    val interaction = remember { MutableInteractionSource() }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        FirmaRuleLayout(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .semantics(mergeDescendants = true) {
                    heading()
                    stateDescription = if (expanded) expandedState else collapsedState
                }
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onToggle)
                .padding(vertical = 6.dp),
            label = {
                Text(
                    text = title,
                    color = JuntaInk,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = serviceCount.toString(),
                        color = JuntaMutedInk,
                        style = firmaMono(14.sp),
                        modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(spokenCount) },
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    FirmaKey(
                        onClick = null,
                        style = FirmaKeyStyle.LIGHT,
                        shape = CircleShape,
                        modifier = Modifier.size(width = 36.dp, height = 39.dp),
                        interactionSource = interaction,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ui_expand_more),
                            contentDescription = null,
                            tint = Firma.keyLightInk,
                            modifier = Modifier.size(22.dp).rotate(rotation),
                        )
                    }
                }
            },
        )
        if (!expanded) FirmaDivider()
    }
}

/** Busy LED and a few ghost rows, so the list does not jump when the catalog arrives. */
@Suppress("FunctionNaming")
@Composable
internal fun CatalogLoadingNotice(modifier: Modifier = Modifier) {
    val bar = JuntaHairline
    val lens = Firma.ledOff
    Column(modifier = modifier.fillMaxWidth().testTag("catalog-loading")) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FirmaLed(state = FirmaLedState.BUSY)
            Text(
                text = stringResource(R.string.catalog_loading),
                color = JuntaMutedInk,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        repeat(GHOST_ROWS) { index ->
            Canvas(modifier = Modifier.fillMaxWidth().height(86.dp).clearAndSetSemantics {}) {
                drawGhostRow(bar, lens, widthFraction = GhostWidths[index % GhostWidths.size])
            }
            FirmaDivider()
        }
    }
}

@Suppress("MagicNumber") // Placeholder bar lengths, as fractions of the row.
private val GhostWidths = listOf(0.7f, 0.52f, 0.62f)

@Suppress("MagicNumber") // Placeholder geometry in dp.
private fun DrawScope.drawGhostRow(bar: Color, lens: Color, widthFraction: Float) {
    val ledRadius = 4.5.dp.toPx()
    val top = 18.dp.toPx()
    drawCircle(lens, radius = ledRadius, center = Offset(2.dp.toPx() + ledRadius, top + 7.dp.toPx()))
    val textStart = 2.dp.toPx() + ledRadius * 2 + 16.dp.toPx()
    val starRadius = 19.dp.toPx()
    val textWidth = size.width - textStart - starRadius * 2 - 24.dp.toPx()
    val lines = listOf(widthFraction to 13.dp, widthFraction * 0.7f to 10.dp, widthFraction * 0.45f to 9.dp)
    var y = top
    lines.forEach { (fraction, height) ->
        val h = height.toPx()
        drawRoundRect(
            bar,
            topLeft = Offset(textStart, y),
            size = Size(textWidth * fraction, h),
            cornerRadius = CornerRadius(h / 2),
        )
        y += h + 9.dp.toPx()
    }
    drawCircle(bar, radius = starRadius, center = Offset(size.width - starRadius - 5.dp.toPx(), size.height / 2))
}

/** Nothing matched: an engraved magnifier over a short explanation, in a shallow recess. */
@Suppress("FunctionNaming")
@Composable
internal fun CatalogEmptyNotice(title: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .firmaBodyRecess(NoticeShape, depth = 3.dp)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FirmaEngraved(size = 52.dp) {
            Icon(
                painter = painterResource(R.drawable.ic_ui_search),
                contentDescription = null,
                tint = JuntaMutedInk,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = title, color = JuntaInk, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            text = text,
            color = JuntaMutedInk,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

/** The legend of the LEDs, then the two printed notes: universal AutoFirma support and long-press. */
@Suppress("FunctionNaming")
@Composable
internal fun CatalogFooterTips(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            CatalogLegendItem(FirmaLedState.ON, stringResource(R.string.catalog_legend_verified))
            CatalogLegendItem(FirmaLedState.OFF, stringResource(R.string.catalog_legend_unverified))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_ui_info),
                contentDescription = null,
                tint = JuntaMutedInk,
                modifier = Modifier.padding(top = 1.dp).size(18.dp),
            )
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
}

@Suppress("FunctionNaming")
@Composable
private fun CatalogLegendItem(state: FirmaLedState, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FirmaLed(state = state, size = 8.dp, animate = false)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, color = JuntaMutedInk, style = MaterialTheme.typography.bodySmall, fontSize = 12.5.sp)
    }
}

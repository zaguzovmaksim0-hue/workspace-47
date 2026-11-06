package dev.junta.firmamobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.junta.firmamobile.ui.theme.FirmaMono
import dev.junta.firmamobile.ui.theme.JuntaHairline
import dev.junta.firmamobile.ui.theme.JuntaInk
import dev.junta.firmamobile.ui.theme.JuntaMutedInk

/** Monospaced "printed" text: file names, domains, counters and dates. */
internal fun firmaMono(size: TextUnit, letterSpacing: TextUnit = 0.sp): TextStyle = TextStyle(
    fontFamily = FirmaMono,
    fontSize = size,
    letterSpacing = letterSpacing,
)

/** Section rule of the device: a small label, a hairline to the edge and a monospaced count. */
@Suppress("FunctionNaming")
@Composable
internal fun FirmaSectionRule(
    label: String,
    modifier: Modifier = Modifier,
    count: String? = null,
    countSpoken: String? = null,
    heading: Boolean = true,
) {
    FirmaRuleLayout(
        modifier = modifier
            .fillMaxWidth()
            .then(if (heading) Modifier.semantics(mergeDescendants = true) { heading() } else Modifier),
        label = {
            Text(
                text = label,
                color = JuntaMutedInk,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailing = count?.let { value ->
            {
                Text(
                    text = value,
                    color = JuntaInk,
                    style = firmaMono(13.sp),
                    modifier = if (countSpoken != null) {
                        Modifier.clearAndSetSemantics { text = AnnotatedString(countSpoken) }
                    } else {
                        Modifier
                    },
                )
            }
        },
    )
}

/**
 * Label, hairline and an optional trailing slot pinned to the end edge.
 *
 * The trailing slot is measured first so it never gets squeezed; the label keeps its natural width up to
 * what is left, and the hairline fills whatever remains between them.
 */
@Suppress("FunctionNaming")
@Composable
internal fun FirmaRuleLayout(
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    gap: Dp = 12.dp,
) {
    val hairline = JuntaHairline
    Layout(
        modifier = modifier,
        content = {
            Box { label() }
            Box(modifier = Modifier.drawBehind { drawRect(hairline) })
            if (trailing != null) Box { trailing() }
        },
    ) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val minRulePx = MinRuleWidth.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val trailingPlaceable = measurables.getOrNull(2)?.measure(
            if (constraints.hasBoundedWidth) loose.copy(maxWidth = constraints.maxWidth / 2) else loose,
        )
        val trailingSpace = trailingPlaceable?.let { it.width + gapPx } ?: 0
        val labelPlaceable = measurables[0].measure(
            if (constraints.hasBoundedWidth) {
                loose.copy(maxWidth = (constraints.maxWidth - trailingSpace - gapPx - minRulePx).coerceAtLeast(0))
            } else {
                loose
            },
        )
        val width = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            labelPlaceable.width + gapPx + minRulePx + trailingSpace
        }
        val ruleWidth = (width - labelPlaceable.width - gapPx - trailingSpace).coerceAtLeast(0)
        val rulePlaceable = measurables[1].measure(Constraints.fixed(ruleWidth, 1.dp.roundToPx().coerceAtLeast(1)))
        val height = maxOf(labelPlaceable.height, trailingPlaceable?.height ?: 0, rulePlaceable.height)
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            labelPlaceable.placeRelative(0, (height - labelPlaceable.height) / 2)
            rulePlaceable.placeRelative(labelPlaceable.width + gapPx, (height - rulePlaceable.height) / 2)
            trailingPlaceable?.placeRelative(width - trailingPlaceable.width, (height - trailingPlaceable.height) / 2)
        }
    }
}

private val MinRuleWidth = 16.dp

/** One-pixel rule in the body's hairline colour. */
@Composable
internal fun Modifier.firmaHairline(): Modifier {
    val color = JuntaHairline
    return drawBehind { drawRect(color) }
}

/** Thin full-width divider between list rows. */
@Suppress("FunctionNaming")
@Composable
internal fun FirmaDivider(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(1.dp).firmaHairline())
}

package dev.junta.firmamobile.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import dev.junta.firmamobile.ui.theme.Firma

private const val PRESSED_SCALE = 0.98f

/** The plastic body: a flat warm surface with a barely visible top-to-bottom falloff of light. */
@Suppress("FunctionNaming")
@Composable
internal fun JuntaBackdrop(modifier: Modifier = Modifier) {
    val top = Firma.bodyTop
    val bottom = Firma.bodyBottom
    Canvas(modifier = modifier) {
        drawRect(Brush.verticalGradient(listOf(top, bottom)))
    }
}

/** Gentle press feedback for custom tappable surfaces; layout and semantics bounds stay unchanged. */
internal fun Modifier.juntaPressScale(interactionSource: InteractionSource): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (pressed) PRESSED_SCALE else 1f, label = "press-scale")
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Pulls a block up over the previous one, e.g. the card's lower edge into the reader slot. */
internal fun Modifier.juntaOverlapAbove(overlap: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = overlap.roundToPx().coerceIn(0, placeable.height)
    layout(placeable.width, placeable.height - shift) {
        placeable.place(0, -shift)
    }
}

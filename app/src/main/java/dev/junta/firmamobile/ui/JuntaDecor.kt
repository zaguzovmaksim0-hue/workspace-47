package dev.junta.firmamobile.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.ui.theme.JuntaPaper
import kotlin.math.abs

/** Mosaic accent tones: fixed sRGB gradient stops shared by bubbles, tiles, seals and monograms. */
@Suppress("MagicNumber") // Fixed sRGB brand stops.
internal enum class JuntaTone(val start: Long, val end: Long) {
    CORAL(0xFFFF8A5C, 0xFFF2415E),
    MINT(0xFF2DD4BF, 0xFF0E9F8E),
    VIOLET(0xFF9B7BFF, 0xFF6236F0),
    SAFFRON(0xFFFFC53D, 0xFFE8780C),
    SKY(0xFF5CB8FF, 0xFF2E6BF5),
    ROSE(0xFFFF7EB3, 0xFFE0457B),
}

internal val JuntaTone.brush: Brush
    get() = Brush.linearGradient(listOf(Color(start), Color(end)))

internal val JuntaTone.accent: Color
    get() = Color(end)

internal val JuntaTone.light: Color
    get() = Color(start)

/** Fixed brand gradients; every white label placed on them keeps at least 4.5:1 contrast. */
@Suppress("MagicNumber") // Fixed sRGB brand stops.
internal object JuntaGradients {
    val Hero = listOf(Color(0xFF4A2BDF), Color(0xFF7F32DC), Color(0xFFAE36C9))
    val HeroNight = listOf(Color(0xFF2E1A9E), Color(0xFF55229F), Color(0xFF7E2793))
    val Catalog = listOf(Color(0xFF4326E0), Color(0xFF2F4FE6), Color(0xFF2563EB))
    val CatalogNight = listOf(Color(0xFF261A8F), Color(0xFF1E3393), Color(0xFF173F99))
    val Action = listOf(Color(0xFF5B3AF0), Color(0xFFA630C4))
    val Card = listOf(Color(0xFF4F2ED8), Color(0xFF7A2FB0), Color(0xFFA62F73))
    val CardLocked = listOf(Color(0xFF2A2546), Color(0xFF3A3360), Color(0xFF4B4470))
    val Ribbon = listOf(Color(0xFF6236F0), Color(0xFFE0457B), Color(0xFFFFC53D), Color(0xFF2DD4BF))
    val Gold = Color(0xFFFFD166)
    val Glow = Color(0xFFFF7A6B)
}

/** True for the dark fallback (and dark dynamic schemes), judged from the bound background. */
internal val juntaNight: Boolean
    @Composable
    @ReadOnlyComposable
    get() = JuntaPaper.luminance() < NIGHT_LUMINANCE

private const val NIGHT_LUMINANCE = 0.4f
private const val DAY_GLOW = 0.2f
private const val NIGHT_GLOW = 0.26f
private const val PRESSED_SCALE = 0.96f

/** Stable tone for free text such as an organisation or certificate holder name. */
internal fun juntaToneFor(key: String): JuntaTone =
    JuntaTone.entries[abs(key.hashCode() % JuntaTone.entries.size)]

/** Soft paper with four colour pools; drawn in code so it follows the light or dark theme. */
@Suppress("FunctionNaming", "MagicNumber") // Decorative geometry as fractions of the canvas.
@Composable
internal fun JuntaBackdrop(modifier: Modifier = Modifier) {
    val paper = JuntaPaper
    val strength = if (juntaNight) NIGHT_GLOW else DAY_GLOW
    Canvas(modifier = modifier) {
        drawRect(paper)
        val w = size.width
        val h = size.height
        drawGlow(JuntaTone.VIOLET.light, Offset(w * 0.1f, h * 0.02f), w * 0.95f, strength)
        drawGlow(JuntaTone.ROSE.light, Offset(w * 1.02f, h * 0.3f), w * 0.72f, strength * 0.75f)
        drawGlow(JuntaTone.MINT.light, Offset(-w * 0.05f, h * 0.66f), w * 0.8f, strength * 0.65f)
        drawGlow(JuntaTone.SAFFRON.light, Offset(w * 0.96f, h * 0.98f), w * 0.78f, strength * 0.7f)
    }
}

internal fun DrawScope.drawGlow(color: Color, center: Offset, radius: Float, alpha: Float) {
    if (radius <= 0f) return
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

/** Eight-point azulejo stars on a staggered lattice, fading in from [fromFraction] of the width. */
@Suppress("MagicNumber")
internal fun DrawScope.drawMosaic(color: Color, cell: Float, fromFraction: Float) {
    if (cell <= 0f || size.width <= 0f) return
    val half = cell * 0.3f
    val stroke = Stroke(width = 1.dp.toPx())
    val columns = (size.width / cell).toInt() + 2
    val rows = (size.height / cell).toInt() + 2
    for (row in 0 until rows) {
        for (column in 0 until columns) {
            val center = Offset(column * cell + if (row % 2 == 0) 0f else cell / 2, row * cell)
            val fade = ((center.x / size.width - fromFraction) / (1f - fromFraction)).coerceIn(0f, 1f)
            if (fade > 0f) drawMosaicStar(color.copy(alpha = color.alpha * fade), center, half, stroke)
        }
    }
}

private fun DrawScope.drawMosaicStar(color: Color, center: Offset, half: Float, stroke: Stroke) {
    val topLeft = Offset(center.x - half, center.y - half)
    val square = Size(half * 2, half * 2)
    drawRect(color, topLeft = topLeft, size = square, style = stroke)
    rotate(degrees = 45f, pivot = center) {
        drawRect(color, topLeft = topLeft, size = square, style = stroke)
    }
    drawCircle(color, radius = half / 4, center = center)
}

/** Four-point sparkle with concave sides. */
internal fun DrawScope.drawSparkle(center: Offset, radius: Float, color: Color) {
    if (radius <= 0f) return
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x, center.y, center.x + radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + radius)
        quadraticTo(center.x, center.y, center.x - radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - radius)
        close()
    }
    drawPath(path, color)
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

/** Pulls a block up over the previous one, so a card can overlap the hero above it. */
internal fun Modifier.juntaOverlapAbove(overlap: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = overlap.roundToPx().coerceIn(0, placeable.height)
    layout(placeable.width, placeable.height - shift) {
        placeable.place(0, -shift)
    }
}

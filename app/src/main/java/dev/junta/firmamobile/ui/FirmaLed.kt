package dev.junta.firmamobile.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.ui.theme.Firma

private const val LED_GLOW_ALPHA = 0.42f
private const val LED_GLOW_REACH = 1.25f
private const val LED_PULSE_MILLIS = 900
private const val LED_BLINK_MILLIS = 640
private const val LED_BLINK_LOW = 0.28f
private const val LED_SPECULAR_ALPHA = 0.55f

/** Status LEDs; decorative, the state is always written next to them. */
internal enum class FirmaLedState { OFF, ON, WARN, BUSY, ALERT }

/**
 * A small LED with a lens highlight. ON, WARN and ALERT glow and pulse once when they light up,
 * BUSY blinks, OFF is a dull, unlit lens.
 */
@Suppress("FunctionNaming")
@Composable
internal fun FirmaLed(
    state: FirmaLedState,
    modifier: Modifier = Modifier,
    size: Dp = 9.dp,
    animate: Boolean = true,
) {
    val color = when (state) {
        FirmaLedState.OFF -> Firma.ledOff
        FirmaLedState.ON -> Firma.led
        FirmaLedState.WARN, FirmaLedState.BUSY -> Firma.ledBusy
        FirmaLedState.ALERT -> Firma.ledAlert
    }
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(state) {
        if (animate && state.flashes) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(durationMillis = LED_PULSE_MILLIS, easing = FastOutSlowInEasing))
        }
    }
    val blink = if (animate && state == FirmaLedState.BUSY) {
        val transition = rememberInfiniteTransition(label = "led-blink")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = LED_BLINK_LOW,
            animationSpec = infiniteRepeatable(tween(LED_BLINK_MILLIS, easing = LinearEasing), RepeatMode.Reverse),
            label = "led-blink-alpha",
        ).value
    } else {
        1f
    }
    val lit = state != FirmaLedState.OFF
    Box(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics {}
            .drawBehind { drawLed(color, lit, blink, pulse.value) },
    )
}

/** Lit states flash once when they come on; BUSY blinks instead and OFF stays dark. */
private val FirmaLedState.flashes: Boolean
    get() = this == FirmaLedState.ON || this == FirmaLedState.WARN || this == FirmaLedState.ALERT

@Suppress("MagicNumber") // Lens proportions of the drawn LED.
private fun DrawScope.drawLed(color: Color, lit: Boolean, brightness: Float, pulse: Float) {
    val radius = size.minDimension / 2
    if (lit) {
        val glow = radius * (LED_GLOW_REACH + 1.6f * (1f - pulse)) + radius
        val alpha = LED_GLOW_ALPHA * brightness * (1f + (1f - pulse))
        drawCircle(
            Brush.radialGradient(
                listOf(color.copy(alpha = alpha.coerceAtMost(0.8f)), color.copy(alpha = 0f)),
                center = center,
                radius = glow,
            ),
            radius = glow,
        )
        drawCircle(color.copy(alpha = 0.35f + 0.65f * brightness), radius = radius)
        drawCircle(
            Color.White.copy(alpha = LED_SPECULAR_ALPHA * brightness),
            radius = radius * 0.32f,
            center = Offset(center.x - radius * 0.3f, center.y - radius * 0.32f),
        )
    } else {
        drawCircle(color, radius = radius)
        drawCircle(Color.Black.copy(alpha = 0.12f), radius = radius, style = Stroke(1.dp.toPx()))
        drawCircle(
            Color.White.copy(alpha = 0.35f),
            radius = radius * 0.28f,
            center = Offset(center.x - radius * 0.28f, center.y - radius * 0.3f),
        )
    }
}

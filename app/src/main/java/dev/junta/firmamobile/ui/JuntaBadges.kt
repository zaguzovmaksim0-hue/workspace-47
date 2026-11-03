package dev.junta.firmamobile.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.ui.theme.JuntaInk

private const val PULSE_REPEATS = 3
private const val PULSE_MILLIS = 1_300
private const val PILL_TINT_ALPHA = 0.16f
private const val PULSE_RING_ALPHA = 0.45f
private const val GLOSS_ALPHA = 0.24f
private const val MONOGRAM_TEXT_RATIO = 0.38f
private const val MONOGRAM_LETTERS = 2

private val MonogramStopWords = setOf(
    "a", "al", "da", "das", "de", "del", "der", "des", "do", "dos", "e", "el", "en",
    "eta", "i", "la", "las", "les", "lo", "los", "o", "of", "the", "y",
)

/** Rounded gradient tile with a white glyph; the glyph is decorative and never announced. */
@Suppress("FunctionNaming")
@Composable
internal fun JuntaIconBubble(
    @DrawableRes iconRes: Int,
    tone: JuntaTone,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
) {
    val shape = RoundedCornerShape(percent = 32)
    Box(
        modifier = modifier
            .size(size)
            .background(tone.brush, shape)
            .background(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = GLOSS_ALPHA), Color.Transparent)),
                shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** Initials avatar for an organisation or certificate holder; hidden from accessibility services. */
@Suppress("FunctionNaming")
@Composable
internal fun JuntaMonogram(name: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val tone = juntaToneFor(name)
    val textSize = with(LocalDensity.current) { (size * MONOGRAM_TEXT_RATIO).toSp() }
    Box(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics {}
            .background(tone.brush, RoundedCornerShape(percent = 32)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = juntaMonogramOf(name),
            color = Color.White,
            fontSize = textSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** First letters of the first two meaningful words, e.g. "Junta de Andalucía" becomes "JA". */
internal fun juntaMonogramOf(name: String): String {
    val words = name.split(' ', '-', '/', '(', ')', ',', '.', '\'', '’')
        .filter { it.isNotBlank() && it.lowercase() !in MonogramStopWords }
    val letters = words.mapNotNull { word -> word.firstOrNull { it.isLetterOrDigit() } }
        .take(MONOGRAM_LETTERS)
        .joinToString("")
    return letters.uppercase().ifEmpty { "·" }
}

/** Status capsule whose dot pulses three times when the status changes, then rests. */
@Suppress("FunctionNaming")
@Composable
internal fun JuntaStatusPill(text: String, tone: JuntaTone, modifier: Modifier = Modifier) {
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(tone, text) {
        repeat(PULSE_REPEATS) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(durationMillis = PULSE_MILLIS, easing = FastOutSlowInEasing))
        }
        pulse.snapTo(0f)
    }
    val dot = tone.accent
    Row(
        modifier = modifier
            .background(dot.copy(alpha = PILL_TINT_ALPHA), CircleShape)
            .padding(start = 8.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Canvas(modifier = Modifier.size(12.dp)) {
            val radius = size.minDimension / 2
            val progress = pulse.value
            if (progress > 0f) {
                drawCircle(
                    color = dot.copy(alpha = (1f - progress) * PULSE_RING_ALPHA),
                    radius = radius * (1f + progress) / 2,
                )
            }
            drawCircle(color = dot, radius = radius / 2)
        }
        Text(
            text = text,
            color = JuntaInk,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

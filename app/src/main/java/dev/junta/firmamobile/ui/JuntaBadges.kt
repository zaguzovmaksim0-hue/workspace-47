package dev.junta.firmamobile.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.ui.theme.Firma
import dev.junta.firmamobile.ui.theme.JuntaInk
import dev.junta.firmamobile.ui.theme.JuntaMutedInk

/** Engraved glyphs sit slightly smaller than printed ones so the recess rim stays visible. */
private const val ENGRAVED_GLYPH_SCALE = 0.92f

private const val MONOGRAM_TEXT_RATIO = 0.36f
private const val MONOGRAM_LETTERS = 2
private const val BADGE_CORNER_RATIO = 0.27f

private val MonogramStopWords = setOf(
    "a", "al", "da", "das", "de", "del", "der", "des", "do", "dos", "e", "el", "en",
    "eta", "i", "la", "las", "les", "lo", "los", "o", "of", "the", "y",
)

/** Icon tint for a semantic tone: ink for neutral marks, colour only where it carries meaning. */
internal val JuntaTone.glyphTint: Color
    @Composable
    @ReadOnlyComposable
    get() = when (this) {
        JuntaTone.CORAL -> MaterialTheme.colorScheme.primary
        JuntaTone.MINT -> MaterialTheme.colorScheme.tertiary
        JuntaTone.SAFFRON -> Firma.cautionInk
        else -> JuntaInk
    }

/** An engraved glyph: a round recess in the body with an ink icon. Decorative, never announced. */
@Suppress("FunctionNaming")
@Composable
internal fun JuntaIconBubble(
    @DrawableRes iconRes: Int,
    tone: JuntaTone,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
) {
    FirmaEngraved(modifier = modifier, size = size) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = tone.glyphTint,
            modifier = Modifier.size(iconSize * ENGRAVED_GLYPH_SCALE),
        )
    }
}

/** Graphite initials badge, like the "F" printed on the card; hidden from accessibility services. */
@Suppress("FunctionNaming")
@Composable
internal fun JuntaMonogram(name: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val textSize = with(LocalDensity.current) { (size * MONOGRAM_TEXT_RATIO).toSp() }
    Box(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics {}
            .background(Firma.keyGraphite, RoundedCornerShape(size * BADGE_CORNER_RATIO)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = juntaMonogramOf(name),
            color = Firma.keyGraphiteInk,
            fontSize = textSize,
            fontWeight = FontWeight.Medium,
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

/** Status line of the device: an LED and its printed label. */
@Suppress("FunctionNaming")
@Composable
internal fun JuntaStatusPill(text: String, tone: JuntaTone, modifier: Modifier = Modifier) {
    val led = when (tone) {
        JuntaTone.MINT -> FirmaLedState.ON
        JuntaTone.SKY -> FirmaLedState.BUSY
        JuntaTone.CORAL -> FirmaLedState.ALERT
        else -> FirmaLedState.OFF
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        FirmaLed(state = led)
        Text(text = text, color = JuntaMutedInk, style = MaterialTheme.typography.bodyMedium)
    }
}

package dev.junta.firmamobile.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.ui.theme.JuntaWordmarkFont
import java.time.LocalTime

private val HeroShape = RoundedCornerShape(32.dp)
private const val INTRO_MILLIS = 2_400
private const val MORNING_FROM = 6
private const val AFTERNOON_FROM = 14
private const val EVENING_FROM = 21
private const val DISCLOSURE_ALPHA = 0.9f
private const val GLASS_ALPHA = 0.16f
private const val GLASS_EDGE_ALPHA = 0.3f

/** Wordmark hero: violet-to-magenta gradient, azulejo stars, a coral glow and a one-shot sparkle intro. */
@Suppress("FunctionNaming")
@Composable
internal fun JuntaBrandHero(modifier: Modifier = Modifier, bottomInset: Dp = 0.dp) {
    val colors = if (juntaNight) JuntaGradients.HeroNight else JuntaGradients.Hero
    val intro = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        intro.animateTo(1f, tween(durationMillis = INTRO_MILLIS, easing = FastOutSlowInEasing))
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 16.dp, shape = HeroShape, ambientColor = colors.first(), spotColor = colors.last())
            .clip(HeroShape)
            .background(Brush.linearGradient(colors))
            .drawBehind { drawHeroDecor(intro.value) }
            .padding(start = 22.dp, end = 18.dp, top = 18.dp, bottom = 24.dp + bottomInset),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeroGreeting(modifier = Modifier.weight(1f))
                HeroSeal()
            }
            Spacer(modifier = Modifier.height(12.dp))
            HeroWordmark()
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.unofficial_disclosure),
                color = Color.White.copy(alpha = DISCLOSURE_ALPHA),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun HeroWordmark() {
    val appName = stringResource(R.string.app_name)
    val accent = appName.substringAfterLast(' ', missingDelimiterValue = "")
    val primary = appName.removeSuffix(accent).trimEnd()
    val title = buildAnnotatedString {
        if (accent.isEmpty() || primary.isEmpty()) {
            append(appName)
        } else {
            append(primary)
            append(" ")
            withStyle(SpanStyle(color = JuntaGradients.Gold)) { append(accent) }
        }
    }
    Text(
        text = title,
        modifier = Modifier
            .testTag("jfm-brand-title")
            .semantics { heading() },
        color = Color.White,
        fontFamily = JuntaWordmarkFont,
        fontSize = 50.sp,
        lineHeight = 48.sp,
        letterSpacing = 1.sp,
        maxLines = 2,
        overflow = TextOverflow.Clip,
        style = TextStyle(
            shadow = Shadow(color = Color.Black.copy(alpha = 0.28f), offset = Offset(x = 0f, y = 6f), blurRadius = 18f),
        ),
    )
}

@Suppress("FunctionNaming")
@Composable
private fun HeroGreeting(modifier: Modifier = Modifier) {
    val hour = remember { LocalTime.now().hour }
    val night = hour < MORNING_FROM || hour >= EVENING_FROM
    val greeting = when (hour) {
        in MORNING_FROM until AFTERNOON_FROM -> R.string.home_greeting_morning
        in AFTERNOON_FROM until EVENING_FROM -> R.string.home_greeting_afternoon
        else -> R.string.home_greeting_evening
    }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .background(Color.White.copy(alpha = GLASS_ALPHA), CircleShape)
                .border(1.dp, Color.White.copy(alpha = GLASS_EDGE_ALPHA), CircleShape)
                .padding(start = 8.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Canvas(modifier = Modifier.size(16.dp)) { if (night) drawMoon() else drawSun() }
            Text(
                text = stringResource(greeting),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun HeroSeal() {
    Box(
        modifier = Modifier
            .size(50.dp)
            .shadow(elevation = 10.dp, shape = CircleShape, spotColor = JuntaGradients.Glow)
            .background(Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_jfm_shield_check),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(30.dp),
        )
    }
}

@Suppress("MagicNumber") // Decorative geometry as fractions of the hero.
private fun DrawScope.drawHeroDecor(intro: Float) {
    val w = size.width
    val h = size.height
    drawGlow(JuntaGradients.Glow, Offset(w * 0.98f, h * 1.08f), w * 0.62f, alpha = 0.6f)
    drawGlow(JuntaGradients.Gold, Offset(w * 0.88f, 0f), w * 0.3f, alpha = 0.3f)
    drawMosaic(Color.White.copy(alpha = 0.11f), cell = 34.dp.toPx(), fromFraction = 0.4f)
    val orbit = Offset(w - 43.dp.toPx(), 43.dp.toPx())
    val ring = Stroke(width = 1.dp.toPx())
    drawCircle(Color.White.copy(alpha = 0.18f), radius = 40.dp.toPx(), center = orbit, style = ring)
    drawCircle(Color.White.copy(alpha = 0.1f), radius = 62.dp.toPx(), center = orbit, style = ring)
    val sparkles = listOf(
        Triple(0.62f, 0.2f, 6f),
        Triple(0.74f, 0.62f, 9f),
        Triple(0.9f, 0.8f, 4f),
        Triple(0.52f, 0.78f, 3.5f),
    )
    sparkles.forEachIndexed { index, (x, y, radius) ->
        val shown = ((intro - index * 0.14f) / 0.45f).coerceIn(0f, 1f)
        drawSparkle(Offset(w * x, h * y), radius.dp.toPx() * shown, Color.White.copy(alpha = 0.92f * shown))
    }
}

@Suppress("MagicNumber")
private fun DrawScope.drawSun() {
    val c = center
    val r = size.minDimension * 0.24f
    drawCircle(JuntaGradients.Gold, radius = r, center = c)
    val rayWidth = size.minDimension * 0.09f
    repeat(8) { index ->
        rotate(degrees = index * 45f, pivot = c) {
            drawLine(
                JuntaGradients.Gold,
                start = Offset(c.x, c.y - r * 1.55f),
                end = Offset(c.x, c.y - r * 1.95f),
                strokeWidth = rayWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Suppress("MagicNumber")
private fun DrawScope.drawMoon() {
    val r = size.minDimension * 0.42f
    val disc = Path().apply { addOval(Rect(center, r)) }
    val bite = Path().apply {
        addOval(Rect(Offset(center.x + r * 0.55f, center.y - r * 0.35f), r * 0.85f))
    }
    val crescent = Path().apply { op(disc, bite, PathOperation.Difference) }
    drawPath(crescent, JuntaGradients.Gold)
    drawSparkle(Offset(center.x + r * 0.7f, center.y + r * 0.55f), r * 0.32f, Color.White)
}

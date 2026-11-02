package dev.junta.firmamobile.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.ui.theme.JuntaInk
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val IdCardShape = RoundedCornerShape(24.dp)
private const val SHEEN_MILLIS = 1_500
private const val SHEEN_DELAY_MILLIS = 350
private const val SHEEN_FROM = -0.4f
private const val SHEEN_TO = 1.4f
internal const val ID_CARD_TEXT_ALPHA = 0.84f
private const val TRACK_ALPHA = 0.24f
private const val LOW_VALIDITY = 0.2f
private const val SLOT_TINT_ALPHA = 0.55f

/** Visual mood of the certificate card; it never changes what the certificate may be used for. */
internal enum class CertificateCardMood { READY, LOCKED, BUSY }

/** Gradient "identity card" with a chip, guilloche lines, a wax seal and a one-shot sheen. */
@Suppress("FunctionNaming")
@Composable
internal fun CertificateIdCard(
    mood: CertificateCardMood,
    label: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = if (mood == CertificateCardMood.READY) JuntaGradients.Card else JuntaGradients.CardLocked
    val sheen = remember { Animatable(SHEEN_FROM) }
    LaunchedEffect(mood) {
        sheen.snapTo(SHEEN_FROM)
        sheen.animateTo(
            SHEEN_TO,
            tween(durationMillis = SHEEN_MILLIS, delayMillis = SHEEN_DELAY_MILLIS, easing = FastOutSlowInEasing),
        )
    }
    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, end = 10.dp)
                .shadow(
                    elevation = 14.dp,
                    shape = IdCardShape,
                    ambientColor = colors.first(),
                    spotColor = colors.last(),
                )
                .clip(IdCardShape)
                .background(Brush.linearGradient(colors))
                .drawBehind { drawCardDecor(sheen.value) }
                .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.size(width = 38.dp, height = 28.dp)) { drawChip() }
                Spacer(modifier = Modifier.width(12.dp))
                if (label != null) {
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f).padding(end = 44.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        letterSpacing = 0.6.sp,
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
        CertificateSeal(mood = mood, modifier = Modifier.align(Alignment.TopEnd))
    }
}

/** Wax-seal rosette in the card corner: a check when ready, a padlock when locked, a spinner when busy. */
@Suppress("FunctionNaming")
@Composable
private fun CertificateSeal(mood: CertificateCardMood, modifier: Modifier = Modifier) {
    val tone = when (mood) {
        CertificateCardMood.READY -> JuntaTone.SAFFRON
        CertificateCardMood.LOCKED -> JuntaTone.CORAL
        CertificateCardMood.BUSY -> JuntaTone.SKY
    }
    Box(modifier = modifier.size(58.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(58.dp)) { drawRosette(tone) }
        when (mood) {
            CertificateCardMood.READY -> Canvas(modifier = Modifier.size(22.dp)) { drawCheck() }
            CertificateCardMood.LOCKED -> Icon(
                painter = painterResource(R.drawable.ic_ui_lock),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
            CertificateCardMood.BUSY -> CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** Remaining validity as a slim bar plus a "days left" caption. */
@Suppress("FunctionNaming")
@Composable
internal fun CertificateValidityMeter(remaining: Float, caption: String?) {
    val fill = if (remaining < LOW_VALIDITY) JuntaTone.CORAL.brush else Brush.horizontalGradient(
        listOf(JuntaTone.MINT.light, Color.White),
    )
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(Color.White.copy(alpha = TRACK_ALPHA), CircleShape),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(remaining.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(fill, CircleShape),
            )
        }
        if (caption != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = caption,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Empty-state twin of the identity card: a dashed slot with a chip outline, ghost lines and a ghost seal. */
@Suppress("FunctionNaming")
@Composable
internal fun EmptyCertificateSlot(text: String, modifier: Modifier = Modifier) {
    val accent = JuntaTone.VIOLET.accent
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(IdCardShape)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = SLOT_TINT_ALPHA))
            .drawBehind { drawEmptySlot(accent) }
            .padding(start = 18.dp, end = 18.dp, top = 60.dp, bottom = 18.dp),
    ) {
        Text(
            text = text,
            color = JuntaInk,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Formatted line with [emphasis] (for example the holder name) set larger and bold. */
internal fun certificateCardLine(full: String, emphasis: String): AnnotatedString = buildAnnotatedString {
    val start = if (emphasis.isEmpty()) -1 else full.indexOf(emphasis)
    if (start < 0) {
        append(full)
    } else {
        append(full.substring(0, start))
        withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)) {
            append(emphasis)
        }
        append(full.substring(start + emphasis.length))
    }
}

@Suppress("MagicNumber") // Decorative geometry as fractions of the card.
private fun DrawScope.drawCardDecor(sheen: Float) {
    val w = size.width
    val h = size.height
    drawGlow(Color.White, Offset(w * 0.92f, h * 0.05f), w * 0.5f, alpha = 0.16f)
    val wave = Path()
    val stroke = Stroke(width = 1.dp.toPx())
    val step = 6.dp.toPx()
    val amplitude = 7.dp.toPx()
    for (line in 0 until 6) {
        wave.reset()
        val baseY = h * (0.42f + line * 0.1f)
        var x = 0f
        wave.moveTo(0f, baseY)
        while (x <= w + step) {
            wave.lineTo(x, baseY + sin(x / w * 4f * PI.toFloat() + line * 0.7f) * amplitude)
            x += step
        }
        drawPath(wave, Color.White.copy(alpha = 0.07f), style = stroke)
    }
    val band = w * 0.28f
    val x = w * sheen
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.2f), Color.Transparent),
            start = Offset(x - band, 0f),
            end = Offset(x + band, h),
        ),
    )
}

@Suppress("MagicNumber")
private fun DrawScope.drawChip() {
    val corner = CornerRadius(5.dp.toPx())
    drawRoundRect(
        brush = Brush.linearGradient(listOf(Color(0xFFFFE7A3), Color(0xFFE3B341), Color(0xFFFFD873))),
        cornerRadius = corner,
    )
    val line = Color(0x66704B00)
    val stroke = 1.dp.toPx()
    drawLine(line, Offset(size.width * 0.33f, 0f), Offset(size.width * 0.33f, size.height), stroke)
    drawLine(line, Offset(size.width * 0.67f, 0f), Offset(size.width * 0.67f, size.height), stroke)
    drawLine(line, Offset(0f, size.height * 0.5f), Offset(size.width * 0.33f, size.height * 0.5f), stroke)
    drawLine(line, Offset(size.width * 0.67f, size.height * 0.5f), Offset(size.width, size.height * 0.5f), stroke)
}

@Suppress("MagicNumber")
private fun DrawScope.drawRosette(tone: JuntaTone) {
    val points = 28
    val outer = size.minDimension / 2
    val inner = outer * 0.88f
    val rosette = Path()
    for (index in 0 until points) {
        val angle = index * 2 * PI / points - PI / 2
        val radius = if (index % 2 == 0) outer else inner
        val point = Offset(center.x + (cos(angle) * radius).toFloat(), center.y + (sin(angle) * radius).toFloat())
        if (index == 0) rosette.moveTo(point.x, point.y) else rosette.lineTo(point.x, point.y)
    }
    rosette.close()
    drawPath(rosette, tone.brush, style = androidx.compose.ui.graphics.drawscope.Fill)
    drawPath(
        rosette,
        Color.White.copy(alpha = 0.85f),
        style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.cornerPathEffect(outer * 0.1f)),
    )
    drawCircle(Color.White.copy(alpha = 0.35f), radius = outer * 0.62f, style = Stroke(width = 1.dp.toPx()))
}

@Suppress("MagicNumber")
private fun DrawScope.drawCheck() {
    val path = Path().apply {
        moveTo(size.width * 0.18f, size.height * 0.54f)
        lineTo(size.width * 0.42f, size.height * 0.76f)
        lineTo(size.width * 0.84f, size.height * 0.28f)
    }
    drawPath(path, Color.White, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Suppress("MagicNumber")
private fun DrawScope.drawEmptySlot(accent: Color) {
    val edge = 1.5.dp.toPx()
    val inset = edge / 2
    drawRoundRect(
        color = accent.copy(alpha = 0.55f),
        topLeft = Offset(inset, inset),
        size = Size(size.width - edge, size.height - edge),
        cornerRadius = CornerRadius(24.dp.toPx() - inset),
        style = Stroke(width = edge, pathEffect = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 6.dp.toPx()))),
    )
    val pad = 18.dp.toPx()
    val chip = Size(38.dp.toPx(), 28.dp.toPx())
    drawRoundRect(
        color = accent.copy(alpha = 0.5f),
        topLeft = Offset(pad, pad),
        size = chip,
        cornerRadius = CornerRadius(5.dp.toPx()),
        style = Stroke(width = edge),
    )
    val ghost = accent.copy(alpha = 0.16f)
    val barX = pad + chip.width + 12.dp.toPx()
    val bar = 8.dp.toPx()
    val room = size.width - barX - pad - 48.dp.toPx()
    drawRoundRect(ghost, Offset(barX, pad + 2.dp.toPx()), Size(room * 0.8f, bar), CornerRadius(bar / 2))
    drawRoundRect(ghost, Offset(barX, pad + 16.dp.toPx()), Size(room * 0.5f, bar), CornerRadius(bar / 2))
    val seal = Offset(size.width - pad - 16.dp.toPx(), pad + 14.dp.toPx())
    val radius = 16.dp.toPx()
    drawCircle(
        color = accent.copy(alpha = 0.5f),
        radius = radius,
        center = seal,
        style = Stroke(width = edge, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
    val arm = radius * 0.42f
    val plus = 2.dp.toPx()
    drawLine(accent, Offset(seal.x - arm, seal.y), Offset(seal.x + arm, seal.y), plus, StrokeCap.Round)
    drawLine(accent, Offset(seal.x, seal.y - arm), Offset(seal.x, seal.y + arm), plus, StrokeCap.Round)
}

package dev.junta.firmamobile.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.certificate.CertificateSummary
import dev.junta.firmamobile.ui.theme.Firma
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

internal val FirmaDisplayShape = RoundedCornerShape(16.dp)
private const val SEGMENTS = 24
private val SegmentGap = 3.dp
private val SegmentHeight = 17.dp
private const val COUNT_UP_MILLIS = 760
private const val SCAN_MILLIS = 1_100
private const val SCAN_WIDTH = 5
private const val FUTURE_ALPHA = 0.94f
private const val WIDE_DIGITS = 4

/**
 * Days of validity left, as a dark device display: the count, the expiry date and a strip of
 * [SEGMENTS] cells spanning the whole validity period, past cells dark and today in orange.
 * Screen readers get one sentence instead of the printed fragments.
 */
@Suppress("FunctionNaming")
@Composable
internal fun FirmaValidityDisplay(summary: CertificateSummary, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val zone = ZoneId.systemDefault()
    val validity = remember(summary) { DisplayValidity.of(summary, Instant.now()) }
    val boot = remember(summary) { Animatable(0f) }
    LaunchedEffect(summary) {
        boot.animateTo(1f, tween(durationMillis = COUNT_UP_MILLIS, easing = FastOutSlowInEasing))
    }
    val days = validity.daysLeft
    val spoken = pluralStringResource(R.plurals.certificate_validity_days_left, days, days)
    val text = Firma.displayText
    val muted = Firma.displayMuted
    Column(
        modifier = modifier
            .fillMaxWidth()
            .firmaDisplay(FirmaDisplayShape)
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(start = 20.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            val digits = if (days.toString().length >= WIDE_DIGITS) 50.sp else 62.sp
            Box {
                // The final number reserves the width so the counting digits never shift the row.
                DisplayNumber(days.toString(), digits, text, Modifier.alpha(0f))
                DisplayNumber((days * boot.value).roundToInt().toString(), digits, text)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = pluralStringResource(R.plurals.display_days_left, days),
                modifier = Modifier.weight(1f).padding(bottom = 8.dp),
                color = muted,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 13.sp,
                lineHeight = 16.sp,
            )
            DisplayUntil(date = DISPLAY_DATE.withZone(zone).format(summary.validUntil), ink = text, muted = muted)
        }
        Spacer(modifier = Modifier.height(6.dp))
        val off = Firma.displayOff
        val accent = Firma.displayAccent
        Canvas(modifier = Modifier.fillMaxWidth().height(SegmentHeight)) {
            drawSegments(validity.todayIndex, boot.value, off, accent, text)
        }
        Spacer(modifier = Modifier.height(6.dp))
        DisplayScale(
            start = summary.validFrom.scaleLabel(zone, locale),
            today = stringResource(R.string.display_today),
            end = summary.validUntil.scaleLabel(zone, locale),
            todayIndex = validity.todayIndex,
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun DisplayNumber(value: String, size: TextUnit, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = value,
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.displayLarge.copy(
            fontSize = size,
            lineHeight = size,
            fontFeatureSettings = "tnum",
        ),
        maxLines = 1,
    )
}

/** "hasta" over the expiry date, right-aligned in the display. */
@Suppress("FunctionNaming")
@Composable
private fun DisplayUntil(date: String, ink: Color, muted: Color) {
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)) {
        Text(stringResource(R.string.display_until), color = muted, style = firmaMono(12.sp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = date, color = ink, style = firmaMono(13.sp), fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** Start and end months under the strip, with "today" under today's cell when it fits between them. */
@Suppress("FunctionNaming")
@Composable
private fun DisplayScale(start: String, today: String, end: String, todayIndex: Int?) {
    val muted = Firma.displayMuted
    val accent = Firma.displayAccent
    Layout(
        content = {
            Text(start, color = muted, style = firmaMono(12.sp), maxLines = 1)
            Text(today, color = accent, style = MaterialTheme.typography.bodySmall, fontSize = 12.5.sp, maxLines = 1)
            Text(end, color = muted, style = firmaMono(12.sp), maxLines = 1)
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0)
        val first = measurables[0].measure(loose)
        val middle = measurables[1].measure(loose)
        val last = measurables[2].measure(loose)
        val width = constraints.maxWidth
        val height = maxOf(first.height, middle.height, last.height)
        layout(width, height) {
            first.placeRelative(0, 0)
            last.placeRelative(width - last.width, 0)
            if (todayIndex != null) {
                val gap = SegmentGap.toPx()
                val cell = (width - gap * (SEGMENTS - 1)) / SEGMENTS
                val centre = todayIndex * (cell + gap) + cell / 2
                val min = first.width + 6.dp.roundToPx()
                val max = width - last.width - 6.dp.roundToPx() - middle.width
                if (min <= max) {
                    middle.placeRelative((centre - middle.width / 2f).roundToInt().coerceIn(min, max), 0)
                }
            }
        }
    }
}

private fun DrawScope.drawSegments(todayIndex: Int?, boot: Float, off: Color, accent: Color, lit: Color) {
    val gap = SegmentGap.toPx()
    val cell = (size.width - gap * (SEGMENTS - 1)) / SEGMENTS
    val radius = CornerRadius(2.5.dp.toPx())
    val shown = boot * SEGMENTS
    for (index in 0 until SEGMENTS) {
        val color = when {
            index >= shown -> off
            todayIndex == null || index > todayIndex -> lit.copy(alpha = FUTURE_ALPHA)
            index == todayIndex -> accent
            else -> off
        }
        drawRoundRect(
            color = color,
            topLeft = Offset(index * (cell + gap), 0f),
            size = Size(cell, size.height),
            cornerRadius = radius,
        )
    }
}

/** Busy display while the certificate is being checked: a lit window scanning along the strip. */
@Suppress("FunctionNaming")
@Composable
internal fun FirmaBusyDisplay(text: String, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "display-scan")
    val scan by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SCAN_MILLIS, easing = LinearEasing), RepeatMode.Reverse),
        label = "display-scan-position",
    )
    val off = Firma.displayOff
    val lit = Firma.displayText
    Column(
        modifier = modifier
            .fillMaxWidth()
            .firmaDisplay(FirmaDisplayShape)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FirmaLed(FirmaLedState.BUSY)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                color = Firma.displayText,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(SegmentHeight)) {
            val gap = SegmentGap.toPx()
            val cell = (size.width - gap * (SEGMENTS - 1)) / SEGMENTS
            val head = scan * (SEGMENTS - SCAN_WIDTH)
            for (index in 0 until SEGMENTS) {
                val inside = index >= floor(head) && index < floor(head) + SCAN_WIDTH
                drawRoundRect(
                    color = if (inside) lit.copy(alpha = FUTURE_ALPHA) else off,
                    topLeft = Offset(index * (cell + gap), 0f),
                    size = Size(cell, size.height),
                    cornerRadius = CornerRadius(2.5.dp.toPx()),
                )
            }
        }
    }
}

/** Whole days left and today's cell on the validity strip; no cell before the period starts. */
private class DisplayValidity(val daysLeft: Int, val todayIndex: Int?) {
    companion object {
        fun of(summary: CertificateSummary, now: Instant): DisplayValidity {
            val total = Duration.between(summary.validFrom, summary.validUntil).toMillis().coerceAtLeast(1L)
            val elapsed = Duration.between(summary.validFrom, now).toMillis()
            val left = Duration.between(now, summary.validUntil).toDays().coerceAtLeast(0L)
            val today = if (elapsed < 0) {
                null
            } else {
                floor(elapsed.toDouble() / total * SEGMENTS).toInt().coerceIn(0, SEGMENTS - 1)
            }
            return DisplayValidity(daysLeft = left.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), todayIndex = today)
        }
    }
}

private fun Instant.scaleLabel(zone: ZoneId, locale: Locale): String =
    DateTimeFormatter.ofPattern("LLL yy", locale).withZone(zone).format(this)
        .replace(".", "")
        .lowercase(locale)

private val DISPLAY_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.uuuu")

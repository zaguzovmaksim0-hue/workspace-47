package dev.junta.firmamobile.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.ui.theme.Firma
import dev.junta.firmamobile.ui.theme.JuntaMutedInk

private val SlotHeight = 12.dp
private val CardInset = 22.dp
private val InsertLift = 26.dp
private const val INSERT_DAMPING = 0.86f
private const val INSERT_STIFFNESS = 240f
private const val INSERT_FADE = 0.55f
private const val SLOT_LIP_AT = 0.62f
private const val CARD_FALLOFF = 0.78f
private const val GHOST_DASH = 7f

/**
 * The card reader: a dark slot across the body with the certificate card standing in it.
 * A new [insertedKey] slides the card down into the slot once; the slot hides the card's foot.
 */
@Suppress("FunctionNaming")
@Composable
internal fun FirmaCardReader(
    insertedKey: Any?,
    modifier: Modifier = Modifier,
    card: @Composable () -> Unit,
) {
    val lift = remember(insertedKey) { Animatable(if (insertedKey != null) 1f else 0f) }
    LaunchedEffect(insertedKey) {
        if (insertedKey != null) {
            lift.animateTo(0f, spring(dampingRatio = INSERT_DAMPING, stiffness = INSERT_STIFFNESS))
        }
    }
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = SlotHeight / 2)
                .drawWithContent {
                    // The slot's mouth: whatever is below it is inside the reader.
                    clipRect(top = -size.height, bottom = size.height) { this@drawWithContent.drawContent() }
                }
                .padding(horizontal = CardInset)
                .graphicsLayer {
                    translationY = -lift.value * InsertLift.toPx()
                    alpha = 1f - INSERT_FADE * lift.value
                },
        ) { card() }
        FirmaSlot(modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/** The slot itself: a rounded dark mouth with shade under its upper lip and light on the lower one. */
@Suppress("FunctionNaming")
@Composable
private fun FirmaSlot(modifier: Modifier = Modifier) {
    val slot = Firma.slot
    val lip = Firma.slotLip
    val light = Firma.recessLight
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .height(SlotHeight)
            .drawBehind {
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(light, topLeft = Offset(0f, 1.dp.toPx()), size = size, cornerRadius = radius)
                drawRoundRect(slot, cornerRadius = radius)
                drawRoundRect(
                    lip,
                    topLeft = Offset(size.height / 2, size.height * SLOT_LIP_AT),
                    size = Size(size.width - size.height, 1.dp.toPx()),
                    cornerRadius = CornerRadius(1.dp.toPx()),
                )
            },
    )
}

/** The certificate card body: chip, the F mark and whatever the card prints. */
@Suppress("FunctionNaming")
@Composable
internal fun FirmaCard(
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val card = Firma.card
    val edge = Firma.cardEdge
    val shade = Firma.cardShade
    val chip = Firma.chip
    val chipLine = Firma.chipLine
    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val radius = CornerRadius(15.dp.toPx())
                drawRoundRect(shade, topLeft = Offset(0f, 2.dp.toPx()), size = size, cornerRadius = radius)
                drawRoundRect(card, cornerRadius = radius)
                drawRoundRect(edge, cornerRadius = radius, style = Stroke(1.dp.toPx()))
                drawRect(
                    Brush.verticalGradient(
                        CARD_FALLOFF to Color.Transparent,
                        1f to shade.copy(alpha = shade.alpha * 2.4f),
                    ),
                )
            }
            .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(modifier = Modifier.size(width = 40.dp, height = 31.dp)) { drawChip(chip, chipLine) }
            Spacer(modifier = Modifier.weight(1f))
            if (locked) {
                Icon(
                    painter = painterResource(R.drawable.ic_ui_lock),
                    contentDescription = null,
                    tint = Firma.cardMuted,
                    modifier = Modifier.padding(end = 10.dp).size(17.dp),
                )
            }
            FirmaMark()
        }
        Spacer(modifier = Modifier.height(14.dp))
        content()
    }
}

/** The black F badge printed in the card's corner. */
@Suppress("FunctionNaming")
@Composable
private fun FirmaMark(size: Dp = 26.dp) {
    val face = Firma.slot
    Box(
        modifier = Modifier
            .size(size)
            .drawBehind { drawRoundRect(face, cornerRadius = CornerRadius(7.dp.toPx())) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "F",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** A small printed caption on the card, such as "titular". */
@Suppress("FunctionNaming")
@Composable
internal fun FirmaCardLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = Firma.cardMuted,
        style = MaterialTheme.typography.bodySmall,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * One printed field: the visual caption and value, announced as the full localized sentence
 * (for example "Titular: Persona de Prueba") so the shortened print never loses meaning.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
internal fun FirmaCardField(
    label: String,
    value: String,
    spoken: String,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
    maxLines: Int = 1,
) {
    Column(modifier = modifier.clearAndSetSemantics { text = AnnotatedString(spoken) }) {
        FirmaCardLabel(label)
        Spacer(modifier = Modifier.height(1.dp))
        Text(
            text = value,
            color = Firma.cardInk,
            style = if (mono) {
                firmaMono(15.sp, letterSpacing = 1.sp)
            } else {
                MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp, lineHeight = 19.sp)
            },
            fontWeight = if (mono) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The empty reader: a dashed outline where the card goes and an arrow into the slot. */
@Suppress("FunctionNaming")
@Composable
internal fun FirmaGhostCard(text: String?, modifier: Modifier = Modifier) {
    val line = JuntaMutedInk.copy(alpha = 0.55f)
    val wash = Firma.recess.copy(alpha = 0.45f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 150.dp)
            .drawBehind {
                val radius = CornerRadius(15.dp.toPx())
                drawRoundRect(wash, cornerRadius = radius)
                drawRoundRect(
                    line,
                    cornerRadius = radius,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(GHOST_DASH * density, GHOST_DASH * density),
                        ),
                    ),
                )
            }
            .padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (text != null) {
            Text(
                text = text,
                color = JuntaMutedInk,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(14.dp))
        }
        Canvas(modifier = Modifier.size(width = 22.dp, height = 26.dp)) { drawInsertArrow(line) }
    }
}

@Suppress("MagicNumber") // Proportions of the drawn arrow.
private fun DrawScope.drawInsertArrow(color: Color) {
    val width = 2.dp.toPx()
    val cx = size.width / 2
    val tip = Offset(cx, size.height * 0.84f)
    val wing = size.width * 0.36f
    drawLine(color, Offset(cx, 0f), Offset(cx, size.height * 0.82f), width, StrokeCap.Round)
    drawLine(color, Offset(cx - wing, size.height / 2), tip, width, StrokeCap.Round)
    drawLine(color, Offset(cx + wing, size.height / 2), tip, width, StrokeCap.Round)
}

@Suppress("MagicNumber") // Proportions of the drawn contact pads.
private fun DrawScope.drawChip(fill: Color, line: Color) {
    val radius = CornerRadius(6.dp.toPx())
    drawRoundRect(fill, cornerRadius = radius)
    val w = size.width
    val h = size.height
    val stroke = 1.dp.toPx()
    drawRoundRect(line, cornerRadius = radius, style = Stroke(stroke))
    val left = w * 0.32f
    val right = w * 0.68f
    drawLine(line, Offset(left, 0f), Offset(left, h), stroke)
    drawLine(line, Offset(right, 0f), Offset(right, h), stroke)
    for (y in listOf(h * 0.34f, h * 0.66f)) {
        drawLine(line, Offset(0f, y), Offset(left, y), stroke)
        drawLine(line, Offset(right, y), Offset(w, y), stroke)
    }
    drawLine(line, Offset(left, h * 0.5f), Offset(right, h * 0.5f), stroke)
}

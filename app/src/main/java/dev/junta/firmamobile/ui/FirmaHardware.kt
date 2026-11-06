package dev.junta.firmamobile.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.ui.theme.Firma

private const val FACE_SHEEN_ALPHA = 0.18f
private const val GRAPHITE_SHEEN_ALPHA = 0.07f
private const val CONTACT_SHADOW_ALPHA = 0.1f
private const val KEY_DAMPING = 0.72f
private const val KEY_SHEEN_FADE = 0.45f
private const val RECESS_REACH_MIN = 0.02f
private const val RECESS_REACH_MAX = 0.6f
private const val RECESS_SIDE_MIN = 0.005f
private const val RECESS_SIDE_MAX = 0.2f
private const val GLARE_EDGE = 0.38f
private const val GLARE_EDGE_ALPHA = 0.012f
private const val GLARE_FALLOFF = 0.01f

/** The three key families printed on the device. */
internal enum class FirmaKeyStyle { ORANGE, GRAPHITE, LIGHT }

@Immutable
internal class FirmaKeyColors(val face: Color, val depth: Color, val ink: Color)

internal val FirmaKeyStyle.colors: FirmaKeyColors
    @Composable
    @ReadOnlyComposable
    get() = when (this) {
        FirmaKeyStyle.ORANGE -> FirmaKeyColors(Firma.keyOrange, Firma.keyOrangeDepth, Firma.keyOrangeInk)
        FirmaKeyStyle.GRAPHITE -> FirmaKeyColors(Firma.keyGraphite, Firma.keyGraphiteDepth, Firma.keyGraphiteInk)
        FirmaKeyStyle.LIGHT -> FirmaKeyColors(Firma.keyLight, Firma.keyLightDepth, Firma.keyLightInk)
    }

/**
 * A physical key: a face that stands [depth] above its own darker side. Pressing sinks the face
 * onto the side with a short haptic tick. A disabled key rests sunk and unlit. The whole key,
 * side included, is one touch target; focus draws an accent ring instead of a ripple.
 * With a null [onClick] the key is only the visible cap of a larger target that shares
 * [interactionSource], such as a key with its printed caption.
 */
@Suppress("LongParameterList", "FunctionNaming")
@Composable
internal fun FirmaKey(
    onClick: (() -> Unit)?,
    style: FirmaKeyStyle,
    shape: Shape,
    modifier: Modifier = Modifier,
    depth: Dp = 3.dp,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = Role.Button,
    interactionSource: MutableInteractionSource? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val colors = style.colors
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(pressed) {
        if (pressed && enabled) haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
    }
    val sunk by animateFloatAsState(
        targetValue = if (pressed || !enabled) 1f else 0f,
        animationSpec = spring(dampingRatio = KEY_DAMPING, stiffness = Spring.StiffnessHigh),
        label = "key-travel",
    )
    val sheen = if (style == FirmaKeyStyle.GRAPHITE) GRAPHITE_SHEEN_ALPHA else FACE_SHEEN_ALPHA
    Box(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = enabled,
                        onClickLabel = onClickLabel,
                        role = role,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .keySide(shape, depth, side = colors.depth.takeIf { enabled })
            .padding(bottom = depth)
            .graphicsLayer { translationY = sunk * depth.toPx() }
            .firmaFocusRing(shape, Firma.accent, visible = focused)
            .keyFace(shape, face = if (enabled) colors.face else Firma.recess, sheen = sheen.takeIf { enabled }),
        contentAlignment = contentAlignment,
        content = content,
    )
}

/** The key's own darker side under the face, plus a faint contact shadow on the body. */
private fun Modifier.keySide(shape: Shape, depth: Dp, side: Color?): Modifier = drawBehind {
    val d = depth.toPx()
    val outline = shape.createOutline(Size(size.width, size.height - d), layoutDirection, this)
    translate(top = d + 1.dp.toPx()) {
        drawOutline(outline, Color.Black.copy(alpha = CONTACT_SHADOW_ALPHA))
    }
    if (side != null) translate(top = d) { drawOutline(outline, side) }
}

/** The face itself with a soft sheen from the top; the printed content goes on top of it. */
private fun Modifier.keyFace(shape: Shape, face: Color, sheen: Float?): Modifier = drawWithContent {
    val outline = shape.createOutline(size, layoutDirection, this)
    drawOutline(outline, face)
    if (sheen != null) {
        clipOutline(outline) {
            drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = sheen), KEY_SHEEN_FADE to Color.Transparent))
        }
    }
    drawContent()
}

/** Accent ring [gap] outside [shape] while [visible]: the focus cue that replaces a ripple. */
internal fun Modifier.firmaFocusRing(shape: Shape, color: Color, visible: Boolean, gap: Dp = 3.dp): Modifier =
    drawWithContent {
        drawContent()
        if (visible) {
            val inset = gap.toPx()
            val outline = shape.createOutline(
                Size(size.width + inset * 2, size.height + inset * 2),
                layoutDirection,
                this,
            )
            translate(-inset, -inset) { drawOutline(outline, color, style = Stroke(2.dp.toPx())) }
        }
    }

private inline fun DrawScope.clipOutline(outline: Outline, block: DrawScope.() -> Unit) {
    val path = Path().apply { addOutline(outline) }
    clipPath(path) { block() }
}

/**
 * A recess moulded into the body: darker fill, a soft shade under the upper lip and one bright
 * line where the lower lip catches the light.
 */
internal fun Modifier.firmaRecess(
    shape: Shape,
    fill: Color,
    shade: Color,
    lip: Color,
    depth: Dp = 4.dp,
): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    translate(top = 1.dp.toPx()) { drawOutline(outline, lip) }
    drawOutline(outline, fill)
    clipOutline(outline) {
        val reach = (depth.toPx() * 2f / size.height).coerceIn(RECESS_REACH_MIN, RECESS_REACH_MAX)
        drawRect(Brush.verticalGradient(0f to shade, reach to Color.Transparent))
        val side = (depth.toPx() / size.width).coerceIn(RECESS_SIDE_MIN, RECESS_SIDE_MAX)
        drawRect(
            Brush.horizontalGradient(
                0f to shade.copy(alpha = shade.alpha * 0.5f),
                side to Color.Transparent,
                1f - side to Color.Transparent,
                1f to shade.copy(alpha = shade.alpha * 0.35f),
            ),
        )
    }
}

/** [firmaRecess] with the body tokens of the current theme. */
@Composable
internal fun Modifier.firmaBodyRecess(shape: Shape, depth: Dp = 4.dp): Modifier =
    firmaRecess(shape, Firma.recess, Firma.recessShade, Firma.recessLight, depth)

/** The dark display window: a deep recess with a faint glass reflection across the top. */
@Composable
internal fun Modifier.firmaDisplay(shape: Shape): Modifier {
    val fill = Firma.display
    val lip = Firma.recessLight
    return this
        .firmaRecess(shape, fill, Color.Black.copy(alpha = 0.55f), lip, depth = 6.dp)
        .drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)
            clipOutline(outline) {
                drawRect(
                    Brush.linearGradient(
                        0f to Color.White.copy(alpha = 0.045f),
                        GLARE_EDGE to Color.White.copy(alpha = GLARE_EDGE_ALPHA),
                        GLARE_EDGE + GLARE_FALLOFF to Color.Transparent,
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                )
            }
        }
}

/** A small engraved glyph tile: a round recess with an ink icon, for non-interactive marks. */
@Suppress("FunctionNaming")
@Composable
internal fun FirmaEngraved(modifier: Modifier = Modifier, size: Dp, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .size(size)
            .firmaBodyRecess(androidx.compose.foundation.shape.CircleShape, depth = 2.dp)
            .padding(1.dp),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * A small key whose touch target is at least 48dp even when its printed face is smaller.
 * The face shares the target's interactions, so it still sinks and shows the focus ring.
 */
@Suppress("LongParameterList", "FunctionNaming")
@Composable
internal fun FirmaCompactKey(
    onClick: () -> Unit,
    style: FirmaKeyStyle,
    shape: Shape,
    faceModifier: Modifier,
    modifier: Modifier = Modifier,
    depth: Dp = 3.dp,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        FirmaKey(
            onClick = null,
            style = style,
            shape = shape,
            modifier = faceModifier,
            depth = depth,
            enabled = enabled,
            interactionSource = interaction,
            content = content,
        )
    }
}

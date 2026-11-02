package dev.junta.firmamobile.catalog

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.ui.JuntaElevatedPanel
import dev.junta.firmamobile.ui.JuntaMonogram
import dev.junta.firmamobile.ui.JuntaTone
import dev.junta.firmamobile.ui.accent
import dev.junta.firmamobile.ui.brush
import dev.junta.firmamobile.ui.drawSparkle
import dev.junta.firmamobile.ui.juntaToneFor
import dev.junta.firmamobile.ui.light
import dev.junta.firmamobile.ui.theme.JuntaHairline
import dev.junta.firmamobile.ui.theme.JuntaInk
import dev.junta.firmamobile.ui.theme.JuntaMutedInk
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

private const val CUE_TINT_ALPHA = 0.12f
private const val VERIFIED_TINT_ALPHA = 0.14f
private const val STAR_POP_MILLIS = 420
private const val STAR_POP_PEAK_AT = STAR_POP_MILLIS / 3
private const val STAR_POP_DIP_AT = STAR_POP_MILLIS * 2 / 3
private const val STAR_POP_PEAK = 1.32f
private const val STAR_POP_DIP = 0.92f
private const val BURST_MILLIS = 560
private const val BURST_RAYS = 6

/** The whole card opens the site; rare options stay behind long-press and the accessibility menu. */
@Suppress("LongParameterList", "FunctionNaming")
@Composable
internal fun CatalogPortalCard(
    portal: PortalCatalogItem,
    isFavorite: Boolean,
    onOpen: (PortalCatalogItem) -> Unit,
    onOptions: (PortalCatalogItem) -> Unit,
    onToggleFavorite: (PortalCatalogItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val optionsLabel = stringResource(R.string.catalog_site_options)
    JuntaElevatedPanel(
        modifier = modifier
            .pointerInput(portal.portalId, onOptions) {
                detectTapGestures(onLongPress = { onOptions(portal) })
            }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(optionsLabel) { onOptions(portal); true },
                )
            },
        testTag = "catalog-card-${portal.portalId.value}",
        padded = false,
    ) {
        Box {
            CatalogPortalOpenSurface(
                portal = portal,
                optionsLabel = optionsLabel,
                onOpen = { onOpen(portal) },
                onOptions = { onOptions(portal) },
            )
            CatalogFavoriteStar(
                isFavorite = isFavorite,
                onToggle = { onToggleFavorite(portal) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp)
                    .testTag("catalog-favorite-${portal.portalId.value}"),
            )
        }
        CatalogPortalVerifiedBadge(portal)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Suppress("FunctionNaming")
@Composable
private fun CatalogPortalOpenSurface(
    portal: PortalCatalogItem,
    optionsLabel: String,
    onOpen: () -> Unit,
    onOptions: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interaction,
                indication = ripple(),
                enabled = portal.canOpen,
                role = Role.Button,
                onLongClickLabel = optionsLabel,
                onLongClick = onOptions,
                onClick = onOpen,
            )
            .testTag("catalog-open-${portal.portalId.value}")
            .padding(start = 16.dp, top = 16.dp, end = 54.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        JuntaMonogram(name = portal.organization, size = 46.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = portal.displayName,
                color = JuntaInk,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${portal.organization} · ${portal.territory}",
                color = JuntaMutedInk,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(6.dp))
            CatalogOpenCue(tone = juntaToneFor(portal.organization), enabled = portal.canOpen)
        }
    }
}

/** "Open site" cue inside the card's single tap target: ink label plus a tone-coloured arrow. */
@Suppress("FunctionNaming")
@Composable
private fun CatalogOpenCue(tone: JuntaTone, enabled: Boolean) {
    val tint = if (enabled) tone.accent else JuntaMutedInk
    Row(
        modifier = Modifier
            .background(tint.copy(alpha = CUE_TINT_ALPHA), CircleShape)
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.catalog_open_site),
            color = if (enabled) JuntaInk else JuntaMutedInk,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(if (enabled) tone.brush else SolidColor(JuntaHairline), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_ui_arrow_forward),
                contentDescription = null,
                tint = if (enabled) Color.White else JuntaMutedInk,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Saffron star with a small pop and a burst of sparks when a site becomes a favourite. */
@Suppress("FunctionNaming")
@Composable
private fun CatalogFavoriteStar(isFavorite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(if (isFavorite) R.string.catalog_favorite_saved else R.string.catalog_favorite_add)
    val haptics = LocalHapticFeedback.current
    val pop = remember { Animatable(1f) }
    val burst = remember { Animatable(1f) }
    var shownFavorite by remember { mutableStateOf(isFavorite) }
    LaunchedEffect(isFavorite) {
        if (isFavorite == shownFavorite) return@LaunchedEffect
        shownFavorite = isFavorite
        if (isFavorite) {
            launch {
                burst.snapTo(0f)
                burst.animateTo(1f, tween(durationMillis = BURST_MILLIS))
            }
        }
        pop.animateTo(
            targetValue = 1f,
            animationSpec = keyframes {
                durationMillis = STAR_POP_MILLIS
                STAR_POP_PEAK at STAR_POP_PEAK_AT
                STAR_POP_DIP at STAR_POP_DIP_AT
            },
        )
    }
    val sparkColor = JuntaTone.SAFFRON.light
    IconButton(
        onClick = {
            haptics.performHapticFeedback(if (isFavorite) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
            onToggle()
        },
        modifier = modifier
            .size(48.dp)
            .drawBehind { drawStarBurst(burst.value, sparkColor) },
    ) {
        Icon(
            painter = painterResource(if (isFavorite) R.drawable.ic_ui_star else R.drawable.ic_ui_star_outline),
            contentDescription = label,
            tint = if (isFavorite) JuntaTone.SAFFRON.accent else JuntaMutedInk,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                },
        )
    }
}

@Suppress("MagicNumber") // Burst geometry relative to the 48 dp touch target.
private fun DrawScope.drawStarBurst(progress: Float, color: Color) {
    if (progress >= 1f) return
    val reach = size.minDimension * (0.22f + 0.3f * progress)
    val fade = 1f - progress
    repeat(BURST_RAYS) { index ->
        val angle = Math.toRadians(index * 360.0 / BURST_RAYS - 90.0)
        val spark = Offset(center.x + reach * cos(angle).toFloat(), center.y + reach * sin(angle).toFloat())
        drawSparkle(spark, 4.dp.toPx() * fade, color.copy(alpha = fade))
    }
}

/** Only an end-to-end verification earns a badge; the catalog notice explains what it means. */
@Suppress("FunctionNaming")
@Composable
private fun CatalogPortalVerifiedBadge(portal: PortalCatalogItem) {
    val verifiedOn = portal.lastVerifiedOn?.takeIf { portal.supportStatus == PortalSupportStatus.VERIFIED_E2E }
        ?: return
    val locale = LocalConfiguration.current.locales[0]
    val date = verifiedOn.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    val mint = JuntaTone.MINT.accent
    Row(
        modifier = Modifier
            .padding(start = 76.dp, end = 16.dp, bottom = 14.dp)
            .background(mint.copy(alpha = VERIFIED_TINT_ALPHA), CircleShape)
            .padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp)
            .testTag("catalog-status-${portal.portalId.value}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_ui_check_circle),
            contentDescription = null,
            tint = mint,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.catalog_verified_on, date),
            color = JuntaInk,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("catalog-verified-on-${portal.portalId.value}"),
        )
    }
}

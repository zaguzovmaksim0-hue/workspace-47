package dev.junta.firmamobile.catalog

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.ui.JuntaGradients
import dev.junta.firmamobile.ui.JuntaIconBubble
import dev.junta.firmamobile.ui.JuntaTone
import dev.junta.firmamobile.ui.accent
import dev.junta.firmamobile.ui.drawGlow
import dev.junta.firmamobile.ui.drawMosaic
import dev.junta.firmamobile.ui.drawSparkle
import dev.junta.firmamobile.ui.juntaNight
import dev.junta.firmamobile.ui.juntaPressScale
import dev.junta.firmamobile.ui.light

private val CatalogHeroShape = RoundedCornerShape(32.dp)
private val RegionChipShape = RoundedCornerShape(22.dp)
private const val INTRO_MILLIS = 1_800
private const val SUBTITLE_ALPHA = 0.9f
private const val LABEL_ALPHA = 0.85f
private const val GLASS_ALPHA = 0.16f
private const val GLASS_EDGE_ALPHA = 0.3f
private const val DARK_GLASS_ALPHA = 0.16f
private const val TILE_STAGGER = 0.18f
private const val TILE_GROW = 0.5f
private const val TILE_START_SCALE = 0.4f

/** One decorative service tile of the hero: icon, tone, resting angle and offset inside the cluster. */
private data class HeroTile(val iconRes: Int, val tone: JuntaTone, val degrees: Float, val x: Dp, val y: Dp)

private val HeroTiles = listOf(
    HeroTile(R.drawable.ic_ui_document, JuntaTone.CORAL, degrees = -12f, x = 0.dp, y = 12.dp),
    HeroTile(R.drawable.ic_ui_globe, JuntaTone.MINT, degrees = 6f, x = 34.dp, y = 0.dp),
    HeroTile(R.drawable.ic_ui_shield, JuntaTone.SAFFRON, degrees = 15f, x = 68.dp, y = 14.dp),
)

/** Indigo-to-blue catalog hero: glass back button, three tumbling service tiles and the region chip. */
@Suppress("FunctionNaming")
@Composable
internal fun CatalogHero(
    regionName: String,
    onBack: () -> Unit,
    onChangeRegion: () -> Unit,
    bottomInset: Dp,
) {
    val colors = if (juntaNight) JuntaGradients.CatalogNight else JuntaGradients.Catalog
    val intro = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        intro.animateTo(1f, tween(durationMillis = INTRO_MILLIS, easing = FastOutSlowInEasing))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = CatalogHeroShape,
                ambientColor = colors.first(),
                spotColor = colors.last(),
            )
            .clip(CatalogHeroShape)
            .background(Brush.linearGradient(colors))
            .drawBehind { drawCatalogHeroDecor(intro.value) }
            .padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 16.dp + bottomInset),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CatalogHeroBackButton(onBack)
            Spacer(modifier = Modifier.weight(1f))
            CatalogHeroTiles(progress = { intro.value })
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.catalog_title),
            modifier = Modifier.padding(horizontal = 8.dp).semantics { heading() },
            color = Color.White,
            fontSize = 30.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.catalog_subtitle),
            modifier = Modifier.padding(horizontal = 8.dp),
            color = Color.White.copy(alpha = SUBTITLE_ALPHA),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(16.dp))
        CatalogRegionChip(regionName = regionName, accent = colors.first(), onClick = onChangeRegion)
    }
}

@Suppress("FunctionNaming")
@Composable
private fun CatalogHeroBackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(Color.White.copy(alpha = GLASS_ALPHA), CircleShape)
                .border(1.dp, Color.White.copy(alpha = GLASS_EDGE_ALPHA), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_ui_arrow_back),
                contentDescription = stringResource(R.string.catalog_back_to_certificate),
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** Three small service tiles that pop in one after another, then rest at a playful angle. */
@Suppress("FunctionNaming")
@Composable
private fun CatalogHeroTiles(progress: () -> Float) {
    Box(modifier = Modifier.size(width = 106.dp, height = 52.dp).clearAndSetSemantics {}) {
        HeroTiles.forEachIndexed { index, tile ->
            JuntaIconBubble(
                iconRes = tile.iconRes,
                tone = tile.tone,
                size = 36.dp,
                iconSize = 18.dp,
                modifier = Modifier
                    .offset(x = tile.x, y = tile.y)
                    .graphicsLayer {
                        val shown = ((progress() - index * TILE_STAGGER) / TILE_GROW).coerceIn(0f, 1f)
                        val scale = TILE_START_SCALE + (1f - TILE_START_SCALE) * shown
                        scaleX = scale
                        scaleY = scale
                        alpha = shown
                        rotationZ = tile.degrees * shown
                    }
                    .shadow(elevation = 6.dp, shape = RoundedCornerShape(percent = 32), spotColor = tile.tone.accent),
            )
        }
    }
}

/** "My region" as a dark-glass chip; the white pill keeps the change action obvious. */
@Suppress("FunctionNaming")
@Composable
private fun CatalogRegionChip(regionName: String, accent: Color, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .juntaPressScale(interaction)
            .clip(RegionChipShape)
            .background(Color.Black.copy(alpha = DARK_GLASS_ALPHA))
            .border(1.dp, Color.White.copy(alpha = GLASS_EDGE_ALPHA), RegionChipShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = Color.White),
                role = Role.Button,
                onClick = onClick,
            )
            .heightIn(min = 60.dp)
            .padding(start = 10.dp, end = 10.dp, top = 9.dp, bottom = 9.dp)
            .testTag("catalog-region-selector"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_ui_location),
                contentDescription = null,
                tint = JuntaTone.CORAL.accent,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.catalog_my_region_label),
                color = Color.White.copy(alpha = LABEL_ALPHA),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = regionName,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = stringResource(R.string.catalog_region_change),
            modifier = Modifier
                .background(Color.White, CircleShape)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            color = accent,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Suppress("MagicNumber") // Decorative geometry as fractions of the hero.
private fun DrawScope.drawCatalogHeroDecor(intro: Float) {
    val w = size.width
    val h = size.height
    drawGlow(JuntaTone.MINT.light, Offset(-w * 0.04f, h * 1.02f), w * 0.6f, alpha = 0.3f)
    drawGlow(JuntaGradients.Gold, Offset(w * 0.9f, -h * 0.04f), w * 0.36f, alpha = 0.24f)
    drawMosaic(Color.White.copy(alpha = 0.1f), cell = 32.dp.toPx(), fromFraction = 0.45f)
    val sparkles = listOf(
        Triple(0.56f, 0.12f, 5f),
        Triple(0.93f, 0.4f, 7f),
        Triple(0.66f, 0.46f, 3.5f),
        Triple(0.84f, 0.06f, 3f),
    )
    sparkles.forEachIndexed { index, (x, y, radius) ->
        val shown = ((intro - 0.25f - index * 0.12f) / 0.4f).coerceIn(0f, 1f)
        drawSparkle(Offset(w * x, h * y), radius.dp.toPx() * shown, Color.White.copy(alpha = 0.9f * shown))
    }
}

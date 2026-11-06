package dev.junta.firmamobile.catalog

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.ui.FirmaCompactKey
import dev.junta.firmamobile.ui.FirmaDivider
import dev.junta.firmamobile.ui.FirmaKeyStyle
import dev.junta.firmamobile.ui.FirmaLed
import dev.junta.firmamobile.ui.FirmaLedState
import dev.junta.firmamobile.ui.firmaMono
import dev.junta.firmamobile.ui.theme.Firma
import dev.junta.firmamobile.ui.theme.JuntaInk
import dev.junta.firmamobile.ui.theme.JuntaMutedInk
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val STAR_POP_MILLIS = 420
private const val STAR_POP_PEAK_AT = STAR_POP_MILLIS / 3
private const val STAR_POP_DIP_AT = STAR_POP_MILLIS * 2 / 3
private const val STAR_POP_PEAK = 1.28f
private const val STAR_POP_DIP = 0.92f

/**
 * One catalog row: the verification LED, name, organisation and domain open the site; the star
 * key keeps it at hand. Rare options stay behind long-press and the accessibility menu.
 */
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(portal.portalId, onOptions) {
                detectTapGestures(onLongPress = { onOptions(portal) })
            }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(optionsLabel) { onOptions(portal); true },
                )
            }
            .testTag("catalog-card-${portal.portalId.value}"),
    ) {
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
                .align(Alignment.CenterEnd)
                .testTag("catalog-favorite-${portal.portalId.value}"),
        )
        FirmaDivider(modifier = Modifier.align(Alignment.BottomCenter))
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
    val verified = portal.supportStatus == PortalSupportStatus.VERIFIED_E2E
    val ink = if (portal.canOpen) JuntaInk else JuntaMutedInk
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = androidx.compose.material3.ripple(),
                enabled = portal.canOpen,
                role = Role.Button,
                onClickLabel = stringResource(R.string.catalog_open_site),
                onLongClickLabel = optionsLabel,
                onLongClick = onOptions,
                onClick = onOpen,
            )
            .testTag("catalog-open-${portal.portalId.value}")
            .padding(start = 2.dp, top = 14.dp, end = 58.dp, bottom = 14.dp),
    ) {
        FirmaLed(
            state = if (verified) FirmaLedState.ON else FirmaLedState.OFF,
            modifier = Modifier.padding(top = 7.dp),
            animate = false,
        )
        Spacer(modifier = Modifier.width(16.dp))
        CatalogPortalTexts(portal, ink, modifier = Modifier.weight(1f))
    }
}

/** Name, organisation, domain and the verification line of one site. */
@Suppress("FunctionNaming")
@Composable
private fun CatalogPortalTexts(portal: PortalCatalogItem, ink: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = portal.displayName,
            color = ink,
            style = MaterialTheme.typography.titleMedium,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = portal.organizationLine(),
            color = JuntaMutedInk,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 13.5.sp,
            lineHeight = 18.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        portal.entryUrl.host?.let { host ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = host.removePrefix("www."),
                color = JuntaMutedInk,
                style = firmaMono(12.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CatalogPortalVerifiedLine(portal)
    }
}

/** Organisation, with the territory only when the organisation's name does not already say it. */
private fun PortalCatalogItem.organizationLine(): String =
    if (territory.isBlank() || organization.contains(territory, ignoreCase = true)) {
        organization
    } else {
        "$organization · $territory"
    }

/** Star key: light when off, orange when the site is kept, with a small pop on change. */
@Suppress("FunctionNaming")
@Composable
private fun CatalogFavoriteStar(isFavorite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(if (isFavorite) R.string.catalog_favorite_saved else R.string.catalog_favorite_add)
    val haptics = LocalHapticFeedback.current
    val pop = remember { Animatable(1f) }
    var shownFavorite by remember { mutableStateOf(isFavorite) }
    LaunchedEffect(isFavorite) {
        if (isFavorite == shownFavorite) return@LaunchedEffect
        shownFavorite = isFavorite
        pop.animateTo(
            targetValue = 1f,
            animationSpec = keyframes {
                durationMillis = STAR_POP_MILLIS
                STAR_POP_PEAK at STAR_POP_PEAK_AT
                STAR_POP_DIP at STAR_POP_DIP_AT
            },
        )
    }
    val style = if (isFavorite) FirmaKeyStyle.ORANGE else FirmaKeyStyle.LIGHT
    FirmaCompactKey(
        onClick = {
            haptics.performHapticFeedback(if (isFavorite) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
            onToggle()
        },
        style = style,
        shape = CircleShape,
        faceModifier = Modifier.size(width = 38.dp, height = 41.dp),
        modifier = modifier.semantics { contentDescription = label },
    ) {
        Icon(
            painter = painterResource(if (isFavorite) R.drawable.ic_ui_star else R.drawable.ic_ui_star_outline),
            contentDescription = null,
            tint = if (isFavorite) Firma.keyOrangeInk else JuntaMutedInk,
            modifier = Modifier
                .size(19.dp)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                },
        )
    }
}

/** Only an end-to-end verification earns the date line; the catalog notice explains what it means. */
@Suppress("FunctionNaming")
@Composable
private fun CatalogPortalVerifiedLine(portal: PortalCatalogItem) {
    val verifiedOn = portal.lastVerifiedOn?.takeIf { portal.supportStatus == PortalSupportStatus.VERIFIED_E2E }
        ?: return
    val locale = LocalConfiguration.current.locales[0]
    val date = verifiedOn.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    Row(
        modifier = Modifier.padding(top = 6.dp).testTag("catalog-status-${portal.portalId.value}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_ui_check_circle),
            contentDescription = null,
            tint = Firma.led,
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.catalog_verified_on, date),
            color = JuntaMutedInk,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 12.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("catalog-verified-on-${portal.portalId.value}"),
        )
    }
}

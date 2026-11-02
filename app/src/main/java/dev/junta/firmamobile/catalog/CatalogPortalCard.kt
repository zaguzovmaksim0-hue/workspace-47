package dev.junta.firmamobile.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.ui.JuntaElevatedPanel
import dev.junta.firmamobile.ui.JuntaFieldShape
import dev.junta.firmamobile.ui.theme.JuntaInk
import dev.junta.firmamobile.ui.theme.JuntaMutedInk
import dev.junta.firmamobile.ui.theme.JuntaPaperElevated
import dev.junta.firmamobile.ui.theme.JuntaTeal
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val CatalogBadgeShape = RoundedCornerShape(8.dp)

/** One visible action. Rare options are an explicit long-press/accessibility
 * menu, never an automatic choice of profile or second navigation attempt. */
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
    ) {
        CatalogPortalHeading(portal, isFavorite, onToggleFavorite)
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = { onOpen(portal) },
                enabled = portal.canOpen,
                modifier = Modifier.heightIn(min = 44.dp).testTag("catalog-open-${portal.portalId.value}"),
                shape = JuntaFieldShape,
                colors = ButtonDefaults.buttonColors(containerColor = JuntaTeal, contentColor = JuntaPaperElevated),
                contentPadding = PaddingValues(start = 18.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            ) {
                Text(
                    stringResource(R.string.catalog_open_site),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(4.dp))
                Icon(painterResource(R.drawable.ic_ui_chevron_right), contentDescription = null,
                    modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun CatalogPortalHeading(
    portal: PortalCatalogItem,
    isFavorite: Boolean,
    onToggleFavorite: (PortalCatalogItem) -> Unit,
) {
    val favoriteLabel = stringResource(
        if (isFavorite) R.string.catalog_favorite_saved else R.string.catalog_favorite_add,
    )
    Row(verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f).padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(portal.displayName, color = JuntaInk, style = MaterialTheme.typography.titleMedium,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text("${portal.organization} · ${portal.territory}",
                color = JuntaMutedInk, style = MaterialTheme.typography.bodySmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            CatalogPortalVerifiedBadge(portal)
        }
        IconButton(
            onClick = { onToggleFavorite(portal) },
            modifier = Modifier.size(48.dp).testTag("catalog-favorite-${portal.portalId.value}"),
        ) {
            Icon(
                painterResource(if (isFavorite) R.drawable.ic_ui_star else R.drawable.ic_ui_star_outline),
                contentDescription = favoriteLabel,
                tint = if (isFavorite) JuntaTeal else JuntaMutedInk,
                modifier = Modifier.size(24.dp),
            )
        }
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
    Row(
        modifier = Modifier
            .padding(top = 4.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CatalogBadgeShape)
            .padding(start = 6.dp, end = 8.dp, top = 3.dp, bottom = 3.dp)
            .testTag("catalog-status-${portal.portalId.value}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_ui_check_circle), contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(R.string.catalog_verified_on, date),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("catalog-verified-on-${portal.portalId.value}"),
        )
    }
}

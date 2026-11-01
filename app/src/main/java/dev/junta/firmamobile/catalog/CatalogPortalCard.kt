package dev.junta.firmamobile.catalog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.ui.JuntaElevatedPanel
import dev.junta.firmamobile.ui.JuntaPrimaryButton
import dev.junta.firmamobile.ui.theme.JuntaHairline
import dev.junta.firmamobile.ui.theme.JuntaInk
import dev.junta.firmamobile.ui.theme.JuntaMutedInk
import dev.junta.firmamobile.ui.theme.JuntaPaper
import dev.junta.firmamobile.ui.theme.JuntaPaperElevated
import dev.junta.firmamobile.ui.theme.JuntaTeal
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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
        CatalogPortalStatusBadge(portal)
        JuntaPrimaryButton(
            text = stringResource(R.string.catalog_open_site),
            onClick = { onOpen(portal) },
            enabled = portal.canOpen,
            modifier = Modifier.testTag("catalog-open-${portal.portalId.value}"),
        )
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
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(portal.displayName, color = JuntaInk, style = MaterialTheme.typography.titleMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${portal.organization} · ${portal.territory}",
                color = JuntaMutedInk, style = MaterialTheme.typography.bodySmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        IconButton(
            onClick = { onToggleFavorite(portal) },
            modifier = Modifier.size(48.dp).testTag("catalog-favorite-${portal.portalId.value}")
                .semantics { contentDescription = favoriteLabel },
        ) {
            Text(if (isFavorite) "★" else "☆",
                color = if (isFavorite) JuntaTeal else JuntaMutedInk,
                style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun CatalogPortalStatusBadge(portal: PortalCatalogItem) {
    val verifiedOn = portal.lastVerifiedOn?.takeIf { portal.supportStatus == PortalSupportStatus.VERIFIED_E2E }
    val verified = verifiedOn != null
    val showUnverified = portal.supportStatus == PortalSupportStatus.IMPLEMENTED_NOT_E2E
    if (!verified && !showUnverified) return
    val label = if (verifiedOn != null) {
        val locale = LocalConfiguration.current.locales[0]
        val date = verifiedOn.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
        stringResource(R.string.catalog_verified_on, date)
    } else {
        stringResource(R.string.compatibility_unverified_short)
    }
    Surface(
        shape = CutCornerShape(6.dp),
        color = if (verified) JuntaTeal else JuntaPaper,
        contentColor = if (verified) JuntaPaperElevated else JuntaInk,
        border = if (verified) null else BorderStroke(1.dp, JuntaHairline),
        modifier = Modifier.testTag("catalog-status-${portal.portalId.value}"),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                .then(if (verified) Modifier.testTag("catalog-verified-on-${portal.portalId.value}") else Modifier),
        )
    }
}

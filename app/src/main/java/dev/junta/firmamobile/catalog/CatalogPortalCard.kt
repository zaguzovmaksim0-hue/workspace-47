package dev.junta.firmamobile.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.ui.theme.JuntaHairline
import dev.junta.firmamobile.ui.theme.JuntaInk
import dev.junta.firmamobile.ui.theme.JuntaMutedInk
import dev.junta.firmamobile.ui.theme.JuntaPaperElevated
import dev.junta.firmamobile.ui.theme.JuntaTeal

/** One visible action. Rare options are an explicit long-press/accessibility
 * menu, never an automatic choice of profile or second navigation attempt. */
@Composable
internal fun CatalogPortalCard(
    portal: PortalCatalogItem,
    isFavorite: Boolean,
    onOpen: (PortalCatalogItem) -> Unit,
    onOptions: (PortalCatalogItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val optionsLabel = stringResource(R.string.catalog_site_options)
    val shape = CutCornerShape(8.dp)
    Column(
        modifier = modifier.fillMaxWidth().background(JuntaPaperElevated, shape)
            .border(1.dp, JuntaHairline, shape).padding(horizontal = 13.dp, vertical = 10.dp)
            .testTag("catalog-card-${portal.portalId.value}")
            .pointerInput(portal.portalId, onOptions) { detectTapGestures(onLongPress = { onOptions(portal) }) }
            .semantics { customActions = listOf(CustomAccessibilityAction(optionsLabel) { onOptions(portal); true }) },
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(portal.displayName, color = JuntaInk, style = MaterialTheme.typography.titleMedium,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text((if (isFavorite) "★  " else "") + "${portal.organization} · ${portal.territory}",
            color = JuntaMutedInk, style = MaterialTheme.typography.bodySmall,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (portal.profileStatus != dev.junta.firmamobile.profile.CompatibilityStatus.VERIFIED_E2E) {
            Text(stringResource(R.string.compatibility_unverified_short),
                color = JuntaMutedInk, style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = { onOpen(portal) }, enabled = portal.canOpen, shape = shape,
            colors = ButtonDefaults.buttonColors(containerColor = JuntaTeal, contentColor = JuntaPaperElevated),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("catalog-open-${portal.portalId.value}")) {
            Text(stringResource(R.string.catalog_open_site))
        }
    }
}

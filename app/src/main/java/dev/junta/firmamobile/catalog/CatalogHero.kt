package dev.junta.firmamobile.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.ui.FirmaCompactKey
import dev.junta.firmamobile.ui.FirmaEngraved
import dev.junta.firmamobile.ui.FirmaKey
import dev.junta.firmamobile.ui.FirmaKeyStyle
import dev.junta.firmamobile.ui.firmaDisplay
import dev.junta.firmamobile.ui.firmaMono
import dev.junta.firmamobile.ui.theme.Firma
import dev.junta.firmamobile.ui.theme.JuntaInk
import dev.junta.firmamobile.ui.theme.JuntaMutedInk

private val CounterShape = RoundedCornerShape(10.dp)

/**
 * Catalog header of the device: the round back key, the title and a small display with the
 * number of services listed, then "my region" with its change key.
 */
@Suppress("FunctionNaming")
@Composable
internal fun CatalogHero(
    regionName: String,
    serviceCount: Int?,
    onBack: () -> Unit,
    onChangeRegion: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CatalogBackKey(onBack)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.catalog_title),
                modifier = Modifier.weight(1f).semantics { heading() },
                color = JuntaInk,
                style = MaterialTheme.typography.headlineSmall,
                fontSize = 19.sp,
                lineHeight = 23.sp,
                letterSpacing = (-0.2).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (serviceCount != null) {
                Spacer(modifier = Modifier.width(8.dp))
                CatalogCounter(serviceCount)
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        CatalogRegionSelector(regionName = regionName, onClick = onChangeRegion)
    }
}

@Suppress("FunctionNaming")
@Composable
private fun CatalogBackKey(onBack: () -> Unit) {
    val description = stringResource(R.string.catalog_back_to_certificate)
    FirmaCompactKey(
        onClick = onBack,
        style = FirmaKeyStyle.GRAPHITE,
        shape = CircleShape,
        faceModifier = Modifier.size(width = 44.dp, height = 47.dp),
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_ui_arrow_back),
            contentDescription = null,
            tint = Firma.keyGraphiteInk,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Number of listed services on a small dark display, value over unit. */
@Suppress("FunctionNaming")
@Composable
private fun CatalogCounter(count: Int) {
    val spoken = pluralStringResource(R.plurals.catalog_section_service_count, count, count)
    Column(
        modifier = Modifier
            .firmaDisplay(CounterShape)
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = count.toString(),
            color = Firma.displayText,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 21.sp,
                lineHeight = 23.sp,
                letterSpacing = 0.sp,
                fontFeatureSettings = "tnum",
            ),
            maxLines = 1,
        )
        Text(
            text = pluralStringResource(R.plurals.catalog_services_unit, count),
            color = Firma.displayMuted,
            style = firmaMono(10.sp),
            maxLines = 1,
        )
    }
}

/** "My region" row; the whole row changes it, the light key only shows where to press. */
@Suppress("FunctionNaming")
@Composable
private fun CatalogRegionSelector(regionName: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .testTag("catalog-region-selector"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FirmaEngraved(size = 40.dp) {
            Icon(
                painter = painterResource(R.drawable.ic_ui_location),
                contentDescription = null,
                tint = Firma.accent,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.catalog_my_region_label),
                color = JuntaMutedInk,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.5.sp,
            )
            Text(
                text = regionName,
                color = JuntaInk,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FirmaKey(
            onClick = null,
            style = FirmaKeyStyle.LIGHT,
            shape = CircleShape,
            interactionSource = interaction,
        ) {
            Text(
                text = stringResource(R.string.catalog_region_change),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = Firma.keyLightInk,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

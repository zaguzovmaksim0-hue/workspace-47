package dev.junta.firmamobile.catalog

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.ui.theme.JuntaHairline
import dev.junta.firmamobile.ui.theme.JuntaInk
import dev.junta.firmamobile.ui.theme.JuntaMutedInk
import dev.junta.firmamobile.ui.theme.JuntaPaper
import dev.junta.firmamobile.ui.theme.JuntaPaperElevated
import dev.junta.firmamobile.ui.theme.JuntaTeal
import dev.junta.firmamobile.ui.theme.JuntaTealDark
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun PortalCatalogScreen(
    state: PortalCatalogUiState,
    onSearchTextChange: (String) -> Unit,
    onSelectRegion: (PortalRegionCode) -> Unit,
    onUseLocation: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onDismissLocationMessage: () -> Unit,
    onToggleFavorite: (PortalId) -> Unit,
    onOpenPortal: (PortalCatalogItem) -> Unit,
    onBackToCertificate: () -> Unit,
    onUserMessageShown: () -> Unit,
    onOpenPublicWeb: ((java.net.URI) -> Unit)? = null,
    onOpenCompatibilityPortal: ((PortalCatalogItem) -> Unit)? = null,
) {
    var regionPickerVisible by rememberSaveable { mutableStateOf(false) }
    var portalOptions by remember { mutableStateOf<PortalCatalogItem?>(null) }
    // Hoisted outside lazy items, deliberately not saved to a state bundle.
    var addressInput by remember { mutableStateOf(CatalogAddressInput.empty()) }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showAddressShortcut by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 } }
    fun openTypedAddress() {
        if (onOpenPublicWeb == null) return
        val submission = addressInput.submit()
        addressInput = submission.state
        submission.destination?.let { destination ->
            focusManager.clearFocus()
            onOpenPublicWeb(destination)
        }
    }
    var expandedRegionalSectionKey by rememberSaveable(
        state.selectedRegion.wireValue,
        state.searchText.isNotBlank(),
    ) {
        mutableStateOf<String?>(null)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val openFailureMessage = stringResource(R.string.catalog_open_failed)
    val locationDetectedMessage = stringResource(R.string.catalog_location_detected)

    BackHandler(onBack = onBackToCertificate)

    LaunchedEffect(state.userMessage) {
        val message = when (state.userMessage) {
            CatalogUserMessage.OPEN_FAILED -> openFailureMessage
            CatalogUserMessage.LOCATION_DETECTED -> locationDetectedMessage
            null -> null
        }
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onUserMessageShown()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(JuntaPaper)) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding().imePadding().testTag("catalog-list"),
            contentPadding = PaddingValues(
                start = 18.dp,
                top = 16.dp,
                end = 18.dp,
                bottom = 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "catalog-header", contentType = "catalog-header") { CatalogHeader(onBackToCertificate) }
            if (onOpenPublicWeb != null) {
                item(key = "catalog-address", contentType = "catalog-address") {
                    CatalogAddressCard(
                        state = addressInput,
                        onEdit = { addressInput = addressInput.edit(it) },
                        onPaste = { addressInput = addressInput.paste(readCatalogClipboardText(context)) },
                        onClear = { addressInput = addressInput.clear() },
                        onSubmit = ::openTypedAddress,
                    )
                }
            }
            if (onOpenCompatibilityPortal != null) {
                item(key = "catalog-universal-afirma", contentType = "catalog-universal-afirma") {
                    Text(
                        text = stringResource(R.string.catalog_universal_afirma_copy),
                        style = MaterialTheme.typography.bodySmall,
                        color = JuntaMutedInk,
                        modifier = Modifier.fillMaxWidth().testTag("catalog-universal-afirma"),
                    )
                }
            }
            item(key = "catalog-region", contentType = "catalog-region") {
                RegionSelectorCard(
                    selectedRegion = state.selectedRegion,
                    onChangeRegion = { regionPickerVisible = true },
                )
            }
            item(key = "catalog-search", contentType = "catalog-search") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.searchText,
                    onValueChange = onSearchTextChange,
                    label = { Text(stringResource(R.string.catalog_search_label)) },
                    singleLine = true,
                    shape = CatalogShape,
                    modifier = Modifier.fillMaxWidth().testTag("catalog-search-field"),
                    trailingIcon = if (state.searchText.isNotEmpty()) {
                        {
                            val label = stringResource(R.string.catalog_search_clear)
                            TextButton(onClick = { onSearchTextChange("") },
                                modifier = Modifier.testTag("catalog-search-clear").semantics { contentDescription = label }) {
                                Text("×")
                            }
                        }
                    } else null,
                )
                }
            }

            item(key = "catalog-options-hint", contentType = "notice") {
                Text(stringResource(R.string.catalog_options_hint), style = MaterialTheme.typography.bodySmall,
                    color = JuntaMutedInk)
            }
            if (state.catalogLoading) {
                item(key = "catalog-loading", contentType = "notice") {
                    Text(stringResource(R.string.catalog_loading), color = JuntaMutedInk,
                        modifier = Modifier.testTag("catalog-loading"))
                }
            } else if (state.sections.isEmpty()) {
                item(key = "catalog-empty", contentType = "catalog-empty") {
                    CatalogNotice(
                        title = stringResource(R.string.catalog_empty_title),
                        text = stringResource(R.string.catalog_empty_copy),
                    )
                }
            } else {
                state.sections.forEach { section ->
                    val sectionKey = section.stableKey
                    val collapsible = state.searchText.isBlank() && section.kind.isRegional
                    val expanded = !collapsible || expandedRegionalSectionKey == sectionKey
                    item(key = "section-$sectionKey", contentType = "section-header") {
                        CatalogSectionHeader(
                            section = section,
                            expanded = expanded,
                            onToggle = if (collapsible) {
                                {
                                    expandedRegionalSectionKey = if (expanded) null else sectionKey
                                }
                            } else {
                                null
                            },
                        )
                    }
                    if (expanded) {
                        items(
                            items = section.items,
                            key = { portal -> "portal-$sectionKey-${portal.portalId.value}" },
                            contentType = { "portal-card" },
                        ) { portal ->
                            CatalogPortalCard(
                                portal = portal,
                                isFavorite = portal.portalId in state.favoritePortalIds,
                                onOpen = onOpenPortal,
                                onOptions = { portalOptions = it },
                            )
                        }
                    }
                }
            }
        }

        if (onOpenPublicWeb != null && showAddressShortcut && snackbarHostState.currentSnackbarData == null) {
            OutlinedButton(
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                colors = ButtonDefaults.outlinedButtonColors(containerColor = JuntaPaperElevated),
                modifier = Modifier.align(Alignment.BottomEnd).safeDrawingPadding().imePadding()
                    .padding(end = 18.dp, bottom = 16.dp).heightIn(min = 48.dp).testTag("catalog-return-to-address"),
            ) { Text(stringResource(R.string.catalog_address_back_to_top)) }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) { snackbarData ->
            Snackbar(
                snackbarData = snackbarData,
                shape = CatalogShape,
                containerColor = JuntaInk,
                contentColor = JuntaPaperElevated,
            )
        }
    }

    if (regionPickerVisible) {
        RegionPickerSheet(
            selectedRegion = state.selectedRegion,
            locationLoading = state.locationState == CatalogLocationState.LOADING,
            onUseLocation = { regionPickerVisible = false; onUseLocation() },
            onDismiss = { regionPickerVisible = false },
            onSelect = { region ->
                onSelectRegion(region)
                regionPickerVisible = false
            },
        )
    }

    portalOptions?.let { portal ->
        AlertDialog(
            onDismissRequest = { portalOptions = null },
            title = { Text(portal.displayName, maxLines = 3, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    TextButton(onClick = { portalOptions = null; onToggleFavorite(portal.portalId) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("catalog-option-favorite")) {
                        Text(stringResource(if (portal.portalId in state.favoritePortalIds) R.string.catalog_favorite_saved else R.string.catalog_favorite_add))
                    }
                    if (onOpenCompatibilityPortal != null && portal.isEnabled) {
                        TextButton(onClick = { portalOptions = null; onOpenCompatibilityPortal(portal) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("catalog-open-profile-${portal.portalId.value}")) {
                            Text(stringResource(R.string.catalog_open_profile_compatibility))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { portalOptions = null }) { Text(stringResource(R.string.catalog_options_close)) } },
            shape = CatalogShape,
            modifier = Modifier.testTag("catalog-site-options"),
        )
    }

    LocationResultDialog(
        state = state.locationState,
        onDismiss = onDismissLocationMessage,
        onChooseManually = {
            onDismissLocationMessage()
            regionPickerVisible = true
        },
        onOpenSettings = {
            onDismissLocationMessage()
            onOpenLocationSettings()
        },
        onOpenAppSettings = {
            onDismissLocationMessage()
            onOpenAppSettings()
        },
    )
}

@Composable
private fun CatalogHeader(onBackToCertificate: () -> Unit) {
    val backLabel = stringResource(R.string.catalog_back_to_certificate)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onBackToCertificate,
                shape = CatalogShape,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = backLabel },
            ) {
                Text("←")
            }
            Text(
                text = stringResource(R.string.catalog_title),
                color = JuntaTealDark,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
        }
        Text(
            text = stringResource(R.string.catalog_subtitle),
            color = JuntaMutedInk,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun RegionSelectorCard(selectedRegion: PortalRegionCode, onChangeRegion: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.catalog_my_region_label), color = JuntaMutedInk, style = MaterialTheme.typography.labelSmall)
            Text(selectedRegion.localizedName(), color = JuntaInk, style = MaterialTheme.typography.titleMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onClick = onChangeRegion, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.catalog_change_region))
        }
    }
}

@Composable
private fun CatalogSectionHeader(
    section: PortalCatalogSection,
    expanded: Boolean,
    onToggle: (() -> Unit)?,
) {
    val expandedState = stringResource(R.string.catalog_section_expanded)
    val collapsedState = stringResource(R.string.catalog_section_collapsed)
    val headerModifier = if (onToggle == null) {
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { heading() }
    } else {
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics(mergeDescendants = true) {
                heading()
                stateDescription = if (expanded) expandedState else collapsedState
            }
            .clickable(role = Role.Button, onClick = onToggle)
    }

    Column {
        HorizontalDivider(color = JuntaTeal, thickness = 2.dp)
        Row(
            modifier = headerModifier.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = section.localizedTitle(),
                    color = JuntaTealDark,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = section.localizedDescription(),
                    color = JuntaMutedInk,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.catalog_section_service_count,
                        section.items.size,
                        section.items.size,
                    ),
                    color = JuntaTeal,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (onToggle != null) {
                Text(
                    text = if (expanded) "\u2212" else "+",
                    color = JuntaTealDark,
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
        }
    }
}

@Composable
private fun CatalogNotice(title: String, text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(JuntaPaperElevated, CatalogShape)
            .border(1.dp, JuntaHairline, CatalogShape)
            .padding(14.dp),
    ) {
        Text(title, color = JuntaTealDark, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(3.dp))
        Text(text, color = JuntaMutedInk, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegionPickerSheet(
    selectedRegion: PortalRegionCode,
    locationLoading: Boolean,
    onUseLocation: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (PortalRegionCode) -> Unit,
) {
    var searchText by rememberSaveable { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val regions = remember(searchText) {
        val needle = searchText.catalogSearchKey()
        (listOf(PortalRegionCode.SPAIN) + PortalRegionCode.selectableRegions).filter { region ->
            needle.isEmpty() || needle in region.spanishName.catalogSearchKey()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = CatalogShape,
        containerColor = JuntaPaper,
        contentColor = JuntaInk,
        dragHandle = {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 128.dp, vertical = 10.dp),
                color = JuntaTeal,
                thickness = 3.dp,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.catalog_choose_region),
                color = JuntaTealDark,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            TextButton(onClick = onUseLocation, enabled = !locationLoading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(if (locationLoading) R.string.catalog_locating else R.string.catalog_use_location))
            }
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it.take(80) },
                label = { Text(stringResource(R.string.catalog_region_search)) },
                singleLine = true,
                shape = CatalogShape,
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(regions, key = { it.wireValue }, contentType = { "region-option" }) { region ->
                    val selectedRegionButton = region == selectedRegion
                    if (selectedRegionButton) {
                        Button(
                            onClick = { onSelect(region) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .semantics { selected = true },
                            shape = CatalogShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = JuntaTeal,
                                contentColor = JuntaPaperElevated,
                            ),
                        ) {
                            Text(region.localizedName())
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onSelect(region) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = CatalogShape,
                        ) {
                            Text(region.localizedName())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationResultDialog(
    state: CatalogLocationState,
    onDismiss: () -> Unit,
    onChooseManually: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    if (state == CatalogLocationState.IDLE || state == CatalogLocationState.LOADING) return
    val title = when (state) {
        CatalogLocationState.PERMISSION_DENIED -> R.string.catalog_location_permission_title
        CatalogLocationState.LOCATION_DISABLED -> R.string.catalog_location_disabled_title
        CatalogLocationState.UNAVAILABLE -> R.string.catalog_location_unavailable_title
        CatalogLocationState.GEOCODING_FAILED -> R.string.catalog_geocoding_failed_title
        CatalogLocationState.LOCATION_TIMEOUT -> R.string.catalog_location_timeout_title
        CatalogLocationState.OUTSIDE_SPAIN -> R.string.catalog_location_outside_title
        CatalogLocationState.IDLE, CatalogLocationState.LOADING -> return
    }
    val copy = when (state) {
        CatalogLocationState.PERMISSION_DENIED -> R.string.catalog_location_permission_copy
        CatalogLocationState.LOCATION_DISABLED -> R.string.catalog_location_disabled_copy
        CatalogLocationState.UNAVAILABLE -> R.string.catalog_location_unavailable_copy
        CatalogLocationState.GEOCODING_FAILED -> R.string.catalog_geocoding_failed_copy
        CatalogLocationState.LOCATION_TIMEOUT -> R.string.catalog_location_timeout_copy
        CatalogLocationState.OUTSIDE_SPAIN -> R.string.catalog_location_outside_copy
        CatalogLocationState.IDLE, CatalogLocationState.LOADING -> return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = CatalogShape,
        containerColor = JuntaPaperElevated,
        titleContentColor = JuntaTealDark,
        textContentColor = JuntaInk,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(copy)) },
        confirmButton = {
            when (state) {
                CatalogLocationState.LOCATION_DISABLED -> {
                    Button(onClick = onOpenSettings, shape = CatalogShape) {
                        Text(stringResource(R.string.catalog_open_location_settings))
                    }
                }
                CatalogLocationState.PERMISSION_DENIED -> {
                    Button(onClick = onOpenAppSettings, shape = CatalogShape) {
                        Text(stringResource(R.string.catalog_open_permissions))
                    }
                }
                else -> {
                    Button(onClick = onChooseManually, shape = CatalogShape) {
                        Text(stringResource(R.string.catalog_choose_manually))
                    }
                }
            }
        },
        dismissButton = {
            if (
                state == CatalogLocationState.LOCATION_DISABLED ||
                state == CatalogLocationState.PERMISSION_DENIED
            ) {
                OutlinedButton(onClick = onChooseManually, shape = CatalogShape) {
                    Text(stringResource(R.string.catalog_choose_manually))
                }
            } else {
                OutlinedButton(onClick = onDismiss, shape = CatalogShape) {
                    Text(stringResource(R.string.close))
                }
            }
        },
    )
}

@Composable
private fun PortalCatalogSection.localizedTitle(): String = when (kind) {
    PortalCatalogSectionKind.FAVORITES -> stringResource(R.string.catalog_section_favorites)
    PortalCatalogSectionKind.RECENT -> stringResource(R.string.catalog_section_recent)
    PortalCatalogSectionKind.SELECTED_REGION -> stringResource(
        R.string.catalog_section_selected_region,
        requireNotNull(regionCode).localizedName(),
    )
    PortalCatalogSectionKind.NATIONAL -> stringResource(R.string.catalog_section_spain)
    PortalCatalogSectionKind.REGION -> requireNotNull(regionCode).localizedName()
    PortalCatalogSectionKind.OTHER_REGIONS -> stringResource(R.string.catalog_section_other_regions)
}

@Composable
private fun PortalCatalogSection.localizedDescription(): String = when (kind) {
    PortalCatalogSectionKind.FAVORITES -> stringResource(R.string.catalog_section_favorites_copy)
    PortalCatalogSectionKind.RECENT -> stringResource(R.string.catalog_section_recent_copy)
    PortalCatalogSectionKind.SELECTED_REGION -> stringResource(R.string.catalog_section_region_copy)
    PortalCatalogSectionKind.NATIONAL -> stringResource(R.string.catalog_section_spain_copy)
    PortalCatalogSectionKind.REGION -> stringResource(R.string.catalog_section_region_copy)
    PortalCatalogSectionKind.OTHER_REGIONS -> stringResource(R.string.catalog_section_other_regions_copy)
}

@Composable
private fun PortalRegionCode.localizedName(): String = stringResource(nameResource)

private val PortalRegionCode.nameResource: Int
    @StringRes get() = when (this) {
        PortalRegionCode.SPAIN -> R.string.region_spain
        PortalRegionCode.ANDALUSIA -> R.string.region_andalusia
        PortalRegionCode.ARAGON -> R.string.region_aragon
        PortalRegionCode.ASTURIAS -> R.string.region_asturias
        PortalRegionCode.CANTABRIA -> R.string.region_cantabria
        PortalRegionCode.CASTILE_AND_LEON -> R.string.region_castile_and_leon
        PortalRegionCode.CASTILE_LA_MANCHA -> R.string.region_castile_la_mancha
        PortalRegionCode.CANARY_ISLANDS -> R.string.region_canary_islands
        PortalRegionCode.CATALONIA -> R.string.region_catalonia
        PortalRegionCode.EXTREMADURA -> R.string.region_extremadura
        PortalRegionCode.GALICIA -> R.string.region_galicia
        PortalRegionCode.BALEARIC_ISLANDS -> R.string.region_balearic_islands
        PortalRegionCode.MURCIA -> R.string.region_murcia
        PortalRegionCode.MADRID -> R.string.region_madrid
        PortalRegionCode.NAVARRE -> R.string.region_navarre
        PortalRegionCode.BASQUE_COUNTRY -> R.string.region_basque_country
        PortalRegionCode.LA_RIOJA -> R.string.region_la_rioja
        PortalRegionCode.VALENCIAN_COMMUNITY -> R.string.region_valencian_community
        PortalRegionCode.CEUTA -> R.string.region_ceuta
        PortalRegionCode.MELILLA -> R.string.region_melilla
    }

private val PortalRegionCode.spanishName: String
    get() = when (this) {
        PortalRegionCode.SPAIN -> "Toda España"
        PortalRegionCode.ANDALUSIA -> "Andalucía"
        PortalRegionCode.ARAGON -> "Aragón"
        PortalRegionCode.ASTURIAS -> "Principado de Asturias"
        PortalRegionCode.CANTABRIA -> "Cantabria"
        PortalRegionCode.CASTILE_AND_LEON -> "Castilla y León"
        PortalRegionCode.CASTILE_LA_MANCHA -> "Castilla-La Mancha"
        PortalRegionCode.CANARY_ISLANDS -> "Canarias"
        PortalRegionCode.CATALONIA -> "Cataluña"
        PortalRegionCode.EXTREMADURA -> "Extremadura"
        PortalRegionCode.GALICIA -> "Galicia"
        PortalRegionCode.BALEARIC_ISLANDS -> "Illes Balears"
        PortalRegionCode.MURCIA -> "Región de Murcia"
        PortalRegionCode.MADRID -> "Comunidad de Madrid"
        PortalRegionCode.NAVARRE -> "Comunidad Foral de Navarra"
        PortalRegionCode.BASQUE_COUNTRY -> "País Vasco"
        PortalRegionCode.LA_RIOJA -> "La Rioja"
        PortalRegionCode.VALENCIAN_COMMUNITY -> "Comunitat Valenciana"
        PortalRegionCode.CEUTA -> "Ciudad Autónoma de Ceuta"
        PortalRegionCode.MELILLA -> "Ciudad Autónoma de Melilla"
    }

private fun String.catalogSearchKey(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase(Locale.ROOT)
    .trim()

private val PortalCatalogSection.stableKey: String
    get() = "${kind.name}-${regionCode?.wireValue.orEmpty()}"

private val PortalCatalogSectionKind.isRegional: Boolean
    get() = when (this) {
        PortalCatalogSectionKind.SELECTED_REGION,
        PortalCatalogSectionKind.NATIONAL,
        PortalCatalogSectionKind.REGION,
        PortalCatalogSectionKind.OTHER_REGIONS,
        -> true
        PortalCatalogSectionKind.FAVORITES,
        PortalCatalogSectionKind.RECENT,
        -> false
    }


private val CatalogShape = CutCornerShape(
    topStart = 7.dp,
    topEnd = 5.dp,
    bottomEnd = 7.dp,
    bottomStart = 5.dp,
)

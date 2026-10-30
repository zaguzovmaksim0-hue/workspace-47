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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
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

@Suppress("LongParameterList", "FunctionNaming")
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
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(onBack = onBackToCertificate)
    DisposableEffect(Unit) {
        onDispose { onSearchTextChange("") }
    }
    CatalogUserMessageEffect(state.userMessage, snackbarHostState, onUserMessageShown)

    Box(modifier = Modifier.fillMaxSize().background(JuntaPaper)) {
        CatalogScreenContent(
            state = state,
            onSearchTextChange = onSearchTextChange,
            onOpenPublicWeb = onOpenPublicWeb,
            onOpenCompatibilityPortal = onOpenCompatibilityPortal,
            onToggleFavorite = onToggleFavorite,
            onOpenPortal = onOpenPortal,
            onBackToCertificate = onBackToCertificate,
            onChangeRegion = { regionPickerVisible = true },
            onShowPortalOptions = { portalOptions = it },
        )
        CatalogSnackbarHost(
            snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    CatalogOverlays(
        state = state,
        regionPickerVisible = regionPickerVisible,
        portalOptions = portalOptions,
        onSelectRegion = onSelectRegion,
        onUseLocation = onUseLocation,
        onToggleFavorite = onToggleFavorite,
        onOpenCompatibilityPortal = onOpenCompatibilityPortal,
        onDismissRegionPicker = { regionPickerVisible = false },
        onDismissPortalOptions = { portalOptions = null },
    )

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

@Suppress("LongParameterList", "FunctionNaming")
@Composable
private fun CatalogOverlays(
    state: PortalCatalogUiState,
    regionPickerVisible: Boolean,
    portalOptions: PortalCatalogItem?,
    onSelectRegion: (PortalRegionCode) -> Unit,
    onUseLocation: () -> Unit,
    onToggleFavorite: (PortalId) -> Unit,
    onOpenCompatibilityPortal: ((PortalCatalogItem) -> Unit)?,
    onDismissRegionPicker: () -> Unit,
    onDismissPortalOptions: () -> Unit,
) {
    if (regionPickerVisible) {
        RegionPickerSheet(
            selectedRegion = state.selectedRegion,
            locationLoading = state.locationState == CatalogLocationState.LOADING,
            onUseLocation = { onDismissRegionPicker(); onUseLocation() },
            onDismiss = onDismissRegionPicker,
            onSelect = { region ->
                onSelectRegion(region)
                onDismissRegionPicker()
            },
        )
    }

    portalOptions?.let { portal ->
        PortalOptionsDialog(
            portal = portal,
            isFavorite = portal.portalId in state.favoritePortalIds,
            canOpenCompatibilityPortal = onOpenCompatibilityPortal != null && portal.isEnabled,
            onDismiss = onDismissPortalOptions,
            onToggleFavorite = onToggleFavorite,
            onOpenCompatibilityPortal = onOpenCompatibilityPortal,
        )
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("FunctionNaming")
@Composable
private fun RegionPickerSheet(
    selectedRegion: PortalRegionCode,
    locationLoading: Boolean,
    onUseLocation: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (PortalRegionCode) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
        RegionPickerContent(selectedRegion, locationLoading, onUseLocation, onSelect)
    }
}

@Suppress("FunctionNaming")
@Composable
private fun RegionPickerContent(
    selectedRegion: PortalRegionCode,
    locationLoading: Boolean,
    onUseLocation: () -> Unit,
    onSelect: (PortalRegionCode) -> Unit,
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
        TextButton(
            onClick = onUseLocation,
            enabled = !locationLoading,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(
                stringResource(
                    if (locationLoading) R.string.catalog_locating else R.string.catalog_use_location,
                ),
            )
        }
        RegionPickerOptions(selectedRegion, onSelect)
    }
}

@Suppress("FunctionNaming")
@Composable
private fun RegionPickerOptions(
    selectedRegion: PortalRegionCode,
    onSelect: (PortalRegionCode) -> Unit,
) {
    val regions = listOf(PortalRegionCode.SPAIN) + PortalRegionCode.selectableRegions
    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(regions, key = { it.wireValue }, contentType = { "region-option" }) { region ->
            if (region == selectedRegion) {
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

@Suppress("FunctionNaming", "ReturnCount")
@Composable
private fun LocationResultDialog(
    state: CatalogLocationState,
    onDismiss: () -> Unit,
    onChooseManually: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    if (state == CatalogLocationState.IDLE || state == CatalogLocationState.LOADING) return
    val title = locationDialogTitle(state) ?: return
    val copy = locationDialogCopy(state) ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = CatalogShape,
        containerColor = JuntaPaperElevated,
        titleContentColor = JuntaTealDark,
        textContentColor = JuntaInk,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(copy)) },
        confirmButton = {
            locationDialogConfirmButton(state, onChooseManually, onOpenSettings, onOpenAppSettings)
        },
        dismissButton = {
            locationDialogDismissButton(state, onChooseManually, onDismiss)
        },
    )
}

@StringRes
private fun locationDialogTitle(state: CatalogLocationState): Int? = when (state) {
    CatalogLocationState.PERMISSION_DENIED -> R.string.catalog_location_permission_title
    CatalogLocationState.LOCATION_DISABLED -> R.string.catalog_location_disabled_title
    CatalogLocationState.UNAVAILABLE -> R.string.catalog_location_unavailable_title
    CatalogLocationState.GEOCODING_FAILED -> R.string.catalog_geocoding_failed_title
    CatalogLocationState.LOCATION_TIMEOUT -> R.string.catalog_location_timeout_title
    CatalogLocationState.OUTSIDE_SPAIN -> R.string.catalog_location_outside_title
    CatalogLocationState.IDLE, CatalogLocationState.LOADING -> null
}

@StringRes
private fun locationDialogCopy(state: CatalogLocationState): Int? = when (state) {
    CatalogLocationState.PERMISSION_DENIED -> R.string.catalog_location_permission_copy
    CatalogLocationState.LOCATION_DISABLED -> R.string.catalog_location_disabled_copy
    CatalogLocationState.UNAVAILABLE -> R.string.catalog_location_unavailable_copy
    CatalogLocationState.GEOCODING_FAILED -> R.string.catalog_geocoding_failed_copy
    CatalogLocationState.LOCATION_TIMEOUT -> R.string.catalog_location_timeout_copy
    CatalogLocationState.OUTSIDE_SPAIN -> R.string.catalog_location_outside_copy
    CatalogLocationState.IDLE, CatalogLocationState.LOADING -> null
}

@Suppress("FunctionNaming")
@Composable
private fun locationDialogConfirmButton(
    state: CatalogLocationState,
    onChooseManually: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
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
}

@Suppress("FunctionNaming")
@Composable
private fun locationDialogDismissButton(
    state: CatalogLocationState,
    onChooseManually: () -> Unit,
    onDismiss: () -> Unit,
) {
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
}

private val CatalogShape = CutCornerShape(8.dp)

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
private fun PortalRegionCode.localizedName(): String = stringResource(
    regionNameResources.getValue(this),
)

private val regionNameResources = mapOf(
    PortalRegionCode.SPAIN to R.string.region_spain,
    PortalRegionCode.ANDALUSIA to R.string.region_andalusia,
    PortalRegionCode.ARAGON to R.string.region_aragon,
    PortalRegionCode.ASTURIAS to R.string.region_asturias,
    PortalRegionCode.CANTABRIA to R.string.region_cantabria,
    PortalRegionCode.CASTILE_AND_LEON to R.string.region_castile_and_leon,
    PortalRegionCode.CASTILE_LA_MANCHA to R.string.region_castile_la_mancha,
    PortalRegionCode.CANARY_ISLANDS to R.string.region_canary_islands,
    PortalRegionCode.CATALONIA to R.string.region_catalonia,
    PortalRegionCode.EXTREMADURA to R.string.region_extremadura,
    PortalRegionCode.GALICIA to R.string.region_galicia,
    PortalRegionCode.BALEARIC_ISLANDS to R.string.region_balearic_islands,
    PortalRegionCode.MURCIA to R.string.region_murcia,
    PortalRegionCode.MADRID to R.string.region_madrid,
    PortalRegionCode.NAVARRE to R.string.region_navarre,
    PortalRegionCode.BASQUE_COUNTRY to R.string.region_basque_country,
    PortalRegionCode.LA_RIOJA to R.string.region_la_rioja,
    PortalRegionCode.VALENCIAN_COMMUNITY to R.string.region_valencian_community,
    PortalRegionCode.CEUTA to R.string.region_ceuta,
    PortalRegionCode.MELILLA to R.string.region_melilla,
)

@Suppress("FunctionNaming")
@Composable
private fun CatalogUserMessageEffect(
    userMessage: CatalogUserMessage?,
    snackbarHostState: SnackbarHostState,
    onUserMessageShown: () -> Unit,
) {
    val openFailureMessage = stringResource(R.string.catalog_open_failed)
    val locationDetectedMessage = stringResource(R.string.catalog_location_detected)

    LaunchedEffect(userMessage) {
        val message = when (userMessage) {
            CatalogUserMessage.OPEN_FAILED -> openFailureMessage
            CatalogUserMessage.LOCATION_DETECTED -> locationDetectedMessage
            null -> null
        }
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onUserMessageShown()
        }
    }
}

@Suppress("LongParameterList", "FunctionNaming")
@Composable
private fun CatalogScreenContent(
    state: PortalCatalogUiState,
    onSearchTextChange: (String) -> Unit,
    onOpenPublicWeb: ((java.net.URI) -> Unit)?,
    onOpenCompatibilityPortal: ((PortalCatalogItem) -> Unit)?,
    onToggleFavorite: (PortalId) -> Unit,
    onOpenPortal: (PortalCatalogItem) -> Unit,
    onBackToCertificate: () -> Unit,
    onChangeRegion: () -> Unit,
    onShowPortalOptions: (PortalCatalogItem) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val searching = state.searchText.isNotBlank()
    var expandedSectionKeys by rememberSaveable(
        state.selectedRegion.wireValue,
        searching,
    ) { mutableStateOf(setOf<String>()) }
    val syncedAddress = rememberSyncedCatalogAddressInput(
        searchText = state.searchText,
        onSearchTextChange = onSearchTextChange,
    )
    val suppressAddressOpening = syncedAddress.suppressTruncatedAddressOpening &&
        syncedAddress.input.text == state.searchText

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding().testTag("catalog-list"),
        contentPadding = PaddingValues(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CatalogTopItems(
            state = state,
            addressInput = syncedAddress.input,
            onAddressInputChange = syncedAddress::update,
            onSearchTextChange = syncedAddress::emitSearchText,
            onOpenPublicWeb = onOpenPublicWeb,
            suppressAddressOpening = suppressAddressOpening,
            onHideKeyboard = { focusManager.clearFocus() },
            onBackToCertificate = onBackToCertificate,
            onChangeRegion = onChangeRegion,
        )
        CatalogResults(
            state = state,
            searching = searching,
            expandedSectionKeys = expandedSectionKeys,
            actions = CatalogSectionActions(
                onToggleSection = { key ->
                    expandedSectionKeys = when {
                        key in expandedSectionKeys -> expandedSectionKeys - key
                        key.startsWith("REGION-") ->
                            expandedSectionKeys.filterNot { it.startsWith("REGION-") }.toSet() + key
                        else -> expandedSectionKeys + key
                    }
                },
                onToggleFavorite = onToggleFavorite,
                onOpenPortal = onOpenPortal,
                onShowPortalOptions = onShowPortalOptions,
            ),
            showFooterCopy = onOpenCompatibilityPortal != null,
        )
    }
}

private class SyncedCatalogAddressInput(
    val input: CatalogAddressInput,
    private val state: MutableState<CatalogAddressInput>,
    private val onSearchTextChange: (String) -> Unit,
    private val pendingSearches: MutableState<List<String>>,
    private val locallyEdited: MutableState<Boolean>,
    val suppressTruncatedAddressOpening: Boolean,
) {
    fun update(value: CatalogAddressInput) {
        locallyEdited.value = true
        state.value = value
    }

    fun emitSearchText(value: String) {
        pendingSearches.value = (pendingSearches.value + value)
            .takeLast(CatalogSearchLimit.MAX_PENDING_SEARCHES)
        onSearchTextChange(value)
    }
}

private fun syncedSearchUpdate(
    pending: List<String>,
    searchText: String,
    draft: CatalogAddressInput,
): Pair<CatalogAddressInput, List<String>> {
    val echoIndex = pending.indexOf(searchText)
    // Without an event identifier, an unknown value cannot be reliably
    // distinguished from an echo evicted from the bounded buffer. Retain the
    // newer draft only for a non-empty value that could be a prefix of it;
    // every other unknown value, including an external reset, is applied so a
    // full pending buffer cannot lock out parent changes.
    val isEvictedEcho = echoIndex < 0 &&
        pending.size == CatalogSearchLimit.MAX_PENDING_SEARCHES &&
        searchText.isNotEmpty() &&
        draft.text.startsWith(searchText)
    val nextDraft = when {
        echoIndex >= 0 || isEvictedEcho -> draft
        searchText.isEmpty() -> draft.clear()
        else -> draft.edit(searchText)
    }
    val nextPending = when {
        echoIndex >= 0 -> pending.drop(echoIndex + 1)
        isEvictedEcho -> pending
        else -> emptyList()
    }
    return nextDraft to nextPending
}

@Composable
private fun rememberSyncedCatalogAddressInput(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
): SyncedCatalogAddressInput {
    val addressInputState = remember {
        mutableStateOf(CatalogAddressInput.empty().edit(searchText))
    }
    var previousSearchText by remember { mutableStateOf(searchText) }
    val pendingSearches = remember { mutableStateOf(emptyList<String>()) }
    val locallyEdited = remember { mutableStateOf(false) }
    val suppressTruncatedAddressOpening = searchText.length == CatalogSearchLimit.CHARS &&
        looksLikeCatalogAddress(searchText) &&
        !locallyEdited.value

    SideEffect {
        if (searchText == previousSearchText) return@SideEffect
        val draft = addressInputState.value
        val (nextDraft, nextPending) = syncedSearchUpdate(
            pending = pendingSearches.value,
            searchText = searchText,
            draft = draft,
        )
        addressInputState.value = nextDraft
        locallyEdited.value = nextDraft == draft
        pendingSearches.value = nextPending
        previousSearchText = searchText
    }

    return SyncedCatalogAddressInput(
        input = addressInputState.value,
        state = addressInputState,
        onSearchTextChange = onSearchTextChange,
        pendingSearches = pendingSearches,
        locallyEdited = locallyEdited,
        suppressTruncatedAddressOpening = suppressTruncatedAddressOpening,
    )
}

@Suppress("LongParameterList", "FunctionNaming")
private fun LazyListScope.CatalogTopItems(
    state: PortalCatalogUiState,
    addressInput: CatalogAddressInput,
    onAddressInputChange: (CatalogAddressInput) -> Unit,
    onSearchTextChange: (String) -> Unit,
    onOpenPublicWeb: ((java.net.URI) -> Unit)?,
    suppressAddressOpening: Boolean,
    onHideKeyboard: () -> Unit,
    onBackToCertificate: () -> Unit,
    onChangeRegion: () -> Unit,
) {
    item(key = "catalog-header", contentType = "catalog-header") {
        CatalogHeader(onBackToCertificate = onBackToCertificate)
    }
    item(key = "catalog-search", contentType = "catalog-search") {
        CatalogSearchAddressField(
            addressInput = addressInput,
            actions = CatalogAddressFieldActions(
                onAddressInputChange = onAddressInputChange,
                onSearchTextChange = onSearchTextChange,
                onOpenPublicWeb = onOpenPublicWeb,
                suppressAddressOpening = suppressAddressOpening,
                onHideKeyboard = onHideKeyboard,
            ),
        )
    }
    item(key = "catalog-region", contentType = "catalog-region") {
        RegionSelectorCard(
            selectedRegion = state.selectedRegion,
            onChangeRegion = onChangeRegion,
        )
    }
}

@Suppress("FunctionNaming")
private fun LazyListScope.CatalogResults(
    state: PortalCatalogUiState,
    searching: Boolean,
    expandedSectionKeys: Set<String>,
    actions: CatalogSectionActions,
    showFooterCopy: Boolean,
) {
    when {
        state.catalogLoading -> CatalogLoadingItem()
        state.sections.isEmpty() -> CatalogEmptyItem()
        else -> CatalogSections(
            state = state,
            searching = searching,
            expandedSectionKeys = expandedSectionKeys,
            actions = actions,
        )
    }
    if (showFooterCopy) {
        CatalogFooterCopyItem()
    }
}

@Suppress("FunctionNaming")
private fun LazyListScope.CatalogLoadingItem() {
    item(key = "catalog-loading", contentType = "notice") {
        Text(
            stringResource(R.string.catalog_loading),
            color = JuntaMutedInk,
            modifier = Modifier.testTag("catalog-loading"),
        )
    }
}

@Suppress("FunctionNaming")
private fun LazyListScope.CatalogEmptyItem() {
    item(key = "catalog-empty", contentType = "catalog-empty") {
        CatalogNotice(
            title = stringResource(R.string.catalog_empty_title),
            text = stringResource(R.string.catalog_empty_copy),
        )
    }
}

@Suppress("FunctionNaming")
private fun LazyListScope.CatalogFooterCopyItem() {
    item(key = "catalog-footer-copy", contentType = "notice") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.catalog_universal_afirma_copy),
                style = MaterialTheme.typography.bodySmall,
                color = JuntaMutedInk,
                modifier = Modifier.fillMaxWidth().testTag("catalog-universal-afirma"),
            )
            Text(
                stringResource(R.string.catalog_options_hint),
                style = MaterialTheme.typography.bodySmall,
                color = JuntaMutedInk,
            )
        }
    }
}

@Suppress("FunctionNaming")
private fun LazyListScope.CatalogSections(
    state: PortalCatalogUiState,
    searching: Boolean,
    expandedSectionKeys: Set<String>,
    actions: CatalogSectionActions,
) {
    state.sections.forEach { section ->
        CatalogSection(
            section = section,
            searching = searching,
            expandedSectionKeys = expandedSectionKeys,
            favoritePortalIds = state.favoritePortalIds,
            actions = actions,
        )
    }
}

@Suppress("FunctionNaming")
private fun LazyListScope.CatalogSection(
    section: PortalCatalogSection,
    searching: Boolean,
    expandedSectionKeys: Set<String>,
    favoritePortalIds: Set<PortalId>,
    actions: CatalogSectionActions,
) {
    val sectionKey = buildString {
        append(section.kind.name)
        section.regionCode?.let { region ->
            append('-')
            append(region.wireValue)
        }
    }
    val collapsible = !searching && section.kind in collapsibleSectionKinds
    val expanded = !collapsible || sectionKey in expandedSectionKeys

    item(key = "section-$sectionKey", contentType = "section-header") {
        CatalogSectionHeader(
            section = section,
            expanded = expanded,
            onToggle = if (collapsible) {
                { actions.onToggleSection(sectionKey) }
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
                isFavorite = portal.portalId in favoritePortalIds,
                onOpen = actions.onOpenPortal,
                onOptions = actions.onShowPortalOptions,
                onToggleFavorite = { actions.onToggleFavorite(it.portalId) },
            )
        }
    }
}

private data class CatalogSectionActions(
    val onToggleSection: (String) -> Unit,
    val onToggleFavorite: (PortalId) -> Unit,
    val onOpenPortal: (PortalCatalogItem) -> Unit,
    val onShowPortalOptions: (PortalCatalogItem) -> Unit,
)

private val collapsibleSectionKinds = setOf(
    PortalCatalogSectionKind.REGION,
    PortalCatalogSectionKind.NATIONAL,
    PortalCatalogSectionKind.OTHER_REGIONS,
)

@Suppress("FunctionNaming")
@Composable
private fun CatalogSnackbarHost(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = modifier.safeDrawingPadding()
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

private data class CatalogAddressFieldActions(
    val onAddressInputChange: (CatalogAddressInput) -> Unit,
    val onSearchTextChange: (String) -> Unit,
    val onOpenPublicWeb: ((java.net.URI) -> Unit)?,
    val suppressAddressOpening: Boolean,
    val onHideKeyboard: () -> Unit,
)

private fun CatalogAddressFieldActions.emitSearchOrAddressTransition(
    previous: CatalogAddressInput,
    next: CatalogAddressInput,
) {
    if (!looksLikeCatalogAddress(next.text)) {
        onSearchTextChange(next.text.take(CatalogSearchLimit.CHARS))
    } else if (!looksLikeCatalogAddress(previous.text)) {
        onSearchTextChange("")
    }
}

@Suppress("FunctionNaming")
@Composable
private fun CatalogSearchAddressField(
    addressInput: CatalogAddressInput,
    actions: CatalogAddressFieldActions,
) {
    val context = LocalContext.current
    val canOpenAddress = actions.onOpenPublicWeb != null

    fun openTypedAddress() {
        if (actions.onOpenPublicWeb == null || actions.suppressAddressOpening) return
        val submission = addressInput.submit()
        actions.onAddressInputChange(submission.state)
        submission.destination?.let { destination ->
            actions.onHideKeyboard()
            actions.onOpenPublicWeb(destination)
        }
    }

    fun updateText(value: String) {
        val next = addressInput.edit(value)
        actions.onAddressInputChange(next)
        actions.emitSearchOrAddressTransition(addressInput, next)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CatalogSearchTextField(
            addressInput = addressInput,
            canOpenAddress = canOpenAddress,
            onSearchTextChange = ::updateText,
            onPaste = {
                val value = readCatalogClipboardText(context)
                val next = addressInput.paste(value)
                actions.onAddressInputChange(next)
                actions.emitSearchOrAddressTransition(addressInput, next)
            },
            onClear = {
                val hadText = addressInput.text.isNotEmpty()
                actions.onAddressInputChange(addressInput.clear())
                if (hadText) {
                    actions.onSearchTextChange("")
                }
            },
            onOpenAddress = ::openTypedAddress,
            onHideKeyboard = actions.onHideKeyboard,
        )
        val addressIntent = looksLikeCatalogAddress(addressInput.text)
        val canShowOpenButton = !actions.suppressAddressOpening &&
            addressInput.canSubmit &&
            (addressInput.destination != null || addressIntent)
        if (canOpenAddress && canShowOpenButton) {
            CatalogOpenAddressButton(onClick = ::openTypedAddress)
        }
    }
}

private object CatalogSearchLimit {
    const val CHARS = 160
    const val MAX_PENDING_SEARCHES = 32
}

private fun looksLikeCatalogAddress(text: String): Boolean {
    val value = text.trim()
    return value.startsWith("http://", ignoreCase = true) ||
        value.startsWith("https://", ignoreCase = true) ||
        (!value.contains("://") && value.contains('.') && !value.contains(' '))
}

@Suppress("LongParameterList", "FunctionNaming")
@Composable
private fun CatalogSearchTextField(
    addressInput: CatalogAddressInput,
    canOpenAddress: Boolean,
    onSearchTextChange: (String) -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit,
    onOpenAddress: () -> Unit,
    onHideKeyboard: () -> Unit,
) {
    val isAddress = addressInput.destination != null || looksLikeCatalogAddress(addressInput.text)
    OutlinedTextField(
        value = addressInput.text,
        onValueChange = onSearchTextChange,
        label = { Text(stringResource(R.string.catalog_search_or_address)) },
        placeholder = { Text("sede.example.org") },
        singleLine = true,
        isError = addressInput.error != null,
        shape = CatalogShape,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Uri,
            imeAction = if (isAddress && canOpenAddress) ImeAction.Go else ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(
            onGo = { onOpenAddress() },
            onDone = { onHideKeyboard() },
        ),
        modifier = Modifier.fillMaxWidth().testTag("public-web-address"),
        trailingIcon = { CatalogSearchFieldActions(addressInput.text, addressInput.error, onPaste, onClear) },
        supportingText = { CatalogAddressErrorText(addressInput.error) },
    )
}

@Suppress("FunctionNaming")
@Composable
private fun CatalogSearchFieldActions(
    searchText: String,
    error: CatalogAddressInput.Error?,
    onPaste: () -> Unit,
    onClear: () -> Unit,
) {
    Row {
        if (searchText.isEmpty()) {
            TextButton(
                onClick = onPaste,
                modifier = Modifier.heightIn(min = 48.dp).testTag("catalog-address-paste"),
            ) {
                Text(stringResource(R.string.catalog_address_paste), style = MaterialTheme.typography.labelMedium)
            }
        }
        if (searchText.isNotEmpty() || error != null) {
            val clearLabel = stringResource(R.string.catalog_address_clear)
            TextButton(
                onClick = onClear,
                modifier = Modifier.heightIn(min = 48.dp).testTag("catalog-address-clear")
                    .semantics { contentDescription = clearLabel },
            ) { Text("×") }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun CatalogAddressErrorText(error: CatalogAddressInput.Error?) {
    if (error == null) return
    Text(
        stringResource(
            when (error) {
                CatalogAddressInput.Error.EMPTY -> R.string.catalog_address_required
                CatalogAddressInput.Error.INVALID -> R.string.catalog_address_invalid
                CatalogAddressInput.Error.TOO_LONG -> R.string.catalog_address_too_long
                CatalogAddressInput.Error.CLIPBOARD_EMPTY -> R.string.catalog_address_clipboard_empty
            },
        ),
        modifier = Modifier.testTag("catalog-address-error"),
    )
}

@Suppress("FunctionNaming")
@Composable
private fun CatalogOpenAddressButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = JuntaTeal, contentColor = JuntaPaperElevated),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("public-web-open-confirm"),
        shape = CatalogShape,
    ) {
        Text(stringResource(R.string.catalog_open_site))
    }
}

@Suppress("FunctionNaming")
@Composable
private fun CatalogHeader(onBackToCertificate: () -> Unit) {
    val backLabel = stringResource(R.string.catalog_back_to_certificate)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onBackToCertificate,
                shape = CatalogShape,
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = backLabel },
            ) { Text("←") }
            Text(
                stringResource(R.string.catalog_title),
                color = JuntaTealDark,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
        }
        Text(
            stringResource(R.string.catalog_subtitle),
            color = JuntaMutedInk,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun RegionSelectorCard(selectedRegion: PortalRegionCode, onChangeRegion: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.catalog_region_inline, selectedRegion.localizedName()),
            color = JuntaInk,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onChangeRegion, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.catalog_region_change))
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun CatalogSectionHeader(
    section: PortalCatalogSection,
    expanded: Boolean,
    onToggle: (() -> Unit)?,
) {
    val expandedState = stringResource(R.string.catalog_section_expanded)
    val collapsedState = stringResource(R.string.catalog_section_collapsed)
    val headerModifier = if (onToggle == null) {
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() }
    } else {
        Modifier.fillMaxWidth().heightIn(min = 64.dp).semantics(mergeDescendants = true) {
            heading()
            stateDescription = if (expanded) expandedState else collapsedState
        }.clickable(role = Role.Button, onClick = onToggle)
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
                Text(section.localizedTitle(), color = JuntaTealDark, style = MaterialTheme.typography.titleLarge)
                Text(section.localizedDescription(), color = JuntaMutedInk, style = MaterialTheme.typography.bodySmall)
                val serviceCount = section.items.size
                Text(
                    pluralStringResource(
                        R.plurals.catalog_section_service_count,
                        serviceCount,
                        serviceCount,
                    ),
                    color = JuntaTeal,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (onToggle != null) {
                Text(
                    if (expanded) "\u2212" else "+",
                    color = JuntaTealDark,
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun CatalogNotice(title: String, text: String) {
    Column(
        modifier = Modifier.fillMaxWidth().background(JuntaPaperElevated, CatalogShape)
            .border(1.dp, JuntaHairline, CatalogShape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, color = JuntaTealDark, style = MaterialTheme.typography.titleMedium)
        Text(text, color = JuntaMutedInk, style = MaterialTheme.typography.bodyMedium)
    }
}

@Suppress("LongParameterList", "FunctionNaming")
@Composable
private fun PortalOptionsDialog(
    portal: PortalCatalogItem,
    isFavorite: Boolean,
    canOpenCompatibilityPortal: Boolean,
    onDismiss: () -> Unit,
    onToggleFavorite: (PortalId) -> Unit,
    onOpenCompatibilityPortal: ((PortalCatalogItem) -> Unit)?,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(portal.displayName, maxLines = 3, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                TextButton(
                    onClick = {
                        onDismiss()
                        onToggleFavorite(portal.portalId)
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("catalog-option-favorite"),
                ) {
                    Text(
                        stringResource(
                            if (isFavorite) R.string.catalog_favorite_saved else R.string.catalog_favorite_add,
                        ),
                    )
                }
                if (canOpenCompatibilityPortal && onOpenCompatibilityPortal != null) {
                    TextButton(
                        onClick = {
                            onDismiss()
                            onOpenCompatibilityPortal(portal)
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .testTag("catalog-open-profile-${portal.portalId.value}"),
                    ) {
                        Text(stringResource(R.string.catalog_open_profile_compatibility))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.catalog_options_close))
            }
        },
        shape = CatalogShape,
        modifier = Modifier.testTag("catalog-site-options"),
    )
}

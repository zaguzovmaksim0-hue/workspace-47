package dev.junta.firmamobile.catalog

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.ui.FirmaCompactKey
import dev.junta.firmamobile.ui.FirmaKeyStyle
import dev.junta.firmamobile.ui.JuntaBackdrop
import dev.junta.firmamobile.ui.JuntaFieldShape
import dev.junta.firmamobile.ui.JuntaIconBubble
import dev.junta.firmamobile.ui.JuntaMonogram
import dev.junta.firmamobile.ui.JuntaPanelShape
import dev.junta.firmamobile.ui.JuntaPrimaryButton
import dev.junta.firmamobile.ui.JuntaTone
import dev.junta.firmamobile.ui.firmaBodyRecess
import dev.junta.firmamobile.ui.theme.Firma
import dev.junta.firmamobile.ui.theme.JuntaInk
import dev.junta.firmamobile.ui.theme.JuntaMutedInk
import dev.junta.firmamobile.ui.theme.JuntaPaper
import dev.junta.firmamobile.ui.theme.JuntaPaperElevated
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
    onOpenUniversalPortal: ((PortalCatalogItem) -> Unit)? = null,
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
        CatalogBackgroundArt()
        CatalogScreenContent(
            state = state,
            onSearchTextChange = onSearchTextChange,
            onOpenPublicWeb = onOpenPublicWeb,
            onOpenUniversalPortal = onOpenUniversalPortal,
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
        onOpenUniversalPortal = onOpenUniversalPortal,
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
    onOpenUniversalPortal: ((PortalCatalogItem) -> Unit)?,
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
            canOpenUniversalPortal = onOpenUniversalPortal != null && portal.isEnabled,
            onDismiss = onDismissPortalOptions,
            onToggleFavorite = onToggleFavorite,
            onOpenUniversalPortal = onOpenUniversalPortal,
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
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = JuntaPaper,
        contentColor = JuntaInk,
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            JuntaIconBubble(iconRes = R.drawable.ic_ui_location, tone = JuntaTone.CORAL, size = 40.dp, iconSize = 20.dp)
            Text(
                text = stringResource(R.string.catalog_choose_region),
                color = JuntaInk,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
        }
        JuntaPrimaryButton(
            text = stringResource(
                if (locationLoading) R.string.catalog_locating else R.string.catalog_use_location,
            ),
            onClick = onUseLocation,
            enabled = !locationLoading,
            iconRes = R.drawable.ic_ui_location,
        )
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
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(regions, key = { it.wireValue }, contentType = { "region-option" }) { region ->
            RegionPickerOption(
                name = region.localizedName(),
                selected = region == selectedRegion,
                onClick = { onSelect(region) },
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun RegionPickerOption(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(JuntaFieldShape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else JuntaPaperElevated)
            .semantics { this.selected = selected }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        JuntaMonogram(name = name, size = 36.dp)
        Text(
            text = name,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else JuntaInk,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_ui_check_circle),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
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

private val CatalogShape = RoundedCornerShape(16.dp)

private val SearchShape = RoundedCornerShape(18.dp)

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
    onOpenUniversalPortal: ((PortalCatalogItem) -> Unit)?,
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
        contentPadding = PaddingValues(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 88.dp),
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
            showFooterCopy = onOpenUniversalPortal != null,
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
        val sections = state.sections
        val serviceCount = remember(sections) {
            sections.flatMapTo(HashSet()) { section -> section.items.map { it.portalId } }.size
        }
        CatalogHero(
            regionName = state.selectedRegion.localizedName(),
            serviceCount = serviceCount.takeUnless { state.catalogLoading },
            onBack = onBackToCertificate,
            onChangeRegion = onChangeRegion,
        )
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
            modifier = Modifier.padding(top = 14.dp),
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
        CatalogLoadingNotice(modifier = Modifier.padding(top = 22.dp))
    }
}

@Suppress("FunctionNaming")
private fun LazyListScope.CatalogEmptyItem() {
    item(key = "catalog-empty", contentType = "catalog-empty") {
        CatalogEmptyNotice(
            title = stringResource(R.string.catalog_empty_title),
            text = stringResource(R.string.catalog_empty_copy),
            modifier = Modifier.padding(top = 22.dp),
        )
    }
}

@Suppress("FunctionNaming")
private fun LazyListScope.CatalogFooterCopyItem() {
    item(key = "catalog-footer-copy", contentType = "notice") {
        CatalogFooterTips(modifier = Modifier.padding(top = 22.dp))
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
            title = section.localizedTitle(),
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
    modifier: Modifier = Modifier,
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

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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

@Suppress("LongParameterList", "FunctionNaming", "LongMethod")
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
    val isError = addressInput.error != null
    val errorMessage = addressInput.error?.let { stringResource(it.messageRes) }
    val interaction = remember { MutableInteractionSource() }
    val colors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = JuntaInk,
        unfocusedTextColor = JuntaInk,
        errorTextColor = JuntaInk,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        errorContainerColor = Color.Transparent,
        cursorColor = Firma.accent,
        errorCursorColor = MaterialTheme.colorScheme.error,
        focusedBorderColor = Firma.accent,
        unfocusedBorderColor = Color.Transparent,
        errorBorderColor = MaterialTheme.colorScheme.error,
        focusedLeadingIconColor = JuntaMutedInk,
        unfocusedLeadingIconColor = JuntaMutedInk,
        errorLeadingIconColor = MaterialTheme.colorScheme.error,
        focusedPlaceholderColor = JuntaMutedInk,
        unfocusedPlaceholderColor = JuntaMutedInk,
        errorPlaceholderColor = JuntaMutedInk,
    )
    BasicTextField(
        value = addressInput.text,
        onValueChange = onSearchTextChange,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { if (errorMessage != null) error(errorMessage) }
            .testTag("public-web-address"),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = JuntaInk),
        cursorBrush = SolidColor(if (isError) MaterialTheme.colorScheme.error else Firma.accent),
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
        singleLine = true,
        interactionSource = interaction,
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = addressInput.text,
                innerTextField = innerTextField,
                enabled = true,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interaction,
                isError = isError,
                placeholder = {
                    Text(
                        stringResource(R.string.catalog_search_or_address),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_ui_search),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
                trailingIcon = { CatalogSearchFieldActions(addressInput.text, addressInput.error, onPaste, onClear) },
                supportingText = addressInput.error?.let { error -> { CatalogAddressErrorText(error) } },
                colors = colors,
                contentPadding = OutlinedTextFieldDefaults.contentPadding(top = 13.dp, bottom = 13.dp),
                container = {
                    Box(modifier = Modifier.fillMaxSize().firmaBodyRecess(SearchShape, depth = 3.dp))
                    OutlinedTextFieldDefaults.Container(
                        enabled = true,
                        isError = isError,
                        interactionSource = interaction,
                        colors = colors,
                        shape = SearchShape,
                        focusedBorderThickness = 2.dp,
                        unfocusedBorderThickness = 1.dp,
                    )
                },
            )
        },
    )
}

/** Paste is a small graphite key inside the recess; once there is text it gives way to clear. */
@Suppress("FunctionNaming")
@Composable
private fun CatalogSearchFieldActions(
    searchText: String,
    error: CatalogAddressInput.Error?,
    onPaste: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (searchText.isEmpty()) {
            FirmaCompactKey(
                onClick = onPaste,
                style = FirmaKeyStyle.GRAPHITE,
                shape = CircleShape,
                faceModifier = Modifier.heightIn(min = 34.dp),
                modifier = Modifier.testTag("catalog-address-paste"),
            ) {
                Text(
                    text = stringResource(R.string.catalog_address_paste),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    color = Firma.keyGraphiteInk,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }
        if (searchText.isNotEmpty() || error != null) {
            IconButton(
                onClick = onClear,
                modifier = Modifier.testTag("catalog-address-clear"),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_ui_close),
                    contentDescription = stringResource(R.string.catalog_address_clear),
                    tint = JuntaMutedInk,
                )
            }
        }
    }
}

@get:StringRes
private val CatalogAddressInput.Error.messageRes: Int
    get() = when (this) {
        CatalogAddressInput.Error.EMPTY -> R.string.catalog_address_required
        CatalogAddressInput.Error.INVALID -> R.string.catalog_address_invalid
        CatalogAddressInput.Error.TOO_LONG -> R.string.catalog_address_too_long
        CatalogAddressInput.Error.CLIPBOARD_EMPTY -> R.string.catalog_address_clipboard_empty
    }

@Suppress("FunctionNaming")
@Composable
private fun CatalogAddressErrorText(error: CatalogAddressInput.Error?) {
    if (error == null) return
    Text(
        stringResource(error.messageRes),
        modifier = Modifier.testTag("catalog-address-error"),
    )
}

@Suppress("FunctionNaming")
@Composable
private fun CatalogBackgroundArt() {
    JuntaBackdrop(modifier = Modifier.fillMaxSize().testTag("jfm-catalog-background"))
}

@Suppress("FunctionNaming")
@Composable
private fun CatalogOpenAddressButton(onClick: () -> Unit) {
    JuntaPrimaryButton(
        text = stringResource(R.string.catalog_open_site),
        onClick = onClick,
        modifier = Modifier.testTag("public-web-open-confirm"),
        iconRes = R.drawable.ic_ui_open_in_new,
    )
}

@Suppress("LongParameterList", "FunctionNaming")
@Composable
private fun PortalOptionsDialog(
    portal: PortalCatalogItem,
    isFavorite: Boolean,
    canOpenUniversalPortal: Boolean,
    onDismiss: () -> Unit,
    onToggleFavorite: (PortalId) -> Unit,
    onOpenUniversalPortal: ((PortalCatalogItem) -> Unit)?,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { JuntaMonogram(name = portal.organization, size = 48.dp) },
        title = { Text(portal.displayName, maxLines = 3, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PortalOptionRow(
                    iconRes = if (isFavorite) R.drawable.ic_ui_star else R.drawable.ic_ui_star_outline,
                    tone = JuntaTone.SAFFRON,
                    text = stringResource(
                        if (isFavorite) R.string.catalog_favorite_saved else R.string.catalog_favorite_add,
                    ),
                    modifier = Modifier.testTag("catalog-option-favorite"),
                    onClick = {
                        onDismiss()
                        onToggleFavorite(portal.portalId)
                    },
                )
                if (canOpenUniversalPortal && onOpenUniversalPortal != null) {
                    PortalOptionRow(
                        iconRes = R.drawable.ic_ui_shield,
                        tone = JuntaTone.VIOLET,
                        text = stringResource(R.string.catalog_open_universal_without_profile),
                        modifier = Modifier.testTag("catalog-open-universal-${portal.portalId.value}"),
                        onClick = {
                            onDismiss()
                            onOpenUniversalPortal(portal)
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.catalog_options_close))
            }
        },
        shape = JuntaPanelShape,
        containerColor = JuntaPaperElevated,
        modifier = Modifier.testTag("catalog-site-options"),
    )
}

/** One option of the long-press menu: a colour tile and a label, the whole row is the target. */
@Suppress("FunctionNaming")
@Composable
private fun PortalOptionRow(
    @DrawableRes iconRes: Int,
    tone: JuntaTone,
    text: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(JuntaFieldShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        JuntaIconBubble(iconRes = iconRes, tone = tone, size = 36.dp, iconSize = 18.dp)
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            color = JuntaInk,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

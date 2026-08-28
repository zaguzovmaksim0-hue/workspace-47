package dev.junta.firmamobile.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class CatalogLocationState {
    IDLE,
    LOADING,
    PERMISSION_DENIED,
    LOCATION_DISABLED,
    UNAVAILABLE,
    OUTSIDE_SPAIN,
}

enum class CatalogUserMessage { OPEN_FAILED, LOCATION_DETECTED }

enum class PortalCatalogSectionKind {
    FAVORITES,
    RECENT,
    SELECTED_REGION,
    NATIONAL,
    REGION,
    OTHER_REGIONS,
}

data class PortalCatalogSection(
    val kind: PortalCatalogSectionKind,
    val items: List<PortalCatalogItem>,
    val regionCode: PortalRegionCode? = null,
)

data class PortalCatalogUiState(
    val searchText: String = "",
    val selectedRegion: PortalRegionCode = PortalRegionCode.SPAIN,
    val favoritePortalIds: Set<PortalId> = emptySet(),
    val recentPortalIds: List<PortalId> = emptyList(),
    val sections: List<PortalCatalogSection> = emptyList(),
    val locationState: CatalogLocationState = CatalogLocationState.IDLE,
    val userMessage: CatalogUserMessage? = null,
)

class PortalCatalogViewModel(
    private val repository: PortalCatalogRepository,
    private val preferencesStore: CatalogPreferencesStore,
    private val regionDetector: RegionDetector,
) : ViewModel() {
    private val searchText = MutableStateFlow("")
    private val locationState = MutableStateFlow(CatalogLocationState.IDLE)
    private val userMessage = MutableStateFlow<CatalogUserMessage?>(null)

    val state = combine(
        preferencesStore.preferences(repository.portalIds),
        searchText,
        locationState,
        userMessage,
    ) { preferences, search, location, message ->
        val items = repository.portals(
            PortalCatalogQuery(
                searchText = search,
                favoritePortalIds = preferences.favoritePortalIds,
                recentPortalIds = preferences.recentPortalIds,
                selectedRegion = preferences.selectedRegion,
            ),
        )
        PortalCatalogUiState(
            searchText = search,
            selectedRegion = preferences.selectedRegion,
            favoritePortalIds = preferences.favoritePortalIds,
            recentPortalIds = preferences.recentPortalIds,
            sections = buildPersonalizedPortalSections(
                items = items,
                selectedRegion = preferences.selectedRegion,
                favoritePortalIds = preferences.favoritePortalIds,
                recentPortalIds = preferences.recentPortalIds,
                searching = search.isNotBlank(),
            ),
            locationState = location,
            userMessage = message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = initialState(),
    )

    init {
        viewModelScope.launch { preferencesStore.sanitize(repository.portalIds) }
    }

    fun updateSearchText(value: String) {
        searchText.value = value.take(MAX_SEARCH_CHARS)
    }

    fun selectRegion(region: PortalRegionCode) {
        viewModelScope.launch {
            preferencesStore.selectRegion(region, CatalogRegionSelectionSource.MANUAL)
        }
    }

    fun toggleFavorite(portalId: PortalId) {
        if (portalId !in repository.portalIds) return
        viewModelScope.launch { preferencesStore.toggleFavorite(portalId) }
    }

    fun recordOpened(portalId: PortalId) {
        if (portalId !in repository.portalIds) return
        viewModelScope.launch { preferencesStore.recordRecent(portalId) }
    }

    fun detectRegion() {
        if (locationState.value == CatalogLocationState.LOADING) return
        viewModelScope.launch {
            locationState.value = CatalogLocationState.LOADING
            when (val result = regionDetector.detect()) {
                is RegionDetectionResult.Success -> {
                    preferencesStore.selectRegion(
                        result.region,
                        CatalogRegionSelectionSource.LOCATION,
                    )
                    locationState.value = CatalogLocationState.IDLE
                    userMessage.value = CatalogUserMessage.LOCATION_DETECTED
                }
                RegionDetectionResult.PermissionDenied ->
                    locationState.value = CatalogLocationState.PERMISSION_DENIED
                RegionDetectionResult.LocationDisabled ->
                    locationState.value = CatalogLocationState.LOCATION_DISABLED
                RegionDetectionResult.Unavailable ->
                    locationState.value = CatalogLocationState.UNAVAILABLE
                RegionDetectionResult.OutsideSpain ->
                    locationState.value = CatalogLocationState.OUTSIDE_SPAIN
            }
        }
    }

    fun onLocationPermissionDenied() {
        locationState.value = CatalogLocationState.PERMISSION_DENIED
    }

    fun dismissLocationMessage() {
        if (locationState.value != CatalogLocationState.LOADING) {
            locationState.value = CatalogLocationState.IDLE
        }
    }

    fun onOpenFailed() {
        userMessage.value = CatalogUserMessage.OPEN_FAILED
    }

    fun onUserMessageShown() {
        userMessage.value = null
    }

    private fun initialState(): PortalCatalogUiState {
        val items = repository.portals(selectedRegionQuery(PortalRegionCode.SPAIN))
        return PortalCatalogUiState(
            sections = buildPersonalizedPortalSections(
                items = items,
                selectedRegion = PortalRegionCode.SPAIN,
                favoritePortalIds = emptySet(),
                recentPortalIds = emptyList(),
                searching = false,
            ),
        )
    }

    private fun selectedRegionQuery(region: PortalRegionCode) = PortalCatalogQuery(
        selectedRegion = region,
    )

    class Factory(
        private val repository: PortalCatalogRepository,
        private val preferencesStore: CatalogPreferencesStore,
        private val regionDetector: RegionDetector,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PortalCatalogViewModel::class.java))
            return PortalCatalogViewModel(repository, preferencesStore, regionDetector) as T
        }
    }

    private companion object {
        const val MAX_SEARCH_CHARS = 160
    }
}

internal fun buildPersonalizedPortalSections(
    items: List<PortalCatalogItem>,
    selectedRegion: PortalRegionCode,
    favoritePortalIds: Set<PortalId>,
    recentPortalIds: List<PortalId>,
    searching: Boolean,
): List<PortalCatalogSection> {
    if (searching) {
        val selectedItems = if (selectedRegion == PortalRegionCode.SPAIN) emptyList() else {
            items.filter { it.regionCode == selectedRegion }
        }
        val nationalItems = items.filter { it.regionCode == PortalRegionCode.SPAIN }
        val otherItems = items.filter {
            it.regionCode != PortalRegionCode.SPAIN && it.regionCode != selectedRegion
        }
        return buildList {
            if (selectedItems.isNotEmpty()) {
                add(
                    PortalCatalogSection(
                        PortalCatalogSectionKind.SELECTED_REGION,
                        selectedItems,
                        selectedRegion,
                    ),
                )
            }
            if (nationalItems.isNotEmpty()) {
                add(PortalCatalogSection(PortalCatalogSectionKind.NATIONAL, nationalItems))
            }
            if (otherItems.isNotEmpty()) {
                add(PortalCatalogSection(PortalCatalogSectionKind.OTHER_REGIONS, otherItems))
            }
        }
    }

    val byId = items.associateBy { it.portalId }
    val favorites = items.filter { it.portalId in favoritePortalIds }
    val recent = recentPortalIds.mapNotNull(byId::get)
    return buildList {
        if (favorites.isNotEmpty()) {
            add(PortalCatalogSection(PortalCatalogSectionKind.FAVORITES, favorites))
        }
        if (recent.isNotEmpty()) {
            add(PortalCatalogSection(PortalCatalogSectionKind.RECENT, recent))
        }
        if (selectedRegion != PortalRegionCode.SPAIN) {
            val regional = items.filter { it.regionCode == selectedRegion }
            if (regional.isNotEmpty()) {
                add(
                    PortalCatalogSection(
                        PortalCatalogSectionKind.SELECTED_REGION,
                        regional,
                        selectedRegion,
                    ),
                )
            }
        }
        val national = items.filter { it.regionCode == PortalRegionCode.SPAIN }
        if (national.isNotEmpty()) {
            add(PortalCatalogSection(PortalCatalogSectionKind.NATIONAL, national))
        }
        if (selectedRegion == PortalRegionCode.SPAIN) {
            PortalRegionCode.selectableRegions.forEach { region ->
                val regional = items.filter { it.regionCode == region }
                if (regional.isNotEmpty()) {
                    add(
                        PortalCatalogSection(
                            PortalCatalogSectionKind.REGION,
                            regional,
                            region,
                        ),
                    )
                }
            }
        }
    }
}

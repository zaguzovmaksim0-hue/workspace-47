package dev.junta.firmamobile.catalog

import dev.junta.firmamobile.profile.BuildTrustPolicy
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.SiteProfileRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class PortalCatalogViewModelTest {
    private val profileCatalog = BuiltInSiteProfiles.catalog
    private val repository by lazy {
        PortalCatalogRepository(
            SiteProfileRegistry(profileCatalog, BuildTrustPolicy.QA),
            profileCatalog,
            loadBundledPublicPortalCatalog(),
        )
    }

    @Test
    fun `successful detection stores only the resolved region`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = FakeCatalogPreferencesStore()
            val viewModel = PortalCatalogViewModel(
                repository,
                store,
                RegionDetector { RegionDetectionResult.Success(PortalRegionCode.GALICIA) },
            )
            val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.state.collect()
            }

            viewModel.detectRegion()
            advanceUntilIdle()

            assertEquals(PortalRegionCode.GALICIA, viewModel.state.value.selectedRegion)
            assertEquals(CatalogRegionSelectionSource.LOCATION, store.current.selectionSource)
            assertEquals(CatalogLocationState.IDLE, viewModel.state.value.locationState)
            assertEquals(CatalogUserMessage.LOCATION_DETECTED, viewModel.state.value.userMessage)
            collection.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `denied location keeps manual catalog available`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = FakeCatalogPreferencesStore()
            val viewModel = PortalCatalogViewModel(
                repository,
                store,
                RegionDetector { RegionDetectionResult.PermissionDenied },
            )
            val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.state.collect()
            }

            viewModel.detectRegion()
            advanceUntilIdle()

            assertEquals(PortalRegionCode.SPAIN, viewModel.state.value.selectedRegion)
            assertEquals(CatalogLocationState.PERMISSION_DENIED, viewModel.state.value.locationState)
            collection.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeCatalogPreferencesStore : CatalogPreferencesStore {
        private val state = MutableStateFlow(CatalogPreferences())
        val current: CatalogPreferences get() = state.value

        override fun preferences(validPortalIds: Set<PortalId>): Flow<CatalogPreferences> =
            state.map { preferences ->
                preferences.copy(
                    favoritePortalIds = preferences.favoritePortalIds.intersect(validPortalIds),
                    recentPortalIds = preferences.recentPortalIds.filter { it in validPortalIds },
                )
            }

        override suspend fun selectRegion(
            region: PortalRegionCode,
            source: CatalogRegionSelectionSource,
        ) {
            state.value = state.value.copy(selectedRegion = region, selectionSource = source)
        }

        override suspend fun toggleFavorite(portalId: PortalId) {
            val favorites = state.value.favoritePortalIds.toMutableSet()
            if (!favorites.remove(portalId)) favorites.add(portalId)
            state.value = state.value.copy(favoritePortalIds = favorites)
        }

        override suspend fun recordRecent(portalId: PortalId) {
            state.value = state.value.copy(
                recentPortalIds = (listOf(portalId) + state.value.recentPortalIds)
                    .distinct()
                    .take(8),
            )
        }

        override suspend fun sanitize(validPortalIds: Set<PortalId>) {
            state.value = state.value.copy(
                favoritePortalIds = state.value.favoritePortalIds.intersect(validPortalIds),
                recentPortalIds = state.value.recentPortalIds.filter { it in validPortalIds },
            )
        }
    }
}

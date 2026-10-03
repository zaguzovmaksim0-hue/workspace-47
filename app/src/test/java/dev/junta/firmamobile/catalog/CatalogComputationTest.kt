package dev.junta.firmamobile.catalog

import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
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
class CatalogComputationTest {
    private fun repository() = PortalCatalogRepository(BuiltInSiteProfiles.qaRegistry, BuiltInSiteProfiles.catalog, loadBundledPublicPortalCatalog())

    @Test fun constructionDoesNotSynchronouslyBuildTheCatalogOnTheUiCaller() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        try {
            val vm = PortalCatalogViewModel(repository(), Store(), RegionDetector { RegionDetectionResult.Unavailable }, dispatcher)
            assertTrue(vm.state.value.catalogLoading); assertTrue(vm.state.value.sections.isEmpty())
            val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }
            assertTrue(vm.state.value.catalogLoading)
            advanceUntilIdle()
            assertFalse(vm.state.value.catalogLoading); assertTrue(vm.state.value.sections.isNotEmpty())
            collection.cancel()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun transientErrorAndLocationStatesReuseTheSameComputedSections() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        try {
            val vm = PortalCatalogViewModel(repository(), Store(), RegionDetector { RegionDetectionResult.Unavailable }, dispatcher)
            val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }
            advanceUntilIdle(); val sections = vm.state.value.sections; assertTrue(sections.isNotEmpty())
            vm.onOpenFailed(); advanceUntilIdle()
            assertEquals(CatalogUserMessage.OPEN_FAILED, vm.state.value.userMessage); assertSame(sections, vm.state.value.sections)
            vm.onUserMessageShown(); vm.onLocationPermissionDenied(); advanceUntilIdle()
            assertEquals(CatalogLocationState.PERMISSION_DENIED, vm.state.value.locationState); assertSame(sections, vm.state.value.sections)
            vm.dismissLocationMessage(); advanceUntilIdle(); assertSame(sections, vm.state.value.sections)
            collection.cancel()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun preferenceChangesStillRebuildTheCorrectRegionAndRecentSections() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        try {
            val repo = repository(); val store = Store()
            val vm = PortalCatalogViewModel(repo, store, RegionDetector { RegionDetectionResult.Unavailable }, dispatcher)
            val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }
            advanceUntilIdle(); val previous = vm.state.value.sections
            vm.selectRegion(PortalRegionCode.ANDALUSIA); advanceUntilIdle()
            assertEquals(PortalRegionCode.ANDALUSIA, vm.state.value.selectedRegion); assertNotSame(previous, vm.state.value.sections)
            val selected = vm.state.value.sections.single { it.kind == PortalCatalogSectionKind.SELECTED_REGION }
            assertTrue(selected.items.all { it.regionCode == PortalRegionCode.ANDALUSIA })
            val item = repo.portals().first(); vm.recordOpened(item.portalId); advanceUntilIdle()
            assertEquals(item.portalId, vm.state.value.sections.single { it.kind == PortalCatalogSectionKind.RECENT }.items.first().portalId)
            collection.cancel()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun rapidTypingPublishesTheLatestMatchingContentNotAnOlderQuery() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        try {
            val repo = repository(); val vm = PortalCatalogViewModel(repo, Store(), RegionDetector { RegionDetectionResult.Unavailable }, dispatcher)
            val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }
            advanceUntilIdle()
            for (text in listOf("u", "un", "une", "UNED")) vm.updateSearchText(text)
            advanceUntilIdle()
            assertEquals("UNED", vm.state.value.searchText)
            assertEquals(repo.portals(PortalCatalogQuery(searchText = "UNED", selectedRegion = PortalRegionCode.SPAIN)).map { it.portalId }.toSet(),
                vm.state.value.sections.flatMap { it.items }.map { it.portalId }.toSet())
            collection.cancel()
        } finally { Dispatchers.resetMain() }
    }

    private class Store : CatalogPreferencesStore {
        val current = MutableStateFlow(CatalogPreferences())
        override fun preferences(validPortalIds: Set<PortalId>): Flow<CatalogPreferences> = current
        override suspend fun selectRegion(region: PortalRegionCode, source: CatalogRegionSelectionSource) {
            current.value = current.value.copy(selectedRegion = region, selectionSource = source)
        }
        override suspend fun toggleFavorite(portalId: PortalId) {
            val ids = current.value.favoritePortalIds.toMutableSet(); if (!ids.remove(portalId)) ids.add(portalId)
            current.value = current.value.copy(favoritePortalIds = ids)
        }
        override suspend fun recordRecent(portalId: PortalId) { current.value = current.value.copy(recentPortalIds = listOf(portalId)) }
        override suspend fun sanitize(validPortalIds: Set<PortalId>) = Unit
    }
}

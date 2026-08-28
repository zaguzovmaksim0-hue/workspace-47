package dev.junta.firmamobile.catalog

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class CatalogPreferencesStoreTest {
    @Test
    fun `region favorites and ordered recent portals survive a new reader`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = ::temporaryPreferencesFile,
        )
        val store = PreferencesCatalogPreferencesStore(dataStore)
        val ids = (1..10).map { PortalId("portal-$it") }

        store.selectRegion(PortalRegionCode.GALICIA, CatalogRegionSelectionSource.LOCATION)
        store.toggleFavorite(ids.first())
        for (id in ids) store.recordRecent(id)

        val restored = PreferencesCatalogPreferencesStore(dataStore).preferences(ids.toSet()).first()
        assertEquals(PortalRegionCode.GALICIA, restored.selectedRegion)
        assertEquals(CatalogRegionSelectionSource.LOCATION, restored.selectionSource)
        assertEquals(setOf(ids.first()), restored.favoritePortalIds)
        assertEquals(ids.reversed().take(8), restored.recentPortalIds)
        assertFalse(
            dataStore.data.first().asMap().keys.any { key ->
                key.name.contains("latitude") || key.name.contains("longitude") ||
                    key.name.contains("address")
            },
        )
    }

    @Test
    fun `unknown ids and corrupted region are dropped without failing`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = ::temporaryPreferencesFile,
        )
        val store = PreferencesCatalogPreferencesStore(dataStore)
        val valid = PortalId("valid-portal")
        val stale = PortalId("stale-portal")
        store.toggleFavorite(stale)
        store.recordRecent(stale)
        dataStore.edit { preferences ->
            preferences[stringPreferencesKey("selected_region")] = "ES-UNKNOWN"
        }

        store.sanitize(setOf(valid))
        val preferences = store.preferences(setOf(valid)).first()

        assertEquals(PortalRegionCode.SPAIN, preferences.selectedRegion)
        assertEquals(emptySet<PortalId>(), preferences.favoritePortalIds)
        assertEquals(emptyList<PortalId>(), preferences.recentPortalIds)
    }

    private fun temporaryPreferencesFile(): File {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return context.preferencesDataStoreFile("catalog-test-${System.nanoTime()}")
    }
}

package dev.junta.firmamobile.catalog

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

enum class CatalogRegionSelectionSource { MANUAL, LOCATION }

data class CatalogPreferences(
    val selectedRegion: PortalRegionCode = PortalRegionCode.SPAIN,
    val selectionSource: CatalogRegionSelectionSource? = null,
    val favoritePortalIds: Set<PortalId> = emptySet(),
    val recentPortalIds: List<PortalId> = emptyList(),
)

interface CatalogPreferencesStore {
    fun preferences(validPortalIds: Set<PortalId>): Flow<CatalogPreferences>

    suspend fun selectRegion(region: PortalRegionCode, source: CatalogRegionSelectionSource)

    suspend fun toggleFavorite(portalId: PortalId)

    suspend fun recordRecent(portalId: PortalId)

    suspend fun sanitize(validPortalIds: Set<PortalId>)
}

val Context.catalogPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "catalog_preferences",
)

class PreferencesCatalogPreferencesStore(
    private val dataStore: DataStore<Preferences>,
) : CatalogPreferencesStore {
    override fun preferences(validPortalIds: Set<PortalId>): Flow<CatalogPreferences> =
        dataStore.data
            .catch { error ->
                if (error is IOException) emit(emptyPreferences()) else throw error
            }
            .map { preferences -> preferences.toCatalogPreferences(validPortalIds) }

    override suspend fun selectRegion(
        region: PortalRegionCode,
        source: CatalogRegionSelectionSource,
    ) {
        dataStore.edit { preferences ->
            preferences[Keys.REGION] = region.wireValue
            preferences[Keys.REGION_SOURCE] = source.name
        }
    }

    override suspend fun toggleFavorite(portalId: PortalId) {
        dataStore.edit { preferences ->
            val favorites = preferences[Keys.FAVORITES].orEmpty().toMutableSet()
            if (!favorites.remove(portalId.value)) favorites.add(portalId.value)
            preferences[Keys.FAVORITES] = favorites
        }
    }

    override suspend fun recordRecent(portalId: PortalId) {
        dataStore.edit { preferences ->
            val recent = decodeRecent(preferences[Keys.RECENT]).toMutableList()
            recent.remove(portalId)
            recent.add(0, portalId)
            preferences[Keys.RECENT] = encodeRecent(recent.take(MAX_RECENT_PORTALS))
        }
    }

    override suspend fun sanitize(validPortalIds: Set<PortalId>) {
        dataStore.edit { preferences ->
            val favorites = preferences[Keys.FAVORITES].orEmpty()
                .mapNotNull(::portalIdOrNull)
                .filterTo(linkedSetOf()) { it in validPortalIds }
                .mapTo(linkedSetOf()) { it.value }
            if (favorites.isEmpty()) preferences.remove(Keys.FAVORITES)
            else preferences[Keys.FAVORITES] = favorites

            val recent = decodeRecent(preferences[Keys.RECENT])
                .filter { it in validPortalIds }
                .distinct()
                .take(MAX_RECENT_PORTALS)
            if (recent.isEmpty()) preferences.remove(Keys.RECENT)
            else preferences[Keys.RECENT] = encodeRecent(recent)

            if (preferences[Keys.REGION]?.let { PortalRegionCode.fromWireValue(it) } == null) {
                preferences.remove(Keys.REGION)
                preferences.remove(Keys.REGION_SOURCE)
            } else if (
                preferences[Keys.REGION_SOURCE]?.let(::selectionSourceOrNull) == null
            ) {
                preferences.remove(Keys.REGION_SOURCE)
            }
        }
    }

    private fun Preferences.toCatalogPreferences(validPortalIds: Set<PortalId>): CatalogPreferences {
        val selectedRegion = this[Keys.REGION]
            ?.let { PortalRegionCode.fromWireValue(it) }
            ?: PortalRegionCode.SPAIN
        val source = this[Keys.REGION_SOURCE]?.let(::selectionSourceOrNull)
        val favorites = this[Keys.FAVORITES].orEmpty()
            .mapNotNull(::portalIdOrNull)
            .filterTo(linkedSetOf()) { it in validPortalIds }
        val recent = decodeRecent(this[Keys.RECENT])
            .filter { it in validPortalIds }
            .distinct()
            .take(MAX_RECENT_PORTALS)
        return CatalogPreferences(selectedRegion, source, favorites, recent)
    }

    private fun decodeRecent(raw: String?): List<PortalId> = raw.orEmpty()
        .split(RECENT_SEPARATOR)
        .filter(String::isNotBlank)
        .mapNotNull(::portalIdOrNull)

    private fun encodeRecent(portalIds: List<PortalId>): String =
        portalIds.joinToString(RECENT_SEPARATOR) { it.value }

    private fun portalIdOrNull(raw: String): PortalId? = runCatching { PortalId(raw) }.getOrNull()

    private fun selectionSourceOrNull(raw: String): CatalogRegionSelectionSource? =
        runCatching { CatalogRegionSelectionSource.valueOf(raw) }.getOrNull()

    private object Keys {
        val REGION = stringPreferencesKey("selected_region")
        val REGION_SOURCE = stringPreferencesKey("selected_region_source")
        val FAVORITES = stringSetPreferencesKey("favorite_portal_ids")
        val RECENT = stringPreferencesKey("recent_portal_ids")
    }

    private companion object {
        const val RECENT_SEPARATOR = "|"
        const val MAX_RECENT_PORTALS = 8
    }
}

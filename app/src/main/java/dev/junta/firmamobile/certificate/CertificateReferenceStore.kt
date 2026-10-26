package dev.junta.firmamobile.certificate

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.Instant
import kotlinx.coroutines.flow.first

data class StoredCertificateReference(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val size: Long?,
    val summary: CertificateSummary?,
    /** Lowercase hex SHA-256 of the chosen entry's certificate when the file holds several keys. */
    val selectedCertificateSha256: String? = null,
)

interface CertificateReferenceStore {
    suspend fun read(): StoredCertificateReference?

    suspend fun write(reference: StoredCertificateReference)

    suspend fun clear()

    /** Whether a saved reference still needs [uri]'s read grant; a single-reference store needs only its own. */
    suspend fun references(uri: Uri): Boolean = read()?.uri == uri
}

val Context.certificateReferenceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "certificate_reference",
)

class PreferencesCertificateReferenceStore(
    private val dataStore: DataStore<Preferences>,
) : CertificateReferenceCatalogStore {
    override suspend fun readCatalog(): StoredCertificateCatalog {
        val preferences = dataStore.data.first()
        if (Keys.CATALOG in preferences || Keys.LEGACY.none { it in preferences }) {
            return readSnapshot(preferences)
        }
        val migrated = dataStore.edit { current -> persist(current, readSnapshot(current)) }
        return CertificateReferenceCatalogCodec.decode(checkNotNull(migrated[Keys.CATALOG]))
    }

    override suspend fun add(reference: StoredCertificateReference): StoredCertificateEntry {
        val entry = CertificateReferenceCatalogCodec.entry(reference)
        dataStore.edit { preferences ->
            val catalog = readSnapshot(preferences)
            if (catalog.entries.any { it.reference?.uri == reference.uri }) {
                throw CertificateCatalogException(CertificateSelectionErrorCode.ALREADY_SAVED)
            }
            if (catalog.entries.size >= CertificateReferenceCatalogCodec.MAX_ENTRIES) {
                throw CertificateCatalogException(CertificateSelectionErrorCode.CATALOG_FULL)
            }
            persist(preferences, catalog.copy(entries = catalog.entries + entry))
        }
        return entry
    }

    override suspend fun selectEntry(id: String, revision: Long): Boolean {
        var selected = false
        dataStore.edit { preferences ->
            val catalog = readSnapshot(preferences)
            selected = catalog.entries.any { it.id == id && it.revision == revision && it.reference != null }
            if (selected) persist(preferences, catalog.copy(activeId = id))
        }
        return selected
    }

    override suspend fun selectKeyEntry(expected: StoredCertificateEntry): Boolean {
        var selected = false
        dataStore.edit { preferences ->
            val catalog = readSnapshot(preferences)
            selected = expected.signingKey != null && catalog.entries.singleOrNull { it.id == expected.id } == expected
            if (selected) persist(preferences, catalog.copy(activeId = expected.id))
        }
        return selected
    }

    override suspend fun write(reference: StoredCertificateReference) {
        dataStore.edit { preferences ->
            val catalog = readSnapshot(preferences)
            val active = catalog.active
            val entry = if (active != null) {
                // A summary refresh retains the key; selecting another file retires it atomically.
                val metadataChanged = active.reference?.copy(summary = null) != reference.copy(summary = null)
                active.copy(
                    reference = reference,
                    signingKey = active.signingKey.takeUnless { metadataChanged },
                    revision = if (metadataChanged) Math.addExact(active.revision, 1) else active.revision,
                )
            } else {
                CertificateReferenceCatalogCodec.entry(reference)
            }
            val entries = if (active == null) catalog.entries + entry else catalog.entries.map {
                if (it.id == active.id) entry else it
            }
            persist(preferences, catalog.copy(
                entries = entries, activeId = entry.id,
                tombstones = catalog.tombstones + listOfNotNull(active?.signingKey?.alias)
                    .filterNot { it == entry.signingKey?.alias },
            ))
        }
    }

    override suspend fun clear() {
        dataStore.edit { preferences ->
            val catalog = readSnapshot(preferences)
            val remaining = catalog.entries.filterNot { it.id == catalog.activeId }
            persist(preferences, catalog.copy(
                entries = remaining, activeId = null,
                tombstones = catalog.tombstones + listOfNotNull(catalog.active?.signingKey?.alias),
            ))
        }
    }

    override suspend fun replace(expected: StoredCertificateEntry, replacement: StoredCertificateEntry) {
        require(replacement.id == expected.id && replacement.revision == Math.addExact(expected.revision, 1))
        dataStore.edit { preferences ->
            val catalog = readSnapshot(preferences)
            check(catalog.entries.singleOrNull { it.id == expected.id } == expected) { "Stale certificate entry" }
            val retired = listOfNotNull(expected.signingKey?.alias).filterNot { it == replacement.signingKey?.alias }
            persist(preferences, catalog.copy(
                entries = catalog.entries.map { if (it.id == expected.id) replacement else it },
                tombstones = catalog.tombstones + retired,
            ))
        }
    }

    override suspend fun remove(expected: StoredCertificateEntry, onlyIfInactive: Boolean) {
        dataStore.edit { preferences ->
            val catalog = readSnapshot(preferences)
            check(catalog.entries.singleOrNull { it.id == expected.id } == expected) { "Stale certificate entry" }
            check(!onlyIfInactive || catalog.activeId != expected.id) { "Active certificate entry" }
            persist(preferences, catalog.copy(
                entries = catalog.entries.filterNot { it.id == expected.id },
                activeId = catalog.activeId.takeUnless { it == expected.id },
                tombstones = catalog.tombstones + listOfNotNull(expected.signingKey?.alias),
            ))
        }
    }

    override suspend fun acknowledgeKeyDeletion(alias: String) {
        dataStore.edit { preferences ->
            val catalog = readSnapshot(preferences)
            persist(preferences, catalog.copy(tombstones = catalog.tombstones - alias))
        }
    }

    private fun readSnapshot(preferences: Preferences): StoredCertificateCatalog {
        return when {
            Keys.CATALOG in preferences -> {
                CertificateReferenceCatalogCodec.decode(checkNotNull(preferences[Keys.CATALOG]))
            }
            Keys.LEGACY.none { it in preferences } -> StoredCertificateCatalog(emptyList(), null)
            else -> {
                val reference = StoredCertificateReference(
                    uri = requireNotNull(preferences[Keys.URI]).toUri(),
                    displayName = CertificateDisplayNamePolicy.sanitize(requireNotNull(preferences[Keys.DISPLAY_NAME])),
                    mimeType = requireNotNull(preferences[Keys.MIME_TYPE]),
                    size = preferences[Keys.SIZE],
                    summary = Keys.readSummary(preferences),
                )
                CertificateReferenceCatalogCodec.validateReference(reference)
                val entry = CertificateReferenceCatalogCodec.entry(reference)
                StoredCertificateCatalog(listOf(entry), entry.id)
            }
        }
    }

    private companion object {
        fun persist(preferences: MutablePreferences, catalog: StoredCertificateCatalog) {
            preferences[Keys.CATALOG] = CertificateReferenceCatalogCodec.encode(catalog)
            Keys.LEGACY.forEach { preferences.remove(it) }
        }
    }

    private object Keys {
        val CATALOG = stringPreferencesKey("catalog")
        val URI = stringPreferencesKey("uri")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val MIME_TYPE = stringPreferencesKey("mime_type")
        val SIZE = longPreferencesKey("size")
        val OWNER = stringPreferencesKey("summary_owner")
        val ISSUER = stringPreferencesKey("summary_issuer")
        val VALID_FROM = longPreferencesKey("summary_valid_from")
        val VALID_UNTIL = longPreferencesKey("summary_valid_until")
        val SUMMARY = listOf(OWNER, ISSUER, VALID_FROM, VALID_UNTIL)
        val LEGACY = listOf(URI, DISPLAY_NAME, MIME_TYPE, SIZE) + SUMMARY

        fun readSummary(preferences: Preferences): CertificateSummary? {
            val owner = preferences[OWNER]
            val issuer = preferences[ISSUER]
            val validFrom = preferences[VALID_FROM]
            val validUntil = preferences[VALID_UNTIL]
            if (SUMMARY.none { it in preferences }) return null
            return CertificateSummary(
                ownerName = requireNotNull(owner),
                issuerName = requireNotNull(issuer),
                validFrom = Instant.ofEpochMilli(requireNotNull(validFrom)),
                validUntil = Instant.ofEpochMilli(requireNotNull(validUntil)),
            )
        }
    }
}

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
)

interface CertificateReferenceStore {
    suspend fun read(): StoredCertificateReference?

    suspend fun write(reference: StoredCertificateReference)

    suspend fun clear()
}

val Context.certificateReferenceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "certificate_reference",
)

class PreferencesCertificateReferenceStore(
    private val dataStore: DataStore<Preferences>,
) : CertificateReferenceCatalogStore {
    override suspend fun read(): StoredCertificateReference? = readCatalog().active?.reference

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
            persist(preferences, catalog.copy(entries = catalog.entries + entry))
        }
        return entry
    }

    override suspend fun selectEntry(id: String, revision: Long): Boolean {
        var selected = false
        dataStore.edit { preferences ->
            val catalog = readSnapshot(preferences)
            selected = catalog.entries.any { it.id == id && it.revision == revision }
            if (selected) persist(preferences, catalog.copy(activeId = id))
        }
        return selected
    }

    override suspend fun remove(id: String, revision: Long): StoredCertificateEntry? {
        var removed: StoredCertificateEntry? = null
        dataStore.edit { preferences ->
            val catalog = readSnapshot(preferences)
            removed = catalog.entries.singleOrNull { it.id == id && it.revision == revision }
            if (removed != null) {
                persist(preferences, StoredCertificateCatalog(
                    entries = catalog.entries.filterNot { it.id == id },
                    activeId = catalog.activeId.takeIf { it != id },
                ))
            }
        }
        return removed
    }

    override suspend fun write(reference: StoredCertificateReference) {
        dataStore.edit { preferences ->
            val catalog = readSnapshot(preferences)
            val active = catalog.active
            val entry = if (active != null) {
                val metadataChanged = active.reference.copy(summary = null) != reference.copy(summary = null)
                active.copy(
                    reference = reference,
                    revision = if (metadataChanged) Math.addExact(active.revision, 1) else active.revision,
                )
            } else {
                CertificateReferenceCatalogCodec.entry(reference)
            }
            val entries = if (active == null) catalog.entries + entry else catalog.entries.map {
                if (it.id == active.id) entry else it
            }
            persist(preferences, StoredCertificateCatalog(entries, entry.id))
        }
    }

    override suspend fun clear() {
        dataStore.edit { preferences ->
            val catalog = readSnapshot(preferences)
            val remaining = catalog.entries.filterNot { it.id == catalog.activeId }
            persist(preferences, StoredCertificateCatalog(remaining, null))
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
                    summary = readSummary(preferences),
                )
                CertificateReferenceCatalogCodec.validateReference(reference)
                val entry = CertificateReferenceCatalogCodec.entry(reference)
                StoredCertificateCatalog(listOf(entry), entry.id)
            }
        }
    }

    private fun persist(preferences: MutablePreferences, catalog: StoredCertificateCatalog) {
        preferences[Keys.CATALOG] = CertificateReferenceCatalogCodec.encode(catalog)
        Keys.LEGACY.forEach { preferences.remove(it) }
    }

    private fun readSummary(preferences: Preferences): CertificateSummary? {
        val owner = preferences[Keys.OWNER]
        val issuer = preferences[Keys.ISSUER]
        val validFrom = preferences[Keys.VALID_FROM]
        val validUntil = preferences[Keys.VALID_UNTIL]
        if (Keys.SUMMARY.none { it in preferences }) return null
        return CertificateSummary(
            ownerName = requireNotNull(owner),
            issuerName = requireNotNull(issuer),
            validFrom = Instant.ofEpochMilli(requireNotNull(validFrom)),
            validUntil = Instant.ofEpochMilli(requireNotNull(validUntil)),
        )
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
    }
}

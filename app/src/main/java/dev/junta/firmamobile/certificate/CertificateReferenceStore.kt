package dev.junta.firmamobile.certificate

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
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
) : CertificateReferenceStore {
    override suspend fun read(): StoredCertificateReference? {
        val preferences = dataStore.data.first()
        val rawUri = preferences[Keys.URI] ?: return null
        val displayName = preferences[Keys.DISPLAY_NAME] ?: return null
        val mimeType = preferences[Keys.MIME_TYPE] ?: return null
        val summary = readSummary(preferences)
        return StoredCertificateReference(
            uri = rawUri.toUri(),
            displayName = displayName,
            mimeType = mimeType,
            size = preferences[Keys.SIZE],
            summary = summary,
        )
    }

    override suspend fun write(reference: StoredCertificateReference) {
        dataStore.edit { preferences ->
            preferences[Keys.URI] = reference.uri.toString()
            preferences[Keys.DISPLAY_NAME] = reference.displayName
            preferences[Keys.MIME_TYPE] = reference.mimeType
            reference.size?.let { preferences[Keys.SIZE] = it }
                ?: preferences.remove(Keys.SIZE)

            val summary = reference.summary
            if (summary == null) {
                preferences.remove(Keys.OWNER)
                preferences.remove(Keys.ISSUER)
                preferences.remove(Keys.VALID_FROM)
                preferences.remove(Keys.VALID_UNTIL)
            } else {
                preferences[Keys.OWNER] = summary.ownerName
                preferences[Keys.ISSUER] = summary.issuerName
                preferences[Keys.VALID_FROM] = summary.validFrom.toEpochMilli()
                preferences[Keys.VALID_UNTIL] = summary.validUntil.toEpochMilli()
            }
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private fun readSummary(preferences: Preferences): CertificateSummary? {
        val owner = preferences[Keys.OWNER] ?: return null
        val issuer = preferences[Keys.ISSUER] ?: return null
        val validFrom = preferences[Keys.VALID_FROM] ?: return null
        val validUntil = preferences[Keys.VALID_UNTIL] ?: return null
        return CertificateSummary(
            ownerName = owner,
            issuerName = issuer,
            validFrom = Instant.ofEpochMilli(validFrom),
            validUntil = Instant.ofEpochMilli(validUntil),
        )
    }

    private object Keys {
        val URI = stringPreferencesKey("uri")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val MIME_TYPE = stringPreferencesKey("mime_type")
        val SIZE = longPreferencesKey("size")
        val OWNER = stringPreferencesKey("summary_owner")
        val ISSUER = stringPreferencesKey("summary_issuer")
        val VALID_FROM = longPreferencesKey("summary_valid_from")
        val VALID_UNTIL = longPreferencesKey("summary_valid_until")
    }
}

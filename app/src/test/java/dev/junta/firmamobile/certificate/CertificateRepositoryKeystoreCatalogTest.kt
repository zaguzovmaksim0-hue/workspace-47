package dev.junta.firmamobile.certificate

import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.junta.firmamobile.signing.syntheticIdentity
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class CertificateRepositoryKeystoreCatalogTest {
    private val data = MemoryPreferences()
    private val store = PreferencesCertificateReferenceStore(data)
    private val backend = FakeSigningKeyStoreBackend(strongBoxAvailable = true)
    private val released = mutableListOf<Uri>()
    private val documents = object : CertificateDocumentAccess {
        override fun queryMetadata(uri: Uri) =
            CertificateDocumentMetadata("${uri.lastPathSegment}.p12", "application/x-pkcs12", null)
        override fun takePersistableReadPermission(uri: Uri) = Unit
        override fun releasePersistableReadPermission(uri: Uri) { released += uri }
        override fun open(uri: Uri): InputStream = error("Key-backed entries must not open backup files")
    }
    private val keys = KeystoreSigningKeyCatalog(store, backend, documents, enabled = true)
    private val repository = CertificateRepository(documents, store, Pkcs12Loader(), Dispatchers.Unconfined, keys)

    @Test fun inactiveKeyDeletionRemovesAliasAndReleasesOnlyTheSourceGrant() = runBlocking {
        val key = entry(keyOnly = false)
        repository.removeInactive(key)
        assertEquals(listOf(checkNotNull(key.signingKey).alias), backend.deleted)
        assertEquals(listOf(checkNotNull(key.reference).uri), released)
        assertTrue(store.readCatalog().entries.isEmpty())
        assertTrue(store.readCatalog().tombstones.isEmpty())
    }

    @Test fun activeEntryRequiresTheBoundConfirmedDeletionPath() = runBlocking {
        val key = entry(active = true)
        assertTrue(runCatching { repository.removeInactive(key) }.isFailure)
        assertTrue(runCatching { repository.forget() }.isFailure)
        assertEquals(key, store.readCatalog().active)
        assertTrue(backend.deleted.isEmpty())
        repository.forget(key)
        assertNull(store.readCatalog().active)
        assertEquals(listOf(checkNotNull(key.signingKey).alias), backend.deleted)
    }

    @Test fun failedAliasDeletionKeepsDurableTombstoneAndCompletesOnReopening() = runBlocking {
        val key = entry()
        val alias = checkNotNull(key.signingKey).alias
        backend.failDelete = true
        assertTrue(runCatching { repository.removeInactive(key) }.exceptionOrNull() is IOException)
        assertEquals(setOf(alias), store.readCatalog().tombstones)
        assertTrue(alias in backend.keys)
        assertTrue(repository.hasPendingKeyDeletion())
        backend.failDelete = false
        val reopened = CertificateRepository(documents, store, Pkcs12Loader(), Dispatchers.Unconfined, keys)
        assertNull(reopened.currentReference())
        assertTrue(backend.keys.isEmpty())
        assertTrue(!reopened.hasPendingKeyDeletion())
    }

    @Test fun failedCatalogueCommitNeverDeletesTheAliasOrLosesTheRecord() = runBlocking {
        val key = entry()
        data.failNextCommit = true
        assertTrue(runCatching { repository.removeInactive(key) }.exceptionOrNull() is IOException)
        assertEquals(listOf(key), store.readCatalog().entries)
        assertTrue(checkNotNull(key.signingKey).alias in backend.keys)
        assertTrue(backend.deleted.isEmpty())
    }

    @Test fun staleConfirmationCannotDeleteAChangedEntry() = runBlocking {
        val key = entry()
        val changed = key.copy(revision = key.revision + 1)
        store.replace(key, changed)
        assertTrue(runCatching { repository.forget(key) }.isFailure)
        assertEquals(listOf(changed), store.readCatalog().entries)
        assertTrue(backend.deleted.isEmpty())
    }

    @Test fun activeKeyWithBackupNeverFallsBackToFilePasswordUnlock() = runBlocking {
        val key = entry(active = true, keyOnly = false)
        assertEquals(key, repository.currentKeyEntry())
        assertNull(repository.currentReference())
        val failure = CertificateLoadResult.Failure(CertificateErrorCode.NO_CERTIFICATE_SELECTED)
        assertEquals(failure, repository.unlock(charArrayOf('x')))
        assertEquals(emptyList<CertificateEntryChoice>(), repository.certificateChoices(charArrayOf('x')))
        assertEquals(failure, repository.unlockChosen(charArrayOf('x'), "b".repeat(64)))
        assertTrue(backend.deleted.isEmpty())
    }

    @Test fun keySelectionChecksRevisionAndPersistsWithoutUnlockingItsBackup() = runBlocking {
        val key = entry(keyOnly = false)
        assertTrue(repository.activateKey(key))
        assertEquals(key, repository.currentKeyEntry())
        assertNull(repository.currentReference())
        val reopened = CertificateRepository(documents, PreferencesCertificateReferenceStore(data),
            Pkcs12Loader(), Dispatchers.Unconfined, keys)
        assertEquals(key, reopened.currentKeyEntry())
        val changed = key.copy(revision = key.revision + 1)
        store.replace(key, changed)
        assertEquals(false, repository.activateKey(key))
        assertEquals(changed, repository.currentKeyEntry())
        assertTrue(backend.deleted.isEmpty())
    }

    @Test fun failedKeySelectionCommitKeepsTheFormerSelectionAndAlias() = runBlocking {
        val key = entry()
        data.failNextCommit = true
        assertTrue(runCatching { repository.activateKey(key) }.exceptionOrNull() is IOException)
        assertNull(store.readCatalog().active)
        assertEquals(listOf(key), store.readCatalog().entries)
        assertTrue(checkNotNull(key.signingKey).alias in backend.keys)
        assertTrue(backend.deleted.isEmpty())
    }

    @Test fun pickingAnotherFilePreservesTheKeyOnlyEntry() = preservesKeyWhenPickingFile(true)

    @Test fun pickingAnotherFilePreservesTheKeyAndItsBackupGrant() = preservesKeyWhenPickingFile(false)

    private fun preservesKeyWhenPickingFile(keyOnly: Boolean) = runBlocking {
        val key = entry(active = true, keyOnly = keyOnly)
        val uri = Uri.parse("content://documents/other")
        assertTrue(repository.select(uri) is CertificateSelectionResult.Success)
        assertEquals(uri, repository.currentReference()?.uri)
        assertEquals(key, store.readCatalog().entries.single { it.id == key.id })
        assertTrue(checkNotNull(key.signingKey).alias in backend.keys)
        assertTrue(backend.deleted.isEmpty())
        assertTrue(released.isEmpty())
        assertTrue(!repository.hasPendingKeyDeletion())
        assertTrue(repository.activateKey(key))
        assertEquals(key, repository.currentKeyEntry())
    }

    @Test fun pendingDeletionFailureDoesNotHideAnotherSelectedFile() = runBlocking {
        val key = entry()
        backend.failDelete = true
        assertTrue(runCatching { repository.removeInactive(key) }.exceptionOrNull() is IOException)
        val file = store.add(StoredCertificateReference(
            Uri.parse("content://documents/other-pending"), "other.p12", "application/x-pkcs12", null, null,
        ))
        assertTrue(repository.activate(file) is CertificateSelectionResult.Success)
        assertEquals(file.reference, repository.currentReference())
        assertTrue(repository.hasPendingKeyDeletion())
        val alias = checkNotNull(key.signingKey).alias
        assertEquals(setOf(alias), store.readCatalog().tombstones)
        assertTrue(alias in backend.keys)
    }

    private suspend fun entry(active: Boolean = false, keyOnly: Boolean = true): StoredCertificateEntry {
        val source = store.add(StoredCertificateReference(
            Uri.parse("content://documents/key"), "key.p12", "application/x-pkcs12", null, null,
        ))
        val record = StoredSigningKeyReference(
            1, KeystoreSigningKeyImporter.ALIAS_PREFIX + "a".repeat(32),
            StoredKeystoreLevel.STRONGBOX, "b".repeat(64), "key.p12",
        )
        val key = source.copy(revision = source.revision + 1,
            reference = source.reference.takeUnless { keyOnly }, signingKey = record)
        store.replace(source, key)
        syntheticIdentity().withPrivateKey { backend.keys[record.alias] = it }
        if (active) data.edit { preferences ->
            preferences[stringPreferencesKey("catalog")] = CertificateReferenceCatalogCodec.encode(
                store.readCatalog().copy(activeId = key.id),
            )
        }
        return key
    }

    private class MemoryPreferences : DataStore<Preferences> {
        var failNextCommit = false
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val updated = transform(data.value)
            if (failNextCommit) { failNextCommit = false; throw IOException("Synthetic commit failure") }
            data.value = updated
            return updated
        }
    }
}

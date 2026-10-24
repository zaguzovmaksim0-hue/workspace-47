package dev.junta.firmamobile.certificate

import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.util.Base64
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class CertificateReferenceCatalogTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() = scopes.forEach { it.cancel() }

    @Test
    fun emptyAndAppendKeepSelectionExplicitAndIdsStable() = runBlocking {
        val data = dataStore(file())
        val store: CertificateReferenceCatalogStore = PreferencesCertificateReferenceStore(data)
        assertEquals(emptyList<StoredCertificateEntry>(), store.readCatalog().entries)
        assertNull(store.read())
        val first = store.add(reference("a"))
        val second = store.add(reference("b"))
        assertNotEquals(first.id, second.id)
        assertEquals(listOf(first, second), store.readCatalog().entries)
        assertNull(store.readCatalog().activeId)
        assertNull(store.read())
        assertEquals(store.readCatalog(), PreferencesCertificateReferenceStore(data).readCatalog())
    }

    @Test
    fun activeFacadeRefreshesAndReplacesOnlyActiveEntry() = runBlocking {
        val store = PreferencesCertificateReferenceStore(dataStore(file()))
        store.write(reference("a"))
        val active = store.readCatalog().entries.single()
        val inactive = store.add(reference("b"))
        val beforeDuplicate = store.readCatalog()
        assertRejected { store.write(reference("b")) }
        assertEquals(beforeDuplicate, store.readCatalog())
        store.write(reference("a").copy(displayName = "renamed.p12"))
        val refreshed = store.readCatalog()
        assertEquals(active.id, refreshed.activeId)
        assertEquals(active.revision + 1, refreshed.entries.first().revision)
        store.write(reference("c"))
        val replaced = store.readCatalog()
        assertEquals(active.id, replaced.activeId)
        assertEquals(active.revision + 2, replaced.active?.revision)
        assertEquals(setOf(inactive.reference, reference("c")), replaced.entries.map { it.reference }.toSet())
        assertEquals(reference("c"), store.read())
        store.clear()
        assertNull(store.read())
        assertNull(store.readCatalog().activeId)
        assertEquals(listOf(inactive), store.readCatalog().entries)
        store.write(reference("a"))
        assertNotEquals(active.id, store.readCatalog().activeId)
        assertEquals(inactive, store.readCatalog().entries.first())
    }

    @Test
    fun rejectsDuplicatesAndSeventeenthEntryWithoutChangingSnapshot() = runBlocking {
        val store = PreferencesCertificateReferenceStore(dataStore(file()))
        store.write(reference("active"))
        val initial = store.readCatalog()
        assertRejected { store.add(reference("active")) }
        assertEquals(initial, store.readCatalog())
        repeat(15) { store.add(reference("inactive-$it")) }
        val full = store.readCatalog()
        assertEquals(16, full.entries.size)
        assertRejected { store.add(reference("overflow")) }
        assertEquals(full, store.readCatalog())
    }

    @Test
    fun invalidReferencesCannotEnterCatalog() = runBlocking {
        val store = PreferencesCertificateReferenceStore(dataStore(file()))
        for (uri in listOf("https://example.invalid/a", "file:///a", "content:///a", "content://docs/a#fragment")) {
            assertRejected { store.add(reference("a").copy(uri = Uri.parse(uri))) }
        }
        assertRejected { store.add(reference("a").copy(size = -1)) }
        assertRejected { store.add(reference("a").copy(mimeType = "x".repeat(129))) }
        assertNull(store.read())
        assertTrue(store.readCatalog().entries.isEmpty())
    }

    @Test
    fun migratesOnceAndSurvivesReopeningRealDataStore() = runBlocking {
        val file = file()
        val data = dataStore(file)
        seedLegacy(data)
        val store = PreferencesCertificateReferenceStore(data)
        val migrated = store.readCatalog()
        assertEquals(reference("legacy"), store.read())
        assertEquals(migrated.entries.single().id, migrated.activeId)
        assertEquals(migrated, store.readCatalog())
        assertNull(data.data.first()[stringPreferencesKey("uri")])
        // Cancel/join the owner before opening the same file in a new process-like scope.
        scopes.last().coroutineContext[kotlinx.coroutines.Job]!!.let { it.cancel(); it.join() }
        assertEquals(migrated, PreferencesCertificateReferenceStore(dataStore(file)).readCatalog())
    }

    @Test
    fun failedMigrationCommitRetainsLegacyAndRetryCreatesOneEntry() = runBlocking {
        val file = file()
        val data = dataStore(file)
        seedLegacy(data)
        val failing = object : DataStore<Preferences> by data {
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                data.updateData { preferences ->
                    transform(preferences)
                    throw IOException("Synthetic commit failure")
                }
        }
        val failure = runCatching { PreferencesCertificateReferenceStore(failing).readCatalog() }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertEquals("content://documents/legacy", data.data.first()[stringPreferencesKey("uri")])
        scopes.last().coroutineContext[kotlinx.coroutines.Job]!!.let { it.cancel(); it.join() }
        val reopened = PreferencesCertificateReferenceStore(dataStore(file))
        val snapshot = reopened.readCatalog()
        assertEquals(1, snapshot.entries.size)
        assertEquals(reference("legacy"), reopened.read())
        assertEquals(snapshot, reopened.readCatalog())
    }

    @Test
    fun storageFailureDoesNotCommitReplacementOrClear() = runBlocking {
        val data = dataStore(file())
        val store = PreferencesCertificateReferenceStore(data)
        store.write(reference("a"))
        val before = store.readCatalog()
        val failing = object : DataStore<Preferences> by data {
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                data.updateData { preferences ->
                    transform(preferences)
                    throw IOException("Synthetic commit failure")
                }
        }
        val broken = PreferencesCertificateReferenceStore(failing)
        assertTrue(runCatching { broken.write(reference("b")) }.exceptionOrNull() is IOException)
        assertTrue(runCatching { broken.clear() }.exceptionOrNull() is IOException)
        assertTrue(runCatching { broken.add(reference("c")) }.exceptionOrNull() is IOException)
        assertEquals(before, store.readCatalog())
        assertEquals(reference("a"), store.read())
    }

    @Test
    fun concurrentAppendsAreSerializedAndDuplicateCannotRace() = runBlocking {
        val data = dataStore(file())
        val stores = List(8) { PreferencesCertificateReferenceStore(data) }
        val outcomes = stores.map { store -> async { runCatching { store.add(reference("same")) } } }.awaitAll()
        assertEquals(1, outcomes.count { it.isSuccess })
        assertEquals(1, stores.first().readCatalog().entries.size)
    }

    @Test
    fun incompleteLegacySummaryFailsWithoutDroppingMetadata() = runBlocking {
        val data = dataStore(file())
        seedLegacy(data)
        data.edit { it[stringPreferencesKey("summary_owner")] = "Synthetic owner" }
        val store = PreferencesCertificateReferenceStore(data)
        assertRejected { store.readCatalog() }
        assertEquals("Synthetic owner", data.data.first()[stringPreferencesKey("summary_owner")])
        assertNull(data.data.first()[stringPreferencesKey("catalog")])
    }

    @Test
    fun summaryUpdateKeepsIdentityAndRevisionForUnlockAndReminders() = runBlocking {
        val store = PreferencesCertificateReferenceStore(dataStore(file()))
        store.write(reference("a"))
        val before = store.readCatalog().entries.single()
        val summary = CertificateSummary("Synthetic", "Test CA", Instant.ofEpochMilli(0), Instant.ofEpochMilli(1000))
        store.write(reference("a").copy(summary = summary))
        val after = store.readCatalog().entries.single()
        assertEquals(before.id, after.id)
        assertEquals(before.revision, after.revision)
        assertEquals(summary, store.read()?.summary)
    }

    @Test
    fun malformedCatalogNeverFallsBackToLegacyOrAllowsMutation() = runBlocking {
        val id = "00000000-0000-0000-0000-000000000001"
        val other = "00000000-0000-0000-0000-000000000002"
        val malformed = listOf(
            "not base64!", "a".repeat(524289),
            payload(version = 2), payload(count = -1), payload(count = 17),
            payload(active = id), payload(ids = listOf(id, id)),
            payload(ids = listOf(id, other), uris = listOf("content://docs/a", "content://docs/a")),
            payload(ids = listOf("invalid-id")), payload(ids = listOf(id), revision = 0),
            payload(ids = listOf(id), uris = listOf("file:///cert")),
            payload(ids = listOf(id), name = "x".repeat(257)),
            payload(ids = listOf(id)).dropLast(8), payload() + "AAAA",
        )
        for (encoded in malformed) {
            val data = dataStore(file())
            seedLegacy(data)
            data.edit { it[stringPreferencesKey("catalog")] = encoded }
            val store = PreferencesCertificateReferenceStore(data)
            assertTrue("Corrupt catalogue must not restore legacy", runCatching { store.read() }.isFailure)
            assertTrue(runCatching { store.write(reference("new")) }.isFailure)
            assertTrue(runCatching { store.clear() }.isFailure)
            assertEquals(encoded, data.data.first()[stringPreferencesKey("catalog")])
            assertEquals("content://documents/legacy", data.data.first()[stringPreferencesKey("uri")])
        }
    }

    // Independent wire fixtures exercise rejection through the storage interface, not the codec.
    private fun payload(
        version: Int = 1,
        active: String = "",
        ids: List<String> = emptyList(),
        uris: List<String> = ids.indices.map { "content://docs/$it" },
        count: Int = ids.size,
        revision: Long = 1,
        name: String = "test.p12",
    ): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(version)
            output.writeUTF(active)
            output.writeInt(count)
            ids.forEachIndexed { index, id ->
                output.writeUTF(id)
                output.writeLong(revision)
                output.writeUTF(uris[index])
                output.writeUTF(name)
                output.writeUTF("application/x-pkcs12")
                output.writeLong(-1)
                output.writeBoolean(false)
            }
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    private suspend fun assertRejected(action: suspend () -> Unit) {
        assertTrue("Must reject invalid catalogue input", runCatching { action() }.exceptionOrNull() is IllegalArgumentException)
    }

    private suspend fun seedLegacy(data: DataStore<Preferences>) {
        data.edit {
            it[stringPreferencesKey("uri")] = "content://documents/legacy"
            it[stringPreferencesKey("display_name")] = "legacy.p12"
            it[stringPreferencesKey("mime_type")] = "application/x-pkcs12"
        }
    }

    private fun reference(name: String) = StoredCertificateReference(
        Uri.parse("content://documents/$name"), "$name.p12", "application/x-pkcs12", null, null,
    )

    private fun file() = File(temporaryFolder.root, "${System.nanoTime()}.preferences_pb")

    private fun dataStore(file: File): DataStore<Preferences> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scopes += scope
        return PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
    }
}

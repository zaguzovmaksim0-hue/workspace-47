package dev.junta.firmamobile.certificate

import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class CertificateFileCatalogTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val access = GrantRecordingAccess()

    // Same decoration as the production module.
    private val store by lazy {
        ReminderCertificateReferenceStore(
            ApplicationProvider.getApplicationContext(),
            PreferencesCertificateReferenceStore(PreferenceDataStoreFactory.create(scope = scope) {
                File(temporaryFolder.root, "catalog.preferences_pb")
            }),
        )
    }
    private val catalog by lazy { CertificateFileCatalog(access, store, Dispatchers.IO) }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun addSavesAnInactiveEntryWithItsGrant() = runBlocking {
        store.write(reference("active"))

        val result = catalog.add(uri("saved"))

        assertEquals(uri("saved"), (result as CertificateSelectionResult.Success).reference.uri)
        val saved = store.readCatalog()
        assertEquals(listOf(uri("active"), uri("saved")), saved.entries.map { it.reference?.uri })
        assertEquals(uri("active"), saved.active?.reference?.uri)
        assertEquals(listOf(uri("saved")), access.persisted)
        assertTrue(access.released.isEmpty())
    }

    @Test
    fun refusedAddsReleaseOnlyTheirOwnGrant() = runBlocking {
        store.write(reference("active"))

        // A duplicate URI fails, and the saved entry keeps its grant.
        assertFailure(catalog.add(uri("active")))
        repeat(CertificateReferenceCatalogCodec.MAX_ENTRIES - 1) {
            assertTrue(catalog.add(uri("e$it")) is CertificateSelectionResult.Success)
        }
        assertFailure(catalog.add(uri("overflow")))

        assertEquals(listOf(uri("overflow")), access.released)
        assertEquals(CertificateReferenceCatalogCodec.MAX_ENTRIES, store.readCatalog().entries.size)
    }

    @Test
    fun removeDropsAnInactiveEntryAndReleasesItsGrant() = runBlocking {
        store.write(reference("active"))
        catalog.add(uri("saved"))

        catalog.remove(store.readCatalog().entries.single { it.reference?.uri == uri("saved") })

        assertEquals(listOf(uri("active")), store.readCatalog().entries.map { it.reference?.uri })
        assertEquals(listOf(uri("saved")), access.released)
    }

    @Test
    fun removeRefusesTheActiveAndKeyBackedEntries() = runBlocking {
        store.write(reference("active"))
        catalog.add(uri("saved"))
        val before = store.readCatalog()
        val active = checkNotNull(before.active)
        val saved = before.entries.single { it.id != active.id }
        val keyed = saved.copy(
            revision = saved.revision + 1,
            signingKey = StoredSigningKeyReference(
                1, KeystoreSigningKeyImporter.ALIAS_PREFIX + "a".repeat(32), StoredKeystoreLevel.TEE,
                "b".repeat(64), "saved.p12",
            ),
        )
        store.replace(saved, keyed)

        assertThrows(IllegalStateException::class.java) { runBlocking { catalog.remove(active) } }
        assertThrows(IllegalStateException::class.java) { runBlocking { catalog.remove(keyed) } }

        val after = store.readCatalog()
        assertEquals(before.entries.map { it.id }, after.entries.map { it.id })
        assertEquals(active.id, after.activeId)
        assertTrue(access.released.isEmpty())
    }

    private fun assertFailure(result: CertificateSelectionResult) = assertEquals(
        CertificateSelectionErrorCode.STORAGE_FAILURE,
        (result as CertificateSelectionResult.Failure).code,
    )

    private fun uri(name: String) = Uri.parse("content://documents/$name")

    private fun reference(name: String) =
        StoredCertificateReference(uri(name), "$name.p12", "application/x-pkcs12", 100, null)

    private class GrantRecordingAccess : CertificateDocumentAccess {
        val persisted = mutableListOf<Uri>()
        val released = mutableListOf<Uri>()

        override fun queryMetadata(uri: Uri) =
            CertificateDocumentMetadata("${uri.lastPathSegment}.p12", "application/x-pkcs12", 100)

        override fun takePersistableReadPermission(uri: Uri) {
            persisted += uri
        }

        override fun releasePersistableReadPermission(uri: Uri) {
            released += uri
        }

        override fun open(uri: Uri): InputStream = error("Adding or removing never reads the file")
    }
}

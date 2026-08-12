package dev.junta.firmamobile.certificate

import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.Clock
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@OptIn(ExperimentalCoroutinesApi::class)
class CertificateRepositoryTest {
    @Test
    fun selectsSupportedContentDocumentAndPersistsReadPermissionBeforeReference() = runTest {
        val events = mutableListOf<String>()
        val bytes = TestCertificateFactory.validRsa()
        val access = FakeDocumentAccess(events).apply {
            metadata = CertificateDocumentMetadata(
                displayName = "identidad.p12",
                mimeType = "application/x-pkcs12",
                size = bytes.size.toLong(),
            )
            content = bytes
        }
        val store = FakeReferenceStore(events)
        val repository = repository(access, store)
        val uri = Uri.parse("content://documents/identity")

        val result = repository.select(uri)

        val success = result as CertificateSelectionResult.Success
        assertEquals(uri, success.reference.uri)
        assertEquals("identidad.p12", success.reference.displayName)
        assertEquals(listOf("persist:$uri", "store:$uri"), events)
        assertEquals(success.reference, store.reference)
    }

    @Test
    fun acceptsCaseInsensitivePfxForOctetStreamFallback() = runTest {
        val access = FakeDocumentAccess().apply {
            metadata = CertificateDocumentMetadata(
                displayName = "IDENTIDAD.PFX",
                mimeType = "application/octet-stream",
                size = 1024,
            )
        }

        val result = repository(access).select(Uri.parse("content://documents/pfx"))

        assertTrue(result is CertificateSelectionResult.Success)
    }

    @Test
    fun stripsUnicodeBidiControlsFromProviderDisplayName() = runTest {
        val access = FakeDocumentAccess().apply {
            metadata = CertificateDocumentMetadata(
                displayName = "cert\u202Eevil\u2066.p12",
                mimeType = "application/pkcs12",
                size = 1024,
            )
        }
        val store = FakeReferenceStore()

        val result = repository(access, store).select(Uri.parse("content://documents/bidi-name"))

        val success = result as CertificateSelectionResult.Success
        assertEquals("certevil.p12", success.reference.displayName)
        assertEquals("certevil.p12", store.reference?.displayName)
    }

    @Test
    fun officialPkcs12MimeDoesNotDependOnProviderFilename() = runTest {
        val access = FakeDocumentAccess().apply {
            metadata = CertificateDocumentMetadata(
                displayName = null,
                mimeType = "application/pkcs12",
                size = null,
            )
        }

        val result = repository(access).select(Uri.parse("content://documents/opaque"))

        val success = result as CertificateSelectionResult.Success
        assertEquals(CertificateRepository.DEFAULT_DISPLAY_NAME, success.reference.displayName)
    }

    @Test
    fun rejectsNonContentUriBeforeQueryOrPermission() = runTest {
        val access = FakeDocumentAccess()

        val result = repository(access).select(Uri.parse("file:///sdcard/identity.p12"))

        assertSelectionFailure(CertificateSelectionErrorCode.INVALID_URI, result)
        assertEquals(0, access.metadataCalls)
        assertTrue(access.persisted.isEmpty())
    }

    @Test
    fun rejectsOversizedOpaqueContentUriBeforeQuery() = runTest {
        val access = FakeDocumentAccess()
        val uri = Uri.parse("content://documents/${"a".repeat(4096)}")

        val result = repository(access).select(uri)

        assertSelectionFailure(CertificateSelectionErrorCode.INVALID_URI, result)
        assertEquals(0, access.metadataCalls)
    }

    @Test
    fun rejectsUnsupportedMimeAndDisguisedFallbackName() = runTest {
        val uri = Uri.parse("content://documents/rejected")
        val access = FakeDocumentAccess().apply {
            metadata = CertificateDocumentMetadata("identity.p12", "text/plain", 100)
        }
        assertSelectionFailure(
            CertificateSelectionErrorCode.UNSUPPORTED_FILE,
            repository(access).select(uri),
        )

        access.metadata = CertificateDocumentMetadata(
            "identity.p12.exe",
            "application/octet-stream",
            100,
        )
        assertSelectionFailure(
            CertificateSelectionErrorCode.UNSUPPORTED_FILE,
            repository(access).select(uri),
        )
    }

    @Test
    fun rejectsDeclaredOversizeBeforePermission() = runTest {
        val access = FakeDocumentAccess().apply {
            metadata = CertificateDocumentMetadata(
                "identity.p12",
                "application/x-pkcs12",
                Pkcs12Loader.MAX_INPUT_BYTES + 1L,
            )
        }

        val result = repository(access).select(Uri.parse("content://documents/large"))

        assertSelectionFailure(CertificateSelectionErrorCode.FILE_TOO_LARGE, result)
        assertTrue(access.persisted.isEmpty())
    }

    @Test
    fun mapsMetadataAndPersistPermissionFailuresWithoutMessages() = runTest {
        val uri = Uri.parse("content://documents/failure")
        val access = FakeDocumentAccess().apply { metadataFailure = true }
        assertSelectionFailure(
            CertificateSelectionErrorCode.DOCUMENT_UNAVAILABLE,
            repository(access).select(uri),
        )

        access.metadataFailure = false
        access.metadata = CertificateDocumentMetadata(
            "identity.p12",
            "application/x-pkcs12",
            100,
        )
        access.persistFailure = true
        assertSelectionFailure(
            CertificateSelectionErrorCode.PERMISSION_DENIED,
            repository(access).select(uri),
        )
    }

    @Test
    fun cancelledSelectionBeforeReferenceCommitReleasesNewPermission() = runTest {
        val uri = Uri.parse("content://documents/cancelled-selection")
        val access = validAccess()
        val store = BlockingReferenceStore()
        val repository = CertificateRepository(
            documentAccess = access,
            referenceStore = store,
            loader = Pkcs12Loader(
                clock = Clock.fixed(TestCertificateFactory.now, ZoneOffset.UTC),
            ),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val selection = async { repository.select(uri) }
        store.writeStarted.await()
        assertEquals(listOf(uri), access.persisted)

        selection.cancelAndJoin()

        assertNull(store.reference)
        assertEquals(listOf(uri), access.released)
    }

    @Test
    fun failedStoreWriteReleasesNewPermissionAndKeepsNoReference() = runTest {
        val uri = Uri.parse("content://documents/write-failure")
        val access = validAccess()
        val store = FakeReferenceStore().apply { writeFailure = true }

        val result = repository(access, store).select(uri)

        assertSelectionFailure(CertificateSelectionErrorCode.STORAGE_FAILURE, result)
        assertEquals(listOf(uri), access.released)
        assertNull(store.reference)
    }

    @Test
    fun failedRewriteOfSameUriKeepsExistingPermissionAndReference() = runTest {
        val uri = Uri.parse("content://documents/existing")
        val existing = StoredCertificateReference(
            uri,
            "existing.p12",
            "application/x-pkcs12",
            100,
            summary = null,
        )
        val access = validAccess()
        val store = FakeReferenceStore().apply {
            reference = existing
            writeFailure = true
        }

        val result = repository(access, store).select(uri)

        assertSelectionFailure(CertificateSelectionErrorCode.STORAGE_FAILURE, result)
        assertTrue(access.released.isEmpty())
        assertEquals(existing, store.reference)
    }

    @Test
    fun replacementReleasesOldPermissionAfterNewReferenceIsStored() = runTest {
        val events = mutableListOf<String>()
        val oldUri = Uri.parse("content://documents/old")
        val newUri = Uri.parse("content://documents/new")
        val oldReference = StoredCertificateReference(
            oldUri,
            "old.p12",
            "application/x-pkcs12",
            100,
            summary = null,
        )
        val store = FakeReferenceStore(events).apply { reference = oldReference }
        val access = validAccess(events)

        val result = repository(access, store).select(newUri)

        assertTrue(result is CertificateSelectionResult.Success)
        assertEquals(
            listOf("persist:$newUri", "store:$newUri", "release:$oldUri"),
            events,
        )
    }

    @Test
    fun recreatedRepositoryReopensUriAndStoresOnlySafeSummary() = runTest {
        val bytes = TestCertificateFactory.validRsa()
        val access = validAccess().apply { content = bytes }
        val store = FakeReferenceStore()
        val uri = Uri.parse("content://documents/recreated")
        val first = repository(access, store)
        assertTrue(first.select(uri) is CertificateSelectionResult.Success)

        val recreated = repository(access, store)
        val password = TestCertificateFactory.password()
        val original = password.copyOf()
        val result = recreated.unlock(password)

        assertTrue(result is CertificateLoadResult.Success)
        assertTrue(store.reference?.summary != null)
        assertEquals(uri, access.opened.single())
        assertTrue(password.contentEquals(original))
        assertFalse(
            StoredCertificateReference::class.java.declaredFields.any {
                it.type == ByteArray::class.java ||
                    it.type == CharArray::class.java ||
                    java.security.PrivateKey::class.java.isAssignableFrom(it.type)
            },
        )
    }

    @Test
    fun cancelledUnlockAfterBlockingLoadDoesNotWriteStaleReferenceSummary() = runTest {
        val bytes = TestCertificateFactory.validRsa()
        val uri = Uri.parse("content://documents/cancelled-unlock")
        val reference = StoredCertificateReference(
            uri,
            "cancelled-unlock.p12",
            "application/x-pkcs12",
            bytes.size.toLong(),
            summary = null,
        )
        val readStarted = CountDownLatch(1)
        val allowRead = CountDownLatch(1)
        val access = validAccess().apply {
            content = bytes
            openOverride = BlockingInputStream(bytes, readStarted, allowRead)
        }
        val store = RecordingReferenceStore(reference)
        val repository = CertificateRepository(
            documentAccess = access,
            referenceStore = store,
            loader = Pkcs12Loader(
                clock = Clock.fixed(TestCertificateFactory.now, ZoneOffset.UTC),
            ),
            ioDispatcher = Dispatchers.Default,
        )
        val password = TestCertificateFactory.password()
        val unlock = async(Dispatchers.Default) { repository.unlock(password) }

        assertTrue(readStarted.await(5, TimeUnit.SECONDS))
        unlock.cancel()
        allowRead.countDown()
        unlock.cancelAndJoin()

        assertTrue(store.writes.isEmpty())
        assertEquals(reference, store.reference)
    }

    @Test
    fun unlockWithoutSelectionAndUnavailableDocumentUseClosedErrors() = runTest {
        assertFailure(
            CertificateErrorCode.NO_CERTIFICATE_SELECTED,
            repository(FakeDocumentAccess()).unlock(TestCertificateFactory.password()),
        )

        val access = validAccess().apply { openFailure = true }
        val store = FakeReferenceStore().apply {
            reference = StoredCertificateReference(
                Uri.parse("content://documents/missing"),
                "missing.p12",
                "application/x-pkcs12",
                100,
                null,
            )
        }
        assertFailure(
            CertificateErrorCode.DOCUMENT_UNAVAILABLE,
            repository(access, store).unlock(TestCertificateFactory.password()),
        )
    }

    @Test
    fun forgetClearsReferenceEvenWhenPermissionReleaseFails() = runTest {
        val uri = Uri.parse("content://documents/forget")
        val access = validAccess().apply { releaseFailure = true }
        val store = FakeReferenceStore().apply {
            reference = StoredCertificateReference(
                uri,
                "forget.p12",
                "application/x-pkcs12",
                100,
                null,
            )
        }

        repository(access, store).forget()

        assertNull(store.reference)
        assertEquals(listOf(uri), access.released)
    }

    private fun repository(
        access: FakeDocumentAccess,
        store: FakeReferenceStore = FakeReferenceStore(),
    ): CertificateRepository = CertificateRepository(
        documentAccess = access,
        referenceStore = store,
        loader = Pkcs12Loader(
            clock = Clock.fixed(TestCertificateFactory.now, ZoneOffset.UTC),
        ),
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    private fun validAccess(events: MutableList<String> = mutableListOf()) =
        FakeDocumentAccess(events).apply {
            metadata = CertificateDocumentMetadata(
                "identity.p12",
                "application/x-pkcs12",
                1024,
            )
        }

    private fun assertSelectionFailure(
        expected: CertificateSelectionErrorCode,
        result: CertificateSelectionResult,
    ) {
        assertEquals(expected, (result as CertificateSelectionResult.Failure).code)
    }

    private fun assertFailure(
        expected: CertificateErrorCode,
        result: CertificateLoadResult,
    ) {
        assertEquals(expected, (result as CertificateLoadResult.Failure).code)
    }

    private class FakeDocumentAccess(
        private val events: MutableList<String> = mutableListOf(),
    ) : CertificateDocumentAccess {
        var metadata = CertificateDocumentMetadata(
            "identity.p12",
            "application/x-pkcs12",
            100,
        )
        var content: ByteArray = byteArrayOf(1)
        var metadataFailure = false
        var persistFailure = false
        var releaseFailure = false
        var openFailure = false
        var openOverride: InputStream? = null
        var metadataCalls = 0
        val persisted = mutableListOf<Uri>()
        val released = mutableListOf<Uri>()
        val opened = mutableListOf<Uri>()

        override fun queryMetadata(uri: Uri): CertificateDocumentMetadata {
            metadataCalls += 1
            if (metadataFailure) throw SecurityException("metadata canary")
            return metadata
        }

        override fun takePersistableReadPermission(uri: Uri) {
            if (persistFailure) throw SecurityException("permission canary")
            persisted += uri
            events += "persist:$uri"
        }

        override fun releasePersistableReadPermission(uri: Uri) {
            released += uri
            events += "release:$uri"
            if (releaseFailure) throw SecurityException("release canary")
        }

        override fun open(uri: Uri): InputStream {
            opened += uri
            if (openFailure) throw SecurityException("open canary")
            return openOverride ?: ByteArrayInputStream(content)
        }
    }

    private class BlockingInputStream(
        bytes: ByteArray,
        private val readStarted: CountDownLatch,
        private val allowRead: CountDownLatch,
    ) : InputStream() {
        private val delegate = ByteArrayInputStream(bytes)
        private var blocked = false

        override fun read(): Int {
            blockOnce()
            return delegate.read()
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            blockOnce()
            return delegate.read(buffer, offset, length)
        }

        override fun close() = delegate.close()

        private fun blockOnce() {
            if (blocked) return
            blocked = true
            readStarted.countDown()
            check(allowRead.await(5, TimeUnit.SECONDS))
        }
    }

    private class RecordingReferenceStore(
        initial: StoredCertificateReference,
    ) : CertificateReferenceStore {
        var reference: StoredCertificateReference? = initial
        val writes = mutableListOf<StoredCertificateReference>()

        override suspend fun read(): StoredCertificateReference? = reference

        override suspend fun write(reference: StoredCertificateReference) {
            writes += reference
            this.reference = reference
        }

        override suspend fun clear() {
            reference = null
        }
    }

    private class BlockingReferenceStore : CertificateReferenceStore {
        val writeStarted = CompletableDeferred<Unit>()
        private val finishWrite = CompletableDeferred<Unit>()
        var reference: StoredCertificateReference? = null

        override suspend fun read(): StoredCertificateReference? = reference

        override suspend fun write(reference: StoredCertificateReference) {
            writeStarted.complete(Unit)
            finishWrite.await()
            this.reference = reference
        }

        override suspend fun clear() {
            reference = null
        }
    }

    private class FakeReferenceStore(
        private val events: MutableList<String> = mutableListOf(),
    ) : CertificateReferenceStore {
        var reference: StoredCertificateReference? = null
        var writeFailure = false

        override suspend fun read(): StoredCertificateReference? = reference

        override suspend fun write(reference: StoredCertificateReference) {
            if (writeFailure) throw IllegalStateException("store canary")
            this.reference = reference
            events += "store:${reference.uri}"
        }

        override suspend fun clear() {
            reference = null
        }
    }
}

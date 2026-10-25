package dev.junta.firmamobile.ui

import android.net.Uri
import dev.junta.firmamobile.certificate.CachedCertificateUnlock
import dev.junta.firmamobile.certificate.CertificateErrorCode
import dev.junta.firmamobile.certificate.CertificateGateway
import dev.junta.firmamobile.certificate.CertificateLoadResult
import dev.junta.firmamobile.certificate.CertificateSelectionErrorCode
import dev.junta.firmamobile.certificate.CertificateSelectionResult
import dev.junta.firmamobile.certificate.CertificateSession
import dev.junta.firmamobile.certificate.CertificateUnlockCache
import dev.junta.firmamobile.certificate.StoredCertificateEntry
import dev.junta.firmamobile.certificate.StoredCertificateReference
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
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
class CertificateViewModelCatalogTest {
    private val dispatcher = StandardTestDispatcher()
    private val active = entry("active")
    private val saved = entry("saved")

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun switchRevokesTheSavedUnlockBeforeActivatingAndListsTheEntries() = runTest(dispatcher) {
        val cache = RecordingCache()
        val gateway = CatalogGateway(cache)
        val viewModel = viewModel(gateway, cache)
        advanceUntilIdle()
        assertEquals(listOf(active, saved), viewModel.savedCertificates.value.entries)

        viewModel.switchToSavedCertificate(saved)
        advanceUntilIdle()

        assertEquals(listOf("revoke", "activate saved"), gateway.calls)
        assertEquals(CertificateUiState.Locked(checkNotNull(saved.reference), null, null), viewModel.state.value)
        assertEquals(saved.reference, gateway.current)
    }

    @Test
    fun unverifiedRevocationRefusesTheSwitchAndKeepsTheSelection() = runTest(dispatcher) {
        val cache = RecordingCache().apply { verified = false }
        val gateway = CatalogGateway(cache)
        val viewModel = viewModel(gateway, cache)
        advanceUntilIdle()

        viewModel.switchToSavedCertificate(saved)
        advanceUntilIdle()

        assertEquals(listOf("revoke"), gateway.calls)
        assertEquals(
            CertificateUiState.Locked(checkNotNull(active.reference), null, CertificateUiError.STORAGE_FAILURE),
            viewModel.state.value,
        )
    }

    @Test
    fun addAndRemoveLeaveTheSelectionAndReportFailures() = runTest(dispatcher) {
        val cache = RecordingCache()
        val gateway = CatalogGateway(cache)
        val viewModel = viewModel(gateway, cache)
        advanceUntilIdle()
        val selected = viewModel.state.value

        viewModel.addSavedCertificate(uri("large"))
        advanceUntilIdle()
        assertEquals(CertificateUiError.FILE_TOO_LARGE, viewModel.savedCertificates.value.error)

        viewModel.removeSavedCertificate(saved)
        advanceUntilIdle()

        assertEquals(SavedCertificatesUiState(listOf(active)), viewModel.savedCertificates.value)
        assertEquals(selected, viewModel.state.value)
        assertTrue("revoke" !in gateway.calls)
    }

    private fun viewModel(gateway: CertificateGateway, cache: CertificateUnlockCache) = CertificateViewModel(
        gateway = gateway,
        session = CertificateSession(),
        unlockCache = cache,
        unlockDuration = Duration.ofHours(1),
        ioDispatcher = dispatcher,
    )

    private fun uri(name: String) = Uri.parse("content://documents/$name")

    private fun entry(name: String) = StoredCertificateEntry(
        name, 1, StoredCertificateReference(uri(name), "$name.p12", "application/x-pkcs12", 100, null),
    )

    private class RecordingCache : CertificateUnlockCache {
        var verified = true
        var onRevoke: () -> Unit = {}
        override val requiresExplicitRestore = true

        override suspend fun store(
            reference: StoredCertificateReference,
            password: CharArray,
            issuedAt: Instant,
            expiresAt: Instant,
            observedAtMonotonicNanos: Long,
        ) = true

        override suspend fun restore(reference: StoredCertificateReference, now: Instant): CachedCertificateUnlock? =
            null

        override fun clear() = Unit

        override fun clearAndReport(): Boolean {
            onRevoke()
            return verified
        }
    }

    private inner class CatalogGateway(cache: RecordingCache) : CertificateGateway {
        val calls = mutableListOf<String>()
        var entries = listOf(active, saved)
        var current = active.reference

        init {
            cache.onRevoke = { calls += "revoke" }
        }

        override suspend fun currentReference() = current

        override suspend fun select(uri: Uri): CertificateSelectionResult = error("Not used")

        override suspend fun unlock(password: CharArray): CertificateLoadResult =
            CertificateLoadResult.Failure(CertificateErrorCode.NO_CERTIFICATE_SELECTED)

        override suspend fun forget() = error("Not used")

        override suspend fun savedEntries() = entries

        override suspend fun saveInactive(uri: Uri): CertificateSelectionResult =
            CertificateSelectionResult.Failure(CertificateSelectionErrorCode.FILE_TOO_LARGE)

        override suspend fun removeInactive(entry: StoredCertificateEntry) {
            entries = entries - entry
        }

        override suspend fun activate(entry: StoredCertificateEntry): CertificateSelectionResult {
            calls += "activate ${entry.id}"
            current = entry.reference
            return CertificateSelectionResult.Success(checkNotNull(entry.reference))
        }
    }
}

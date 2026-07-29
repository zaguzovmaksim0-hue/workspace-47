package dev.junta.firmamobile.ui

import android.net.Uri
import dev.junta.firmamobile.certificate.CachedCertificateUnlock
import dev.junta.firmamobile.certificate.CertificateGateway
import dev.junta.firmamobile.certificate.CertificateUnlockCache
import dev.junta.firmamobile.certificate.CertificateLoadResult
import dev.junta.firmamobile.certificate.CertificateRepository
import dev.junta.firmamobile.certificate.CertificateSelectionResult
import dev.junta.firmamobile.certificate.CertificateSession
import dev.junta.firmamobile.certificate.Pkcs12Loader
import dev.junta.firmamobile.certificate.StoredCertificateReference
import dev.junta.firmamobile.certificate.TestCertificateFactory
import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
class CertificateViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun processRecreationRestoresSelectedCertificateLocked() = runTest(dispatcher) {
        val reference = reference(summary = validIdentity().summary)
        val gateway = FakeCertificateGateway().apply { current = reference }

        val viewModel = viewModel(gateway)
        advanceUntilIdle()

        assertEquals(
            CertificateUiState.Locked(reference, reference.summary, error = null),
            viewModel.state.value,
        )
    }


    @Test
    fun processRecreationRestoresCertificateUnlockedFromValidCache() = runTest(dispatcher) {
        val identity = validIdentity()
        val selected = reference(summary = identity.summary)
        val gateway = FakeCertificateGateway().apply {
            current = selected
            unlockResult = CertificateLoadResult.Success(identity)
        }
        val expiresAt = TestCertificateFactory.now.plus(Duration.ofHours(12))
        val cache = FakeCertificateUnlockCache().apply {
            restoredPassword = TestCertificateFactory.password()
            restoredExpiry = expiresAt
        }
        val session = CertificateSession(
            Clock.fixed(TestCertificateFactory.now, ZoneOffset.UTC),
            Duration.ofHours(24),
        )

        val viewModel = viewModel(gateway, session, cache)
        advanceUntilIdle()

        assertEquals(CertificateUiState.Unlocked(selected, identity.summary), viewModel.state.value)
        assertSame(identity, session.identityForSigning())
        assertArrayEquals(TestCertificateFactory.password(), gateway.receivedPassword)
        assertEquals(1, cache.restoreCalls)
        assertTrue(cache.lastReturnedPassword!!.all { it == '\u0000' })

        val restoredState = session.state() as dev.junta.firmamobile.certificate.CertificateSessionState.Unlocked
        assertEquals(expiresAt, restoredState.expiresAt)
    }

    @Test
    fun failedCachedPasswordIsClearedAndFallsBackToLockedWithoutMisleadingError() = runTest(dispatcher) {
        val selected = reference()
        val gateway = FakeCertificateGateway().apply {
            current = selected
            unlockResult = CertificateLoadResult.Failure(
                dev.junta.firmamobile.certificate.CertificateErrorCode.INVALID_PASSWORD_OR_FILE,
            )
        }
        val cache = FakeCertificateUnlockCache().apply {
            restoredPassword = "stale-password".toCharArray()
            restoredExpiry = TestCertificateFactory.now.plus(Duration.ofHours(12))
        }

        val viewModel = viewModel(gateway, cache = cache)
        advanceUntilIdle()

        assertEquals(CertificateUiState.Locked(selected, null, null), viewModel.state.value)
        assertEquals(1, cache.clearCalls)
        assertTrue(cache.lastReturnedPassword!!.all { it == '\u0000' })
    }

    @Test
    fun selectionMovesFromEmptyToLockedReference() = runTest(dispatcher) {
        val gateway = FakeCertificateGateway()
        val cache = FakeCertificateUnlockCache()
        val viewModel = viewModel(gateway, cache = cache)
        advanceUntilIdle()
        val selected = reference()
        gateway.selectionResult = CertificateSelectionResult.Success(selected)

        viewModel.onCertificateSelected(selected.uri)
        advanceUntilIdle()

        assertEquals(CertificateUiState.Locked(selected, null, null), viewModel.state.value)
        assertTrue(cache.clearCalls >= 1)
    }

    @Test
    fun unlockClearsTransferredPasswordAndKeepsIdentityOnlyInSession() = runTest(dispatcher) {
        val selected = reference()
        val identity = validIdentity()
        val gateway = FakeCertificateGateway().apply {
            current = selected
            unlockResult = CertificateLoadResult.Success(identity)
        }
        val session = CertificateSession(
            clock = Clock.fixed(TestCertificateFactory.now, ZoneOffset.UTC),
            unlockDuration = Duration.ofHours(24),
        )
        val cache = FakeCertificateUnlockCache()
        val viewModel = viewModel(gateway, session, cache)
        advanceUntilIdle()
        val passphrase = "test-password-123".toCharArray()
        val expectedPassphrase = passphrase.copyOf()

        viewModel.unlock(passphrase)
        advanceUntilIdle()

        assertArrayEquals(expectedPassphrase, gateway.receivedPassword)
        assertArrayEquals(CharArray(passphrase.size), passphrase)
        assertEquals(
            CertificateUiState.Unlocked(selected, identity.summary),
            viewModel.state.value,
        )
        assertSame(identity, session.identityForSigning())
        assertArrayEquals(expectedPassphrase, cache.storedPassword)
        assertEquals(TestCertificateFactory.now, cache.storedIssuedAt)
        assertEquals(TestCertificateFactory.now.plus(Duration.ofHours(24)), cache.storedExpiry)
    }

    @Test
    fun cancellingUnlockBeforeCoroutineStartsStillClearsPassword() = runTest(dispatcher) {
        val selected = reference()
        val gateway = FakeCertificateGateway().apply { current = selected }
        val viewModel = viewModel(gateway)
        advanceUntilIdle()
        val passphrase = "queued-password".toCharArray()

        viewModel.unlock(passphrase)
        viewModel.lock()
        advanceUntilIdle()

        assertArrayEquals(CharArray(passphrase.size), passphrase)
        assertNull(gateway.receivedPassword)
        assertEquals(CertificateUiState.Locked(selected, null, null), viewModel.state.value)
    }

    @Test
    fun wrongPasswordReturnsLockedStateWithClosedUiError() = runTest(dispatcher) {
        val selected = reference()
        val gateway = FakeCertificateGateway().apply {
            current = selected
            unlockResult = CertificateLoadResult.Failure(
                dev.junta.firmamobile.certificate.CertificateErrorCode.INVALID_PASSWORD_OR_FILE,
            )
        }
        val viewModel = viewModel(gateway)
        advanceUntilIdle()

        viewModel.unlock("wrong".toCharArray())
        advanceUntilIdle()

        assertEquals(
            CertificateUiState.Locked(
                selected,
                null,
                CertificateUiError.PASSWORD_INVALID_OR_FILE,
            ),
            viewModel.state.value,
        )
    }

    @Test
    fun manualLockDropsUnlockedIdentity() = runTest(dispatcher) {
        val selected = reference()
        val identity = validIdentity()
        val gateway = FakeCertificateGateway().apply {
            current = selected
            unlockResult = CertificateLoadResult.Success(identity)
        }
        val session = CertificateSession(
            Clock.fixed(TestCertificateFactory.now, ZoneOffset.UTC),
            Duration.ofHours(24),
        )
        val cache = FakeCertificateUnlockCache()
        val viewModel = viewModel(gateway, session, cache)
        advanceUntilIdle()
        viewModel.unlock(TestCertificateFactory.password())
        advanceUntilIdle()

        viewModel.lock()

        assertNull(session.identityForSigning())
        assertEquals(CertificateUiState.Locked(selected, identity.summary, null), viewModel.state.value)
        assertTrue(cache.clearCalls >= 1)
    }

    @Test
    fun backgroundKeepsUnlockedIdentityAndUiState() = runTest(dispatcher) {
        val selected = reference()
        val identity = validIdentity()
        val gateway = FakeCertificateGateway().apply {
            current = selected
            unlockResult = CertificateLoadResult.Success(identity)
        }
        val session = CertificateSession(
            Clock.fixed(TestCertificateFactory.now, ZoneOffset.UTC),
            Duration.ofHours(24),
        )
        val viewModel = viewModel(gateway, session)
        advanceUntilIdle()
        viewModel.unlock(TestCertificateFactory.password())
        advanceUntilIdle()

        viewModel.onAppBackgrounded()

        assertSame(identity, session.identityForSigning())
        assertEquals(CertificateUiState.Unlocked(selected, identity.summary), viewModel.state.value)
    }


    @Test
    fun memoryPressureReleasesIdentityThenRestoresItFromCacheWithoutPasswordPrompt() = runTest(dispatcher) {
        val selected = reference()
        val identity = validIdentity()
        val gateway = FakeCertificateGateway().apply {
            current = selected
            unlockResult = CertificateLoadResult.Success(identity)
        }
        val cache = FakeCertificateUnlockCache()
        val session = CertificateSession(
            Clock.fixed(TestCertificateFactory.now, ZoneOffset.UTC),
            Duration.ofHours(24),
        )
        val viewModel = viewModel(gateway, session, cache)
        advanceUntilIdle()
        viewModel.unlock(TestCertificateFactory.password())
        advanceUntilIdle()
        gateway.receivedPassword = null

        viewModel.onMemoryPressure()
        advanceUntilIdle()

        assertSame(identity, session.identityForSigning())
        assertEquals(CertificateUiState.Unlocked(selected, identity.summary), viewModel.state.value)
        assertArrayEquals(TestCertificateFactory.password(), gateway.receivedPassword)
    }

    @Test
    fun forgetClearsRepositorySessionAndUi() = runTest(dispatcher) {
        val selected = reference()
        val gateway = FakeCertificateGateway().apply { current = selected }
        val session = CertificateSession()
        val cache = FakeCertificateUnlockCache()
        val viewModel = viewModel(gateway, session, cache)
        advanceUntilIdle()

        viewModel.forget()
        viewModel.onAppBackgrounded()
        advanceUntilIdle()

        assertEquals(1, gateway.forgetCalls)
        assertEquals(CertificateUiState.NoCertificate(), viewModel.state.value)
        assertEquals(dev.junta.firmamobile.certificate.CertificateSessionState.Empty, session.state())
        assertTrue(cache.clearCalls >= 1)
    }

    private fun viewModel(
        gateway: FakeCertificateGateway,
        session: CertificateSession = CertificateSession(),
        cache: CertificateUnlockCache = FakeCertificateUnlockCache(),
    ) = CertificateViewModel(
        gateway = gateway,
        session = session,
        unlockCache = cache,
        clock = Clock.fixed(TestCertificateFactory.now, ZoneOffset.UTC),
        unlockDuration = Duration.ofHours(24),
    )

    private fun reference(
        summary: dev.junta.firmamobile.certificate.CertificateSummary? = null,
    ) = StoredCertificateReference(
        uri = Uri.parse("content://documents/synthetic"),
        displayName = "synthetic.p12",
        mimeType = CertificateRepository.MIME_X_PKCS12,
        size = 4096,
        summary = summary,
    )

    private suspend fun validIdentity(): dev.junta.firmamobile.certificate.UnlockedIdentity {
        val bytes = TestCertificateFactory.validRsa()
        val result = Pkcs12Loader(
            Clock.fixed(TestCertificateFactory.now, ZoneOffset.UTC),
        ).load(
            ByteArrayInputStream(bytes),
            bytes.size.toLong(),
            TestCertificateFactory.password(),
        )
        return (result as CertificateLoadResult.Success).identity
    }


    private class FakeCertificateUnlockCache : CertificateUnlockCache {
        var restoredPassword: CharArray? = null
        var restoredExpiry: Instant? = null
        var storedPassword: CharArray? = null
        var storedIssuedAt: Instant? = null
        var storedExpiry: Instant? = null
        var restoreCalls = 0
        var clearCalls = 0
        var lastReturnedPassword: CharArray? = null

        override suspend fun store(
            reference: StoredCertificateReference,
            password: CharArray,
            issuedAt: Instant,
            expiresAt: Instant,
        ): Boolean {
            storedPassword?.fill('\u0000')
            storedPassword = password.copyOf()
            storedIssuedAt = issuedAt
            storedExpiry = expiresAt
            restoredPassword?.fill('\u0000')
            restoredPassword = password.copyOf()
            restoredExpiry = expiresAt
            return true
        }

        override suspend fun restore(
            reference: StoredCertificateReference,
            now: Instant,
        ): CachedCertificateUnlock? {
            restoreCalls += 1
            val password = restoredPassword?.copyOf() ?: return null
            val expiry = restoredExpiry
            if (expiry == null) {
                password.fill('\u0000')
                return null
            }
            lastReturnedPassword = password
            return CachedCertificateUnlock(password, expiry)
        }

        override fun clear() {
            clearCalls += 1
            restoredPassword?.fill('\u0000')
            restoredPassword = null
            restoredExpiry = null
        }
    }

    private class FakeCertificateGateway : CertificateGateway {
        var current: StoredCertificateReference? = null
        var selectionResult: CertificateSelectionResult =
            CertificateSelectionResult.Failure(
                dev.junta.firmamobile.certificate.CertificateSelectionErrorCode.DOCUMENT_UNAVAILABLE,
            )
        var unlockResult: CertificateLoadResult = CertificateLoadResult.Failure(
            dev.junta.firmamobile.certificate.CertificateErrorCode.NO_CERTIFICATE_SELECTED,
        )
        var receivedPassword: CharArray? = null
        var forgetCalls = 0

        override suspend fun currentReference(): StoredCertificateReference? = current

        override suspend fun select(uri: Uri): CertificateSelectionResult = selectionResult

        override suspend fun unlock(password: CharArray): CertificateLoadResult {
            receivedPassword = password.copyOf()
            return unlockResult
        }

        override suspend fun forget() {
            forgetCalls += 1
            current = null
        }
    }
}

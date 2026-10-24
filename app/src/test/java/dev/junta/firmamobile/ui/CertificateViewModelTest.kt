package dev.junta.firmamobile.ui

import android.net.Uri
import dev.junta.firmamobile.certificate.CachedCertificateUnlock
import dev.junta.firmamobile.certificate.CertificateErrorCode
import dev.junta.firmamobile.certificate.CertificateGateway
import dev.junta.firmamobile.certificate.CertificateUnlockRetention
import dev.junta.firmamobile.certificate.CertificateUnlockCache
import dev.junta.firmamobile.certificate.CertificateUnlockLease
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
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
    fun cachedLeaseExpiringDuringGatewayReloadCannotBeRenewedFromCivilExpiry() = runTest(dispatcher) {
        val identity = validIdentity()
        val selected = reference(summary = identity.summary)
        val monotonic = MutableMonotonicClock(1_000_000_000L)
        val expiresAt = TestCertificateFactory.now.plus(Duration.ofHours(12))
        val cache = FakeCertificateUnlockCache().apply {
            restoredPassword = TestCertificateFactory.password()
            restoredExpiry = expiresAt
            restoredLease = CertificateUnlockLease(
                expiresAt = expiresAt,
                observedAtMonotonicNanos = monotonic.nowNanos(),
                lifetimeNanos = Duration.ofMinutes(1).toNanos(),
            )
        }
        val gateway = FakeCertificateGateway().apply {
            current = selected
            unlockResult = CertificateLoadResult.Success(identity)
            beforeUnlockResult = { monotonic.advance(Duration.ofMinutes(2)) }
        }
        val session = CertificateSession(
            clock = Clock.fixed(TestCertificateFactory.now, ZoneOffset.UTC),
            unlockDuration = Duration.ofHours(24),
            monotonicNanos = monotonic::nowNanos,
        )

        val viewModel = viewModel(gateway, session, cache)
        advanceUntilIdle()

        assertEquals(
            CertificateUiState.Locked(selected, identity.summary, null),
            viewModel.state.value,
        )
        assertNull(session.identityForSigning())
        assertTrue(cache.clearCalls >= 1)
        assertTrue(cache.lastReturnedPassword!!.all { it == '\u0000' })
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
    fun sessionUnlockIsNotPublishedBeforeCacheCommitCompletes() = runTest(dispatcher) {
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
        val cache = BlockingCertificateUnlockCache()
        val viewModel = viewModel(gateway, session, cache)
        advanceUntilIdle()
        val passphrase = "commit-order-password".toCharArray()
        var identityBeforeCommit: dev.junta.firmamobile.certificate.UnlockedIdentity? = null

        try {
            viewModel.unlock(passphrase)
            runCurrent()
            assertTrue(cache.storeStarted.isCompleted)
            identityBeforeCommit = session.identityForSigning()
        } finally {
            viewModel.lock()
            cache.finishStore.complete(Unit)
            advanceUntilIdle()
        }

        assertNull(identityBeforeCommit)
        assertNull(session.identityForSigning())
        assertEquals(CertificateUiState.Locked(selected, null, null), viewModel.state.value)
        assertArrayEquals(CharArray(passphrase.size), passphrase)
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
    fun preciseImportErrorsReturnLockedStateWithClosedUiErrors() = runTest(dispatcher) {
        val cases = listOf(
            CertificateErrorCode.INVALID_PASSWORD to CertificateUiError.INVALID_PASSWORD,
            CertificateErrorCode.INVALID_PKCS12_FILE to CertificateUiError.INVALID_PKCS12_FILE,
            CertificateErrorCode.UNSUPPORTED_PKCS12_ENCRYPTION to CertificateUiError.UNSUPPORTED_PKCS12_ENCRYPTION,
            CertificateErrorCode.INVALID_PASSWORD_OR_FILE to CertificateUiError.PASSWORD_INVALID_OR_FILE,
        )
        for ((code, error) in cases) {
            val selected = reference()
            val gateway = FakeCertificateGateway().apply {
                current = selected
                unlockResult = CertificateLoadResult.Failure(code)
            }
            val viewModel = viewModel(gateway)
            advanceUntilIdle()

            viewModel.unlock("wrong".toCharArray())
            advanceUntilIdle()

            assertEquals(CertificateUiState.Locked(selected, null, error), viewModel.state.value)
        }
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
        assertTrue((viewModel.state.value as CertificateUiState.Locked).revocationPending)
        advanceUntilIdle()
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
        val session = CertificateSession(clock = Clock.fixed(TestCertificateFactory.now, ZoneOffset.UTC))
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

    @Test
    fun unverifiedRevocationKeepsTheSelectionLockedUntilARetrySucceeds() = runTest(dispatcher) {
        val selected = reference()
        val replacement = selected.copy(uri = Uri.parse("content://documents/replacement"))
        val gateway = FakeCertificateGateway().apply {
            current = selected
            selectionResult = CertificateSelectionResult.Success(replacement)
        }
        val session = CertificateSession(clock = Clock.fixed(TestCertificateFactory.now, ZoneOffset.UTC))
        val cache = FakeCertificateUnlockCache().apply { clearVerified = false }
        val model = viewModel(gateway, session, cache)
        advanceUntilIdle()
        val refused = CertificateUiState.Locked(selected, null, CertificateUiError.STORAGE_FAILURE)

        model.onCertificateSelected(replacement.uri)
        advanceUntilIdle()
        assertEquals(refused, model.state.value)
        model.forget()
        advanceUntilIdle()
        assertEquals(refused, model.state.value)
        assertEquals(0, gateway.forgetCalls)
        assertNull(session.identityForSigning())

        cache.clearVerified = true
        model.forget()
        advanceUntilIdle()
        assertEquals(1, gateway.forgetCalls)
        assertEquals(CertificateUiState.NoCertificate(), model.state.value)
    }

    @Test
    fun explicitLockReportsUnverifiedRevocationAndStillLocksIdentity() = runTest(dispatcher) {
        val selected = reference()
        val gateway = FakeCertificateGateway().apply { current = selected }
        val session = CertificateSession(clock = Clock.fixed(TestCertificateFactory.now, ZoneOffset.UTC))
        val cache = FakeCertificateUnlockCache().apply { clearVerified = false }
        val model = viewModel(gateway, session, cache)
        advanceUntilIdle()
        model.lock()
        assertNull(session.identityForSigning())
        assertTrue((model.state.value as CertificateUiState.Locked).revocationPending)
        advanceUntilIdle()
        val state = model.state.value as CertificateUiState.Locked
        assertEquals(CertificateUiError.STORAGE_FAILURE, state.error)
    }

    @Test
    fun lockDeniesKeyAndRestoreBeforeQueuedDurableRevocationRuns() = runTest(dispatcher) {
        val identity = validIdentity()
        val selected = reference(summary = identity.summary)
        val gateway = FakeCertificateGateway().apply { current = selected }
        val session = CertificateSession(clock = Clock.fixed(TestCertificateFactory.now, ZoneOffset.UTC))
        val cache = FakeCertificateUnlockCache()
        val model = viewModel(gateway, session, cache)
        advanceUntilIdle()
        session.unlock(identity)
        val clears = cache.clearCalls
        val restores = cache.restoreCalls
        model.lock()
        assertNull(session.identityForSigning())
        assertEquals(clears, cache.clearCalls)
        assertTrue((model.state.value as CertificateUiState.Locked).revocationPending)
        model.onAppForegrounded()
        val password = "queued".toCharArray()
        model.unlock(password)
        assertTrue(password.all { it == '\u0000' })
        advanceUntilIdle()
        assertTrue(cache.clearCalls > clears)
        assertEquals(restores, cache.restoreCalls)
        assertTrue(!(model.state.value as CertificateUiState.Locked).revocationPending)
    }

    @Test
    fun eachRetentionChoiceControlsBothPersistedExpiryAndSessionLease() = runTest(dispatcher) {
        for (retention in CertificateUnlockRetention.entries) {
            val identity = validIdentity()
            val selected = reference()
            val gateway = FakeCertificateGateway().apply {
                current = selected
                unlockResult = CertificateLoadResult.Success(identity)
            }
            val session = CertificateSession(clock = Clock.fixed(TestCertificateFactory.now, ZoneOffset.UTC))
            val cache = FakeCertificateUnlockCache()
            val model = viewModel(gateway, session, cache)
            advanceUntilIdle()
            val password = TestCertificateFactory.password()
            model.unlock(password, retention)
            advanceUntilIdle()
            val expectedExpiry = TestCertificateFactory.now.plus(retention.duration)
            assertEquals(expectedExpiry, cache.storedExpiry)
            val state = session.state() as dev.junta.firmamobile.certificate.CertificateSessionState.Unlocked
            assertEquals(expectedExpiry, state.expiresAt)
            assertArrayEquals(CharArray(password.size), password)
            model.lock()
            advanceUntilIdle()
        }
    }

    @Test
    fun productionDefaultUnlockDurationIsFifteenMinutes() = runTest(dispatcher) {
        val gateway = FakeCertificateGateway().apply {
            current = reference()
            unlockResult = CertificateLoadResult.Success(validIdentity())
        }
        val cache = FakeCertificateUnlockCache()
        val model = CertificateViewModel(
            gateway = gateway,
            session = CertificateSession(clock = Clock.fixed(TestCertificateFactory.now, ZoneOffset.UTC)),
            unlockCache = cache,
            clock = Clock.fixed(TestCertificateFactory.now, ZoneOffset.UTC),
            ioDispatcher = dispatcher,
        )
        advanceUntilIdle()
        model.unlock(TestCertificateFactory.password())
        advanceUntilIdle()
        assertEquals(TestCertificateFactory.now.plus(Duration.ofMinutes(15)), cache.storedExpiry)
    }

    @Test
    fun deniedSaveClearsOldRecordButKeepsManualMemorySession() = runTest(dispatcher) {
        val identity = validIdentity()
        val gateway = FakeCertificateGateway().apply {
            current = reference()
            unlockResult = CertificateLoadResult.Success(identity)
        }
        val cache = FakeCertificateUnlockCache().apply {
            requiresExplicitRestore = true
            storeAllowed = false
            restoredPassword = TestCertificateFactory.password()
            restoredExpiry = TestCertificateFactory.now.plus(Duration.ofHours(24))
        }
        val session = CertificateSession(clock = Clock.fixed(TestCertificateFactory.now, ZoneOffset.UTC))
        val model = viewModel(gateway, session, cache)
        advanceUntilIdle()
        model.unlock(TestCertificateFactory.password())
        advanceUntilIdle()
        assertTrue(model.state.value is CertificateUiState.Unlocked)
        assertSame(identity, session.identityForSigning())
        assertNull(cache.restoredPassword)
        assertEquals(1, cache.clearCalls)
    }

    @Test
    fun deniedSaveWithUnverifiedCleanupFailsClosed() = runTest(dispatcher) {
        val gateway = FakeCertificateGateway().apply {
            current = reference()
            unlockResult = CertificateLoadResult.Success(validIdentity())
        }
        val cache = FakeCertificateUnlockCache().apply {
            requiresExplicitRestore = true
            storeAllowed = false
            clearVerified = false
        }
        val session = CertificateSession(clock = Clock.fixed(TestCertificateFactory.now, ZoneOffset.UTC))
        val model = viewModel(gateway, session, cache)
        advanceUntilIdle()
        model.unlock(TestCertificateFactory.password())
        advanceUntilIdle()
        val locked = model.state.value as CertificateUiState.Locked
        assertTrue(locked.revocationPending)
        assertEquals(CertificateUiError.STORAGE_FAILURE, locked.error)
        assertNull(session.identityForSigning())
        model.restoreSavedUnlock()
        advanceUntilIdle()
        assertEquals(0, cache.restoreCalls)
    }

    @Test
    fun nativePromptBackgroundDoesNotCancelExplicitRestore() = runTest(dispatcher) {
        val approval = CompletableDeferred<Unit>()
        val identity = validIdentity()
        val gateway = FakeCertificateGateway().apply {
            current = reference()
            unlockResult = CertificateLoadResult.Success(identity)
        }
        val cache = FakeCertificateUnlockCache().apply {
            requiresExplicitRestore = true
            restoredPassword = TestCertificateFactory.password()
            restoredExpiry = TestCertificateFactory.now.plus(Duration.ofMinutes(15))
            beforeRestore = { isAwaitingAuthentication = true; approval.await() }
        }
        val session = CertificateSession(clock = Clock.fixed(TestCertificateFactory.now, ZoneOffset.UTC))
        val model = viewModel(gateway, session, cache)
        advanceUntilIdle()
        model.restoreSavedUnlock()
        runCurrent()
        model.onAppBackgrounded()
        approval.complete(Unit)
        advanceUntilIdle()
        assertTrue(model.state.value is CertificateUiState.Unlocked)
        assertSame(identity, session.identityForSigning())
    }

    @Test
    fun authenticatedCacheNeverRestoresAtStartupForegroundOrMemoryPressure() = runTest(dispatcher) {
        val gateway = FakeCertificateGateway().apply { current = reference() }
        val cache = FakeCertificateUnlockCache().apply { requiresExplicitRestore = true }
        val model = viewModel(gateway, cache = cache)
        advanceUntilIdle(); model.onAppForegrounded(); model.onMemoryPressure(); advanceUntilIdle()
        assertEquals(0, cache.restoreCalls)
        assertTrue(model.state.value is CertificateUiState.Locked)
    }

    @Test
    fun explicitRestoreUsesOriginalLeaseAndConsumesPasswordOnlyOnce() = runTest(dispatcher) {
        val identity = validIdentity()
        val gateway = FakeCertificateGateway().apply { current = reference(); unlockResult = CertificateLoadResult.Success(identity) }
        val cache = FakeCertificateUnlockCache().apply {
            requiresExplicitRestore = true
            restoredPassword = TestCertificateFactory.password()
            restoredExpiry = TestCertificateFactory.now.plus(Duration.ofMinutes(15))
        }
        val session = CertificateSession(clock = Clock.fixed(TestCertificateFactory.now, ZoneOffset.UTC))
        val model = viewModel(gateway, session, cache)
        advanceUntilIdle(); model.restoreSavedUnlock(); model.restoreSavedUnlock(); advanceUntilIdle()
        assertEquals(1, cache.restoreCalls)
        assertTrue(model.state.value is CertificateUiState.Unlocked)
        assertSame(identity, session.identityForSigning())
        assertTrue(cache.lastReturnedPassword!!.all { it == '\u0000' })
    }

    @Test
    fun deniedExplicitRestoreStaysLocked() = runTest(dispatcher) {
        val gateway = FakeCertificateGateway().apply { current = reference() }
        val cache = FakeCertificateUnlockCache().apply { requiresExplicitRestore = true }
        val session = CertificateSession()
        val model = viewModel(gateway, session, cache)
        advanceUntilIdle(); model.restoreSavedUnlock(); advanceUntilIdle()
        assertTrue(model.state.value is CertificateUiState.Locked)
        assertNull(session.identityForSigning()); assertNull(gateway.receivedPassword)
    }

    @Test
    fun manualLockCancelsPendingExplicitApproval() = runTest(dispatcher) {
        val approval = kotlinx.coroutines.CompletableDeferred<Unit>()
        val identity = validIdentity()
        val gateway = FakeCertificateGateway().apply { current = reference(); unlockResult = CertificateLoadResult.Success(identity) }
        val cache = FakeCertificateUnlockCache().apply {
            requiresExplicitRestore = true
            beforeRestore = { approval.await() }
            restoredPassword = TestCertificateFactory.password()
            restoredExpiry = TestCertificateFactory.now.plus(Duration.ofMinutes(15))
        }
        val session = CertificateSession()
        val model = viewModel(gateway, session, cache)
        advanceUntilIdle(); model.restoreSavedUnlock(); runCurrent(); model.lock(); approval.complete(Unit); advanceUntilIdle()
        assertTrue(model.state.value is CertificateUiState.Locked)
        assertNull(session.identityForSigning()); assertNull(gateway.receivedPassword)
    }

    private fun viewModel(
        gateway: FakeCertificateGateway,
        session: CertificateSession = CertificateSession(clock = Clock.fixed(TestCertificateFactory.now, ZoneOffset.UTC)),
        cache: CertificateUnlockCache = FakeCertificateUnlockCache(),
    ) = CertificateViewModel(
        gateway = gateway,
        session = session,
        unlockCache = cache,
        clock = Clock.fixed(TestCertificateFactory.now, ZoneOffset.UTC),
        unlockDuration = Duration.ofHours(24),
        ioDispatcher = dispatcher,
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


    private class BlockingCertificateUnlockCache : CertificateUnlockCache {
        val storeStarted = CompletableDeferred<Unit>()
        val finishStore = CompletableDeferred<Unit>()
        var clearCalls = 0

        override suspend fun store(
            reference: StoredCertificateReference,
            password: CharArray,
            issuedAt: Instant,
            expiresAt: Instant,
            observedAtMonotonicNanos: Long,
        ): Boolean {
            storeStarted.complete(Unit)
            withContext(NonCancellable) { finishStore.await() }
            return true
        }

        override suspend fun restore(
            reference: StoredCertificateReference,
            now: Instant,
        ): CachedCertificateUnlock? = null

        override fun clear() {
            clearCalls += 1
        }
    }

    private class FakeCertificateUnlockCache : CertificateUnlockCache {
        override var requiresExplicitRestore = false
        var storeAllowed = true
        override var isAwaitingAuthentication = false
        var beforeRestore: suspend () -> Unit = {}
        var clearVerified = true
        override fun clearAndReport(): Boolean { clear(); return clearVerified }
        var restoredPassword: CharArray? = null
        var restoredExpiry: Instant? = null
        var restoredLease: CertificateUnlockLease? = null
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
            observedAtMonotonicNanos: Long,
        ): Boolean {
            if (!storeAllowed) return false
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
            beforeRestore()
            val password = restoredPassword?.copyOf() ?: return null
            val expiry = restoredExpiry
            if (expiry == null) {
                password.fill('\u0000')
                return null
            }
            val lease = restoredLease ?: CertificateUnlockLease(
                expiresAt = expiry,
                observedAtMonotonicNanos = System.nanoTime(),
                lifetimeNanos = Duration.between(now, expiry).toNanos(),
            )
            lastReturnedPassword = password
            return CachedCertificateUnlock(password, expiry, lease)
        }

        override fun clear() {
            clearCalls += 1
            restoredPassword?.fill('\u0000')
            restoredPassword = null
            restoredExpiry = null
            restoredLease = null
        }
    }

    private class MutableMonotonicClock(
        private var current: Long,
    ) {
        fun nowNanos(): Long = current

        fun advance(duration: Duration) {
            current = Math.addExact(current, duration.toNanos())
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
        var beforeUnlockResult: (() -> Unit)? = null
        var forgetCalls = 0

        override suspend fun currentReference(): StoredCertificateReference? = current

        override suspend fun select(uri: Uri): CertificateSelectionResult = selectionResult

        override suspend fun unlock(password: CharArray): CertificateLoadResult {
            receivedPassword = password.copyOf()
            beforeUnlockResult?.invoke()
            return unlockResult
        }

        override suspend fun forget() {
            forgetCalls += 1
            current = null
        }
    }
}

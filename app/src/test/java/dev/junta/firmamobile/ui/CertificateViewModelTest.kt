package dev.junta.firmamobile.ui

import android.net.Uri
import dev.junta.firmamobile.certificate.CertificateGateway
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
    fun selectionMovesFromEmptyToLockedReference() = runTest(dispatcher) {
        val gateway = FakeCertificateGateway()
        val viewModel = viewModel(gateway)
        advanceUntilIdle()
        val selected = reference()
        gateway.selectionResult = CertificateSelectionResult.Success(selected)

        viewModel.onCertificateSelected(selected.uri)
        advanceUntilIdle()

        assertEquals(CertificateUiState.Locked(selected, null, null), viewModel.state.value)
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
            unlockDuration = Duration.ofMinutes(10),
        )
        val viewModel = viewModel(gateway, session)
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
            Duration.ofMinutes(10),
        )
        val viewModel = viewModel(gateway, session)
        advanceUntilIdle()
        viewModel.unlock(TestCertificateFactory.password())
        advanceUntilIdle()

        viewModel.lock()

        assertNull(session.identityForSigning())
        assertEquals(CertificateUiState.Locked(selected, identity.summary, null), viewModel.state.value)
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
            Duration.ofMinutes(10),
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
    fun forgetClearsRepositorySessionAndUi() = runTest(dispatcher) {
        val selected = reference()
        val gateway = FakeCertificateGateway().apply { current = selected }
        val session = CertificateSession()
        val viewModel = viewModel(gateway, session)
        advanceUntilIdle()

        viewModel.forget()
        viewModel.onAppBackgrounded()
        advanceUntilIdle()

        assertEquals(1, gateway.forgetCalls)
        assertEquals(CertificateUiState.NoCertificate(), viewModel.state.value)
        assertEquals(dev.junta.firmamobile.certificate.CertificateSessionState.Empty, session.state())
    }

    private fun viewModel(
        gateway: FakeCertificateGateway,
        session: CertificateSession = CertificateSession(),
    ) = CertificateViewModel(gateway, session)

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

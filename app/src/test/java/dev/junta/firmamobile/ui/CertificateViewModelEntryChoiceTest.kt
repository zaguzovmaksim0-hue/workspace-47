package dev.junta.firmamobile.ui

import android.net.Uri
import dev.junta.firmamobile.certificate.CertificateEntryChoice
import dev.junta.firmamobile.certificate.CertificateErrorCode
import dev.junta.firmamobile.certificate.CertificateGateway
import dev.junta.firmamobile.certificate.CertificateLoadResult
import dev.junta.firmamobile.certificate.CertificateSelectionResult
import dev.junta.firmamobile.certificate.CertificateSession
import dev.junta.firmamobile.certificate.CertificateSummary
import dev.junta.firmamobile.certificate.StoredCertificateReference
import dev.junta.firmamobile.signing.syntheticIdentity
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
class CertificateViewModelEntryChoiceTest {
    private val dispatcher = StandardTestDispatcher()
    private val reference = StoredCertificateReference(
        Uri.parse("content://documents/several"), "several.p12", "application/x-pkcs12", 100, null,
    )
    private val choices = listOf(choice(1, "a"), choice(2, "b"))
    private val identity = syntheticIdentity()
    private val clock = Clock.fixed(identity.certificate.notBefore.toInstant().plusSeconds(60), ZoneOffset.UTC)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun multipleKeysOfferTheEntriesAndTheChosenOneUnlocksWithTheTypedPassword() = runTest(dispatcher) {
        val gateway = ChoiceGateway()
        val viewModel = choosing(gateway)
        assertEquals(choices, (viewModel.state.value as CertificateUiState.Locked).choices)
        assertEquals(null, (viewModel.state.value as CertificateUiState.Locked).error)

        viewModel.chooseCertificate(choices[1])
        runCurrent()

        assertEquals(listOf("b" to "secret"), gateway.chosen)
        assertTrue(viewModel.state.value is CertificateUiState.Unlocked)
        assertTrue("The held password copy must be cleared", gateway.chosenPassword!!.all { it == '\u0000' })
    }

    @Test
    fun everyExitDropsTheChoiceSoNoEntryCanBeUnlockedLater() = runTest(dispatcher) {
        val exits: List<Pair<String, (CertificateViewModel) -> Unit>> = listOf(
            "cancel" to { it.cancelCertificateChoice() },
            "lock" to { it.lock() },
            "new file" to { it.prepareForCertificateSelection() },
            "background" to { it.onAppBackgrounded() },
            "memory pressure" to { it.onMemoryPressure() },
            "foreign choice" to { it.chooseCertificate(choice(3, "c")) },
        )
        for ((name, exit) in exits) {
            val gateway = ChoiceGateway()
            val viewModel = choosing(gateway)
            assertEquals(name, choices, (viewModel.state.value as CertificateUiState.Locked).choices)
            exit(viewModel)
            runCurrent()
            assertEquals(name, emptyList<CertificateEntryChoice>(), (viewModel.state.value as CertificateUiState.Locked).choices)
            viewModel.chooseCertificate(choices[0])
            runCurrent()
            assertEquals(name, emptyList<Pair<String, String>>(), gateway.chosen)
        }
    }

    @Test
    fun theChoiceExpires() = runTest(dispatcher) {
        val gateway = ChoiceGateway()
        val viewModel = choosing(gateway)
        assertEquals(choices, (viewModel.state.value as CertificateUiState.Locked).choices)
        advanceTimeBy(119_000)
        runCurrent()
        assertEquals(choices, (viewModel.state.value as CertificateUiState.Locked).choices)
        advanceTimeBy(2_000)
        advanceUntilIdle()
        assertEquals(emptyList<CertificateEntryChoice>(), (viewModel.state.value as CertificateUiState.Locked).choices)
        viewModel.chooseCertificate(choices[0])
        advanceUntilIdle()
        assertTrue(gateway.chosen.isEmpty())
    }

    @Test
    fun unlistableFileKeepsTheMultipleKeysError() = runTest(dispatcher) {
        val gateway = ChoiceGateway().apply { listed = emptyList() }
        val viewModel = viewModel(gateway)
        advanceUntilIdle()
        viewModel.unlock("secret".toCharArray())
        advanceUntilIdle()
        assertEquals(
            CertificateUiState.Locked(reference, null, CertificateUiError.MULTIPLE_PRIVATE_KEYS),
            viewModel.state.value,
        )
    }

    private fun TestScope.choosing(gateway: ChoiceGateway): CertificateViewModel {
        val viewModel = viewModel(gateway)
        runCurrent()
        viewModel.unlock("secret".toCharArray())
        runCurrent()
        return viewModel
    }

    private fun viewModel(gateway: CertificateGateway) = CertificateViewModel(
        gateway = gateway,
        session = CertificateSession(clock, Duration.ofHours(24)),
        clock = clock,
        unlockDuration = Duration.ofHours(1),
        ioDispatcher = dispatcher,
    )

    private fun choice(number: Int, hash: String) = CertificateEntryChoice(
        number,
        CertificateSummary("Owner $number", "Issuer", Instant.EPOCH, Instant.EPOCH.plusSeconds(1)),
        hash,
    )

    private inner class ChoiceGateway : CertificateGateway {
        var listed = choices
        val chosen = mutableListOf<Pair<String, String>>()
        var chosenPassword: CharArray? = null

        override suspend fun currentReference() = reference

        override suspend fun select(uri: Uri): CertificateSelectionResult = error("Not used")

        override suspend fun unlock(password: CharArray): CertificateLoadResult =
            CertificateLoadResult.Failure(CertificateErrorCode.MULTIPLE_PRIVATE_KEYS)

        override suspend fun forget() = error("Not used")

        override suspend fun certificateChoices(password: CharArray) = listed

        override suspend fun unlockChosen(password: CharArray, certificateSha256: String): CertificateLoadResult {
            chosen += certificateSha256 to String(password)
            chosenPassword = password
            return CertificateLoadResult.Success(identity)
        }
    }
}

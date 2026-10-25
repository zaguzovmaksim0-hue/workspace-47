package dev.junta.firmamobile.ui.offlineverification

import dev.junta.firmamobile.history.LocalOutcome
import dev.junta.firmamobile.verification.OfflineCmsResult
import dev.junta.firmamobile.verification.OfflineCmsStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineVerificationTerminalCancellationTest {
    @Test
    fun cancellingAnAcceptedButQueuedVerifyEmitsOnceWithoutOpeningTheDocument() = runTest {
        val events = mutableListOf<LocalOutcome>()
        val owner = OfflineVerificationController(this, StandardTestDispatcher(testScheduler), onTerminal = events::add)
        val source = SyntheticOfflineDocument(byteArrayOf(1))
        choose(owner, source)
        owner.verify()
        owner.cancel()
        owner.cancel()
        owner.close()
        advanceUntilIdle()
        assertEquals(listOf(LocalOutcome.CANCELLED), events)
        assertEquals(0, source.opens)
        assertEquals(OfflineVerificationState(), owner.state.value)
    }

    @Test
    fun reselectDuringCryptoEmitsCancelledAndNeverItsStaleCompletedOrFailedResult() = runTest {
        for (obsoleteStatus in listOf(OfflineCmsStatus.VERIFIED, OfflineCmsStatus.INVALID)) {
            val events = mutableListOf<LocalOutcome>()
            lateinit var owner: OfflineVerificationController
            var calls = 0
            owner = OfflineVerificationController(
                this, StandardTestDispatcher(testScheduler), onTerminal = events::add,
            ) { _, _ ->
                calls++
                if (calls == 1) {
                    choose(owner, SyntheticOfflineDocument(byteArrayOf(2)))
                    OfflineCmsResult(obsoleteStatus, 1)
                } else {
                    OfflineCmsResult(OfflineCmsStatus.VERIFIED, 1)
                }
            }
            choose(owner, SyntheticOfflineDocument(byteArrayOf(1)))
            owner.verify()
            advanceUntilIdle()
            assertEquals(listOf(LocalOutcome.CANCELLED), events)
            assertNull(owner.state.value.outcome)
            owner.verify()
            advanceUntilIdle()
            owner.close()
            assertEquals(listOf(LocalOutcome.CANCELLED, LocalOutcome.COMPLETED), events)
            assertEquals(2, calls)
        }
    }

    @Test
    fun closingDuringCryptoConsumesTheTerminalEventBeforeItsObsoleteReturn() = runTest {
        val events = mutableListOf<LocalOutcome>()
        lateinit var owner: OfflineVerificationController
        owner = OfflineVerificationController(
            this, StandardTestDispatcher(testScheduler), onTerminal = events::add,
        ) { _, _ ->
            owner.close()
            owner.close()
            OfflineCmsResult(OfflineCmsStatus.VERIFIED, 1)
        }
        choose(owner, SyntheticOfflineDocument(byteArrayOf(1)))
        owner.verify()
        advanceUntilIdle()
        assertEquals(listOf(LocalOutcome.CANCELLED), events)
        assertEquals(OfflineVerificationState(), owner.state.value)
    }

    @Test
    fun clearingOriginalDuringReadCancelsOnlyTheAcceptedVerify() = runTest {
        val events = mutableListOf<LocalOutcome>()
        val owner = OfflineVerificationController(this, StandardTestDispatcher(testScheduler), onTerminal = events::add)
        choose(owner, SyntheticOfflineDocument(byteArrayOf(1), beforeRead = owner::clearOriginal))
        owner.verify()
        advanceUntilIdle()
        owner.clearOriginal()
        owner.cancel()
        owner.close()
        assertEquals(listOf(LocalOutcome.CANCELLED), events)
    }

    @Test
    fun invalidClicksPickerFailureAndNoStartDismissalNeverEmit() = runTest {
        val events = mutableListOf<LocalOutcome>()
        val owner = OfflineVerificationController(this, StandardTestDispatcher(testScheduler), onTerminal = events::add)
        owner.verify()
        owner.cancel()
        val stale = owner.beginSelection(OfflineInputKind.CMS)!!
        val current = owner.beginSelection(OfflineInputKind.CMS)!!
        owner.completeSelection(stale, SyntheticOfflineDocument(byteArrayOf(1)))
        owner.verify()
        owner.selectionFailed(current)
        owner.completeSelection(current, SyntheticOfflineDocument(byteArrayOf(2)))
        owner.verify()
        owner.clearOriginal()
        owner.close()
        owner.verify()
        advanceUntilIdle()
        assertEquals(emptyList<LocalOutcome>(), events)
    }

    private fun choose(owner: OfflineVerificationController, source: OfflineDocumentSource) {
        owner.completeSelection(owner.beginSelection(OfflineInputKind.CMS)!!, source)
    }
}

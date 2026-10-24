package dev.junta.firmamobile.ui.offlineverification

import dev.junta.firmamobile.history.LocalOutcome
import dev.junta.firmamobile.verification.OfflineCmsResult
import dev.junta.firmamobile.verification.OfflineCmsStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineVerificationTerminalEventTest {
    @Test
    fun acceptedVerifyEmitsOnceAfterTheLocalIntegrityResultIsAccepted() = runTest {
        val events = mutableListOf<LocalOutcome>()
        lateinit var owner: OfflineVerificationController
        owner = OfflineVerificationController(
            this, StandardTestDispatcher(testScheduler),
            onTerminal = { outcome ->
                assertEquals(OfflineVerificationOutcome.VERIFIED, owner.state.value.outcome)
                events.add(outcome)
            },
        ) { _, _ -> OfflineCmsResult(OfflineCmsStatus.VERIFIED, 1) }
        owner.completeSelection(owner.beginSelection(OfflineInputKind.CMS)!!, SyntheticOfflineDocument(byteArrayOf(1)))
        owner.verify()
        owner.verify() // A busy click is not another accepted operation.
        assertEquals(emptyList<LocalOutcome>(), events)
        advanceUntilIdle()
        assertEquals(listOf(LocalOutcome.COMPLETED), events)
        owner.cancel()
        owner.close()
        assertEquals(listOf(LocalOutcome.COMPLETED), events)
    }
}

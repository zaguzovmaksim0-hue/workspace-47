package dev.junta.firmamobile.ui.history

import dev.junta.firmamobile.history.InMemoryOperationJournal
import dev.junta.firmamobile.history.LocalOutcome
import dev.junta.firmamobile.history.OperationKind
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VerificationJournalClearTest {
    @Test
    fun clearForgetsRetainedEntriesButDoesNotRevokeAnAlreadyIssuedSnapshot() = runTest {
        val journal = InMemoryOperationJournal()
        journal.append(OperationKind.VERIFY, LocalOutcome.COMPLETED, Instant.EPOCH)
        val owner = VerificationJournalController(journal, this, StandardTestDispatcher(testScheduler))
        val snapshot = owner.state.value.snapshot
        owner.clear()
        assertTrue(journal.snapshot().entries.isEmpty())
        assertTrue(owner.state.value.snapshot.entries.isEmpty())
        assertEquals(1, snapshot.entries.size)
        assertEquals(LocalOutcome.COMPLETED, snapshot.entries.single().outcome)
        journal.append(OperationKind.VERIFY, LocalOutcome.FAILED, Instant.EPOCH.plusSeconds(1))
        assertTrue(owner.state.value.snapshot.entries.isEmpty())
        val reopened = VerificationJournalController(journal, this, StandardTestDispatcher(testScheduler))
        assertEquals(LocalOutcome.FAILED, reopened.state.value.snapshot.entries.single().outcome)
        reopened.close()
        owner.close()
    }

    @Test
    fun clearWhileAwaitingSafRejectsTheStaleResultAndTheNextExportHasANewGeneration() = runTest {
        val journal = InMemoryOperationJournal()
        journal.append(OperationKind.VERIFY, LocalOutcome.COMPLETED, Instant.EPOCH)
        val owner = VerificationJournalController(journal, this, StandardTestDispatcher(testScheduler))
        val obsolete = owner.beginExport()!!
        owner.clear()
        assertNull(owner.state.value.exportTicket)
        assertEquals(JournalExportStatus.IDLE, owner.state.value.exportStatus)
        var opens = 0
        val oldOutput = RecordingJournalOutput()
        owner.exportTo(obsolete, JournalExportDestination { opens++; oldOutput })
        advanceUntilIdle()
        assertEquals(0, opens)
        assertEquals(0, oldOutput.size())
        val current = owner.beginExport()!!
        assertNotEquals(obsolete, current)
        owner.exportLaunchFailed(obsolete)
        owner.exportTo(obsolete, JournalExportDestination { opens++; oldOutput })
        assertEquals(current, owner.state.value.exportTicket)
        val output = RecordingJournalOutput()
        owner.exportTo(current, JournalExportDestination { opens++; output })
        advanceUntilIdle()
        assertEquals(1, opens)
        assertEquals("{\"schemaVersion\":1,\"entries\":[]}\n", output.toString("UTF-8"))
        owner.close()
    }
}

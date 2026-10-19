package dev.junta.firmamobile.ui.history

import dev.junta.firmamobile.history.InMemoryOperationJournal
import dev.junta.firmamobile.history.LocalOutcome
import dev.junta.firmamobile.history.OperationKind
import java.io.ByteArrayOutputStream
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VerificationJournalExportLifecycleTest {
    @Test
    fun cancelledPickerAndReplayedCallbacksCannotWriteOrConsumeANewerRequest() = runTest {
        val owner = VerificationJournalController(journal(), this, StandardTestDispatcher(testScheduler))
        var opens = 0
        val output = RecordingJournalOutput()
        val destination = JournalExportDestination { opens++; output }
        val cancelled = owner.beginExport()!!
        owner.exportTo(cancelled, null)
        assertEquals(JournalExportStatus.CANCELLED, owner.state.value.exportStatus)
        owner.exportTo(cancelled, destination)
        owner.exportLaunchFailed(cancelled)
        assertEquals(JournalExportStatus.CANCELLED, owner.state.value.exportStatus)
        val current = owner.beginExport()!!
        owner.exportTo(cancelled, destination)
        assertEquals(current, owner.state.value.exportTicket)
        owner.exportTo(current, destination)
        owner.exportTo(current, destination)
        owner.exportLaunchFailed(current)
        advanceUntilIdle()
        assertEquals(1, opens)
        assertEquals(1, output.closes)
        assertEquals(JournalExportStatus.EXPORTED, owner.state.value.exportStatus)
        owner.close()
    }

    @Test
    fun clearAndCloseBeforeWorkerStartsCannotOpenAProvider() = runTest {
        for (dismiss in listOf(false, true)) {
            val owner = VerificationJournalController(journal(), this, StandardTestDispatcher(testScheduler))
            var opens = 0
            owner.exportTo(owner.beginExport()!!, JournalExportDestination { opens++; RecordingJournalOutput() })
            if (dismiss) owner.close() else owner.clear()
            advanceUntilIdle()
            assertEquals(0, opens)
            assertEquals(JournalExportStatus.IDLE, owner.state.value.exportStatus)
            owner.close()
        }
    }

    @Test
    fun dismissalDuringNonpreemptibleOpenClosesTheStreamWithoutWriting() = runTest {
        val owner = VerificationJournalController(journal(), this, StandardTestDispatcher(testScheduler))
        val output = RecordingJournalOutput()
        owner.exportTo(owner.beginExport()!!, JournalExportDestination {
            owner.close()
            output
        })
        advanceUntilIdle()
        assertEquals(0, output.size())
        assertEquals(1, output.closes)
        assertEquals(JournalExportStatus.IDLE, owner.state.value.exportStatus)
        assertNull(owner.beginExport())
    }

    @Test
    fun clearDuringAnAlreadyRunningWriteCannotRevokeExternalBytesButSuppressesItsResultAndCleansOwnedBytes() = runTest {
        val owner = VerificationJournalController(journal(), this, StandardTestDispatcher(testScheduler))
        var retained: ByteArray? = null
        var closes = 0
        val output = object : ByteArrayOutputStream() {
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                retained = bytes
                owner.clear()
                super.write(bytes, offset, length) // Simulates a nonpreemptible provider already writing.
            }
            override fun close() { closes++; super.close() }
        }
        owner.exportTo(owner.beginExport()!!, JournalExportDestination { output })
        advanceUntilIdle()
        assertTrue(output.size() > 0) // External writes are not atomic and cannot be undone here.
        assertEquals(1, closes)
        assertTrue(retained!!.all { it == 0.toByte() })
        assertEquals(JournalExportStatus.IDLE, owner.state.value.exportStatus)
        assertTrue(owner.state.value.snapshot.entries.isEmpty())
        owner.close()
    }

    @Test
    fun noRequestForgedTicketLaunchFailureAndDismissalNeverAuthorizeAnotherWrite() = runTest {
        val owner = VerificationJournalController(journal(), this, StandardTestDispatcher(testScheduler))
        var opens = 0
        val destination = JournalExportDestination { opens++; RecordingJournalOutput() }
        owner.exportTo(JournalExportTicket(123), destination)
        val failed = owner.beginExport()!!
        owner.exportLaunchFailed(failed)
        assertEquals(JournalExportStatus.FAILED, owner.state.value.exportStatus)
        owner.exportTo(failed, destination)
        val dismissed = owner.beginExport()!!
        owner.close()
        owner.close()
        owner.clear()
        owner.exportTo(dismissed, destination)
        owner.exportLaunchFailed(dismissed)
        advanceUntilIdle()
        assertEquals(0, opens)
        assertNull(owner.beginExport())
    }

    private fun journal() = InMemoryOperationJournal().apply {
        append(OperationKind.VERIFY, LocalOutcome.COMPLETED, Instant.EPOCH)
    }
}

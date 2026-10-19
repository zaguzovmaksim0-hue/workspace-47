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
class VerificationJournalControllerTest {
    @Test
    fun exportRequiresAnExplicitRequestAndWritesOnlyItsOwnedSnapshotThenClearsItsBuffer() = runTest {
        val journal = InMemoryOperationJournal()
        journal.append(OperationKind.VERIFY, LocalOutcome.COMPLETED, Instant.EPOCH)
        val owner = VerificationJournalController(journal, this, StandardTestDispatcher(testScheduler))
        val output = RecordingJournalOutput()
        var opens = 0
        val destination = JournalExportDestination { opens++; output }
        journal.append(OperationKind.VERIFY, LocalOutcome.FAILED, Instant.EPOCH.plusSeconds(1))
        advanceUntilIdle()
        assertEquals(0, opens)
        assertEquals(JournalExportStatus.IDLE, owner.state.value.exportStatus)
        assertEquals(1, owner.state.value.snapshot.entries.size)
        val ticket = owner.beginExport()!!
        assertEquals(JournalExportStatus.CHOOSING, owner.state.value.exportStatus)
        assertNull(owner.beginExport())
        owner.exportTo(ticket, destination)
        assertEquals(0, opens)
        advanceUntilIdle()
        assertEquals(1, opens)
        assertEquals(1, output.closes)
        assertEquals(
            "{\"schemaVersion\":1,\"entries\":[{\"sequence\":1,\"occurredAt\":\"1970-01-01T00:00:00Z\",\"kind\":\"VERIFY\",\"outcome\":\"COMPLETED\"}]}\n",
            output.toString("UTF-8"),
        )
        assertEquals(JournalExportStatus.EXPORTED, owner.state.value.exportStatus)
        assertTrue(output.buffers.isNotEmpty())
        assertTrue(output.buffers.all { bytes -> bytes.all { it == 0.toByte() } })
        owner.close()
    }
}

internal class RecordingJournalOutput : ByteArrayOutputStream() {
    var closes = 0
    val buffers = mutableListOf<ByteArray>()
    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        buffers.add(bytes)
        super.write(bytes, offset, length)
    }
    override fun close() { closes++; super.close() }
}

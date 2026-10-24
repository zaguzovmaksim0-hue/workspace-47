package dev.junta.firmamobile.ui.history

import dev.junta.firmamobile.history.InMemoryOperationJournal
import dev.junta.firmamobile.history.LocalOutcome
import dev.junta.firmamobile.history.OperationJournalJson
import dev.junta.firmamobile.history.OperationKind
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JournalExportFailureAndBoundsTest {
    @Test
    fun maxCapacitySnapshotAndOwnedExportKeepTheExistingSixtyFourEntryAndByteBounds() = runTest {
        val journal = InMemoryOperationJournal(InMemoryOperationJournal.MAX_CAPACITY)
        repeat(65) { journal.append(OperationKind.VERIFY, LocalOutcome.COMPLETED, Instant.MAX) }
        val owner = VerificationJournalController(journal, this, StandardTestDispatcher(testScheduler))
        val output = RecordingJournalOutput()
        owner.exportTo(owner.beginExport()!!, JournalExportDestination { output })
        advanceUntilIdle()
        assertEquals(64, owner.state.value.snapshot.entries.size)
        assertEquals(2L, owner.state.value.snapshot.entries.first().sequence)
        assertEquals(65L, owner.state.value.snapshot.entries.last().sequence)
        assertTrue(output.size() in 1..16_384)
        assertEquals(64, Regex("\"kind\":\"VERIFY\"").findAll(output.toString("UTF-8")).count())
        assertTrue(output.buffers.all { bytes -> bytes.all { it == 0.toByte() } })
        owner.close()
        val defaultJournal = InMemoryOperationJournal()
        repeat(33) { defaultJournal.append(OperationKind.VERIFY, LocalOutcome.FAILED, Instant.EPOCH) }
        assertEquals(32, defaultJournal.snapshot().entries.size)
    }

    @Test
    fun writerRejectsLimitPlusOneBeforeOpeningAndAlwaysClearsItsOwnedBytes() = runTest {
        var opens = 0
        val bytes = ByteArray(16_385) { 42 }
        val status = JournalExportWriter.write(bytes, JournalExportDestination { opens++; RecordingJournalOutput() })
        assertEquals(JournalExportStatus.FAILED, status)
        assertEquals(0, opens)
        assertTrue(bytes.all { it == 0.toByte() })
        val exact = ByteArray(16_384) { 7 }
        val output = RecordingJournalOutput()
        assertEquals(JournalExportStatus.EXPORTED, JournalExportWriter.write(exact, JournalExportDestination { output }))
        assertEquals(16_384, output.size())
        assertEquals(1, output.closes)
        assertTrue(exact.all { it == 0.toByte() })
    }

    @Test
    fun nullAndThrowingProvidersProduceOnlyFixedFailedStateAndNeverExposeTheirPrivateError() = runTest {
        for (throwOnOpen in listOf(false, true)) {
            val journal = InMemoryOperationJournal()
            val owner = VerificationJournalController(journal, this, StandardTestDispatcher(testScheduler))
            var opens = 0
            owner.exportTo(owner.beginExport()!!, JournalExportDestination {
                opens++
                if (throwOnOpen) throw IOException("content://private/FILE-CERTIFICATE-CANARY.json")
                null
            })
            advanceUntilIdle()
            assertEquals(1, opens)
            assertEquals(JournalExportStatus.FAILED, owner.state.value.exportStatus)
            assertFalse(owner.state.value.toString().contains("CANARY"))
            owner.close()
        }
    }

    @Test
    fun partialWriteFlushAndCloseFailuresCloseStreamsClearBuffersAndReportNoFalseSuccess() = runTest {
        for (stage in listOf("write", "flush", "close")) {
            val journal = InMemoryOperationJournal().apply {
                append(OperationKind.VERIFY, LocalOutcome.COMPLETED, Instant.EPOCH)
            }
            val owner = VerificationJournalController(journal, this, StandardTestDispatcher(testScheduler))
            var retained: ByteArray? = null
            var closes = 0
            val output = object : ByteArrayOutputStream() {
                override fun write(bytes: ByteArray, offset: Int, length: Int) {
                    retained = bytes
                    if (stage == "write") {
                        super.write(bytes, offset, 4)
                        throw IOException("private-write-CANARY")
                    }
                    super.write(bytes, offset, length)
                }
                override fun flush() { if (stage == "flush") throw IOException("private-flush-CANARY") }
                override fun close() { closes++; if (stage == "close") throw IOException("private-close-CANARY") }
            }
            owner.exportTo(owner.beginExport()!!, JournalExportDestination { output })
            advanceUntilIdle()
            assertEquals(JournalExportStatus.FAILED, owner.state.value.exportStatus)
            assertEquals(1, closes)
            assertTrue(output.size() > 0)
            assertTrue(output.size() <= OperationJournalJson.MAX_EXPORT_BYTES)
            assertTrue(retained!!.all { it == 0.toByte() })
            assertFalse(owner.state.value.toString().contains("CANARY"))
            owner.close()
        }
    }
}

package dev.junta.firmamobile.ui.history

import dev.junta.firmamobile.history.InMemoryOperationJournal
import java.io.ByteArrayOutputStream
import java.util.Collections
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VerificationJournalThreadTest {
    @Test
    fun providerOpenWriteFlushAndCloseAllRunOnTheWorkerNotTheUiScopeThread() {
        val ui = Executors.newSingleThreadExecutor { Thread(it, "journal-synthetic-ui") }.asCoroutineDispatcher()
        val worker = Executors.newSingleThreadExecutor { Thread(it, "journal-synthetic-worker") }.asCoroutineDispatcher()
        val calls = Collections.synchronizedList(mutableListOf<String>())
        val output = object : ByteArrayOutputStream() {
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                calls.add(threadName())
                super.write(bytes, offset, length)
            }
            override fun flush() { calls.add(threadName()) }
            override fun close() { calls.add(threadName()); super.close() }
        }
        try {
            runBlocking(ui) {
                assertEquals("journal-synthetic-ui", threadName())
                val owner = VerificationJournalController(InMemoryOperationJournal(), this, worker)
                owner.exportTo(owner.beginExport()!!, JournalExportDestination {
                    calls.add(threadName())
                    output
                })
                withTimeout(10_000) { owner.state.first { it.exportStatus == JournalExportStatus.EXPORTED } }
                owner.close()
            }
            assertEquals(4, calls.size)
            assertTrue(calls.all { it == "journal-synthetic-worker" })
            assertEquals("{\"schemaVersion\":1,\"entries\":[]}\n", output.toString("UTF-8"))
        } finally {
            ui.close()
            worker.close()
        }
    }

    // CI enables coroutine debug mode, which appends " @coroutine#N" to thread names.
    private fun threadName() = Thread.currentThread().name.substringBefore(" @coroutine#")
}

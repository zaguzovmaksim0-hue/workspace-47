package dev.junta.firmamobile.security

import java.util.concurrent.Executor
import org.junit.Assert.*
import org.junit.Test

class AsyncSanitizedLogSinkTest {
    private class ManualExecutor : Executor {
        val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.addLast(command) }
        fun runAll() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
    }

    @Test fun emitDoesNotWriteOnTheCallerAndSchedulesOneBoundedDrain() {
        val executor = ManualExecutor()
        val records = mutableListOf<String>()
        val sink = AsyncSanitizedLogSink(SanitizedLogSink(records::add), executor, capacity = 2)
        sink.emit("one"); sink.emit("two"); sink.emit("three")
        assertTrue(records.isEmpty())
        assertEquals(1, executor.tasks.size)
        executor.runAll()
        assertEquals(listOf("two", "three"), records)
        sink.emit("four"); executor.runAll()
        assertEquals(listOf("two", "three", "four"), records)
    }

    @Test fun clearErasesExistingAndInvalidatesAllQueuedRecords() {
        val executor = ManualExecutor()
        val records = mutableListOf<String>()
        val target = object : SanitizedLogSink {
            override fun emit(record: String) { records.add(record) }
            override fun clear() { records.clear() }
        }
        val sink = AsyncSanitizedLogSink(target, executor)
        sink.emit("before"); executor.runAll(); sink.emit("queued")
        sink.clear()
        assertTrue(records.isEmpty())
        sink.emit("after"); executor.runAll()
        assertEquals(listOf("after"), records)
    }

    @Test fun writerFailureDoesNotStrandTheDrain() {
        val executor = ManualExecutor()
        val records = mutableListOf<String>()
        val sink = AsyncSanitizedLogSink(SanitizedLogSink {
            if (it == "bad") error("writer failure") else records.add(it)
        }, executor)
        sink.emit("bad"); sink.emit("good"); executor.runAll()
        assertEquals(listOf("good"), records)
    }

    @Test fun oversizedRecordCannotFillTheQueue() {
        val executor = ManualExecutor()
        val sink = AsyncSanitizedLogSink(SanitizedLogSink {}, executor)
        sink.emit("x".repeat(QaDiagnosticFileSink.MAX_RECORD_BYTES + 1))
        assertTrue(executor.tasks.isEmpty())
    }
}

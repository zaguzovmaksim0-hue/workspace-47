package dev.junta.firmamobile.security

import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** Bounded best-effort diagnostics. Clear is a synchronous erasure barrier. */
internal class AsyncSanitizedLogSink(
    private val delegate: SanitizedLogSink,
    private val executor: Executor = writer,
    private val capacity: Int = 128,
) : SanitizedLogSink {
    private data class Entry(val generation: Long, val record: String)
    private val lock = Any()
    private val ioLock = Any()
    private val pending = ArrayDeque<Entry>()
    private var generation = 0L
    private var scheduled = false

    init { require(capacity > 0) }

    override fun emit(record: String) {
        if (record.length > QaDiagnosticFileSink.MAX_RECORD_BYTES) return
        val schedule = synchronized(lock) {
            if (pending.size == capacity) pending.removeFirst()
            pending.addLast(Entry(generation, record))
            if (scheduled) false else { scheduled = true; true }
        }
        if (schedule) {
            try { executor.execute(::drain) } catch (_: RuntimeException) {
                synchronized(lock) { scheduled = false; pending.clear() }
            }
        }
    }

    private fun drain() {
        while (true) {
            val entry = synchronized(lock) {
                if (pending.isEmpty()) { scheduled = false; null } else pending.removeFirst()
            } ?: return
            synchronized(ioLock) {
                val current = synchronized(lock) { entry.generation == generation }
                if (current) runCatching { delegate.emit(entry.record) }
            }
        }
    }

    override fun clear() {
        synchronized(ioLock) {
            synchronized(lock) { generation++; pending.clear() }
            delegate.clear()
        }
    }

    private companion object {
        val writer: Executor = Executors.newSingleThreadExecutor { task ->
            Thread(task, "jfm-diagnostics").apply { isDaemon = true }
        }
    }
}

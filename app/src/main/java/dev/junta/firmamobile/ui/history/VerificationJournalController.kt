package dev.junta.firmamobile.ui.history

import dev.junta.firmamobile.history.InMemoryOperationJournal
import dev.junta.firmamobile.history.OperationJournalJson
import java.io.Closeable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** UI-thread-confined owner of a dialog snapshot and one explicitly requested export. */
internal class VerificationJournalController(
    private val journal: InMemoryOperationJournal,
    private val scope: CoroutineScope,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Closeable {
    private val mutableState = MutableStateFlow(VerificationJournalState(journal.snapshot()))
    val state: StateFlow<VerificationJournalState> = mutableState.asStateFlow()
    private var generation = 0L
    private var pending: PendingJournalExport? = null
    private var operation: Job? = null
    private var closed = false

    fun beginExport(): JournalExportTicket? {
        if (closed || !state.value.canExport) return null
        val bytes = OperationJournalJson.toUtf8(state.value.snapshot)
        val ticket = JournalExportTicket(++generation)
        pending = PendingJournalExport(ticket, bytes)
        mutableState.value = state.value.copy(exportStatus = JournalExportStatus.CHOOSING, exportTicket = ticket)
        return ticket
    }

    fun exportTo(ticket: JournalExportTicket, destination: JournalExportDestination?) {
        val owned = takePending(ticket) ?: return
        if (destination == null) {
            owned.bytes.fill(0)
            mutableState.value = state.value.copy(exportStatus = JournalExportStatus.CANCELLED, exportTicket = null)
            return
        }
        mutableState.value = state.value.copy(exportStatus = JournalExportStatus.WRITING, exportTicket = null)
        operation = scope.launch {
            val result = withContext(workerDispatcher) { JournalExportWriter.write(owned.bytes, destination) }
            if (!closed && ticket.generation == generation) {
                mutableState.value = state.value.copy(exportStatus = result)
            }
        }.also { job ->
            // Also clears a buffer if its coroutine is cancelled before its body ever starts.
            job.invokeOnCompletion { owned.bytes.fill(0) }
        }
    }

    fun exportLaunchFailed(ticket: JournalExportTicket) {
        val owned = takePending(ticket) ?: return
        owned.bytes.fill(0)
        mutableState.value = state.value.copy(exportStatus = JournalExportStatus.FAILED, exportTicket = null)
    }

    fun clear() {
        if (closed) return
        invalidateExport()
        journal.clear()
        mutableState.value = VerificationJournalState(journal.snapshot())
    }

    override fun close() {
        if (closed) return
        closed = true
        invalidateExport()
        mutableState.value = state.value.copy(exportStatus = JournalExportStatus.IDLE, exportTicket = null)
    }

    private fun invalidateExport() {
        generation++
        pending?.bytes?.fill(0)
        pending = null
        operation?.cancel()
        operation = null
        // An in-use nonpreemptible write keeps its buffer until its worker finishes, then clears it.
    }

    private fun takePending(ticket: JournalExportTicket): PendingJournalExport? {
        if (closed || ticket.generation != generation || pending?.ticket != ticket) return null
        return pending.also { pending = null }
    }
}

private class PendingJournalExport(val ticket: JournalExportTicket, val bytes: ByteArray)

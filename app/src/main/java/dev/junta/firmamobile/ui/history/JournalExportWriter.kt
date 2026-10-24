package dev.junta.firmamobile.ui.history

import dev.junta.firmamobile.history.OperationJournalJson
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** A transient chosen destination, used only on the export worker dispatcher. */
internal fun interface JournalExportDestination {
    fun open(): OutputStream?
}

internal object JournalExportWriter {
    suspend fun write(bytes: ByteArray, destination: JournalExportDestination): JournalExportStatus {
        val context = currentCoroutineContext()
        return try {
            check(bytes.size <= OperationJournalJson.MAX_EXPORT_BYTES)
            context.ensureActive()
            val stream = destination.open() ?: return JournalExportStatus.FAILED
            stream.use {
                context.ensureActive() // Dismissal during a blocking open must not authorize a later write.
                it.write(bytes)
                it.flush()
                context.ensureActive()
            }
            JournalExportStatus.EXPORTED
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Opening, writing, flushing or closing can fail after an external partial write.
            JournalExportStatus.FAILED
        } finally {
            bytes.fill(0)
        }
    }
}

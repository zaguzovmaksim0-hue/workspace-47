package dev.junta.firmamobile.ui.history

import dev.junta.firmamobile.history.OperationJournalSnapshot

internal enum class JournalExportStatus { IDLE, CHOOSING, WRITING, EXPORTED, CANCELLED, FAILED }

internal data class JournalExportTicket(val generation: Long)

/** Detached allowlisted metadata and fixed statuses only; never a destination, bytes or exception. */
internal data class VerificationJournalState(
    val snapshot: OperationJournalSnapshot,
    val exportStatus: JournalExportStatus = JournalExportStatus.IDLE,
    val exportTicket: JournalExportTicket? = null,
) {
    val canExport: Boolean
        get() = exportStatus != JournalExportStatus.CHOOSING && exportStatus != JournalExportStatus.WRITING
}

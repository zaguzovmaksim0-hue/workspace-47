package dev.junta.firmamobile.history

import java.util.Collections

/** Detached immutable metadata. Clearing its journal cannot revoke a snapshot already issued. */
class OperationJournalSnapshot internal constructor(entries: Collection<OperationJournalEntry>) {
    val entries: List<OperationJournalEntry>

    init {
        require(entries.size <= InMemoryOperationJournal.MAX_CAPACITY) {
            "Operation journal snapshot exceeds its fixed bound"
        }
        this.entries = Collections.unmodifiableList(ArrayList(entries))
    }
}

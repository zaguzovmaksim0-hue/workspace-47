package dev.junta.firmamobile.history

import java.time.Instant
import java.util.ArrayDeque

/**
 * Ephemeral, process-local metadata only; nothing is persisted or exported automatically.
 * Entries are oldest-first by append sequence, not wall time. All Instant values are supported,
 * including pre-epoch times and clock rollback. This is not a security/replay ledger.
 *
 * No runtime/coordinator integration is provided here; end-to-end operation history is still TODO.
 * The internal sequence seed is a synthetic overflow-test seam; normal instances start at zero.
 */
class InMemoryOperationJournal internal constructor(
    private val capacity: Int,
    initialSequence: Long,
) {
    constructor(capacity: Int = DEFAULT_CAPACITY) : this(capacity, 0L)

    init {
        require(capacity in 1..MAX_CAPACITY) { "Operation journal capacity is outside its fixed bound" }
        require(initialSequence >= 0L) { "Operation journal sequence must not be negative" }
    }

    private val entries = ArrayDeque<OperationJournalEntry>(capacity)
    private var lastSequence = initialSequence

    /** Accepts only the four allowlisted metadata types; the local sequence is allocated here. */
    @Synchronized
    fun append(kind: OperationKind, outcome: LocalOutcome, occurredAt: Instant): OperationJournalEntry {
        check(lastSequence < Long.MAX_VALUE) { "Operation journal sequence is exhausted" }
        val entry = OperationJournalEntry(lastSequence + 1L, occurredAt, kind, outcome)
        if (entries.size == capacity) entries.removeFirst()
        entries.addLast(entry)
        lastSequence = entry.sequence
        return entry
    }

    @Synchronized
    fun snapshot(): OperationJournalSnapshot = OperationJournalSnapshot(entries)

    /** Forgets retained entries and their counter. Already-issued snapshots/exports remain with callers. */
    @Synchronized
    fun clear() {
        entries.clear()
        lastSequence = 0L
    }

    companion object {
        const val DEFAULT_CAPACITY = 32
        const val MAX_CAPACITY = 64
    }
}

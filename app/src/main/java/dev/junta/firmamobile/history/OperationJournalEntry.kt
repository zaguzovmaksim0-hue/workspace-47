package dev.junta.firmamobile.history

import java.time.Instant

/** Coarse local operation categories, independent of requests and their data. */
enum class OperationKind {
    SIGN,
    COSIGN,
    COUNTERSIGN,
    BATCH,
    VERIFY,
    IDENTIFY,
}

/** Local completion only: no claim about remote delivery, acceptance or signature validity. */
enum class LocalOutcome {
    COMPLETED,
    CANCELLED,
    FAILED,
}

/** Closed allowlist: no free text, document, identity, request or error input can be retained. */
data class OperationJournalEntry(
    val sequence: Long,
    val occurredAt: Instant,
    val kind: OperationKind,
    val outcome: LocalOutcome,
) {
    init {
        require(sequence > 0L) { "Operation journal sequence must be positive" }
    }
}

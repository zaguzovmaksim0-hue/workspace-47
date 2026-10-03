package dev.junta.firmamobile.afirma.servlet

import java.util.Collections

/** Display snapshot only. The original protocol response is forwarded unchanged;
 * this is neither a persistent receipt nor proof of government acceptance. */
internal class NativeBatchReceipt(origin: Origin, entries: List<Entry>) {
    enum class Origin { LOCAL, SERVICE }
    data class Entry(val documentLabel: String, val statusCode: String, val description: String?) {
        override fun toString(): String = "BatchEntry(status=$statusCode, hasDescription=${description != null})"
    }
    val origin: Origin = origin
    val entries: List<Entry>
    init {
        require(entries.size in 1..32)
        this.entries = Collections.unmodifiableList(entries.map {
            require(it.statusCode.matches(Regex("[A-Z_]{1,40}")))
            Entry(NativeBatchDisplayText.bounded(it.documentLabel, 256).ifEmpty { "—" }, it.statusCode,
                it.description?.let { text -> NativeBatchDisplayText.bounded(text, 512).ifEmpty { null } })
        })
    }
    override fun toString(): String = "NativeBatchReceipt(origin=$origin, count=${entries.size})"

    companion object {
        /** Exact identifiers remain available for binding only. UI order always
         * follows the approved descriptor, not a server-supplied reordering. */
        fun from(batch: NativeBatchDescriptor, outcomes: List<NativeBatchProtocol.Outcome>, origin: Origin): NativeBatchReceipt {
            val approved = batch.items.map { it.id }
            require(approved.size in 1..32 && approved.toSet().size == approved.size)
            require(outcomes.size == approved.size && outcomes.map { it.id }.toSet() == approved.toSet())
            val byId = outcomes.associateBy { it.id }
            return NativeBatchReceipt(origin, approved.map { id ->
                val result = byId.getValue(id)
                Entry(id, result.status, result.description)
            })
        }
    }
}

package dev.junta.firmamobile.history

/**
 * On-demand, deterministic UTF-8 JSON bytes only; no file, sharing, clipboard or network side effects.
 * Schema 1 has fixed key order, enum spellings, ISO-8601 UTC Instants, no BOM and one final newline.
 * There is no generated-at value or other ambient state. Changing the schema requires a version change.
 */
object OperationJournalJson {
    const val MAX_EXPORT_BYTES = 16_384

    fun toUtf8(snapshot: OperationJournalSnapshot): ByteArray {
        // At most 64 entries, each under 160 ASCII bytes, including full-range Instants and Longs.
        val json = buildString {
            append("{\"schemaVersion\":1,\"entries\":[")
            snapshot.entries.forEachIndexed { index, entry ->
                if (index > 0) append(',')
                appendEntry(entry)
            }
            append("]}\n")
        }
        val bytes = json.toByteArray(Charsets.UTF_8)
        check(bytes.size <= MAX_EXPORT_BYTES) { "Operation journal export exceeds its fixed bound" }
        return bytes
    }

    private fun StringBuilder.appendEntry(entry: OperationJournalEntry) {
        // Only closed typed values reach the serializer; no free-form string escaping/redaction seam.
        append("{\"sequence\":")
        append(entry.sequence)
        append(",\"occurredAt\":\"")
        append(entry.occurredAt.toString())
        append("\",\"kind\":\"")
        append(entry.kind.name)
        append("\",\"outcome\":\"")
        append(entry.outcome.name)
        append("\"}")
    }
}

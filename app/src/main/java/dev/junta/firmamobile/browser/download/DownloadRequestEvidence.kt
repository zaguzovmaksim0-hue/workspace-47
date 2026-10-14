package dev.junta.firmamobile.browser.download

internal enum class ObservedMethod { GET, POST, OTHER }

/** Recent request metadata from every frame. It only tells a download offer
 * whether its final request was a form submission; an address that was never
 * observed (the target of a server redirect) was requested with GET. */
internal class DownloadRequestEvidence(private val nowNanos: () -> Long = System::nanoTime) {
    private class Entry(val method: ObservedMethod, val born: Long)
    private val entries = LinkedHashMap<String, Entry>()

    @Synchronized fun record(url: String, method: String) {
        val key = key(url) ?: return
        val time = runCatching(nowNanos).getOrNull() ?: return
        val observed = when (method.uppercase(java.util.Locale.ROOT)) {
            "GET" -> ObservedMethod.GET
            "POST" -> ObservedMethod.POST
            else -> ObservedMethod.OTHER
        }
        // Re-insert so that the most recent request for an address wins.
        entries.remove(key); entries[key] = Entry(observed, time)
        // A server may take seconds to generate a document while the page keeps
        // loading resources; evict ordinary requests before a form submission.
        if (entries.size > MAX_ENTRIES) {
            val victim = entries.entries.firstOrNull { it.value.method != ObservedMethod.POST } ?: entries.entries.first()
            entries.remove(victim.key)
        }
    }

    @Synchronized fun methodOf(url: String): ObservedMethod? {
        val key = key(url) ?: return null
        val value = entries[key] ?: return null
        val current = runCatching {
            val elapsed = nowNanos() - value.born
            elapsed >= 0L && elapsed < MAX_AGE_NANOS
        }.getOrDefault(false)
        if (!current) { entries.remove(key); return null }
        return value.method
    }

    @Synchronized fun clear() { entries.clear() }

    companion object {
        private const val MAX_ENTRIES = 64
        private const val MAX_AGE_NANOS = 60_000_000_000L
        /** A fragment never reaches the server, so it does not distinguish requests. */
        fun key(url: String): String? = url.substringBefore('#').takeIf { it.isNotBlank() && it.length <= 8192 }
    }
}

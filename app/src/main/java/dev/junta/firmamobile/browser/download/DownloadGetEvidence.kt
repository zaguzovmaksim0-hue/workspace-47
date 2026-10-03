package dev.junta.firmamobile.browser.download

/** Only an observation of the actual request, never permission to replay it.
 * A user must separately approve the downloader's new GET. */
internal class DownloadGetEvidence(private val nowNanos: () -> Long = System::nanoTime) {
    private class Candidate(val url: String, val isGet: Boolean, val born: Long)
    private var candidate: Candidate? = null
    @Synchronized fun record(url: String, method: String, isMainFrame: Boolean) {
        if (!isMainFrame) return
        val time = runCatching(nowNanos).getOrNull()
        candidate = if (url.isBlank() || url.length > 8192 || time == null) null else Candidate(url, method == "GET", time)
    }
    @Synchronized fun navigationStarted(url: String) {
        val value = candidate ?: return
        if (value.url != url || !current(value)) candidate = null
    }
    @Synchronized fun consume(url: String): Boolean {
        val value = candidate?.takeIf { it.url == url } ?: return false
        candidate = null
        return value.isGet && current(value)
    }
    @Synchronized fun clear() { candidate = null }
    private fun current(value: Candidate) = runCatching {
        val elapsed = nowNanos() - value.born
        elapsed >= 0L && elapsed < 30_000_000_000L
    }.getOrDefault(false)
}

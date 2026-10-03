package dev.junta.firmamobile.browser.download

import dev.junta.firmamobile.browser.PublicBrowserAddress
import java.net.URI
import java.util.UUID

/** Never a data class: URLs and the optional session cookie must not appear in
 * generated toString output, Intent extras, saved state or diagnostics. */
internal class DocumentDownloadPlan private constructor(
    val url: URI,
    val sourceOrigin: String,
    val targetOrigin: String,
    val fileName: String,
    val mimeType: String,
    val lengthHint: Long,
    val userAgent: String,
    val cookie: String?,
) {
    companion object {
        const val MAX_BYTES = 32L * 1024 * 1024
        fun create(page: String, url: String, proposedName: String?, mimeType: String?, lengthHint: Long,
            userAgent: String?, cookieForTarget: String?): DocumentDownloadPlan? {
            val source = PublicBrowserAddress.parse(page) ?: return null
            val target = PublicBrowserAddress.parse(url) ?: return null
            // A fragment has no HTTP meaning; do not silently turn a different
            // callback address into the observed request.
            if (target.rawFragment != null || lengthHint < -1 || lengthHint > MAX_BYTES) return null
            val ua = userAgent.orEmpty()
            if (ua.length > 2048 || ua.any { it.code !in 0x20..0x7e }) return null
            val mime = mimeType?.substringBefore(';')?.trim()?.lowercase(java.util.Locale.ROOT)
                ?.takeIf { it.length <= 127 && MIME.matches(it) } ?: "application/octet-stream"
            val sourceOrigin = origin(source); val targetOrigin = origin(target)
            val cookie = cookieForTarget?.takeIf {
                sourceOrigin == targetOrigin && it.length <= 32768 && it.all { c -> c.code in 0x20..0x7e }
            }
            return DocumentDownloadPlan(target, sourceOrigin, targetOrigin, DownloadFileName.safe(proposedName), mime, lengthHint, ua, cookie)
        }
        private val MIME = Regex("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+")
        private fun origin(uri: URI) = "https://" + uri.host.lowercase(java.util.Locale.ROOT)
    }
}

/** One short-lived in-process ticket: no URL/cookie is placed in an Intent.
 * Process death or a rejected launch discards the offer instead of replaying it. */
internal class DownloadTicketStore(private val nowNanos: () -> Long = System::nanoTime) {
    private class Slot(val token: String, val plan: DocumentDownloadPlan, val born: Long)
    private var slot: Slot? = null
    @Synchronized fun offer(plan: DocumentDownloadPlan): String? {
        expire()
        if (slot != null) return null
        return UUID.randomUUID().toString().also { slot = Slot(it, plan, nowNanos()) }
    }
    @Synchronized fun take(token: String): DocumentDownloadPlan? {
        expire()
        val value = slot?.takeIf { it.token == token } ?: return null
        slot = null
        return value.plan
    }
    @Synchronized fun revoke(token: String) { if (slot?.token == token) slot = null }
    private fun expire() {
        val value = slot ?: return
        val age = nowNanos() - value.born
        if (age < 0 || age >= 120_000_000_000L) slot = null
    }
}

internal object DocumentDownloadTickets { val store = DownloadTicketStore() }

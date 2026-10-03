package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.security.MonotonicSecurityTime
import java.time.Duration
import java.util.UUID

internal data class AfirmaFallbackPrompt(val token: UUID, val invalid: Boolean)

/** Keeps a fallback button bound to exactly the document/request it describes.
 * Raw URI is never included in observable UI state, logs or a data-class dump. */
internal class AfirmaFallbackController<Owner : Any>(
    private val isCurrent: (Owner, Long) -> Boolean,
    private val canRespond: () -> Boolean,
    private val onPrompt: (AfirmaFallbackPrompt?) -> Unit,
    private val monotonicNanos: () -> Long = MonotonicSecurityTime::nowNanos,
) {
    private class Pending<Owner>(val owner: Owner, val epoch: Long, val raw: String?, val born: Long) {
        val token = UUID.randomUUID()
    }
    private var current: Pending<Owner>? = null
    val hasPending: Boolean get() = current != null

    fun offer(owner: Owner, epoch: Long, rawUnsupportedUri: String?): Boolean {
        if (current != null || !isCurrent(owner, epoch) || !canRespond()) return false
        val pending = Pending(owner, epoch, rawUnsupportedUri, monotonicNanos())
        current = pending
        onPrompt(AfirmaFallbackPrompt(pending.token, pending.raw == null))
        return true
    }

    fun consume(token: UUID): String? {
        val pending = current?.takeIf { it.token == token } ?: return null
        current = null
        onPrompt(null)
        return if (live(pending) && canRespond()) pending.raw else null
    }

    fun dismiss(token: UUID) { if (current?.token == token) invalidate() }
    fun invalidate() { if (current != null) { current = null; onPrompt(null) } }
    fun tick() { current?.takeIf { !live(it) }?.let { invalidate() } }

    private fun live(pending: Pending<Owner>) = runCatching {
        isCurrent(pending.owner, pending.epoch) && !MonotonicSecurityTime.isExpiredOrInvalid(
            pending.born, Duration.ofMinutes(5).toNanos(), monotonicNanos(),
        )
    }.getOrDefault(false)
}

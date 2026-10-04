package dev.junta.firmamobile.browser

import java.util.UUID

/** UI-thread-only one-use consent tied to a particular visible document. No
 * outgoing URL, credential, result or persistent permission is stored here. */
internal class ExternalAppConsentLease<Owner : Any>(
    private val isCurrent: (Owner, Long) -> Boolean,
    private val canRespond: () -> Boolean,
    private val nowNanos: () -> Long = System::nanoTime,
) {
    private class Pending<Owner>(val owner: Owner, val epoch: Long, val born: Long) {
        val token: UUID = UUID.randomUUID()
    }
    private var pending: Pending<Owner>? = null
    private var reserving = false
    val hasPending: Boolean get() = pending != null

    fun reserve(owner: Owner, epoch: Long): UUID? {
        if (pending != null || reserving) return null
        reserving = true
        return try {
            if (!isCurrent(owner, epoch) || !canRespond()) return null
            val value = Pending(owner, epoch, nowNanos())
            if (pending != null) null else { pending = value; value.token }
        } catch (_: Exception) { null }
        finally { reserving = false }
    }
    fun isValid(token: UUID): Boolean {
        val value = pending?.takeIf { it.token == token } ?: return false
        return valid(value)
    }
    fun consume(token: UUID): Boolean {
        val value = pending?.takeIf { it.token == token } ?: return false
        val allowed = valid(value)
        if (pending !== value) return false
        pending = null
        return allowed
    }
    fun invalidate() { pending = null }
    private fun valid(value: Pending<Owner>): Boolean {
        val live = runCatching {
            val age = nowNanos() - value.born
            age >= 0L && age < 120_000_000_000L && isCurrent(value.owner, value.epoch) && canRespond()
        }.getOrDefault(false)
        return live && pending === value
    }
}

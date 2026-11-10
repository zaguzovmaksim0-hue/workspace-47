package dev.junta.firmamobile.browser

import java.util.UUID

/** One UI-thread-owned slot; the timeout applies only before attachment. */
internal class PopupOwnerLease<Owner : Any>(
    private val isCurrent: (Owner, Long) -> Boolean,
    private val nowNanos: () -> Long = System::nanoTime,
) {
    private class Slot<Owner>(val owner: Owner, val epoch: Long, val born: Long, var confirmed: Boolean) {
        val token: UUID = UUID.randomUUID()
        var attached = false
    }
    private var slot: Slot<Owner>? = null
    val hasWindow: Boolean get() = slot != null
    fun reserve(owner: Owner, epoch: Long, requiresConfirmation: Boolean = false): UUID? {
        if (slot != null || !runCatching { isCurrent(owner, epoch) }.getOrDefault(false)) return null
        return runCatching(nowNanos).getOrNull()?.takeIf { slot == null }?.let { born ->
            Slot(owner, epoch, born, confirmed = !requiresConfirmation).also { slot = it }.token
        }
    }
    fun isCurrent(token: UUID): Boolean {
        val current = slot?.takeIf { it.token == token } ?: return false
        val live = runCatching {
            isCurrent(current.owner, current.epoch) && (current.attached ||
                (nowNanos() - current.born).let { it >= 0 && it < 15_000_000_000L })
        }.getOrDefault(false)
        return live && slot === current
    }
    /** Confirm only the same live request; confirmation never renews the attachment deadline. */
    fun confirm(token: UUID): Boolean {
        val current = slot?.takeIf { it.token == token && !it.confirmed && !it.attached } ?: return false
        return if (isCurrent(token) && slot === current) {
            current.confirmed = true
            true
        } else {
            false
        }
    }
    fun canAttach(token: UUID): Boolean {
        val current = slot?.takeIf { it.token == token && it.confirmed && !it.attached } ?: return false
        return isCurrent(token) && slot === current
    }
    fun markAttached(token: UUID): Boolean {
        val current = slot?.takeIf { it.token == token && !it.attached } ?: return false
        if (!canAttach(token) || slot !== current) return false
        current.attached = true
        return true
    }
    fun release(token: UUID): Boolean {
        if (slot?.token != token) return false
        slot = null
        return true
    }
    fun invalidate() { slot = null }
}

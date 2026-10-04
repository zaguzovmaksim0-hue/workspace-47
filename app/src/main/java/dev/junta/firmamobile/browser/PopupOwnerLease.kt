package dev.junta.firmamobile.browser

import java.util.UUID

/** One UI-thread-owned slot; the timeout applies only before attachment. */
internal class PopupOwnerLease<Owner : Any>(
    private val isCurrent: (Owner, Long) -> Boolean,
    private val nowNanos: () -> Long = System::nanoTime,
) {
    private class Slot<Owner>(val owner: Owner, val epoch: Long, val born: Long) {
        val token: UUID = UUID.randomUUID()
        var attached = false
    }
    private var slot: Slot<Owner>? = null
    val hasWindow: Boolean get() = slot != null
    fun reserve(owner: Owner, epoch: Long): UUID? {
        if (slot != null || !runCatching { isCurrent(owner, epoch) }.getOrDefault(false)) return null
        val born = runCatching(nowNanos).getOrNull() ?: return null
        if (slot != null) return null
        return Slot(owner, epoch, born).also { slot = it }.token
    }
    fun isCurrent(token: UUID): Boolean {
        val current = slot?.takeIf { it.token == token } ?: return false
        val live = runCatching {
            isCurrent(current.owner, current.epoch) && (current.attached ||
                (nowNanos() - current.born).let { it >= 0 && it < 15_000_000_000L })
        }.getOrDefault(false)
        return live && slot === current
    }
    fun markAttached(token: UUID): Boolean {
        val current = slot?.takeIf { it.token == token && !it.attached } ?: return false
        if (!isCurrent(token) || slot !== current) return false
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

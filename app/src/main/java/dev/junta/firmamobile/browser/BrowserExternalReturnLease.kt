package dev.junta.firmamobile.browser

import dev.junta.firmamobile.security.MonotonicSecurityTime
import java.time.Duration
import java.util.UUID

/** One app-launched external activity may return to its original browser owner.
 * This lease is not signing consent, a TLS grant or permission to replay POST.
 */
class BrowserExternalReturnLease<Owner : Any>(
    private val monotonicNanos: () -> Long = MonotonicSecurityTime::nowNanos,
) {
    private data class Pending<Owner>(val token: UUID, val owner: Owner, val epoch: Long, val started: Long)
    private var pending: Pending<Owner>? = null

    fun begin(owner: Owner, epoch: Long): UUID {
        val token = UUID.randomUUID()
        pending = Pending(token, owner, epoch, monotonicNanos())
        return token
    }

    fun cancel(token: UUID) {
        if (pending?.token == token) pending = null
    }

    fun invalidate() { pending = null }

    fun isValid(owner: Owner, epoch: Long): Boolean {
        val lease = pending ?: return false
        val valid = lease.owner === owner && lease.epoch == epoch && runCatching {
            !MonotonicSecurityTime.isExpiredOrInvalid(lease.started, LIFETIME.toNanos(), monotonicNanos())
        }.getOrDefault(false)
        if (!valid) pending = null
        return valid
    }

    fun consume(owner: Owner, epoch: Long): Boolean {
        val valid = isValid(owner, epoch)
        pending = null
        return valid
    }

    private companion object { val LIFETIME: Duration = Duration.ofMinutes(5) }
}

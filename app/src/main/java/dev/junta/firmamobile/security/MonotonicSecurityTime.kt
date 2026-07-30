package dev.junta.firmamobile.security

import java.time.Duration

/** Process-local security time. Civil-clock changes never extend an authorization window. */
internal object MonotonicSecurityTime {
    fun nowNanos(): Long = System.nanoTime()

    fun durationNanos(duration: Duration): Long {
        require(!duration.isNegative && !duration.isZero)
        return try {
            duration.toNanos().also { require(it > 0L) }
        } catch (error: ArithmeticException) {
            throw IllegalArgumentException("Security duration is too large", error)
        }
    }

    fun isExpiredOrInvalid(
        startedAtNanos: Long,
        durationNanos: Long,
        nowNanos: Long,
    ): Boolean {
        require(durationNanos > 0L)
        val elapsed = nowNanos - startedAtNanos
        return elapsed < 0L || elapsed >= durationNanos
    }

    fun remaining(
        startedAtNanos: Long,
        durationNanos: Long,
        nowNanos: Long,
    ): Duration {
        require(durationNanos > 0L)
        val elapsed = nowNanos - startedAtNanos
        if (elapsed < 0L || elapsed >= durationNanos) return Duration.ZERO
        return Duration.ofNanos(durationNanos - elapsed)
    }

    /** Clock rollback never prunes replay evidence. */
    fun retentionElapsed(
        recordedAtNanos: Long,
        retentionNanos: Long,
        nowNanos: Long,
    ): Boolean {
        require(retentionNanos > 0L)
        val elapsed = nowNanos - recordedAtNanos
        return elapsed >= retentionNanos
    }
}

internal class BoundedReplayLedger<K : Any>(
    private val monotonicNanos: () -> Long = MonotonicSecurityTime::nowNanos,
    retention: Duration,
    private val maxEntries: Int,
) {
    private val retentionNanos = MonotonicSecurityTime.durationNanos(retention)
    private val entries = linkedMapOf<K, Long>()

    init {
        require(maxEntries > 0)
    }

    @Synchronized
    fun contains(key: K): Boolean {
        prune(monotonicNanos())
        return key in entries
    }

    @Synchronized
    fun recordNew(key: K): Boolean {
        val now = monotonicNanos()
        prune(now)
        if (key in entries || entries.size >= maxEntries) return false
        entries[key] = now
        return true
    }

    @Synchronized
    fun refresh(key: K) {
        val now = monotonicNanos()
        prune(now)
        if (key in entries) entries[key] = now
    }

    @Synchronized
    internal fun size(): Int {
        prune(monotonicNanos())
        return entries.size
    }

    private fun prune(now: Long) {
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (MonotonicSecurityTime.retentionElapsed(entry.value, retentionNanos, now)) {
                iterator.remove()
            }
        }
    }
}

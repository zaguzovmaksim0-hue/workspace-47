package dev.junta.firmamobile.browser

import android.os.SystemClock
import java.time.Duration

enum class ClientCertPreferenceBarrierState {
    IDLE,
    CLEARING,
    READY,
    FAILED,
}

@ConsistentCopyVisibility
data class ClientCertPreferenceBarrierToken internal constructor(
    val generation: Long,
)

/**
 * Generation-bound gate for WebView client-certificate preference clearing.
 * It stores only the pending in-memory grant and never a certificate or key.
 */
internal class ClientCertPreferenceBarrier<T : Any>(
    private val monotonicMillis: () -> Long = SystemClock::elapsedRealtime,
) {
    private var generation = 0L
    private var currentState = ClientCertPreferenceBarrierState.IDLE
    private var deadlineMillis = 0L
    private var pending: T? = null

    @Synchronized
    fun state(): ClientCertPreferenceBarrierState = currentState

    @Synchronized
    fun beginClear(value: T): ClientCertPreferenceBarrierToken {
        generation = nextGeneration(generation)
        pending = value
        deadlineMillis = safeDeadline(monotonicMillis())
        currentState = ClientCertPreferenceBarrierState.CLEARING
        return ClientCertPreferenceBarrierToken(generation)
    }

    @Synchronized
    fun complete(token: ClientCertPreferenceBarrierToken): Boolean {
        if (!isCurrent(token) || currentState != ClientCertPreferenceBarrierState.CLEARING) return false
        if (monotonicMillis() >= deadlineMillis) {
            failCurrent()
            return false
        }
        currentState = ClientCertPreferenceBarrierState.READY
        return true
    }

    @Synchronized
    fun consumeReady(token: ClientCertPreferenceBarrierToken): T? {
        if (!isCurrent(token) || currentState != ClientCertPreferenceBarrierState.READY) return null
        val value = pending
        pending = null
        currentState = ClientCertPreferenceBarrierState.IDLE
        generation = nextGeneration(generation)
        deadlineMillis = 0L
        return value
    }

    @Synchronized
    fun expire(token: ClientCertPreferenceBarrierToken): Boolean {
        if (!isCurrent(token) || currentState != ClientCertPreferenceBarrierState.CLEARING ||
            monotonicMillis() < deadlineMillis
        ) {
            return false
        }
        failCurrent()
        return true
    }

    @Synchronized
    fun fail(token: ClientCertPreferenceBarrierToken): Boolean {
        if (!isCurrent(token) || currentState != ClientCertPreferenceBarrierState.CLEARING) return false
        failCurrent()
        return true
    }

    @Synchronized
    fun consumeFailure(token: ClientCertPreferenceBarrierToken): Boolean {
        if (!isCurrent(token) || currentState != ClientCertPreferenceBarrierState.FAILED) return false
        pending = null
        deadlineMillis = 0L
        currentState = ClientCertPreferenceBarrierState.IDLE
        generation = nextGeneration(generation)
        return true
    }

    @Synchronized
    fun invalidate() {
        generation = nextGeneration(generation)
        pending = null
        deadlineMillis = 0L
        currentState = ClientCertPreferenceBarrierState.IDLE
    }

    private fun isCurrent(token: ClientCertPreferenceBarrierToken): Boolean =
        token.generation == generation

    private fun failCurrent() {
        pending = null
        deadlineMillis = 0L
        currentState = ClientCertPreferenceBarrierState.FAILED
    }

    private fun safeDeadline(startMillis: Long): Long {
        val timeout = CLEAR_TIMEOUT.toMillis()
        return if (startMillis > Long.MAX_VALUE - timeout) Long.MAX_VALUE else startMillis + timeout
    }

    private fun nextGeneration(current: Long): Long =
        if (current == Long.MAX_VALUE) 1L else current + 1L

    companion object {
        val CLEAR_TIMEOUT: Duration = Duration.ofSeconds(3)
    }
}

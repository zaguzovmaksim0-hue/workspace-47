package dev.junta.firmamobile.browser

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ClientCertPreferenceClearResult {
    CLEARED,
    FAILED,
}

@ConsistentCopyVisibility
data class ClientCertPreferenceClearRequest internal constructor(
    val generation: Long,
)

internal fun interface ClientCertPreferenceTimeoutHandle {
    fun cancel()
}

internal fun interface ClientCertPreferenceTimeoutScheduler {
    fun schedule(delayMillis: Long, action: () -> Unit): ClientCertPreferenceTimeoutHandle
}

internal class AndroidClientCertPreferenceTimeoutScheduler(
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : ClientCertPreferenceTimeoutScheduler {
    override fun schedule(
        delayMillis: Long,
        action: () -> Unit,
    ): ClientCertPreferenceTimeoutHandle {
        require(delayMillis >= 0)
        val runnable = Runnable(action)
        if (!handler.postDelayed(runnable, delayMillis)) {
            throw IllegalStateException("Unable to schedule client certificate preference timeout")
        }
        return ClientCertPreferenceTimeoutHandle { handler.removeCallbacks(runnable) }
    }
}

/**
 * Process-scoped coordinator for WebView client-certificate preference clearing.
 *
 * FAILED is sticky until a later request completes successfully. UI callbacks are
 * generation-bound and may be detached without cancelling the platform clear.
 */
class ClientCertPreferenceCoordinator internal constructor(
    private val clearer: ClientCertPreferenceClearer = AndroidClientCertPreferenceClearer,
    private val scheduler: ClientCertPreferenceTimeoutScheduler =
        AndroidClientCertPreferenceTimeoutScheduler(),
    private val barrier: ClientCertPreferenceBarrier<Unit> = ClientCertPreferenceBarrier(),
) {
    private val mutableState = MutableStateFlow(barrier.state())
    val state: StateFlow<ClientCertPreferenceBarrierState> = mutableState.asStateFlow()

    private var active: ActiveClear? = null

    internal fun requestClear(
        callback: ((ClientCertPreferenceClearRequest, ClientCertPreferenceClearResult) -> Unit)? = null,
    ): ClientCertPreferenceClearRequest {
        val token: ClientCertPreferenceBarrierToken
        val request: ClientCertPreferenceClearRequest
        val previousTimeout: ClientCertPreferenceTimeoutHandle?
        synchronized(this) {
            previousTimeout = active?.timeout
            token = barrier.beginClear(Unit)
            request = ClientCertPreferenceClearRequest(token.generation)
            active = ActiveClear(token = token, request = request, callback = callback)
            mutableState.value = ClientCertPreferenceBarrierState.CLEARING
        }
        previousTimeout?.cancel()

        val timeout = try {
            scheduler.schedule(ClientCertPreferenceBarrier.CLEAR_TIMEOUT.toMillis()) {
                finishFailure(token, requireExpiry = true)
            }
        } catch (_: Exception) {
            finishFailure(token, requireExpiry = false)
            return request
        }
        synchronized(this) {
            val current = active
            if (current?.token == token) {
                current.timeout = timeout
            } else {
                timeout.cancel()
            }
        }

        try {
            clearer.clear { finishSuccess(token) }
        } catch (_: Exception) {
            finishFailure(token, requireExpiry = false)
        }
        return request
    }

    internal fun cancelCallback(request: ClientCertPreferenceClearRequest) {
        synchronized(this) {
            active?.takeIf { it.request == request }?.callback = null
        }
    }

    private fun finishSuccess(token: ClientCertPreferenceBarrierToken) {
        var delivery: Delivery? = null
        synchronized(this) {
            val current = active?.takeIf { it.token == token } ?: return
            if (barrier.complete(token)) {
                check(barrier.consumeReady(token) != null)
                current.timeout?.cancel()
                active = null
                mutableState.value = ClientCertPreferenceBarrierState.IDLE
                delivery = current.callback?.let {
                    Delivery(it, current.request, ClientCertPreferenceClearResult.CLEARED)
                }
            } else if (barrier.state() == ClientCertPreferenceBarrierState.FAILED) {
                current.timeout?.cancel()
                active = null
                mutableState.value = ClientCertPreferenceBarrierState.FAILED
                delivery = current.callback?.let {
                    Delivery(it, current.request, ClientCertPreferenceClearResult.FAILED)
                }
            }
        }
        delivery?.deliver()
    }

    private fun finishFailure(
        token: ClientCertPreferenceBarrierToken,
        requireExpiry: Boolean,
    ) {
        var delivery: Delivery? = null
        synchronized(this) {
            val current = active?.takeIf { it.token == token } ?: return
            val failed = if (requireExpiry) barrier.expire(token) else barrier.fail(token)
            if (!failed) return
            current.timeout?.cancel()
            active = null
            mutableState.value = ClientCertPreferenceBarrierState.FAILED
            delivery = current.callback?.let {
                Delivery(it, current.request, ClientCertPreferenceClearResult.FAILED)
            }
        }
        delivery?.deliver()
    }

    private data class ActiveClear(
        val token: ClientCertPreferenceBarrierToken,
        val request: ClientCertPreferenceClearRequest,
        var callback: ((ClientCertPreferenceClearRequest, ClientCertPreferenceClearResult) -> Unit)?,
        var timeout: ClientCertPreferenceTimeoutHandle? = null,
    )

    private data class Delivery(
        val callback: (ClientCertPreferenceClearRequest, ClientCertPreferenceClearResult) -> Unit,
        val request: ClientCertPreferenceClearRequest,
        val result: ClientCertPreferenceClearResult,
    ) {
        fun deliver() = callback(request, result)
    }
}

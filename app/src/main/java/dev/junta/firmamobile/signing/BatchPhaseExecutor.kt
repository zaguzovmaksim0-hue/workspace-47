package dev.junta.firmamobile.signing

import dev.junta.firmamobile.network.ProfileHttpCancellation
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Keep ownership/UI on the caller. Join the worker before releasing its inputs,
 * cancel active HTTP immediately, and dispose a result lost at dispatcher return. */
internal class BatchPhaseExecutor(private val dispatcher: CoroutineDispatcher = Dispatchers.IO) {
    suspend fun <T : Any> run(
        cancellation: ProfileHttpCancellation,
        discard: (T) -> Unit,
        block: () -> T,
    ): T {
        val produced = AtomicReference<T?>(null)
        try {
            val result = coroutineScope {
                val finished = AtomicBoolean(false)
                val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
                    try { awaitCancellation() }
                    finally { if (!finished.get()) cancellation.cancel() }
                }
                try {
                    withContext(dispatcher) {
                        ensureActive()
                        block().also { produced.set(it) }
                    }
                } finally {
                    finished.set(true)
                    watcher.cancel()
                }
            }
            produced.set(null)
            return result
        } finally {
            produced.getAndSet(null)?.let(discard)
        }
    }
}

package dev.junta.firmamobile.afirma.servlet

import java.io.Closeable
import java.security.MessageDigest
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import dev.junta.firmamobile.security.MonotonicSecurityTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

internal data class AfirmaRetrievalPrompt(
    val token: UUID,
    val sourceOrigin: String,
    val retrievalEndpoint: String,
    val loading: Boolean,
    val problem: AfirmaRetrievalProblem?,
)

/** Main-thread ownership of one destructive-read attempt. Resolving a request
 * never signs it. The handoff receives fresh explicit-consent state only after
 * data, operation and output destination have been checked together.
 */
internal class AfirmaRetrievalController<Owner : Any>(
    private val scope: CoroutineScope,
    private val resolver: AfirmaRequestResolver,
    private val isCurrent: (Owner, Long) -> Boolean,
    private val canRespond: () -> Boolean,
    private val onPrompt: (AfirmaRetrievalPrompt?) -> Unit,
    /** Consumes the operation on every normal return, including rejection,
     * matching AfirmaConsentController.offer. Must close it when returning false. */
    private val onPrepared: (Owner, Long, PreparedAfirmaOperation) -> Boolean,
    private val monotonicNanos: () -> Long = MonotonicSecurityTime::nowNanos,
) : Closeable {
    private class Pending<Owner>(val owner: Owner, val epoch: Long, val request: AfirmaDeferredInvocation, val born: Long) {
        val token = UUID.randomUUID()
        var loading = true
        var job: Job? = null
        var released = false
        fun release() { if (!released) { released = true; request.close() } }
    }
    private var current: Pending<Owner>? = null
    private var closed = false
    private var backgrounded = false
    private var rememberedOwner: Owner? = null
    private var rememberedEpoch = Long.MIN_VALUE
    private val attempted = hashSetOf<String>()
    val hasPending: Boolean get() = current != null

    fun offer(owner: Owner, epoch: Long, request: AfirmaDeferredInvocation): Boolean {
        if (closed || backgrounded || current != null || !isCurrent(owner, epoch) || !canRespond()) {
            request.close(); return false
        }
        val time = runCatching(monotonicNanos).getOrNull()
        if (time == null) { request.close(); return false }
        if (rememberedOwner !== owner || rememberedEpoch != epoch) {
            attempted.clear(); rememberedOwner = owner; rememberedEpoch = epoch
        }
        val pending = Pending(owner, epoch, request, time)
        current = pending
        val id = fingerprint(request)
        if (id in attempted || attempted.size >= MAX_ATTEMPTS_PER_DOCUMENT) {
            finish(pending, AfirmaRetrievalProblem.UNAVAILABLE)
            return true
        }
        // Record before yielding; a repeated Android callback cannot initiate a
        // second consuming read after cancellation, error, success or dismissal.
        attempted += id
        publish(pending, null)
        if (current !== pending || !pending.loading || !valid(pending) || backgrounded || !canRespond()) {
            if (current === pending) finish(pending, AfirmaRetrievalProblem.CANCELLED)
            return false
        }
        val entered = AtomicBoolean(false)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            entered.set(true)
            var operation: PreparedAfirmaOperation? = null
            var transferred = false
            try {
                currentCoroutineContext().ensureActive()
                if (!ownedForeground(pending)) throw CancellationException("Configuration owner changed")
                operation = resolver.resolve(request)
                currentCoroutineContext().ensureActive()
                if (!ownedForeground(pending)) throw CancellationException("Configuration owner changed")
                // Keep current set while hiding the loader: reentrant requests
                // are still blocked until the receiving consent owns the data.
                onPrompt(null)
                currentCoroutineContext().ensureActive()
                if (!ownedForeground(pending)) throw CancellationException("Configuration changed at handoff")
                val handoff = checkNotNull(operation)
                operation = null
                transferred = true
                val accepted = try { onPrepared(owner, epoch, handoff) }
                catch (error: Exception) { handoff.close(); throw error }
                if (accepted) {
                    if (current === pending) current = null
                    pending.loading = false
                    pending.release()
                } else if (current === pending) finish(pending, AfirmaRetrievalProblem.CANCELLED)
            } catch (e: CancellationException) {
                if (current === pending && pending.loading) finish(pending, AfirmaRetrievalProblem.CANCELLED)
                throw e
            } catch (e: AfirmaRetrievalException) {
                if (current === pending) finish(pending, e.problem)
            } catch (_: Exception) {
                if (current === pending) finish(pending, AfirmaRetrievalProblem.INVALID)
            } finally {
                if (!transferred) operation?.close()
                pending.release()
            }
        }
        pending.job = job
        job.invokeOnCompletion {
            if (!entered.get()) scope.launch(NonCancellable) {
                if (current === pending) finish(pending, AfirmaRetrievalProblem.CANCELLED)
                else pending.release()
            }
        }
        if (!job.start()) { finish(pending, AfirmaRetrievalProblem.CANCELLED); return false }
        return true
    }

    fun cancel(token: UUID) {
        val pending = current?.takeIf { it.token == token } ?: return
        if (!pending.loading) return
        finish(pending, AfirmaRetrievalProblem.CANCELLED)
        pending.job?.cancel()
    }
    fun dismiss(token: UUID) {
        val pending = current?.takeIf { it.token == token } ?: return
        current = null; pending.job?.cancel(); pending.release(); onPrompt(null)
    }
    fun invalidate() { current?.let { dismiss(it.token) } }
    fun tick() {
        current?.takeIf { it.loading && !valid(it) }?.let {
            finish(it, AfirmaRetrievalProblem.EXPIRED); it.job?.cancel()
        }
    }
    fun onBackground() { backgrounded = true; current?.takeIf { it.loading }?.let { cancel(it.token) } }
    fun onForeground() { backgrounded = false; tick() }
    override fun close() {
        closed = true; invalidate(); attempted.clear(); rememberedOwner = null
    }

    private fun ownedForeground(pending: Pending<Owner>): Boolean =
        current === pending && pending.loading && valid(pending) && !backgrounded && canRespond()
    private fun valid(pending: Pending<Owner>): Boolean = runCatching {
        !closed && isCurrent(pending.owner, pending.epoch) && !MonotonicSecurityTime.isExpiredOrInvalid(
            pending.born, LIFETIME.toNanos(), monotonicNanos(),
        )
    }.getOrDefault(false)
    private fun finish(pending: Pending<Owner>, problem: AfirmaRetrievalProblem) {
        if (!pending.loading) return
        pending.loading = false; pending.release(); publish(pending, problem)
    }
    private fun publish(pending: Pending<Owner>, problem: AfirmaRetrievalProblem?) {
        if (current === pending) onPrompt(AfirmaRetrievalPrompt(pending.token, pending.request.sourceOrigin,
            pending.request.retrievalDisplay, pending.loading, problem))
    }
    private fun fingerprint(request: AfirmaDeferredInvocation): String {
        val input = (request.retrievalUrl.toASCIIString() + "\u0000" + request.fileId).toByteArray(Charsets.UTF_8)
        return try { MessageDigest.getInstance("SHA-256").digest(input).joinToString("") { "%02x".format(it) } }
        finally { input.fill(0) }
    }
    private companion object { val LIFETIME: Duration = Duration.ofMinutes(5); const val MAX_ATTEMPTS_PER_DOCUMENT = 32 }
}

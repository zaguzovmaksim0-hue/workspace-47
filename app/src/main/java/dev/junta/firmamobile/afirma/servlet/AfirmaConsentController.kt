package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.certificate.UnlockedIdentity
import dev.junta.firmamobile.security.MonotonicSecurityTime
import java.io.Closeable
import java.security.MessageDigest
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Public facts only: never a session ID, cipher key, encoded payload or URL query. */
internal data class AfirmaConsentDetails(
    val sourceOrigin: String,
    val destination: String,
    val operation: String,
    val format: String?,
    val algorithm: String?,
    val payloadBytes: Int,
    val payloadSha256: String?,
    val serviceDestinations: List<String> = emptyList(),
    val batchItems: Int? = null,
    val delegatedSigning: Boolean = false,
    val requiresExactCertificate: Boolean = false,
    val providedDigestAlgorithm: String? = null,
    val providedDigestItems: Int = 0,
)

internal interface PreparedAfirmaOperation : Closeable {
    val details: AfirmaConsentDetails
    val resultSummary: String? get() = null
    val batchReceipt: NativeBatchReceipt? get() = null
    fun certificateCompatible(identity: UnlockedIdentity): Boolean
    /** Implementations call authorizeUpload immediately before the one upload.
     * It must return to the caller's UI dispatcher and revalidate ownership. */
    suspend fun execute(identity: UnlockedIdentity, authorizeUpload: suspend () -> Unit): AfirmaDeliveryResult
    /** Revalidate ownership between phases without consuming the single final
     * upload permission. Default keeps existing one-stage operations unchanged. */
    suspend fun executeWithCheckpoints(identity: UnlockedIdentity, checkpoint: suspend () -> Unit,
        authorizeUpload: suspend () -> Unit): AfirmaDeliveryResult {
        checkpoint()
        return execute(identity, authorizeUpload)
    }
    /** Optional one-shot CANCEL notification, never uses a personal key or document.
     * The caller must explicitly authorize before upload. Unsupported operations
     * return NOT_SENT; implementations share a terminal gate with execute. */
    suspend fun notifyCancellation(authorizeUpload: suspend () -> Unit): AfirmaDeliveryResult = AfirmaDeliveryResult.NOT_SENT
}

internal enum class AfirmaConsentPhase { REVIEW, WORKING, SENDING, CANCELLING, FINISHED }
internal enum class AfirmaConsentProblem { LOCKED, INCOMPATIBLE, EXPIRED, CANCELLED, FAILED, ACKNOWLEDGED, REJECTED, UNCERTAIN, NOT_SENT, CANCEL_ACKNOWLEDGED, CANCEL_NOT_SENT, CANCEL_REJECTED, CANCEL_UNCERTAIN }
internal data class AfirmaConsentPrompt(
    val token: UUID,
    val details: AfirmaConsentDetails,
    val phase: AfirmaConsentPhase,
    val certificateOwner: String?,
    val problem: AfirmaConsentProblem?,
    val resultSummary: String? = null,
    val batchReceipt: NativeBatchReceipt? = null,
) {
    val canConfirm: Boolean get() = phase == AfirmaConsentPhase.REVIEW && problem == null && certificateOwner != null
}

/** Main-thread request ownership. Unlock != consent; no automatic retry after
 * signing, cancellation or a possibly accepted upload. Ordinary browsing and
 * the server's cookies are not changed by this controller. */
internal class AfirmaConsentController<Owner : Any>(
    private val scope: CoroutineScope,
    private val isCurrent: (Owner, Long) -> Boolean,
    private val identityProvider: () -> UnlockedIdentity?,
    private val canRespond: () -> Boolean,
    private val onPrompt: (AfirmaConsentPrompt?) -> Unit,
    private val monotonicNanos: () -> Long = MonotonicSecurityTime::nowNanos,
) : Closeable {
    private class Pending<Owner>(val owner: Owner, val epoch: Long, val operation: PreparedAfirmaOperation, val born: Long) {
        val details = operation.details
        var token = UUID.randomUUID()
        var fingerprint: String? = null
        var certificateOwner: String? = null
        var phase = AfirmaConsentPhase.REVIEW
        var problem: AfirmaConsentProblem? = null
        var job: Job? = null
        var started = false
        var cancellationOutcome: AfirmaCancellationOutcome? = null
        var terminalBatchReceipt: NativeBatchReceipt? = null
        var released = false
        fun release() { if (!released) { released = true; operation.close() } }
    }
    private var current: Pending<Owner>? = null
    private var closed = false
    private var backgrounded = false

    val hasPending: Boolean get() = current != null

    fun offer(owner: Owner, epoch: Long, operation: PreparedAfirmaOperation): Boolean {
        if (closed || backgrounded || current != null || !isCurrent(owner, epoch) || !canRespond()) {
            operation.close(); return false
        }
        val born = runCatching(monotonicNanos).getOrNull()
        if (born == null) { operation.close(); return false }
        current = Pending(owner, epoch, operation, born)
        refreshIdentity()
        return true
    }

    fun refreshIdentity() {
        val pending = current ?: return
        if (pending.phase == AfirmaConsentPhase.FINISHED) return
        if (!valid(pending)) { expire(pending); return }
        // Cancellation is key-free, including recompositions and foreground return.
        if (pending.cancellationOutcome != null) return
        val identity = identityProvider()
        val fingerprint = fingerprint(identity)
        if (pending.started) {
            if (fingerprint != pending.fingerprint) cancel(pending.token)
            return
        }
        val problem = when {
            identity == null || fingerprint == null -> AfirmaConsentProblem.LOCKED
            !runCatching { pending.operation.certificateCompatible(identity) }.getOrDefault(false) -> AfirmaConsentProblem.INCOMPATIBLE
            else -> null
        }
        if (pending.fingerprint != fingerprint || pending.problem != problem) pending.token = UUID.randomUUID()
        pending.fingerprint = fingerprint
        pending.certificateOwner = identity?.summary?.ownerName
        pending.problem = problem
        publish(pending)
    }

    fun confirm(token: UUID): Boolean {
        val pending = current?.takeIf { it.token == token && !it.started && it.phase == AfirmaConsentPhase.REVIEW } ?: return false
        if (backgrounded || !canRespond()) return false
        if (!valid(pending)) { expire(pending); return false }
        val identity = identityProvider()
        val fp = fingerprint(identity)
        if (identity == null || fp == null || fp != pending.fingerprint || pending.problem != null ||
            !runCatching { pending.operation.certificateCompatible(identity) }.getOrDefault(false)
        ) { refreshIdentity(); return false }
        pending.started = true
        pending.phase = AfirmaConsentPhase.WORKING
        publish(pending)
        if (current !== pending || pending.phase != AfirmaConsentPhase.WORKING || !valid(pending) || backgrounded || !canRespond()) {
            if (current === pending && pending.phase == AfirmaConsentPhase.WORKING) {
                finish(pending, if (!valid(pending)) AfirmaConsentProblem.EXPIRED else AfirmaConsentProblem.CANCELLED)
            }
            return false
        }
        val bodyEntered = AtomicBoolean(false)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            bodyEntered.set(true)
            try {
                val checkpoint: suspend () -> Unit = {
                    currentCoroutineContext().ensureActive()
                    if (current !== pending || pending.phase !in setOf(AfirmaConsentPhase.WORKING, AfirmaConsentPhase.SENDING) ||
                        !valid(pending) || backgrounded || !canRespond() || fingerprint(identityProvider()) != pending.fingerprint
                    ) throw CancellationException("Signing phase no longer owned")
                }
                val delivery = pending.operation.executeWithCheckpoints(identity, checkpoint) {
                    // execute must call this in the controller's coroutine
                    // context, never from a detached global scope.
                    if (current !== pending || pending.phase != AfirmaConsentPhase.WORKING || !valid(pending) || backgrounded || !canRespond() ||
                        fingerprint(identityProvider()) != pending.fingerprint
                    ) throw CancellationException("Request no longer owned")
                    pending.phase = AfirmaConsentPhase.SENDING
                    publish(pending)
                    currentCoroutineContext().ensureActive()
                    if (current !== pending || pending.phase != AfirmaConsentPhase.SENDING || !valid(pending) ||
                        backgrounded || !canRespond() || fingerprint(identityProvider()) != pending.fingerprint
                    ) throw CancellationException("Request changed during upload notification")
                }
                if (current === pending) finish(pending, when (delivery) {
                    AfirmaDeliveryResult.ACKNOWLEDGED -> AfirmaConsentProblem.ACKNOWLEDGED
                    AfirmaDeliveryResult.REJECTED -> AfirmaConsentProblem.REJECTED
                    AfirmaDeliveryResult.NOT_SENT -> AfirmaConsentProblem.NOT_SENT
                    AfirmaDeliveryResult.UNCERTAIN -> AfirmaConsentProblem.UNCERTAIN
                })
            } catch (error: CancellationException) {
                if (current === pending && pending.phase != AfirmaConsentPhase.FINISHED) {
                    finish(pending, if (pending.phase == AfirmaConsentPhase.SENDING) AfirmaConsentProblem.UNCERTAIN else AfirmaConsentProblem.CANCELLED)
                }
                throw error
            } catch (_: Exception) {
                if (current === pending) finish(pending,
                    if (pending.phase == AfirmaConsentPhase.SENDING) AfirmaConsentProblem.UNCERTAIN else AfirmaConsentProblem.FAILED)
            } finally { pending.release() }
        }
        pending.job = job
        job.invokeOnCompletion {
            if (!bodyEntered.get()) {
                // A canceled scheduled coroutine may never enter its finally.
                // Keep the owning dispatcher, but not the canceled parent Job,
                // for this tiny idempotent cleanup. It performs no signing/I/O.
                scope.launch(NonCancellable) {
                    if (current === pending) finish(pending, AfirmaConsentProblem.CANCELLED)
                    else pending.release()
                }
            }
        }
        if (!job.start()) {
            finish(pending, AfirmaConsentProblem.CANCELLED)
            return false
        }
        return true
    }

    /** Explicit REVIEW button only. Lifecycle callers continue using local cancel. */
    fun requestCancellation(token: UUID): Boolean {
        val pending = current?.takeIf {
            it.token == token && !it.started && it.phase == AfirmaConsentPhase.REVIEW
        } ?: return false
        if (backgrounded || !canRespond()) return false
        if (!valid(pending)) { expire(pending); return false }
        val outcome = AfirmaCancellationOutcome()
        pending.cancellationOutcome = outcome
        pending.started = true
        pending.phase = AfirmaConsentPhase.CANCELLING
        pending.problem = null
        publish(pending)
        if (!ownsCancellation(pending)) {
            if (current === pending && pending.phase != AfirmaConsentPhase.FINISHED) finish(pending, outcome.interrupt())
            return false
        }
        val entered = AtomicBoolean(false)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            entered.set(true)
            try {
                val delivery = withTimeout(15_000L) {
                    pending.operation.notifyCancellation {
                        currentCoroutineContext().ensureActive()
                        if (!ownsCancellation(pending) || !outcome.markAuthorized()) {
                            throw CancellationException("Cancellation request no longer owned")
                        }
                        currentCoroutineContext().ensureActive()
                        if (!ownsCancellation(pending)) throw CancellationException("Cancellation ownership changed")
                    }
                }
                currentCoroutineContext().ensureActive()
                if (current === pending && pending.phase == AfirmaConsentPhase.CANCELLING) {
                    finish(pending, outcome.complete(delivery))
                }
            } catch (error: CancellationException) {
                if (current === pending && pending.phase == AfirmaConsentPhase.CANCELLING) finish(pending, outcome.interrupt())
                throw error
            } catch (_: Exception) {
                if (current === pending && pending.phase == AfirmaConsentPhase.CANCELLING) finish(pending, outcome.interrupt())
            } finally { pending.release() }
        }
        pending.job = job
        job.invokeOnCompletion {
            if (!entered.get()) scope.launch(NonCancellable) {
                if (current === pending && pending.phase == AfirmaConsentPhase.CANCELLING) finish(pending, outcome.interrupt())
                else pending.release()
            }
        }
        if (!job.start()) {
            finish(pending, outcome.interrupt())
            return false
        }
        return true
    }

    private fun ownsCancellation(pending: Pending<Owner>): Boolean =
        current === pending && pending.phase == AfirmaConsentPhase.CANCELLING &&
            valid(pending) && !backgrounded && canRespond()

    fun cancel(token: UUID) {
        val pending = current?.takeIf { it.token == token } ?: return
        if (pending.phase == AfirmaConsentPhase.FINISHED) return
        val reason = pending.cancellationOutcome?.interrupt()
            ?: if (pending.phase == AfirmaConsentPhase.SENDING) AfirmaConsentProblem.UNCERTAIN else AfirmaConsentProblem.CANCELLED
        finish(pending, reason)
        pending.job?.cancel()
    }

    fun dismiss(token: UUID) {
        val pending = current?.takeIf { it.token == token } ?: return
        current = null
        pending.job?.cancel()
        pending.release()
        onPrompt(null)
    }

    fun invalidate() { current?.let { dismiss(it.token) } }

    fun tick() { current?.takeIf { it.phase != AfirmaConsentPhase.FINISHED && !valid(it) }?.let(::expire) }

    fun onBackground(expectedExternalReturn: Boolean) {
        backgrounded = true
        val pending = current ?: return
        // Never let signing/upload continue while the user is away. Only an
        // unsigned review may survive an explicitly owned certificate picker.
        if (pending.started || !expectedExternalReturn) cancel(pending.token)
    }

    fun onForeground(returnStillValid: Boolean) {
        if (backgrounded && !returnStillValid) current?.let { cancel(it.token) }
        backgrounded = false
        refreshIdentity()
    }

    override fun close() { closed = true; invalidate() }

    private fun valid(pending: Pending<Owner>): Boolean = runCatching {
        !closed && isCurrent(pending.owner, pending.epoch) &&
            !MonotonicSecurityTime.isExpiredOrInvalid(pending.born, LIFETIME.toNanos(), monotonicNanos())
    }.getOrDefault(false)

    private fun expire(pending: Pending<Owner>) {
        finish(pending, pending.cancellationOutcome?.interrupt()
            ?: if (pending.phase == AfirmaConsentPhase.SENDING) AfirmaConsentProblem.UNCERTAIN else AfirmaConsentProblem.EXPIRED)
        pending.job?.cancel()
    }

    private fun finish(pending: Pending<Owner>, result: AfirmaConsentProblem) {
        if (pending.phase == AfirmaConsentPhase.FINISHED) return
        pending.phase = AfirmaConsentPhase.FINISHED
        pending.problem = result
        pending.terminalBatchReceipt = pending.operation.batchReceipt
        pending.release()
        publish(pending)
    }

    private fun publish(pending: Pending<Owner>) {
        if (current === pending) onPrompt(AfirmaConsentPrompt(pending.token, pending.details,
            pending.phase, pending.certificateOwner, pending.problem, pending.operation.resultSummary,
            if (pending.phase == AfirmaConsentPhase.FINISHED) pending.terminalBatchReceipt else null))
    }

    private fun fingerprint(identity: UnlockedIdentity?): String? = runCatching {
        val bytes = identity?.certificate?.encoded ?: return null
        try { MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } }
        finally { bytes.fill(0) }
    }.getOrNull()

    private companion object { val LIFETIME: Duration = Duration.ofMinutes(5) }
}

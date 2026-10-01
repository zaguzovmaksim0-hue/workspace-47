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

/** Public facts only: never a session ID, cipher key, encoded payload or URL query. */
internal data class AfirmaConsentDetails(
    val sourceOrigin: String,
    val destination: String,
    val operation: String,
    val format: String?,
    val algorithm: String?,
    val payloadBytes: Int,
    val payloadSha256: String?,
)

internal interface PreparedAfirmaOperation : Closeable {
    val details: AfirmaConsentDetails
    fun certificateCompatible(identity: UnlockedIdentity): Boolean
    /** Implementations call authorizeUpload immediately before the one upload.
     * It must return to the caller's UI dispatcher and revalidate ownership. */
    suspend fun execute(identity: UnlockedIdentity, authorizeUpload: suspend () -> Unit): AfirmaDeliveryResult
}

internal enum class AfirmaConsentPhase { REVIEW, WORKING, SENDING, FINISHED }
internal enum class AfirmaConsentProblem { LOCKED, INCOMPATIBLE, EXPIRED, CANCELLED, FAILED, ACKNOWLEDGED, REJECTED, UNCERTAIN, NOT_SENT }
internal data class AfirmaConsentPrompt(
    val token: UUID,
    val details: AfirmaConsentDetails,
    val phase: AfirmaConsentPhase,
    val certificateOwner: String?,
    val problem: AfirmaConsentProblem?,
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
                val delivery = pending.operation.execute(identity) {
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

    fun cancel(token: UUID) {
        val pending = current?.takeIf { it.token == token } ?: return
        if (pending.phase == AfirmaConsentPhase.FINISHED) return
        val reason = if (pending.phase == AfirmaConsentPhase.SENDING) AfirmaConsentProblem.UNCERTAIN else AfirmaConsentProblem.CANCELLED
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
        finish(pending, if (pending.phase == AfirmaConsentPhase.SENDING) AfirmaConsentProblem.UNCERTAIN else AfirmaConsentProblem.EXPIRED)
        pending.job?.cancel()
    }

    private fun finish(pending: Pending<Owner>, result: AfirmaConsentProblem) {
        if (pending.phase == AfirmaConsentPhase.FINISHED) return
        pending.phase = AfirmaConsentPhase.FINISHED
        pending.problem = result
        pending.release()
        publish(pending)
    }

    private fun publish(pending: Pending<Owner>) {
        if (current === pending) onPrompt(AfirmaConsentPrompt(pending.token, pending.details,
            pending.phase, pending.certificateOwner, pending.problem))
    }

    private fun fingerprint(identity: UnlockedIdentity?): String? = runCatching {
        val bytes = identity?.certificate?.encoded ?: return null
        try { MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } }
        finally { bytes.fill(0) }
    }.getOrNull()

    private companion object { val LIFETIME: Duration = Duration.ofMinutes(5) }
}

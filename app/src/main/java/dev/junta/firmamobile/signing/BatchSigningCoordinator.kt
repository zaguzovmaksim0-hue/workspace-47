package dev.junta.firmamobile.signing

import dev.junta.firmamobile.certificate.CertificateSession
import dev.junta.firmamobile.certificate.CertificateSigningSnapshot
import dev.junta.firmamobile.network.TrustedOrigin
import dev.junta.firmamobile.security.MonotonicSecurityTime
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class BatchSigningCoordinator(
    private val certificateSession: CertificateSession,
    private val adapter: BatchSigningProtocolAdapter,
    @Suppress("unused")
    private val localSignatureEngine: LocalSignatureEngine,
    private val currentOrigin: () -> TrustedOrigin?,
    private val currentNavigationEpoch: () -> Long = { 0L },
    private val monotonicNanos: () -> Long = MonotonicSecurityTime::nowNanos,
    private val expiryScheduler: SigningExpiryScheduler,
    private val profileDisplayName: String,
    private val supportLevel: String,
) : AutoCloseable {
    private val mutableState = MutableStateFlow<SigningUiState>(SigningUiState.Idle)
    val state: StateFlow<SigningUiState> = mutableState.asStateFlow()

    private var pending: PendingOperation? = null

    init {
        require(profileDisplayName.isNotBlank())
        require(supportLevel.isNotBlank())
    }

    @Synchronized
    fun prepare(
        request: NormalizedBatchSigningRequest,
        reply: BatchSigningReplySink,
    ): SigningPreparationResult {
        validateBoundary(request, reply)?.let { code ->
            return reject(request, reply, code)
        }
        if (pending != null) {
            return reject(request, reply, SigningErrorCode.PROTOCOL_FAILED)
        }
        val certificateSnapshot = certificateSession.signingSnapshot()
            ?: return reject(request, reply, SigningErrorCode.CERTIFICATE_LOCKED)
        val expiryDelay = try {
            val startedAt = monotonicNanos()
            MonotonicSecurityTime.remaining(
                startedAt,
                CONFIRMATION_LIFETIME_NANOS,
                monotonicNanos(),
            )
        } catch (_: Exception) {
            certificateSnapshot.close()
            return reject(request, reply, SigningErrorCode.PROTOCOL_FAILED)
        }
        if (expiryDelay.isZero) {
            certificateSnapshot.close()
            return reject(request, reply, SigningErrorCode.REQUEST_EXPIRED)
        }

        val operation = PendingOperation(
            request = request,
            certificateSnapshot = certificateSnapshot,
            reply = reply,
        )
        pending = operation
        try {
            operation.attachExpiry(
                expiryScheduler.schedule(expiryDelay) { expirePending(request.requestId) },
            )
        } catch (_: Exception) {
            val ownsTerminal = operation.claimTerminal()
            if (pending === operation) pending = null
            operation.clearSensitive()
            if (ownsTerminal) {
                runCatching { reply.failure(SigningErrorCode.PROTOCOL_FAILED) }
                mutableState.value = SigningUiState.Failed(
                    request.requestId,
                    SigningErrorCode.PROTOCOL_FAILED,
                )
                return SigningPreparationResult.Rejected(SigningErrorCode.PROTOCOL_FAILED)
            }
            return SigningPreparationResult.Rejected(SigningErrorCode.REQUEST_EXPIRED)
        }

        if (pending !== operation || operation.isTerminal()) {
            return SigningPreparationResult.Rejected(SigningErrorCode.REQUEST_EXPIRED)
        }
        mutableState.value = SigningUiState.AwaitingConfirmation(
            requestId = request.requestId,
            siteHost = request.context.origin.host,
            profileName = profileDisplayName,
            supportLevel = supportLevel,
            safeDescription = batchDescription(request.documents.size),
            format = request.format.displayName(),
            algorithm = request.algorithm.displayName(),
            certificateOwner = certificateSnapshot.summary.ownerName,
            requiresLegacySha1Warning = request.algorithm == SigningAlgorithm.SHA1_WITH_RSA,
        )
        return SigningPreparationResult.Ready(request.requestId)
    }

    fun cancel(
        reason: SigningCancelReason,
        requestId: UUID? = null,
    ): Boolean {
        val operation = synchronized(this) {
            val candidate = pending
                ?.takeIf { requestId == null || it.request.requestId == requestId }
                ?: return false
            if (!candidate.claimTerminal()) return false
            pending = null
            candidate.clearSensitive()
            mutableState.value = SigningUiState.Idle
            candidate
        }
        if (reason.abandonReply) {
            runCatching { operation.reply.abandon() }
        } else {
            runCatching { operation.reply.failure(reason.code) }
        }
        return true
    }

    @Synchronized
    fun dismissTerminalState() {
        if (mutableState.value is SigningUiState.Completed ||
            mutableState.value is SigningUiState.Failed
        ) {
            mutableState.value = SigningUiState.Idle
        }
    }

    override fun close() {
        cancel(SigningCancelReason.BACKGROUND)
        synchronized(this) {
            pending?.clearSensitive()
            pending = null
            mutableState.value = SigningUiState.Idle
        }
    }

    private fun validateBoundary(
        request: NormalizedBatchSigningRequest,
        reply: BatchSigningReplySink,
    ): SigningErrorCode? {
        if (!request.isOpen() || reply.requestId != request.requestId) {
            return SigningErrorCode.INVALID_REQUEST
        }
        if (request.protocolId != adapter.id) return SigningErrorCode.UNSUPPORTED_PROTOCOL
        return if (currentOriginSafely() == request.context.origin &&
            currentNavigationEpochSafely() == request.context.navigationEpoch
        ) {
            null
        } else {
            SigningErrorCode.ORIGIN_NOT_ALLOWED
        }
    }

    private fun reject(
        request: NormalizedBatchSigningRequest,
        reply: BatchSigningReplySink,
        code: SigningErrorCode,
    ): SigningPreparationResult.Rejected {
        request.close()
        runCatching { reply.failure(code) }
        mutableState.value = SigningUiState.Failed(request.requestId, code)
        return SigningPreparationResult.Rejected(code)
    }

    private fun expirePending(requestId: UUID) {
        val operation = synchronized(this) {
            val candidate = pending?.takeIf { it.request.requestId == requestId } ?: return
            if (!candidate.claimTerminal()) return
            pending = null
            candidate.clearSensitive()
            mutableState.value = SigningUiState.Failed(
                requestId,
                SigningErrorCode.REQUEST_EXPIRED,
            )
            candidate
        }
        runCatching { operation.reply.failure(SigningErrorCode.REQUEST_EXPIRED) }
    }

    private fun currentOriginSafely(): TrustedOrigin? = try {
        currentOrigin()
    } catch (_: Exception) {
        null
    }

    private fun currentNavigationEpochSafely(): Long = try {
        currentNavigationEpoch().takeIf { it >= 0L } ?: Long.MIN_VALUE
    } catch (_: Exception) {
        Long.MIN_VALUE
    }

    private fun batchDescription(documentCount: Int): String =
        if (documentCount == 1) {
            "Firma por lotes (1 documento)"
        } else {
            "Firma por lotes ($documentCount documentos)"
        }

    private fun BatchSigningFormat.displayName(): String = when (this) {
        BatchSigningFormat.CADES -> "CAdES"
        BatchSigningFormat.PADES -> "PAdES"
        BatchSigningFormat.XADES -> "XAdES"
    }

    private fun SigningAlgorithm.displayName(): String = when (this) {
        SigningAlgorithm.SHA1_WITH_RSA -> "SHA1withRSA"
        SigningAlgorithm.SHA256_WITH_RSA -> "SHA256withRSA"
        SigningAlgorithm.SHA512_WITH_RSA -> "SHA512withRSA"
    }

    private class PendingOperation(
        val request: NormalizedBatchSigningRequest,
        val certificateSnapshot: CertificateSigningSnapshot,
        val reply: BatchSigningReplySink,
    ) {
        private val terminal = AtomicBoolean(false)
        private var expiryHandle: SigningExpiryHandle? = null
        private var cleared = false

        fun claimTerminal(): Boolean = terminal.compareAndSet(false, true)

        fun isTerminal(): Boolean = terminal.get()

        @Synchronized
        fun attachExpiry(handle: SigningExpiryHandle) {
            if (cleared) {
                handle.cancel()
                return
            }
            check(expiryHandle == null)
            expiryHandle = handle
        }

        @Synchronized
        fun clearSensitive() {
            if (cleared) return
            cleared = true
            expiryHandle?.cancel()
            expiryHandle = null
            request.close()
            certificateSnapshot.close()
        }
    }

    private companion object {
        val CONFIRMATION_LIFETIME: Duration = Duration.ofMinutes(2)
        val CONFIRMATION_LIFETIME_NANOS: Long =
            MonotonicSecurityTime.durationNanos(CONFIRMATION_LIFETIME)
    }
}

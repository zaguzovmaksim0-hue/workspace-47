package dev.junta.firmamobile.signing

import dev.junta.firmamobile.certificate.CertificateSession
import dev.junta.firmamobile.certificate.CertificateSigningSnapshot
import dev.junta.firmamobile.certificate.UnlockedIdentity
import dev.junta.firmamobile.network.TrustedOrigin
import dev.junta.firmamobile.security.MonotonicSecurityTime
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class BatchSigningCoordinator(
    private val certificateSession: CertificateSession,
    private val adapter: BatchSigningProtocolAdapter,
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

    private var pending: Operation? = null
    private var active: Operation? = null

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
        if (pending != null || active != null) {
            return reject(request, reply, SigningErrorCode.PROTOCOL_FAILED)
        }
        val certificateSnapshot = certificateSession.signingSnapshot()
            ?: return reject(request, reply, SigningErrorCode.CERTIFICATE_LOCKED)
        val startedAtNanos: Long
        val expiryDelay: Duration
        try {
            startedAtNanos = monotonicNanos()
            expiryDelay = MonotonicSecurityTime.remaining(
                startedAtNanos,
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

        val operation = Operation(
            request = request,
            certificateSnapshot = certificateSnapshot,
            reply = reply,
            startedAtNanos = startedAtNanos,
        )
        pending = operation
        try {
            operation.attachExpiry(
                expiryScheduler.schedule(expiryDelay) { expirePending(request.requestId) },
            )
        } catch (_: Exception) {
            val ownsTerminal = operation.claimFlow()
            if (pending === operation) pending = null
            operation.clearSensitive()
            val code = operation.cancellationCode() ?: SigningErrorCode.PROTOCOL_FAILED
            if (ownsTerminal) {
                runCatching { reply.failure(code) }
                mutableState.value = SigningUiState.Failed(request.requestId, code)
            }
            return SigningPreparationResult.Rejected(code)
        }

        if (pending !== operation || operation.hasTerminalClaim()) {
            return SigningPreparationResult.Rejected(
                operation.cancellationCode() ?: SigningErrorCode.REQUEST_EXPIRED,
            )
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

    suspend fun confirm(requestId: UUID): SigningExecutionResult {
        val operation = synchronized(this) {
            val candidate = pending?.takeIf { it.request.requestId == requestId }
                ?: return@synchronized null
            candidate.cancelExpiry()
            pending = null
            active = candidate
            mutableState.value = SigningUiState.Signing(requestId)
            candidate
        } ?: return SigningExecutionResult.Failed(SigningErrorCode.INVALID_REQUEST)

        var preSign: BatchPreSignResult? = null
        val localSignatures = mutableListOf<LocalSignature>()
        try {
            operationContextError(operation)?.let { code ->
                return fail(operation, code)
            }
            val identity = certificateSession.identityForSigning(operation.certificateSnapshot)
                ?: return fail(operation, SigningErrorCode.CERTIFICATE_LOCKED)
            operationValidationError(operation, identity)?.let { code ->
                return fail(operation, code)
            }
            val ownedPreSign = when (val prepared = adapter.prepare(operation.request, identity.chain)) {
                is BatchProtocolPrepareResult.Failure -> return fail(operation, prepared.code)
                is BatchProtocolPrepareResult.Success -> prepared.preSign
            }
            preSign = ownedPreSign
            operationValidationError(operation, identity)?.let { code ->
                return fail(operation, code)
            }

            repeat(ownedPreSign.inputCount) { index ->
                operationValidationError(operation, identity)?.let { code ->
                    return fail(operation, code)
                }
                val localSignature = when (
                    val result = ownedPreSign.withInput(index) { bytes ->
                        localSignatureEngine.sign(bytes, identity, operation.request.algorithm)
                    }
                ) {
                    is LocalSignatureResult.Failure ->
                        return fail(operation, SigningErrorCode.LOCAL_SIGNATURE_FAILED)
                    is LocalSignatureResult.Success -> result.signature
                }
                localSignatures += localSignature
            }

            operationValidationError(operation, identity)?.let { code ->
                return fail(operation, code)
            }
            val completion = try {
                adapter.complete(operation.request, ownedPreSign, localSignatures)
            } finally {
                localSignatures.forEach(LocalSignature::close)
                localSignatures.clear()
            }
            val response = when (completion) {
                is BatchProtocolCompletionResult.Failure -> return fail(operation, completion.code)
                is BatchProtocolCompletionResult.Success -> completion.response
            }
            operationValidationError(operation, identity)?.let { code ->
                response.close()
                return fail(operation, code)
            }
            if (!operation.claimFlow()) {
                response.close()
                return SigningExecutionResult.Failed(
                    operation.cancellationCode() ?: SigningErrorCode.RESULT_DELIVERY_FAILED,
                )
            }
            val delivered = try {
                operation.reply.success(response)
            } catch (_: Exception) {
                false
            } finally {
                response.close()
            }
            if (!delivered) {
                synchronized(this) {
                    if (active === operation && operation.cancellationCode() == null) {
                        mutableState.value = SigningUiState.Failed(
                            requestId,
                            SigningErrorCode.RESULT_DELIVERY_FAILED,
                        )
                    }
                }
                return SigningExecutionResult.Failed(SigningErrorCode.RESULT_DELIVERY_FAILED)
            }
            synchronized(this) {
                if (active === operation) {
                    mutableState.value = SigningUiState.Completed(requestId)
                }
            }
            return SigningExecutionResult.Delivered(requestId)
        } catch (cancellation: CancellationException) {
            fail(
                operation,
                operation.cancellationCode() ?: SigningErrorCode.NAVIGATION_CHANGED,
            )
            throw cancellation
        } catch (_: Exception) {
            return fail(operation, SigningErrorCode.PROTOCOL_FAILED)
        } finally {
            localSignatures.forEach(LocalSignature::close)
            preSign?.close()
            operation.clearSensitive()
            synchronized(this) {
                if (active === operation) active = null
            }
        }
    }

    fun cancel(
        reason: SigningCancelReason,
        requestId: UUID? = null,
    ): Boolean {
        val operation = synchronized(this) {
            val candidate = pending?.takeIf { requestId == null || it.request.requestId == requestId }
                ?: active?.takeIf { requestId == null || it.request.requestId == requestId }
                ?: return false
            if (!candidate.claimCancellation(reason.code)) return false
            if (pending === candidate) {
                pending = null
                candidate.clearSensitive()
            }
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

    private fun fail(
        operation: Operation,
        code: SigningErrorCode,
    ): SigningExecutionResult.Failed {
        if (operation.claimFlow()) {
            runCatching { operation.reply.failure(code) }
        }
        val cancellationCode = operation.cancellationCode()
        synchronized(this) {
            if (active === operation && cancellationCode == null) {
                mutableState.value = SigningUiState.Failed(operation.request.requestId, code)
            }
        }
        return SigningExecutionResult.Failed(cancellationCode ?: code)
    }

    private fun expirePending(requestId: UUID) {
        val operation = synchronized(this) {
            val candidate = pending?.takeIf { it.request.requestId == requestId } ?: return
            if (!candidate.claimFlow()) return
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

    @Synchronized
    private fun isActive(operation: Operation): Boolean =
        active === operation && operation.cancellationCode() == null

    private fun operationContextError(operation: Operation): SigningErrorCode? {
        operation.cancellationCode()?.let { return it }
        if (!isActive(operation)) return SigningErrorCode.NAVIGATION_CHANGED
        val nowNanos = try {
            monotonicNanos()
        } catch (_: Exception) {
            return SigningErrorCode.REQUEST_EXPIRED
        }
        if (MonotonicSecurityTime.isExpiredOrInvalid(
                operation.startedAtNanos,
                CONFIRMATION_LIFETIME_NANOS,
                nowNanos,
            )
        ) return SigningErrorCode.REQUEST_EXPIRED
        if (!originStillMatches(operation)) return SigningErrorCode.NAVIGATION_CHANGED
        return operation.cancellationCode()
    }

    private fun operationValidationError(
        operation: Operation,
        identity: UnlockedIdentity,
    ): SigningErrorCode? {
        operationContextError(operation)?.let { return it }
        if (certificateSession.identityForSigning(operation.certificateSnapshot) !== identity) {
            return SigningErrorCode.CERTIFICATE_LOCKED
        }
        return operation.cancellationCode()
    }

    private fun originStillMatches(operation: Operation): Boolean =
        currentOriginSafely() == operation.request.context.origin &&
            currentNavigationEpochSafely() == operation.request.context.navigationEpoch

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

    private class Operation(
        val request: NormalizedBatchSigningRequest,
        val certificateSnapshot: CertificateSigningSnapshot,
        val reply: BatchSigningReplySink,
        val startedAtNanos: Long,
    ) {
        private val terminalClaim = AtomicReference<TerminalClaim?>(null)
        private var expiryHandle: SigningExpiryHandle? = null
        private var cleared = false

        fun claimFlow(): Boolean = terminalClaim.compareAndSet(null, TerminalClaim.Flow)

        fun claimCancellation(code: SigningErrorCode): Boolean =
            terminalClaim.compareAndSet(null, TerminalClaim.Cancellation(code))

        fun cancellationCode(): SigningErrorCode? =
            (terminalClaim.get() as? TerminalClaim.Cancellation)?.code

        fun hasTerminalClaim(): Boolean = terminalClaim.get() != null

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
        fun cancelExpiry() {
            expiryHandle?.cancel()
            expiryHandle = null
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

    private sealed interface TerminalClaim {
        data object Flow : TerminalClaim

        data class Cancellation(val code: SigningErrorCode) : TerminalClaim
    }

    private companion object {
        val CONFIRMATION_LIFETIME: Duration = Duration.ofMinutes(2)
        val CONFIRMATION_LIFETIME_NANOS: Long =
            MonotonicSecurityTime.durationNanos(CONFIRMATION_LIFETIME)
    }
}

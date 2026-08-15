package dev.junta.firmamobile.signing

import dev.junta.firmamobile.browser.VeaMultiModeBridgeAdapter
import dev.junta.firmamobile.browser.VeaMultiModeBridgeRequest
import dev.junta.firmamobile.certificate.CertificateSession
import dev.junta.firmamobile.certificate.CertificateSigningSnapshot
import dev.junta.firmamobile.certificate.UnlockedIdentity
import dev.junta.firmamobile.network.TrustedOrigin
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SiteProfileRegistry
import dev.junta.firmamobile.security.MonotonicSecurityTime
import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VeaMultiModeSigningCoordinator(
    private val certificateSession: CertificateSession,
    private val adapter: VeaMultiModeSigningAdapter,
    private val currentOrigin: () -> TrustedOrigin?,
    private val currentNavigationEpoch: () -> Long = { 0L },
    private val currentPageUrl: () -> String? = { null },
    private val currentDocumentId: () -> UUID? = { null },
    private val monotonicNanos: () -> Long = MonotonicSecurityTime::nowNanos,
    private val expiryScheduler: SigningExpiryScheduler,
    private val profileRegistry: SiteProfileRegistry = BuiltInSiteProfiles.runtimeRegistry,
) : AutoCloseable {
    private val mutableState = MutableStateFlow<SigningUiState>(SigningUiState.Idle)
    val state: StateFlow<SigningUiState> = mutableState.asStateFlow()

    private var pending: Operation? = null
    private var active: Operation? = null

    @Synchronized
    fun prepare(
        request: VeaMultiModeBridgeRequest,
        reply: VeaMultiModeReplySink,
    ): SigningPreparationResult {
        val boundaryError = validateBoundary(request)
        if (boundaryError != null) return reject(request, reply, boundaryError)

        val profile = profileRegistry.profile(request.profileId)
            ?: return reject(request, reply, SigningErrorCode.PROFILE_NOT_ACTIVE)

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
            if (pending === operation) pending = null
            operation.clearSensitive()
            runCatching { reply.failure(SigningErrorCode.PROTOCOL_FAILED) }
            mutableState.value = SigningUiState.Failed(request.requestId, SigningErrorCode.PROTOCOL_FAILED)
            return SigningPreparationResult.Rejected(SigningErrorCode.PROTOCOL_FAILED)
        }

        val safeDescription = if (request.arrayLength == 1) {
            "Firma electrónica de 1 documento"
        } else {
            "Firma electrónica de ${request.arrayLength} documentos"
        }

        mutableState.value = SigningUiState.AwaitingConfirmation(
            requestId = request.requestId,
            siteHost = request.sourceOrigin.host,
            profileName = profile.displayName,
            supportLevel = profile.compatibilityStatus.name,
            safeDescription = safeDescription,
            format = request.format.uppercase(),
            algorithm = request.algorithm,
            certificateOwner = certificateSnapshot.summary.ownerName,
            requiresLegacySha1Warning = request.hashAlgorithm == PrecalculatedHashAlgorithm.SHA1 ||
                request.algorithm.contains("SHA1", ignoreCase = true),
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

        try {
            val boundaryError = validateBoundary(operation.request)
            if (boundaryError != null) {
                return fail(operation, boundaryError)
            }
            val identity = certificateSession.identityForSigning(operation.certificateSnapshot)
                ?: return fail(operation, SigningErrorCode.CERTIFICATE_LOCKED)

            val executed = adapter.execute(operation.request, identity, operation.reply)
            return if (executed) {
                synchronized(this) {
                    if (active === operation) active = null
                    operation.clearSensitive()
                    mutableState.value = SigningUiState.Idle
                }
                SigningExecutionResult.Success
            } else {
                fail(operation, SigningErrorCode.LOCAL_SIGNATURE_FAILED)
            }
        } catch (_: Exception) {
            return fail(operation, SigningErrorCode.PROTOCOL_FAILED)
        }
    }

    @Synchronized
    fun cancel(reason: SigningCancelReason, requestId: UUID? = null): Boolean {
        val candidate = pending?.takeIf { requestId == null || it.request.requestId == requestId }
            ?: active?.takeIf { requestId == null || it.request.requestId == requestId }
            ?: return false

        val targetRequestId = candidate.request.requestId
        candidate.cancelExpiry()
        if (pending === candidate) pending = null
        if (active === candidate) active = null
        candidate.clearSensitive()

        val errorCode = reason.signingErrorCode
        runCatching { candidate.reply.failure(errorCode) }

        if (reason.showsErrorUi) {
            mutableState.value = SigningUiState.Failed(targetRequestId, errorCode)
        } else {
            mutableState.value = SigningUiState.Idle
        }
        return true
    }

    @Synchronized
    fun dismissTerminalState() {
        if (mutableState.value is SigningUiState.Failed) {
            mutableState.value = SigningUiState.Idle
        }
    }

    override fun close() {
        cancel(SigningCancelReason.BACKGROUND)
    }

    private fun validateBoundary(request: VeaMultiModeBridgeRequest): SigningErrorCode? {
        val currentEpoch = currentNavigationEpoch()
        if (currentEpoch != request.navigationEpoch) return SigningErrorCode.NAVIGATION_CHANGED
        val curOrigin = currentOrigin()
        if (curOrigin == null || curOrigin != request.sourceOrigin) return SigningErrorCode.ORIGIN_NOT_ALLOWED
        val curDocId = currentDocumentId()
        if (curDocId == null || curDocId != request.documentId) return SigningErrorCode.NAVIGATION_CHANGED
        val curUrl = currentPageUrl() ?: return SigningErrorCode.NAVIGATION_CHANGED
        val canonicalCur = VeaMultiModeBridgeAdapter.canonicalizeVeaUrl(curUrl)
        val canonicalReq = VeaMultiModeBridgeAdapter.canonicalizeVeaUrl(request.pageUrl)
        if (canonicalCur == null || canonicalCur != canonicalReq) return SigningErrorCode.NAVIGATION_CHANGED
        return null
    }

    private fun reject(
        request: VeaMultiModeBridgeRequest,
        reply: VeaMultiModeReplySink,
        code: SigningErrorCode,
    ): SigningPreparationResult {
        runCatching { reply.failure(code) }
        mutableState.value = SigningUiState.Failed(request.requestId, code)
        return SigningPreparationResult.Rejected(code)
    }

    private fun fail(operation: Operation, code: SigningErrorCode): SigningExecutionResult {
        synchronized(this) {
            if (active === operation) active = null
            if (pending === operation) pending = null
            operation.clearSensitive()
            runCatching { operation.reply.failure(code) }
            mutableState.value = SigningUiState.Failed(operation.request.requestId, code)
        }
        return SigningExecutionResult.Failed(code)
    }

    private fun expirePending(requestId: UUID) {
        synchronized(this) {
            val candidate = pending?.takeIf { it.request.requestId == requestId } ?: return
            pending = null
            candidate.clearSensitive()
            runCatching { candidate.reply.failure(SigningErrorCode.REQUEST_EXPIRED) }
            mutableState.value = SigningUiState.Failed(requestId, SigningErrorCode.REQUEST_EXPIRED)
        }
    }

    private class Operation(
        val request: VeaMultiModeBridgeRequest,
        val certificateSnapshot: CertificateSigningSnapshot,
        val reply: VeaMultiModeReplySink,
        val startedAtNanos: Long,
    ) {
        private var expiryHandle: AutoCloseable? = null

        fun attachExpiry(handle: AutoCloseable) {
            expiryHandle = handle
        }

        fun cancelExpiry() {
            expiryHandle?.close()
            expiryHandle = null
        }

        fun clearSensitive() {
            cancelExpiry()
            certificateSnapshot.close()
        }
    }

    companion object {
        private val CONFIRMATION_LIFETIME_NANOS = MonotonicSecurityTime.durationNanos(Duration.ofMinutes(5))
    }
}

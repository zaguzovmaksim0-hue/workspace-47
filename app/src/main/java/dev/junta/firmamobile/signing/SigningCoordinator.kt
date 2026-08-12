package dev.junta.firmamobile.signing

import dev.junta.firmamobile.certificate.CertificateSession
import dev.junta.firmamobile.certificate.CertificateSigningSnapshot
import dev.junta.firmamobile.certificate.UnlockedIdentity
import dev.junta.firmamobile.network.ProfileHttpRoute
import dev.junta.firmamobile.network.TunnelRouteEvent
import dev.junta.firmamobile.network.TunnelRouteStage
import dev.junta.firmamobile.network.TrustedOrigin
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.ProtocolOperation
import dev.junta.firmamobile.profile.SignatureAlgorithm as ProfileSignatureAlgorithm
import dev.junta.firmamobile.profile.SignatureFormat as ProfileSignatureFormat
import dev.junta.firmamobile.security.MonotonicSecurityTime
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SigningUiState {
    data object Idle : SigningUiState

    data class AwaitingConfirmation(
        val requestId: UUID,
        val siteHost: String,
        val profileName: String,
        val supportLevel: String,
        val safeDescription: String,
        val format: String,
        val algorithm: String,
        val certificateOwner: String,
        val requiresLegacySha1Warning: Boolean,
    ) : SigningUiState

    data class Signing(val requestId: UUID) : SigningUiState

    data class ConnectingSecurely(val requestId: UUID) : SigningUiState

    data class Completed(val requestId: UUID) : SigningUiState

    data class Failed(
        val requestId: UUID?,
        val code: SigningErrorCode,
    ) : SigningUiState
}

sealed interface SigningPreparationResult {
    data class Ready(val requestId: UUID) : SigningPreparationResult

    data class Rejected(val code: SigningErrorCode) : SigningPreparationResult
}

sealed interface SigningExecutionResult {
    data class Delivered(val requestId: UUID) : SigningExecutionResult

    data class Failed(val code: SigningErrorCode) : SigningExecutionResult
}

enum class SigningCancelReason(
    internal val code: SigningErrorCode,
    internal val abandonReply: Boolean,
) {
    USER(SigningErrorCode.USER_CANCELLED, false),
    JAVASCRIPT(SigningErrorCode.USER_CANCELLED, true),
    NAVIGATION(SigningErrorCode.NAVIGATION_CHANGED, false),
    RELOAD(SigningErrorCode.NAVIGATION_CHANGED, false),
    CERTIFICATE_LOCKED(SigningErrorCode.CERTIFICATE_LOCKED, false),
    BACKGROUND(SigningErrorCode.CERTIFICATE_LOCKED, true),
}

class SigningCoordinator internal constructor(
    private val certificateSession: CertificateSession,
    adapter: SigningProtocolAdapter,
    private val localSignatureEngine: LocalSignatureEngine,
    private val currentOrigin: () -> TrustedOrigin?,
    private val currentNavigationEpoch: () -> Long = { 0L },
    private val clock: Clock = Clock.systemUTC(),
    private val monotonicNanos: () -> Long = MonotonicSecurityTime::nowNanos,
    private val pendingStore: PendingSignRequestStore = PendingSignRequestStore(
        clock = clock,
        monotonicNanos = monotonicNanos,
    ),
    private val expiryScheduler: SigningExpiryScheduler,
    private val adapterResolver: (SigningProtocolId) -> SigningProtocolAdapter? = { id ->
        adapter.takeIf { it.id == id }
    },
    private val profileRegistry: dev.junta.firmamobile.profile.SiteProfileRegistry =
        BuiltInSiteProfiles.runtimeRegistry,
    private val bindingRegistry: ProtocolAdapterRegistry = BuiltInProtocolAdapterRegistry.registry,
) : AutoCloseable {
    private val mutableState = MutableStateFlow<SigningUiState>(SigningUiState.Idle)
    val state: StateFlow<SigningUiState> = mutableState.asStateFlow()

    private var pending: Operation? = null
    private var active: Operation? = null

    @Synchronized
    fun prepare(
        request: NormalizedSignRequest,
        reply: SigningReplySink,
    ): SigningPreparationResult {
        val boundaryError = validateBoundary(request, reply)
        if (boundaryError != null) return reject(request, reply, boundaryError)
        val profile = profileRegistry.profile(ProfileId(request.context.profileId))
            ?: return reject(request, reply, SigningErrorCode.PROFILE_NOT_ACTIVE)
        val operationAdapter = adapterResolver(request.protocolId)
            ?: return reject(request, reply, SigningErrorCode.UNSUPPORTED_PROTOCOL)
        if (pending != null || active != null) {
            return reject(request, reply, SigningErrorCode.PROTOCOL_FAILED)
        }
        val certificateSnapshot = certificateSession.signingSnapshot()
            ?: return reject(request, reply, SigningErrorCode.CERTIFICATE_LOCKED)
        val payloadFingerprint = try {
            request.withPayload(::sha256)
        } catch (_: Exception) {
            certificateSnapshot.close()
            return reject(request, reply, SigningErrorCode.INVALID_REQUEST)
        }
        val summary = try {
            pendingStore.put(request)
        } catch (_: Exception) {
            payloadFingerprint.fill(0)
            certificateSnapshot.close()
            return reject(request, reply, SigningErrorCode.INVALID_REQUEST)
        }
        val operation = Operation(
            summary = summary,
            payloadFingerprint = payloadFingerprint,
            certificateSnapshot = certificateSnapshot,
            reply = reply,
            adapter = operationAdapter,
        )
        val expiryDelay = MonotonicSecurityTime.remaining(
            summary.observedAtMonotonicNanos,
            summary.lifetimeNanos,
            monotonicNanos(),
        )
        if (expiryDelay.isZero) {
            pendingStore.clear(ConsumeError.REQUEST_EXPIRED)
            operation.clearSensitive()
            return reject(request, reply, SigningErrorCode.REQUEST_EXPIRED)
        }
        pending = operation
        try {
            operation.attachExpiry(
                expiryScheduler.schedule(
                    delay = expiryDelay,
                    action = { expirePending(summary.requestId) },
                ),
            )
        } catch (_: Exception) {
            pending = null
            pendingStore.clear(ConsumeError.CANCELLED)
            operation.clearSensitive()
            return reject(request, reply, SigningErrorCode.PROTOCOL_FAILED)
        }
        mutableState.value = SigningUiState.AwaitingConfirmation(
            requestId = summary.requestId,
            siteHost = summary.context.origin.host,
            profileName = profile.displayName,
            supportLevel = profile.compatibilityStatus.name,
            safeDescription = summary.safeDescription,
            format = summary.format.displayName(),
            algorithm = summary.algorithm.displayName(),
            certificateOwner = certificateSnapshot.summary.ownerName,
            requiresLegacySha1Warning = summary.algorithm == SigningAlgorithm.SHA1_WITH_RSA,
        )
        return SigningPreparationResult.Ready(summary.requestId)
    }

    suspend fun confirm(requestId: UUID): SigningExecutionResult {
        val operation = synchronized(this) {
            val candidate = pending
            if (candidate == null || candidate.summary.requestId != requestId) return@synchronized null
            candidate.cancelExpiry()
            pending = null
            active = candidate
            mutableState.value = SigningUiState.Signing(requestId)
            candidate
        } ?: return SigningExecutionResult.Failed(SigningErrorCode.INVALID_REQUEST)

        var acceptedRequest: NormalizedSignRequest? = null
        try {
            if (!originStillMatches(operation)) {
                return fail(operation, SigningErrorCode.ORIGIN_NOT_ALLOWED)
            }
            val consumed = pendingStore.consume(
                PendingValidationContext(
                    requestId = operation.summary.requestId,
                    profileId = operation.summary.context.profileId,
                    profileVersion = operation.summary.context.profileVersion,
                    origin = operation.summary.context.origin,
                    navigationId = operation.summary.context.navigationId,
                    payloadFingerprint = operation.payloadFingerprint,
                ),
            )
            if (consumed is PendingConsumeResult.Rejected) {
                return fail(operation, consumed.error.toSigningError())
            }
            val request = (consumed as PendingConsumeResult.Accepted).request
            acceptedRequest = request
            if (!isActive(operation) || !originStillMatches(operation)) {
                return fail(operation, SigningErrorCode.NAVIGATION_CHANGED)
            }
            val identity = certificateSession.identityForSigning(operation.certificateSnapshot)
                ?: return fail(operation, SigningErrorCode.CERTIFICATE_LOCKED)
            val preSign = when (val prepared = operation.adapter.prepare(request, identity.chain)) {
                is ProtocolPrepareResult.Failure -> return fail(operation, prepared.code)
                is ProtocolPrepareResult.Success -> prepared.preSign
            }
            val completion = preSign.use { ownedPreSign ->
                operationValidationError(operation, identity)?.let { code ->
                    return fail(operation, code)
                }
                val localSignature = when (
                    val local = ownedPreSign.withBytesToSign { bytes ->
                        localSignatureEngine.sign(bytes, identity, request.algorithm)
                    }
                ) {
                    is LocalSignatureResult.Failure -> {
                        return fail(operation, SigningErrorCode.LOCAL_SIGNATURE_FAILED)
                    }
                    is LocalSignatureResult.Success -> local.signature
                }
                try {
                    operationValidationError(operation, identity)?.let { code ->
                        return fail(operation, code)
                    }
                    operation.adapter.complete(request, ownedPreSign, localSignature)
                } finally {
                    localSignature.close()
                }
            }
            val finalSignature = when (completion) {
                is ProtocolCompletionResult.Failure -> return fail(operation, completion.code)
                is ProtocolCompletionResult.Success -> completion.signature
            }
            val finalValidationError = operationValidationError(operation, identity)
            if (finalValidationError != null) {
                finalSignature.close()
                return fail(operation, finalValidationError)
            }
            val certificateDer = try {
                identity.certificate.encoded
            } catch (_: Exception) {
                finalSignature.close()
                return fail(operation, SigningErrorCode.RESULT_DELIVERY_FAILED)
            }
            if (!operation.claimFlow()) {
                finalSignature.close()
                certificateDer.fill(0)
                return SigningExecutionResult.Failed(
                    operation.cancellationCode() ?: SigningErrorCode.RESULT_DELIVERY_FAILED,
                )
            }
            val delivered = try {
                operation.reply.success(finalSignature, certificateDer)
            } catch (_: Exception) {
                false
            } finally {
                finalSignature.close()
                certificateDer.fill(0)
            }
            if (!delivered) {
                synchronized(this) {
                    if (active === operation && operation.cancellationCode() == null) {
                        mutableState.value = SigningUiState.Failed(
                            operation.summary.requestId,
                            SigningErrorCode.RESULT_DELIVERY_FAILED,
                        )
                    }
                }
                return SigningExecutionResult.Failed(SigningErrorCode.RESULT_DELIVERY_FAILED)
            }
            synchronized(this) {
                if (active === operation) {
                    active = null
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
            acceptedRequest?.close()
            pendingStore.clear(ConsumeError.CANCELLED)
            operation.clearSensitive()
            synchronized(this) {
                if (active === operation) active = null
            }
        }
    }

    @Synchronized
    internal fun onTunnelRouteEvent(requestId: UUID, event: TunnelRouteEvent): Boolean {
        val operation = active ?: return false
        if (operation.summary.requestId != requestId || operation.cancellationCode() != null) {
            return false
        }

        if (event.route == ProfileHttpRoute.SECURE_TUNNEL) {
            mutableState.value = when (event.stage) {
                TunnelRouteStage.TUNNEL_CONNECTING -> when (val current = mutableState.value) {
                    is SigningUiState.Signing -> if (current.requestId == requestId) {
                        SigningUiState.ConnectingSecurely(requestId)
                    } else {
                        current
                    }
                    else -> current
                }
                TunnelRouteStage.TUNNEL_ESTABLISHED,
                TunnelRouteStage.TUNNEL_FAILED,
                -> when (val current = mutableState.value) {
                    is SigningUiState.ConnectingSecurely -> if (current.requestId == requestId) {
                        SigningUiState.Signing(requestId)
                    } else {
                        current
                    }
                    else -> current
                }
                TunnelRouteStage.DIRECT_FAILED_PRE_HTTP -> mutableState.value
            }
        }
        return true
    }

    fun cancel(
        reason: SigningCancelReason,
        requestId: UUID? = null,
    ): Boolean {
        val operation = synchronized(this) {
            val candidate = pending?.takeIf { requestId == null || it.summary.requestId == requestId }
                ?: active?.takeIf { requestId == null || it.summary.requestId == requestId }
                ?: return false
            if (!candidate.claimCancellation(reason.code)) return false
            if (pending === candidate) {
                pending = null
                pendingStore.clear(ConsumeError.CANCELLED)
                candidate.clearSensitive()
            }
            mutableState.value = SigningUiState.Idle
            candidate
        }
        if (reason.abandonReply) {
            operation.reply.abandon()
        } else {
            operation.reply.failure(reason.code)
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
            pendingStore.clear(ConsumeError.CANCELLED)
            pending?.clearSensitive()
            pending = null
            mutableState.value = SigningUiState.Idle
        }
    }

    private fun validateBoundary(
        request: NormalizedSignRequest,
        reply: SigningReplySink,
    ): SigningErrorCode? {
        if (reply.requestId != request.requestId) return SigningErrorCode.INVALID_REQUEST
        val profileId = runCatching { ProfileId(request.context.profileId) }.getOrNull()
            ?: return SigningErrorCode.PROFILE_NOT_ACTIVE
        val profile = profileRegistry.profile(profileId)
            ?: return SigningErrorCode.PROFILE_NOT_ACTIVE
        if (request.context.profileVersion != profile.profileVersion) return SigningErrorCode.PROFILE_NOT_ACTIVE
        if (profile.initiatorOrigins.none { it.toTrustedOrigin() == request.context.origin }) {
            return SigningErrorCode.ORIGIN_NOT_ALLOWED
        }
        val operation = profile.operationPolicies[ProtocolOperation.SIGN]
            ?: return SigningErrorCode.UNSUPPORTED_PROTOCOL
        val binding = bindingRegistry.resolve(profileId, ProtocolOperation.SIGN)
            ?: return SigningErrorCode.UNSUPPORTED_PROTOCOL
        if (binding.signingProtocolId != request.protocolId ||
            adapterResolver(request.protocolId)?.id != request.protocolId
        ) return SigningErrorCode.UNSUPPORTED_PROTOCOL
        val expectedAlgorithm = when (request.algorithm) {
            SigningAlgorithm.SHA1_WITH_RSA -> ProfileSignatureAlgorithm.SHA1_WITH_RSA
            SigningAlgorithm.SHA256_WITH_RSA -> ProfileSignatureAlgorithm.SHA256_WITH_RSA
            SigningAlgorithm.SHA512_WITH_RSA -> ProfileSignatureAlgorithm.SHA512_WITH_RSA
        }
        val expectedFormat = when (request.format) {
            SigningFormat.CADES -> ProfileSignatureFormat.CADES
            SigningFormat.XADES -> ProfileSignatureFormat.XADES
        }
        if (expectedAlgorithm !in operation.algorithms || expectedFormat != operation.format) {
            return SigningErrorCode.UNSUPPORTED_PROTOCOL
        }
        if (MonotonicSecurityTime.isExpiredOrInvalid(
                request.observedAtMonotonicNanos,
                REQUEST_LIFETIME_NANOS,
                monotonicNanos(),
            )
        ) return SigningErrorCode.REQUEST_EXPIRED
        return if (currentOriginSafely() == request.context.origin &&
            currentNavigationEpochSafely() == request.context.navigationEpoch
        ) {
            null
        } else {
            SigningErrorCode.ORIGIN_NOT_ALLOWED
        }
    }

    private fun reject(
        request: NormalizedSignRequest,
        reply: SigningReplySink,
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
        pendingStore.clear(ConsumeError.CANCELLED)
        if (operation.claimFlow()) {
            runCatching { operation.reply.failure(code) }
        }
        val cancellationCode = operation.cancellationCode()
        synchronized(this) {
            if (active === operation && cancellationCode == null) {
                mutableState.value = SigningUiState.Failed(operation.summary.requestId, code)
            }
        }
        return SigningExecutionResult.Failed(cancellationCode ?: code)
    }

    private fun expirePending(requestId: UUID) {
        val operation = synchronized(this) {
            val candidate = pending?.takeIf { it.summary.requestId == requestId } ?: return
            if (!candidate.claimFlow()) return
            pending = null
            pendingStore.clear(ConsumeError.REQUEST_EXPIRED)
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

    private fun operationValidationError(
        operation: Operation,
        identity: UnlockedIdentity,
    ): SigningErrorCode? {
        operation.cancellationCode()?.let { return it }
        if (!isActive(operation)) return SigningErrorCode.NAVIGATION_CHANGED
        if (MonotonicSecurityTime.isExpiredOrInvalid(
                operation.summary.observedAtMonotonicNanos,
                operation.summary.lifetimeNanos,
                monotonicNanos(),
            )
        ) return SigningErrorCode.REQUEST_EXPIRED
        if (!originStillMatches(operation)) return SigningErrorCode.NAVIGATION_CHANGED
        if (certificateSession.identityForSigning(operation.certificateSnapshot) !== identity) {
            return SigningErrorCode.CERTIFICATE_LOCKED
        }
        return operation.cancellationCode()
    }

    private fun originStillMatches(operation: Operation): Boolean =
        currentOriginSafely() == operation.summary.context.origin &&
            currentNavigationEpochSafely() == operation.summary.context.navigationEpoch

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

    private fun sha256(payload: ByteArray): ByteArray =
        MessageDigest.getInstance(SHA_256).digest(payload)

    private fun ConsumeError.toSigningError(): SigningErrorCode = when (this) {
        ConsumeError.REQUEST_EXPIRED -> SigningErrorCode.REQUEST_EXPIRED
        ConsumeError.PROFILE_CHANGED -> SigningErrorCode.PROFILE_NOT_ACTIVE
        ConsumeError.ORIGIN_CHANGED -> SigningErrorCode.ORIGIN_NOT_ALLOWED
        ConsumeError.NAVIGATION_CHANGED -> SigningErrorCode.NAVIGATION_CHANGED
        ConsumeError.PAYLOAD_CHANGED -> SigningErrorCode.PAYLOAD_CHANGED
        ConsumeError.NOT_FOUND,
        ConsumeError.ALREADY_CONSUMED,
        ConsumeError.CANCELLED,
        -> SigningErrorCode.INVALID_REQUEST
    }

    private fun SigningFormat.displayName(): String = when (this) {
        SigningFormat.CADES -> "CAdES"
        SigningFormat.XADES -> "XAdES Detached"
    }

    private fun SigningAlgorithm.displayName(): String = when (this) {
        SigningAlgorithm.SHA1_WITH_RSA -> "SHA1withRSA"
        SigningAlgorithm.SHA256_WITH_RSA -> "SHA256withRSA"
        SigningAlgorithm.SHA512_WITH_RSA -> "SHA512withRSA"
    }

    private class Operation(
        val summary: PendingSignSummary,
        val payloadFingerprint: ByteArray,
        val certificateSnapshot: CertificateSigningSnapshot,
        val reply: SigningReplySink,
        val adapter: SigningProtocolAdapter,
    ) {
        private val terminalClaim = AtomicReference<TerminalClaim?>(null)
        private var expiryHandle: SigningExpiryHandle? = null
        private var cleared = false

        fun claimFlow(): Boolean = terminalClaim.compareAndSet(null, TerminalClaim.Flow)

        fun claimCancellation(code: SigningErrorCode): Boolean =
            terminalClaim.compareAndSet(null, TerminalClaim.Cancellation(code))

        fun cancellationCode(): SigningErrorCode? =
            (terminalClaim.get() as? TerminalClaim.Cancellation)?.code

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
            payloadFingerprint.fill(0)
            certificateSnapshot.close()
        }
    }

    private sealed interface TerminalClaim {
        data object Flow : TerminalClaim

        data class Cancellation(val code: SigningErrorCode) : TerminalClaim
    }

    private companion object {
        const val SHA_256 = "SHA-256"
        val REQUEST_LIFETIME: Duration = Duration.ofMinutes(2)
        val REQUEST_LIFETIME_NANOS: Long = MonotonicSecurityTime.durationNanos(REQUEST_LIFETIME)
    }
}

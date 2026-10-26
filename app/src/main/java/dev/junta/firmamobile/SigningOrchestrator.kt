package dev.junta.firmamobile

import dev.junta.firmamobile.browser.BatchBridgeSigningRoutes
import dev.junta.firmamobile.browser.MelillaBatchBridgeRequest
import dev.junta.firmamobile.browser.MelillaBatchReplyChannel
import dev.junta.firmamobile.browser.MiniAppletBridgeRequest
import dev.junta.firmamobile.network.TunnelRouteEvent
import dev.junta.firmamobile.signing.BatchSigningCoordinator
import dev.junta.firmamobile.signing.BatchSigningReplySink
import dev.junta.firmamobile.signing.SigningCancelReason
import dev.junta.firmamobile.signing.SigningCoordinator
import dev.junta.firmamobile.signing.NormalizedBatchSigningRequest
import dev.junta.firmamobile.signing.SigningErrorCode
import dev.junta.firmamobile.signing.SigningPreparationResult
import dev.junta.firmamobile.signing.SigningReplySink
import dev.junta.firmamobile.signing.SigningUiState
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * One owner for both signing flows of a single Activity host. It is deliberately not a retained
 * ViewModel: signing must not survive the Activity, so the host creates it with its own scope and
 * closes it on destroy.
 */
internal class SigningOrchestrator(
    private val signingCoordinator: SigningCoordinator,
    private val batchSigningCoordinator: BatchSigningCoordinator,
    private val batchBridgeRoutes: BatchBridgeSigningRoutes,
    private val scope: CoroutineScope,
    private val externalAfirmaActive: () -> Boolean,
    private val recordMiniAppletStage: (MiniAppletBridgeRequest, String, SigningErrorCode?) -> Unit,
) : AutoCloseable {
    private val signingFlowOwnership = SigningFlowOwnershipGate()
    private val signingJobs = SigningJobRegistry()
    private var closed = false

    val ordinaryState: StateFlow<SigningUiState> get() = signingCoordinator.state
    val batchState: StateFlow<SigningUiState> get() = batchSigningCoordinator.state

    fun hasOwner(): Boolean = signingFlowOwnership.current() != null

    /** Callers pass both collected values so composition stays subscribed to both flows from Idle. */
    fun visibleState(ordinary: SigningUiState, batch: SigningUiState): SigningUiState =
        when (signingFlowOwnership.current()?.kind) {
            SigningFlowKind.BATCH -> batch
            SigningFlowKind.ORDINARY -> ordinary
            null -> SigningUiState.Idle
        }

    fun prepareMiniAppletSigning(
        request: MiniAppletBridgeRequest,
        reply: SigningReplySink,
    ) {
        val requestId = request.normalized.requestId
        if (externalAfirmaActive() || !signingFlowOwnership.acquire(SigningFlowKind.ORDINARY, requestId)) {
            recordMiniAppletStage(request, "OWNERSHIP_REJECTED", SigningErrorCode.PROTOCOL_FAILED)
            request.normalized.close()
            runCatching { reply.failure(SigningErrorCode.PROTOCOL_FAILED) }
            return
        }
        val result = signingCoordinator.prepare(request.normalized, reply)
        when (result) {
            is SigningPreparationResult.Ready -> recordMiniAppletStage(request, "PREPARE_READY", null)
            is SigningPreparationResult.Rejected -> recordMiniAppletStage(request, "PREPARE_REJECTED", result.code)
        }
        if (result is SigningPreparationResult.Rejected && signingCoordinator.state.value is SigningUiState.Idle) {
            signingFlowOwnership.release(SigningFlowKind.ORDINARY, requestId)
        }
    }

    fun prepareMelillaBatchSigning(
        request: MelillaBatchBridgeRequest,
        reply: MelillaBatchReplyChannel,
    ) {
        val routed = batchBridgeRoutes.dispatch(request, reply)
        val normalized = routed?.normalized
        when {
            routed == null -> runCatching { reply.failure(SigningErrorCode.INVALID_REQUEST) }
            normalized == null -> runCatching { routed.replySink.failure(SigningErrorCode.INVALID_REQUEST) }
            else -> prepareBatch(normalized, routed.replySink)
        }
    }

    private fun prepareBatch(
        normalized: NormalizedBatchSigningRequest,
        replySink: BatchSigningReplySink,
    ) {
        val requestId = normalized.requestId
        if (externalAfirmaActive() || !signingFlowOwnership.acquire(SigningFlowKind.BATCH, requestId)) {
            normalized.close()
            runCatching { replySink.failure(SigningErrorCode.PROTOCOL_FAILED) }
            return
        }
        val result = batchSigningCoordinator.prepare(normalized, replySink)
        if (result is SigningPreparationResult.Rejected && batchSigningCoordinator.state.value is SigningUiState.Idle) {
            signingFlowOwnership.release(SigningFlowKind.BATCH, requestId)
        }
    }

    fun confirmSigning(requestId: UUID) {
        val owner = signingFlowOwnership.current()?.takeIf { it.requestId == requestId } ?: return
        val awaiting = when (owner.kind) {
            SigningFlowKind.ORDINARY -> signingCoordinator.state.value
            SigningFlowKind.BATCH -> batchSigningCoordinator.state.value
        } as? SigningUiState.AwaitingConfirmation
        if (awaiting?.requestId != requestId) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            when (owner.kind) {
                SigningFlowKind.ORDINARY -> signingCoordinator.confirm(requestId)
                SigningFlowKind.BATCH -> batchSigningCoordinator.confirm(requestId)
            }
        }
        if (signingJobs.register(requestId, job)) {
            job.start()
        } else {
            job.cancel()
        }
    }

    fun cancelSigning(
        reason: SigningCancelReason,
        requestId: UUID? = null,
    ) {
        val owner = signingFlowOwnership.current() ?: return
        if (requestId != null && owner.requestId != requestId) return
        val accepted = when (owner.kind) {
            SigningFlowKind.ORDINARY -> signingCoordinator.cancel(reason, requestId)
            SigningFlowKind.BATCH -> batchSigningCoordinator.cancel(reason, requestId)
        }
        val cancellationJob = signingJobs.takeForCancellation(requestId, accepted)
        if (accepted && cancellationJob != null) {
            cancellationJob.invokeOnCompletion {
                signingFlowOwnership.release(owner.kind, owner.requestId)
            }
            cancellationJob.cancel()
        } else if (accepted) {
            signingFlowOwnership.release(owner.kind, owner.requestId)
        }
    }

    fun dismissSigningState() {
        val owner = signingFlowOwnership.current() ?: return
        when (owner.kind) {
            SigningFlowKind.ORDINARY -> signingCoordinator.dismissTerminalState()
            SigningFlowKind.BATCH -> batchSigningCoordinator.dismissTerminalState()
        }
        val state = when (owner.kind) {
            SigningFlowKind.ORDINARY -> signingCoordinator.state.value
            SigningFlowKind.BATCH -> batchSigningCoordinator.state.value
        }
        if (state is SigningUiState.Idle) {
            signingFlowOwnership.release(owner.kind, owner.requestId)
        }
    }

    /** True only when the active ordinary request accepted the event; callers log only then. */
    fun onTunnelRouteEvent(requestId: UUID, event: TunnelRouteEvent): Boolean =
        signingCoordinator.onTunnelRouteEvent(requestId, event)

    override fun close() {
        if (closed) return
        cancelSigning(SigningCancelReason.BACKGROUND)
        closed = true
        signingCoordinator.close()
        batchSigningCoordinator.close()
    }
}

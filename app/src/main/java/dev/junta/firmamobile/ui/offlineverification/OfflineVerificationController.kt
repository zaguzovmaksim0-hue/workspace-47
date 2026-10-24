package dev.junta.firmamobile.ui.offlineverification

import dev.junta.firmamobile.verification.OfflineCmsResult
import dev.junta.firmamobile.verification.OfflineCmsVerifier
import java.io.Closeable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Ephemeral dialog owner. All entrypoints are confined to the UI/scope thread. */
internal class OfflineVerificationController(
    private val scope: CoroutineScope,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val verifyCms: (ByteArray, ByteArray?) -> OfflineCmsResult = OfflineCmsVerifier::verify,
) : Closeable {
    private val mutableState = MutableStateFlow(OfflineVerificationState())
    val state: StateFlow<OfflineVerificationState> = mutableState.asStateFlow()
    private var cms: OfflineDocumentSource? = null
    private var original: OfflineDocumentSource? = null
    private var operation: Job? = null
    private var generation = 0L
    private var closed = false

    fun beginSelection(kind: OfflineInputKind): OfflineSelection? {
        if (closed) return null
        invalidate()
        when (kind) {
            OfflineInputKind.CMS -> cms = null
            OfflineInputKind.ORIGINAL -> original = null
        }
        mutableState.value = OfflineVerificationState(cms != null, original != null, picking = kind)
        return OfflineSelection(generation, kind)
    }

    fun completeSelection(token: OfflineSelection, source: OfflineDocumentSource?) {
        if (!ownsSelection(token)) return
        when (token.kind) {
            OfflineInputKind.CMS -> cms = source
            OfflineInputKind.ORIGINAL -> original = source
        }
        mutableState.value = OfflineVerificationState(cms != null, original != null)
    }

    fun selectionFailed(token: OfflineSelection) {
        if (!ownsSelection(token)) return
        completeSelection(token, null)
        mutableState.value = state.value.copy(outcome = OfflineVerificationOutcome.READ_FAILED)
    }

    fun clearOriginal() {
        if (closed) return
        invalidate()
        original = null
        mutableState.value = OfflineVerificationState(cmsSelected = cms != null)
    }

    fun verify() {
        if (closed || !state.value.canVerify) return
        val cmsSource = cms ?: return
        val originalSource = original
        invalidate()
        val ticket = generation
        mutableState.value = state.value.copy(busy = true, outcome = null, verifiedSigners = 0)
        operation = scope.launch {
            val result = withContext(workerDispatcher) {
                OfflineVerificationWorker.verify(cmsSource, originalSource, verifyCms)
            }
            if (!closed && ticket == generation) {
                mutableState.value = state.value.copy(
                    busy = false,
                    outcome = result.outcome,
                    verifiedSigners = result.verifiedSigners,
                )
            }
        }
    }

    fun cancel() {
        if (closed) return
        invalidate()
        cms = null
        original = null
        mutableState.value = OfflineVerificationState()
    }

    override fun close() {
        cancel()
        closed = true
    }

    private fun invalidate() {
        generation++
        operation?.cancel()
        operation = null
    }

    private fun ownsSelection(token: OfflineSelection): Boolean =
        !closed && token.generation == generation && state.value.picking == token.kind
}

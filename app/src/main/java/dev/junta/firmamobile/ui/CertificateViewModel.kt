package dev.junta.firmamobile.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.junta.firmamobile.certificate.CertificateEntryChoice
import dev.junta.firmamobile.certificate.CertificateErrorCode
import dev.junta.firmamobile.certificate.CertificateGateway
import dev.junta.firmamobile.certificate.CertificateLoadResult
import dev.junta.firmamobile.certificate.CertificateSelectionErrorCode
import dev.junta.firmamobile.certificate.CertificateSelectionResult
import dev.junta.firmamobile.certificate.CertificateSession
import dev.junta.firmamobile.certificate.CertificateUnlockRetention
import dev.junta.firmamobile.certificate.CertificateUnlockCache
import dev.junta.firmamobile.certificate.NoOpCertificateUnlockCache
import dev.junta.firmamobile.certificate.StoredCertificateEntry
import dev.junta.firmamobile.certificate.StoredCertificateReference
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CertificateViewModel(
    private val gateway: CertificateGateway,
    private val session: CertificateSession,
    private val unlockCache: CertificateUnlockCache = NoOpCertificateUnlockCache,
    private val clock: Clock = Clock.systemUTC(),
    private val unlockDuration: Duration = CertificateUnlockRetention.FIFTEEN_MINUTES.duration,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow<CertificateUiState>(
        CertificateUiState.LoadingReference,
    )
    val state: StateFlow<CertificateUiState> = mutableState.asStateFlow()

    private val mutableSaved = MutableStateFlow(SavedCertificatesUiState())
    val savedCertificates: StateFlow<SavedCertificatesUiState> = mutableSaved.asStateFlow()

    private var operationJob: Job? = null
    private var revocationJob: Job? = null
    private var restoreSuppressed = false
    private var pendingChoice: PendingChoice? = null
    private var choiceExpiryJob: Job? = null

    private class PendingChoice(val password: CharArray, val duration: Duration)

    private suspend fun clearCache() {
        revocationJob?.join()
        withContext(ioDispatcher) { unlockCache.clear() }
    }

    /** Verified revocation before the stored selection changes. An unverified saved unlock could otherwise
     * restore without a password once the same file is chosen again, so the change is refused instead. */
    private suspend fun revokeCacheBeforeSelectionChange(): Boolean {
        revocationJob?.join()
        return withContext(ioDispatcher) { runCatching { unlockCache.clearAndReport() }.getOrDefault(false) }
    }

    init {
        require(!unlockDuration.isNegative && !unlockDuration.isZero)
        require(unlockDuration <= MAX_PERSISTED_UNLOCK_DURATION)
        operationJob = viewModelScope.launch {
            mutableState.value = loadReferenceAndRestore()
            refreshSavedCertificates()
        }
    }

    fun prepareForCertificateSelection() {
        lock()
    }

    fun onCertificateSelected(uri: Uri) = changeSelection { gateway.select(uri) }

    /** Switches to a saved entry through the same verified revocation as choosing a new file. */
    fun switchToSavedCertificate(entry: StoredCertificateEntry) =
        changeSelection(entry.takeIf { it.signingKey != null }) { gateway.activate(entry) }

    private fun changeSelection(
        key: StoredCertificateEntry? = null,
        change: suspend () -> CertificateSelectionResult,
    ) {
        cancelCurrentOperation()
        restoreSuppressed = true
        unlockCache.invalidate()
        session.forget()
        val previous = mutableState.value.referenceOrNull()
        mutableState.value = CertificateUiState.LoadingReference
        operationJob = viewModelScope.launch {
            mutableState.value = try {
                if (!revokeCacheBeforeSelectionChange()) {
                    previous.keptAfterRefusedChange()
                } else if (key != null) {
                    if (gateway.activateKey(key)) CertificateUiState.NoCertificate()
                    else previous.keptAfterRefusedChange()
                } else when (val result = change()) {
                    is CertificateSelectionResult.Success -> result.reference.toLockedState()
                    is CertificateSelectionResult.Failure -> {
                        val error = result.code.toUiError()
                        previous?.toLockedState(error)
                            ?: CertificateUiState.NoCertificate(error)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                previous?.toLockedState(CertificateUiError.STORAGE_FAILURE)
                    ?: CertificateUiState.NoCertificate(CertificateUiError.STORAGE_FAILURE)
            }
            refreshSavedCertificates()
        }
    }

    fun addSavedCertificate(uri: Uri) = changeSavedCertificates {
        when (val result = gateway.saveInactive(uri)) {
            is CertificateSelectionResult.Success -> null
            is CertificateSelectionResult.Failure -> result.code.toUiError()
        }
    }

    fun removeSavedCertificate(entry: StoredCertificateEntry) = changeSavedCertificates {
        if (entry.signingKey != null && gateway.currentKeyEntry() == entry) {
            forgetSelection { gateway.forget(entry) }
        } else {
            gateway.removeInactive(entry)
        }
        null
    }

    // Inactive edits leave signing alone; removing the selected key uses forgetSelection and revokes its unlock.
    private fun changeSavedCertificates(change: suspend () -> CertificateUiError?) {
        viewModelScope.launch {
            val error = try {
                change()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                CertificateUiError.STORAGE_FAILURE
            }
            refreshSavedCertificates(error)
        }
    }

    private suspend fun refreshSavedCertificates(error: CertificateUiError? = null) {
        val saved = try {
            SavedCertificatesUiState(
                gateway.savedEntries(), error, gateway.hasPendingKeyDeletion(), gateway.currentKeyEntry(),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            mutableSaved.value = mutableSaved.value.copy(error = CertificateUiError.STORAGE_FAILURE)
            return
        }
        mutableSaved.value = saved
    }

    fun unlock(password: CharArray) = unlockForDuration(password, unlockDuration)

    fun unlock(password: CharArray, retention: CertificateUnlockRetention) =
        unlockForDuration(password, retention.duration)

    /** Unlocks [choice] with the password typed before the list was shown; that copy is then cleared. */
    fun chooseCertificate(choice: CertificateEntryChoice) {
        val pending = pendingChoice
        val locked = mutableState.value as? CertificateUiState.Locked
        if (pending == null || locked == null || choice !in locked.choices) {
            cancelCertificateChoice()
            return
        }
        pendingChoice = null
        choiceExpiryJob?.cancel()
        unlockForDuration(pending.password, pending.duration) { gateway.unlockChosen(it, choice.certificateSha256) }
    }

    fun cancelCertificateChoice() = dropPendingChoice()

    private fun dropPendingChoice() {
        choiceExpiryJob?.cancel()
        choiceExpiryJob = null
        pendingChoice?.password?.fill('\u0000')
        pendingChoice = null
        val locked = mutableState.value as? CertificateUiState.Locked
        if (locked != null && locked.choices.isNotEmpty()) mutableState.value = locked.copy(choices = emptyList())
    }

    private suspend fun offerChoices(
        password: CharArray,
        duration: Duration,
        locked: CertificateUiState.Locked,
    ): Boolean {
        val choices = gateway.certificateChoices(password)
        currentCoroutineContext().ensureActive()
        if (choices.size < 2) return false
        pendingChoice = PendingChoice(password.copyOf(), duration)
        mutableState.value = locked.copy(error = null, choices = choices)
        choiceExpiryJob = viewModelScope.launch {
            delay(CHOICE_LIFETIME_MILLIS)
            dropPendingChoice()
        }
        return true
    }

    private fun unlockForDuration(
        password: CharArray,
        duration: Duration,
        load: suspend (CharArray) -> CertificateLoadResult = gateway::unlock,
    ) {
        val current = mutableState.value as? CertificateUiState.Locked
        if (current == null || current.revocationPending || revocationJob?.isActive == true) {
            password.fill('\u0000')
            return
        }
        val locked = current.copy(choices = emptyList())
        cancelCurrentOperation()
        mutableState.value = CertificateUiState.Unlocking(locked.reference, locked.summary)
        val unlockJob = viewModelScope.launch {
            try {
                when (val result = load(password)) {
                    is CertificateLoadResult.Success -> {
                        ensureActive()
                        val issuedAt = clock.instant()
                        val expiresAt = issuedAt.plus(duration)
                        val lease = session.createUnlockLease(expiresAt, duration)
                        val persisted = unlockCache.store(
                            reference = locked.reference,
                            password = password,
                            issuedAt = issuedAt,
                            expiresAt = expiresAt,
                            observedAtMonotonicNanos = lease.observedAtMonotonicNanos,
                        )
                        ensureActive()
                        if (!persisted && !clearFailedPersistence(locked)) return@launch
                        ensureActive()
                        session.unlock(result.identity, lease)
                        restoreSuppressed = false
                        mutableState.value = CertificateUiState.Unlocked(
                            reference = locked.reference,
                            summary = result.identity.summary,
                        )
                    }
                    is CertificateLoadResult.Failure -> {
                        clearCache()
                        session.lock()
                        val chooser = result.code == CertificateErrorCode.MULTIPLE_PRIVATE_KEYS &&
                            offerChoices(password, duration, locked)
                        if (!chooser) mutableState.value = locked.copy(error = result.code.toUiError())
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                clearCache()
                session.lock()
                mutableState.value = locked.copy(error = CertificateUiError.STORAGE_FAILURE)
            } finally {
                password.fill('\u0000')
            }
        }
        unlockJob.invokeOnCompletion { password.fill('\u0000') }
        operationJob = unlockJob
    }

    private suspend fun clearFailedPersistence(locked: CertificateUiState.Locked): Boolean {
        if (withContext(ioDispatcher) { unlockCache.clearAndReport() }) return true
        session.lock()
        restoreSuppressed = true
        mutableState.value = locked.copy(error = CertificateUiError.STORAGE_FAILURE, revocationPending = true)
        return false
    }

    fun lock() {
        cancelCurrentOperation()
        restoreSuppressed = true
        session.lock()
        unlockCache.invalidate()
        val current = mutableState.value
        val reference = current.referenceOrNull()
        if (reference != null) {
            mutableState.value = reference.toLockedState(current.summaryOrNull()).copy(revocationPending = true)
        }
        val previous = revocationJob
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            previous?.join()
            val revoked = withContext(NonCancellable + ioDispatcher) {
                runCatching { unlockCache.clearAndReport() }.getOrDefault(false)
            }
            val latest = mutableState.value as? CertificateUiState.Locked
            if (revocationJob === currentCoroutineContext()[Job] && latest != null && latest.reference == reference) {
                mutableState.value = latest.copy(revocationPending = false,
                    error = if (revoked) null else CertificateUiError.STORAGE_FAILURE)
            }
        }
        revocationJob = job
        job.start()
    }

    fun onAppForegrounded() {
        if (restoreSuppressed || revocationJob?.isActive == true) return
        when (val current = mutableState.value) {
            CertificateUiState.LoadingReference,
            is CertificateUiState.NoCertificate,
            is CertificateUiState.Unlocking,
            -> return
            is CertificateUiState.Unlocked -> {
                if (session.identityForSigning() != null) return
                mutableState.value = current.reference.toLockedState(current.summary)
                restoreFromCache(current.reference)
            }
            is CertificateUiState.Locked -> restoreFromCache(current.reference)
        }
    }

    fun onAppBackgrounded() {
        dropPendingChoice()
        // Native credential UI may temporarily cover the host. The controller
        // resumes only the same live/resumed host, with a bounded timeout.
        if (unlockCache.isAwaitingAuthentication) {
            session.onAppBackgrounded()
            return
        }
        cancelUnlockOperation()
        session.onAppBackgrounded()
        if (session.identityForSigning() != null) return
        val current = mutableState.value
        if (current is CertificateUiState.Locked) return
        val reference = current.referenceOrNull() ?: return
        mutableState.value = reference.toLockedState(current.summaryOrNull())
    }

    fun onMemoryPressure() {
        dropPendingChoice()
        cancelUnlockOperation()
        session.onMemoryPressure()
        val current = mutableState.value
        if (current is CertificateUiState.Locked) return
        val reference = current.referenceOrNull() ?: return
        mutableState.value = reference.toLockedState(current.summaryOrNull())
        restoreFromCache(reference)
    }

    fun forget() = forgetSelection { gateway.forget() }

    private fun forgetSelection(remove: suspend () -> Unit) {
        cancelCurrentOperation()
        restoreSuppressed = true
        unlockCache.invalidate()
        session.forget()
        val previous = mutableState.value.referenceOrNull()
        mutableState.value = CertificateUiState.LoadingReference
        operationJob = viewModelScope.launch {
            mutableState.value = try {
                if (revokeCacheBeforeSelectionChange()) {
                    remove()
                    CertificateUiState.NoCertificate()
                } else {
                    previous.keptAfterRefusedChange()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                previous?.toLockedState(CertificateUiError.STORAGE_FAILURE)
                    ?: CertificateUiState.NoCertificate(CertificateUiError.STORAGE_FAILURE)
            }
            refreshSavedCertificates()
        }
    }

    private suspend fun loadReferenceAndRestore(): CertificateUiState = try {
        val reference = gateway.currentReference()
        if (reference == null) {
            clearCache()
            CertificateUiState.NoCertificate()
        } else if (unlockCache.requiresExplicitRestore) {
            reference.toLockedState()
        } else {
            restore(reference)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        CertificateUiState.NoCertificate(CertificateUiError.STORAGE_FAILURE)
    }

    fun restoreSavedUnlock() {
        val locked = mutableState.value as? CertificateUiState.Locked ?: return
        if (locked.revocationPending || restoreSuppressed || revocationJob?.isActive == true) return
        restoreFromCache(locked.reference, explicit = true)
    }

    private fun restoreFromCache(reference: StoredCertificateReference, explicit: Boolean = false) {
        if (restoreSuppressed || revocationJob?.isActive == true) return
        if (unlockCache.requiresExplicitRestore && !explicit) return
        cancelCurrentOperation()
        if (explicit) mutableState.value = CertificateUiState.Unlocking(reference, reference.summary)
        operationJob = viewModelScope.launch {
            mutableState.value = restore(reference)
        }
    }

    private suspend fun restore(reference: StoredCertificateReference): CertificateUiState {
        val cached = try {
            unlockCache.restore(reference, clock.instant())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            clearCache()
            session.lock()
            return reference.toLockedState(CertificateUiError.STORAGE_FAILURE)
        } ?: return reference.toLockedState()

        return cached.use { owned ->
            try {
                when (val result = gateway.unlock(owned.password)) {
                    is CertificateLoadResult.Success -> {
                        currentCoroutineContext().ensureActive()
                        session.unlock(result.identity, owned.lease)
                        CertificateUiState.Unlocked(reference, result.identity.summary)
                    }
                    is CertificateLoadResult.Failure -> {
                        clearCache()
                        session.lock()
                        reference.toLockedState()
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                clearCache()
                session.lock()
                reference.toLockedState()
            }
        }
    }

    private fun cancelCurrentOperation() {
        operationJob?.cancel()
        operationJob = null
        dropPendingChoice()
    }

    private fun cancelUnlockOperation() {
        if (mutableState.value is CertificateUiState.Unlocking) {
            cancelCurrentOperation()
        }
    }

    override fun onCleared() {
        cancelCurrentOperation()
        session.lock()
        super.onCleared()
    }

    private fun StoredCertificateReference.toLockedState(
        error: CertificateUiError? = null,
    ) = CertificateUiState.Locked(this, summary, error)

    // Like a lock whose revocation failed: the former selection stays, locked, with the error and no pending flag.
    private fun StoredCertificateReference?.keptAfterRefusedChange(): CertificateUiState =
        this?.toLockedState(CertificateUiError.STORAGE_FAILURE)
            ?: CertificateUiState.NoCertificate(CertificateUiError.STORAGE_FAILURE)

    private fun StoredCertificateReference.toLockedState(
        summary: dev.junta.firmamobile.certificate.CertificateSummary?,
    ) = CertificateUiState.Locked(this, summary, null)

    private fun CertificateUiState.referenceOrNull(): StoredCertificateReference? = when (this) {
        CertificateUiState.LoadingReference -> null
        is CertificateUiState.NoCertificate -> null
        is CertificateUiState.Locked -> reference
        is CertificateUiState.Unlocking -> reference
        is CertificateUiState.Unlocked -> reference
    }

    private fun CertificateUiState.summaryOrNull() = when (this) {
        CertificateUiState.LoadingReference -> null
        is CertificateUiState.NoCertificate -> null
        is CertificateUiState.Locked -> summary
        is CertificateUiState.Unlocking -> summary
        is CertificateUiState.Unlocked -> summary
    }

    private fun CertificateSelectionErrorCode.toUiError() = when (this) {
        CertificateSelectionErrorCode.INVALID_URI,
        CertificateSelectionErrorCode.DOCUMENT_UNAVAILABLE,
        -> CertificateUiError.DOCUMENT_UNAVAILABLE
        CertificateSelectionErrorCode.UNSUPPORTED_FILE -> CertificateUiError.UNSUPPORTED_FILE
        CertificateSelectionErrorCode.FILE_TOO_LARGE -> CertificateUiError.FILE_TOO_LARGE
        CertificateSelectionErrorCode.PERMISSION_DENIED -> CertificateUiError.PERMISSION_DENIED
        CertificateSelectionErrorCode.CATALOG_FULL -> CertificateUiError.CATALOG_FULL
        CertificateSelectionErrorCode.ALREADY_SAVED -> CertificateUiError.ALREADY_SAVED
        CertificateSelectionErrorCode.STORAGE_FAILURE -> CertificateUiError.STORAGE_FAILURE
    }

    private fun CertificateErrorCode.toUiError() = CertificateErrorPresentation.forCode(this)

    class Factory(
        private val gateway: CertificateGateway,
        private val session: CertificateSession,
        private val unlockCache: CertificateUnlockCache = NoOpCertificateUnlockCache,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CertificateViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return CertificateViewModel(gateway, session, unlockCache) as T
        }
    }

    private companion object {
        val MAX_PERSISTED_UNLOCK_DURATION: Duration = Duration.ofHours(24)
        const val CHOICE_LIFETIME_MILLIS = 120_000L
    }
}

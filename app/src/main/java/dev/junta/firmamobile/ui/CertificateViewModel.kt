package dev.junta.firmamobile.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.junta.firmamobile.certificate.CertificateErrorCode
import dev.junta.firmamobile.certificate.CertificateGateway
import dev.junta.firmamobile.certificate.CertificateLoadResult
import dev.junta.firmamobile.certificate.CertificateSelectionErrorCode
import dev.junta.firmamobile.certificate.CertificateSelectionResult
import dev.junta.firmamobile.certificate.CertificateSession
import dev.junta.firmamobile.certificate.CertificateUnlockCache
import dev.junta.firmamobile.certificate.NoOpCertificateUnlockCache
import dev.junta.firmamobile.certificate.StoredCertificateReference
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
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
    private val unlockDuration: Duration = Duration.ofHours(24),
) : ViewModel() {
    private val mutableState = MutableStateFlow<CertificateUiState>(
        CertificateUiState.LoadingReference,
    )
    val state: StateFlow<CertificateUiState> = mutableState.asStateFlow()

    private var operationJob: Job? = null

    init {
        require(!unlockDuration.isNegative && !unlockDuration.isZero)
        require(unlockDuration <= MAX_PERSISTED_UNLOCK_DURATION)
        operationJob = viewModelScope.launch {
            mutableState.value = loadReferenceAndRestore()
        }
    }

    fun prepareForCertificateSelection() {
        lock()
    }

    fun onCertificateSelected(uri: Uri) {
        cancelCurrentOperation()
        unlockCache.clear()
        session.forget()
        val previous = mutableState.value.referenceOrNull()
        mutableState.value = CertificateUiState.LoadingReference
        operationJob = viewModelScope.launch {
            mutableState.value = try {
                when (val result = gateway.select(uri)) {
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
        }
    }

    fun unlock(password: CharArray) {
        val locked = mutableState.value as? CertificateUiState.Locked
        if (locked == null) {
            password.fill('\u0000')
            return
        }
        cancelCurrentOperation()
        mutableState.value = CertificateUiState.Unlocking(locked.reference, locked.summary)
        val unlockJob = viewModelScope.launch {
            try {
                when (val result = gateway.unlock(password)) {
                    is CertificateLoadResult.Success -> {
                        ensureActive()
                        val issuedAt = clock.instant()
                        val expiresAt = issuedAt.plus(unlockDuration)
                        val lease = session.createUnlockLease(expiresAt, unlockDuration)
                        unlockCache.store(
                            reference = locked.reference,
                            password = password,
                            issuedAt = issuedAt,
                            expiresAt = expiresAt,
                            observedAtMonotonicNanos = lease.observedAtMonotonicNanos,
                        )
                        ensureActive()
                        session.unlock(result.identity, lease)
                        mutableState.value = CertificateUiState.Unlocked(
                            reference = locked.reference,
                            summary = result.identity.summary,
                        )
                    }
                    is CertificateLoadResult.Failure -> {
                        unlockCache.clear()
                        session.lock()
                        mutableState.value = locked.copy(error = result.code.toUiError())
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                unlockCache.clear()
                session.lock()
                mutableState.value = locked.copy(error = CertificateUiError.STORAGE_FAILURE)
            } finally {
                password.fill('\u0000')
            }
        }
        unlockJob.invokeOnCompletion { password.fill('\u0000') }
        operationJob = unlockJob
    }

    fun lock() {
        cancelCurrentOperation()
        unlockCache.clear()
        session.lock()
        val current = mutableState.value
        val reference = current.referenceOrNull() ?: return
        mutableState.value = reference.toLockedState(current.summaryOrNull())
    }

    fun onAppForegrounded() {
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
        cancelUnlockOperation()
        session.onAppBackgrounded()
        if (session.identityForSigning() != null) return
        val current = mutableState.value
        val reference = current.referenceOrNull() ?: return
        mutableState.value = reference.toLockedState(current.summaryOrNull())
    }

    fun onMemoryPressure() {
        cancelUnlockOperation()
        session.onMemoryPressure()
        val current = mutableState.value
        val reference = current.referenceOrNull() ?: return
        mutableState.value = reference.toLockedState(current.summaryOrNull())
        restoreFromCache(reference)
    }

    fun forget() {
        cancelCurrentOperation()
        unlockCache.clear()
        session.forget()
        val previous = mutableState.value.referenceOrNull()
        mutableState.value = CertificateUiState.LoadingReference
        operationJob = viewModelScope.launch {
            mutableState.value = try {
                gateway.forget()
                CertificateUiState.NoCertificate()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                previous?.toLockedState(CertificateUiError.STORAGE_FAILURE)
                    ?: CertificateUiState.NoCertificate(CertificateUiError.STORAGE_FAILURE)
            }
        }
    }

    private suspend fun loadReferenceAndRestore(): CertificateUiState = try {
        val reference = gateway.currentReference()
        if (reference == null) {
            unlockCache.clear()
            CertificateUiState.NoCertificate()
        } else {
            restore(reference)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        CertificateUiState.NoCertificate(CertificateUiError.STORAGE_FAILURE)
    }

    private fun restoreFromCache(reference: StoredCertificateReference) {
        cancelCurrentOperation()
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
            unlockCache.clear()
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
                        unlockCache.clear()
                        session.lock()
                        reference.toLockedState()
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                unlockCache.clear()
                session.lock()
                reference.toLockedState()
            }
        }
    }

    private fun cancelCurrentOperation() {
        operationJob?.cancel()
        operationJob = null
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
        CertificateSelectionErrorCode.STORAGE_FAILURE -> CertificateUiError.STORAGE_FAILURE
    }

    private fun CertificateErrorCode.toUiError() = when (this) {
        CertificateErrorCode.FILE_TOO_LARGE -> CertificateUiError.FILE_TOO_LARGE
        CertificateErrorCode.INVALID_PASSWORD_OR_FILE -> CertificateUiError.PASSWORD_INVALID_OR_FILE
        CertificateErrorCode.TOO_MANY_ENTRIES,
        CertificateErrorCode.CHAIN_TOO_LONG,
        -> CertificateUiError.CERTIFICATE_NOT_USABLE
        CertificateErrorCode.PRIVATE_KEY_MISSING -> CertificateUiError.PRIVATE_KEY_MISSING
        CertificateErrorCode.MULTIPLE_PRIVATE_KEYS -> CertificateUiError.MULTIPLE_PRIVATE_KEYS
        CertificateErrorCode.CERTIFICATE_NOT_X509 -> CertificateUiError.CERTIFICATE_NOT_X509
        CertificateErrorCode.UNSUPPORTED_KEY_TYPE -> CertificateUiError.UNSUPPORTED_KEY_TYPE
        CertificateErrorCode.CERTIFICATE_EXPIRED -> CertificateUiError.CERTIFICATE_EXPIRED
        CertificateErrorCode.CERTIFICATE_NOT_YET_VALID -> CertificateUiError.CERTIFICATE_NOT_YET_VALID
        CertificateErrorCode.KEY_USAGE_NOT_PERMITTED -> CertificateUiError.KEY_USAGE_NOT_PERMITTED
        CertificateErrorCode.KEY_CERTIFICATE_MISMATCH -> CertificateUiError.KEY_CERTIFICATE_MISMATCH
        CertificateErrorCode.NO_CERTIFICATE_SELECTED -> CertificateUiError.CERTIFICATE_NOT_SELECTED
        CertificateErrorCode.DOCUMENT_UNAVAILABLE -> CertificateUiError.DOCUMENT_UNAVAILABLE
        CertificateErrorCode.REFERENCE_STORAGE_FAILURE -> CertificateUiError.STORAGE_FAILURE
    }

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
    }
}

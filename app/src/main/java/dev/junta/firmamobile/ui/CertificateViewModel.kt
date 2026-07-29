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
import dev.junta.firmamobile.certificate.StoredCertificateReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CertificateViewModel(
    private val gateway: CertificateGateway,
    private val session: CertificateSession,
) : ViewModel() {
    private val mutableState = MutableStateFlow<CertificateUiState>(
        CertificateUiState.LoadingReference,
    )
    val state: StateFlow<CertificateUiState> = mutableState.asStateFlow()

    private var operationJob: Job? = null

    init {
        operationJob = viewModelScope.launch {
            mutableState.value = try {
                gateway.currentReference()?.toLockedState()
                    ?: CertificateUiState.NoCertificate()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                CertificateUiState.NoCertificate(CertificateUiError.STORAGE_FAILURE)
            }
        }
    }

    fun prepareForCertificateSelection() {
        lock()
    }

    fun onCertificateSelected(uri: Uri) {
        cancelCurrentOperation()
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
                        session.unlock(result.identity)
                        mutableState.value = CertificateUiState.Unlocked(
                            reference = locked.reference,
                            summary = result.identity.summary,
                        )
                    }
                    is CertificateLoadResult.Failure -> {
                        session.lock()
                        mutableState.value = locked.copy(error = result.code.toUiError())
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
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
        session.lock()
        val current = mutableState.value
        val reference = current.referenceOrNull() ?: return
        mutableState.value = reference.toLockedState(current.summaryOrNull())
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
    }

    fun forget() {
        cancelCurrentOperation()
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
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CertificateViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return CertificateViewModel(gateway, session) as T
        }
    }
}

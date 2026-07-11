package dev.junta.firmamobile.ui

import dev.junta.firmamobile.certificate.CertificateSummary
import dev.junta.firmamobile.certificate.StoredCertificateReference

sealed interface CertificateUiState {
    data object LoadingReference : CertificateUiState

    data class NoCertificate(
        val error: CertificateUiError? = null,
    ) : CertificateUiState

    data class Locked(
        val reference: StoredCertificateReference,
        val summary: CertificateSummary?,
        val error: CertificateUiError?,
    ) : CertificateUiState

    data class Unlocking(
        val reference: StoredCertificateReference,
        val summary: CertificateSummary?,
    ) : CertificateUiState

    data class Unlocked(
        val reference: StoredCertificateReference,
        val summary: CertificateSummary,
    ) : CertificateUiState
}

enum class CertificateUiError {
    PASSWORD_INVALID_OR_FILE,
    FILE_TOO_LARGE,
    PRIVATE_KEY_MISSING,
    MULTIPLE_PRIVATE_KEYS,
    CERTIFICATE_NOT_X509,
    UNSUPPORTED_KEY_TYPE,
    CERTIFICATE_EXPIRED,
    CERTIFICATE_NOT_YET_VALID,
    KEY_USAGE_NOT_PERMITTED,
    KEY_CERTIFICATE_MISMATCH,
    UNSUPPORTED_FILE,
    DOCUMENT_UNAVAILABLE,
    PERMISSION_DENIED,
    STORAGE_FAILURE,
    CERTIFICATE_NOT_SELECTED,
    CERTIFICATE_NOT_USABLE,
}

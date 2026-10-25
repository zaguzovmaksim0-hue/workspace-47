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
        val revocationPending: Boolean = false,
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

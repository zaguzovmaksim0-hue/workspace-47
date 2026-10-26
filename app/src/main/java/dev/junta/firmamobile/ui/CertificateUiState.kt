package dev.junta.firmamobile.ui

import dev.junta.firmamobile.certificate.CertificateEntryChoice
import dev.junta.firmamobile.certificate.CertificateSummary
import dev.junta.firmamobile.certificate.StoredCertificateEntry
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
        /** Entries of a multi-key file awaiting the user's choice; the typed password is held until then. */
        val choices: List<CertificateEntryChoice> = emptyList(),
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

/** Saved catalogue entries beside the selection; [error] reports the last failed add or remove. */
data class SavedCertificatesUiState(
    val entries: List<StoredCertificateEntry> = emptyList(),
    val error: CertificateUiError? = null,
    val keyDeletionPending: Boolean = false,
    val activeKey: StoredCertificateEntry? = null,
)

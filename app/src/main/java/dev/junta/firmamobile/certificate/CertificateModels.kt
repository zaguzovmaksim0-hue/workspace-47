package dev.junta.firmamobile.certificate

import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.time.Instant

data class CertificateSummary(
    val ownerName: String,
    val issuerName: String,
    val validFrom: Instant,
    val validUntil: Instant,
)

/** One private-key entry of a multi-key file, for display and choice only. */
data class CertificateEntryChoice(
    val number: Int,
    val summary: CertificateSummary,
    val certificateSha256: String,
)

class UnlockedIdentity internal constructor(
    private val privateKey: PrivateKey,
    val certificate: X509Certificate,
    val chain: List<X509Certificate>,
    val summary: CertificateSummary,
    /** Where an AndroidKeyStore copy lives; null for an in-memory PKCS#12 key. */
    internal val keystorePlacement: KeystorePlacement? = null,
) {
    internal fun <T> withPrivateKey(block: (PrivateKey) -> T): T = block(privateKey)
}

enum class CertificateErrorCode {
    FILE_TOO_LARGE,
    INVALID_PASSWORD_OR_FILE,
    INVALID_PASSWORD,
    INVALID_PKCS12_FILE,
    UNSUPPORTED_PKCS12_ENCRYPTION,
    TOO_MANY_ENTRIES,
    PRIVATE_KEY_MISSING,
    MULTIPLE_PRIVATE_KEYS,
    CERTIFICATE_NOT_X509,
    CHAIN_TOO_LONG,
    UNSUPPORTED_KEY_TYPE,
    CERTIFICATE_EXPIRED,
    CERTIFICATE_NOT_YET_VALID,
    KEY_USAGE_NOT_PERMITTED,
    KEY_CERTIFICATE_MISMATCH,
    NO_CERTIFICATE_SELECTED,
    DOCUMENT_UNAVAILABLE,
    REFERENCE_STORAGE_FAILURE,
}

sealed interface CertificateLoadResult {
    data class Success(val identity: UnlockedIdentity) : CertificateLoadResult

    data class Failure(val code: CertificateErrorCode) : CertificateLoadResult
}

internal enum class SensitiveCopyKind {
    PASSWORD,
    PKCS12_BYTES,
    KEY_MATCH_CHALLENGE,
}

internal fun interface SensitiveCopyObserver {
    fun onCleared(kind: SensitiveCopyKind, allZero: Boolean)
}

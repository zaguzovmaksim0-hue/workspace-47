package dev.junta.firmamobile.signing

import dev.junta.firmamobile.afirma.servlet.NativeXadesPackaging

enum class XsigCryptographicStatus {
    VERIFIED,
    INVALID,
    UNSUPPORTED,
    LIMIT_EXCEEDED,
    ORIGINAL_REQUIRED,
    READ_FAILED,
}

enum class XsigCertificateTrust {
    NOT_EVALUATED,
    UNTRUSTED_EMBEDDED_ONLY,
}

enum class XsigInputRequirement {
    NONE,
    ORIGINAL_DOCUMENT,
}

data class XsigVerificationResult(
    val cryptographicStatus: XsigCryptographicStatus,
    val certificateTrust: XsigCertificateTrust,
    val packaging: NativeXadesPackaging?,
    val signerDisplay: String?,
)

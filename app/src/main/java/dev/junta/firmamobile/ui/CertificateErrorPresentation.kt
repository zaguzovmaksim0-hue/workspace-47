package dev.junta.firmamobile.ui

import dev.junta.firmamobile.certificate.CertificateErrorCode

/** Typed display mapping only; no failure details or certificate state enter this boundary. */
internal object CertificateErrorPresentation {
    private val uiErrors: Map<CertificateErrorCode, CertificateUiError> = mapOf(
        CertificateErrorCode.FILE_TOO_LARGE to CertificateUiError.FILE_TOO_LARGE,
        CertificateErrorCode.INVALID_PASSWORD_OR_FILE to CertificateUiError.PASSWORD_INVALID_OR_FILE,
        CertificateErrorCode.INVALID_PASSWORD to CertificateUiError.INVALID_PASSWORD,
        CertificateErrorCode.INVALID_PKCS12_FILE to CertificateUiError.INVALID_PKCS12_FILE,
        CertificateErrorCode.UNSUPPORTED_PKCS12_ENCRYPTION to CertificateUiError.UNSUPPORTED_PKCS12_ENCRYPTION,
        CertificateErrorCode.TOO_MANY_ENTRIES to CertificateUiError.CERTIFICATE_NOT_USABLE,
        CertificateErrorCode.PRIVATE_KEY_MISSING to CertificateUiError.PRIVATE_KEY_MISSING,
        CertificateErrorCode.MULTIPLE_PRIVATE_KEYS to CertificateUiError.MULTIPLE_PRIVATE_KEYS,
        CertificateErrorCode.CERTIFICATE_NOT_X509 to CertificateUiError.CERTIFICATE_NOT_X509,
        CertificateErrorCode.CHAIN_TOO_LONG to CertificateUiError.CERTIFICATE_NOT_USABLE,
        CertificateErrorCode.UNSUPPORTED_KEY_TYPE to CertificateUiError.UNSUPPORTED_KEY_TYPE,
        CertificateErrorCode.CERTIFICATE_EXPIRED to CertificateUiError.CERTIFICATE_EXPIRED,
        CertificateErrorCode.CERTIFICATE_NOT_YET_VALID to CertificateUiError.CERTIFICATE_NOT_YET_VALID,
        CertificateErrorCode.KEY_USAGE_NOT_PERMITTED to CertificateUiError.KEY_USAGE_NOT_PERMITTED,
        CertificateErrorCode.KEY_CERTIFICATE_MISMATCH to CertificateUiError.KEY_CERTIFICATE_MISMATCH,
        CertificateErrorCode.NO_CERTIFICATE_SELECTED to CertificateUiError.CERTIFICATE_NOT_SELECTED,
        CertificateErrorCode.DOCUMENT_UNAVAILABLE to CertificateUiError.DOCUMENT_UNAVAILABLE,
        CertificateErrorCode.REFERENCE_STORAGE_FAILURE to CertificateUiError.STORAGE_FAILURE,
    )

    init {
        check(uiErrors.keys == CertificateErrorCode.entries.toSet())
    }

    fun forCode(code: CertificateErrorCode): CertificateUiError = uiErrors.getValue(code)
}

enum class CertificateUiError {
    PASSWORD_INVALID_OR_FILE,
    INVALID_PASSWORD,
    INVALID_PKCS12_FILE,
    UNSUPPORTED_PKCS12_ENCRYPTION,
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
    CATALOG_FULL,
    ALREADY_SAVED,
    STORAGE_FAILURE,
    CERTIFICATE_NOT_SELECTED,
    CERTIFICATE_NOT_USABLE,
}

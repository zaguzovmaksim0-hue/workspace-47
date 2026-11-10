package dev.junta.firmamobile.afirma.servlet

/** Safe, request-independent outcomes. Never include a URI, document, certificate or exception message. */
internal enum class ExternalAfirmaReceiveResult {
    OFFERED,
    BUSY,
    UNAVAILABLE,
    INVALID_REQUEST,
    INDIRECT_RETRIEVAL_UNSUPPORTED,
    DESKTOP_WEBSOCKET_UNSUPPORTED,
    CERTIFICATE_FILTER_UNSUPPORTED,
    REQUEST_CONSTRAINT_UNSUPPORTED,
    CONFIRMATION_REQUIRED,
    UNSUPPORTED_REQUEST,
    REVIEW_FAILED;

    companion object {
        fun unsupported(reason: String): ExternalAfirmaReceiveResult = when (reason) {
            "indirect_retrieval_not_allowed" -> INDIRECT_RETRIEVAL_UNSUPPORTED
            "desktop_websocket_not_implemented" -> DESKTOP_WEBSOCKET_UNSUPPORTED
            "certificate_filter_not_implemented" -> CERTIFICATE_FILTER_UNSUPPORTED
            "minimum_client_or_key_constraint" -> REQUEST_CONSTRAINT_UNSUPPORTED
            "external_consent_suppression_not_allowed" -> CONFIRMATION_REQUIRED
            else -> UNSUPPORTED_REQUEST
        }
    }
}

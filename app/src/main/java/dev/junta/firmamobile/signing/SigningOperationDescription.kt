package dev.junta.firmamobile.signing

/** UI facts; neither localized text nor a portal protocol value. */
sealed interface SigningOperationDescription {
    data class Literal(val value: String) : SigningOperationDescription
    data class Batch(val documentCount: Int) : SigningOperationDescription
}

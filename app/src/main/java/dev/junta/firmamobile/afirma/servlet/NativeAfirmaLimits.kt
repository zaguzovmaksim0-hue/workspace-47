package dev.junta.firmamobile.afirma.servlet

/**
 * End-to-end budgets for local universal PDF signing. Profile adapters and
 * non-PDF formats keep their narrower reviewed limits. Large Android external
 * intents remain separately capped because Binder is not a document channel.
 */
internal object NativeAfirmaLimits {
    const val PDF_INPUT_BYTES = 8 * 1024 * 1024
    const val PDF_OUTPUT_BYTES = 10 * 1024 * 1024
    const val URI_CHARS = 16 * 1024 * 1024
    const val CONFIGURATION_BYTES = 12 * 1024 * 1024
    // Includes Base64, AES padding, DES framing and protocol envelope overhead.
    const val RETRIEVAL_WIRE_BYTES = 17 * 1024 * 1024
    const val RESULT_CHARS = 16 * 1024 * 1024
    const val RELAY_MESSAGE_BYTES = RETRIEVAL_WIRE_BYTES + 64 * 1024
}

package dev.junta.firmamobile.ui.offlineverification

internal enum class OfflineVerificationOutcome {
    VERIFIED,
    INVALID,
    UNSUPPORTED,
    LIMIT_EXCEEDED,
    DETACHED_CONTENT_REQUIRED,
    READ_FAILED,
}

/** Only bounded display values. Never put a source, URI, bytes or exception here. */
internal data class OfflineVerificationState(
    val cmsSelected: Boolean = false,
    val originalSelected: Boolean = false,
    val picking: OfflineInputKind? = null,
    val busy: Boolean = false,
    val outcome: OfflineVerificationOutcome? = null,
    val verifiedSigners: Int = 0,
) {
    val canVerify: Boolean get() = cmsSelected && !busy && picking == null
    val hasSelection: Boolean get() = cmsSelected || originalSelected
}

internal data class OfflineSelection(val generation: Long, val kind: OfflineInputKind)

internal data class OfflineVerificationCompletion(
    val outcome: OfflineVerificationOutcome,
    val verifiedSigners: Int = 0,
)

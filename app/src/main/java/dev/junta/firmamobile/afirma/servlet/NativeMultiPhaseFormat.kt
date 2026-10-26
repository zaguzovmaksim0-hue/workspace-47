package dev.junta.firmamobile.afirma.servlet

/** Presentation category only; the service format remains separate. */
internal enum class NativeMultiPhaseFormat {
    TRIPHASE,
    LOCAL_BATCH,
    REMOTE_BATCH,
    LOCAL_CADES_COSIGN_BATCH,
}

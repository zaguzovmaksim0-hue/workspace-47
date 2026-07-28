package dev.junta.firmamobile.network

internal enum class ProfileHttpFailurePhase {
    DNS_BEFORE_CONNECT,
    TCP_BEFORE_HTTP_BYTES,
    TLS_BEFORE_HTTP_BYTES,
    HTTP_WRITE_STARTED,
    READ_AFTER_HTTP_WRITE,
    UNKNOWN,
}

internal data class ProfileHttpFailureDetail(
    val code: ProfileHttpFailure,
    val phase: ProfileHttpFailurePhase,
    val httpWriteStarted: Boolean,
) {
    val safeForRouteFallback: Boolean
        get() = !httpWriteStarted && phase in SAFE_PRE_WRITE_PHASES

    private companion object {
        val SAFE_PRE_WRITE_PHASES = setOf(
            ProfileHttpFailurePhase.DNS_BEFORE_CONNECT,
            ProfileHttpFailurePhase.TCP_BEFORE_HTTP_BYTES,
            ProfileHttpFailurePhase.TLS_BEFORE_HTTP_BYTES,
        )
    }
}

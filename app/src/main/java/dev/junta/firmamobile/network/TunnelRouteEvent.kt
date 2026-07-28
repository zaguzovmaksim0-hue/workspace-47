package dev.junta.firmamobile.network

internal enum class ProfileHttpRoute {
    DIRECT,
    SECURE_TUNNEL,
}

internal enum class TunnelRouteStage {
    DIRECT_FAILED_PRE_HTTP,
    TUNNEL_CONNECTING,
    TUNNEL_ESTABLISHED,
    TUNNEL_FAILED,
}

/**
 * Closed, non-sensitive route progress. It intentionally contains no request
 * identifier, URL, authority, credential, exception text, or arbitrary data.
 */
internal data class TunnelRouteEvent(
    val route: ProfileHttpRoute,
    val stage: TunnelRouteStage,
    val phase: ProfileHttpFailurePhase? = null,
    val resultCode: ProfileHttpFailure? = null,
)

internal fun interface TunnelRouteObserver {
    fun onEvent(event: TunnelRouteEvent)
}

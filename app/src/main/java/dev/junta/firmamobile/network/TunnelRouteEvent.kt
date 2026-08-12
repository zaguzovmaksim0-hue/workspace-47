package dev.junta.firmamobile.network

import java.util.UUID
import java.util.concurrent.TimeUnit

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

internal enum class TunnelRouteDurationBucket {
    NOT_AVAILABLE,
    UNDER_ONE_SECOND,
    ONE_TO_THREE_SECONDS,
    THREE_TO_TEN_SECONDS,
    TEN_TO_THIRTY_SECONDS,
    OVER_THIRTY_SECONDS;

    internal companion object {
        fun fromElapsedNanos(elapsedNanos: Long): TunnelRouteDurationBucket = when {
            elapsedNanos < 0L -> NOT_AVAILABLE
            elapsedNanos < TimeUnit.SECONDS.toNanos(1) -> UNDER_ONE_SECOND
            elapsedNanos < TimeUnit.SECONDS.toNanos(3) -> ONE_TO_THREE_SECONDS
            elapsedNanos < TimeUnit.SECONDS.toNanos(10) -> THREE_TO_TEN_SECONDS
            elapsedNanos < TimeUnit.SECONDS.toNanos(30) -> TEN_TO_THIRTY_SECONDS
            else -> OVER_THIRTY_SECONDS
        }
    }
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
    val durationBucket: TunnelRouteDurationBucket = TunnelRouteDurationBucket.NOT_AVAILABLE,
)

/**
 * The request identifier is carried separately for in-memory UI correlation.
 * It must never be copied into [TunnelRouteEvent] or diagnostic records.
 */
internal fun interface TunnelRouteObserver {
    fun onEvent(requestId: UUID, event: TunnelRouteEvent)
}

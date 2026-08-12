package dev.junta.firmamobile.network

import dev.junta.firmamobile.profile.ProfileId
import java.net.URI

/**
 * Executes the original one-shot request directly first. A single tunnel
 * attempt is permitted only for the exact approved profile/endpoint tuple and
 * only when the direct failure proves that no HTTP request bytes could have
 * started.
 */
internal class DirectFirstProfileHttpTransport(
    private val profileId: ProfileId,
    private val endpoint: URI,
    private val policy: SecureTunnelPolicy,
    private val direct: ProfileHttpTransport,
    private val tunnel: ProfileHttpTransport?,
    private val observer: TunnelRouteObserver,
    private val monotonicNanos: () -> Long = System::nanoTime,
) : ProfileHttpTransport {
    override fun post(
        request: ProfileHttpRequest,
        cancellation: ProfileHttpCancellation,
    ): ProfileHttpResult {
        val retry = try {
            request.duplicateForRetry()
        } catch (_: Exception) {
            return uncertainFailure()
        }

        return retry.use {
            val directResult = try {
                direct.post(request, cancellation)
            } catch (_: Exception) {
                return@use uncertainFailure()
            }
            if (directResult !is ProfileHttpResult.Failure) {
                return@use directResult
            }

            val normalizedDirect = normalizeDirectFailure(directResult)
            if (!canFallback(request, directResult)) {
                return@use normalizedDirect
            }

            emit(
                request.requestId,
                TunnelRouteEvent(
                    route = ProfileHttpRoute.DIRECT,
                    stage = TunnelRouteStage.DIRECT_FAILED_PRE_HTTP,
                    phase = directResult.detail.phase,
                    resultCode = ProfileHttpFailure.DIRECT_CONNECT_UNAVAILABLE,
                ),
            )
            if (!cancellation.bindRouteFallback()) {
                return@use normalizedDirect
            }

            val tunnelStartedAt = monotonicNanosOrNull()
            emit(
                request.requestId,
                TunnelRouteEvent(
                    route = ProfileHttpRoute.SECURE_TUNNEL,
                    stage = TunnelRouteStage.TUNNEL_CONNECTING,
                ),
            )
            val tunnelResult = try {
                requireNotNull(tunnel).post(retry, cancellation)
            } catch (_: Exception) {
                uncertainFailure()
            }

            when (tunnelResult) {
                is ProfileHttpResult.Success -> {
                    emit(
                        request.requestId,
                        TunnelRouteEvent(
                            route = ProfileHttpRoute.SECURE_TUNNEL,
                            stage = TunnelRouteStage.TUNNEL_ESTABLISHED,
                            durationBucket = durationBucketSince(tunnelStartedAt),
                        ),
                    )
                    tunnelResult
                }
                is ProfileHttpResult.Failure -> {
                    val normalizedTunnel = normalizeTunnelFailure(tunnelResult)
                    emit(
                        request.requestId,
                        TunnelRouteEvent(
                            route = ProfileHttpRoute.SECURE_TUNNEL,
                            stage = TunnelRouteStage.TUNNEL_FAILED,
                            phase = normalizedTunnel.detail.phase,
                            resultCode = normalizedTunnel.code,
                            durationBucket = durationBucketSince(tunnelStartedAt),
                        ),
                    )
                    normalizedTunnel
                }
            }
        }
    }

    private fun canFallback(
        request: ProfileHttpRequest,
        failure: ProfileHttpResult.Failure,
    ): Boolean =
        tunnel != null &&
            request.url.uri == endpoint &&
            policy.allows(profileId, endpoint) &&
            failure.code == ProfileHttpFailure.NETWORK_ERROR &&
            failure.detail.safeForRouteFallback

    private fun normalizeDirectFailure(
        failure: ProfileHttpResult.Failure,
    ): ProfileHttpResult.Failure = when {
        failure.code != ProfileHttpFailure.NETWORK_ERROR -> failure
        failure.detail.safeForRouteFallback -> failure.withCode(ProfileHttpFailure.DIRECT_CONNECT_UNAVAILABLE)
        else -> failure.withCode(ProfileHttpFailure.NETWORK_RESULT_UNCERTAIN)
    }

    private fun normalizeTunnelFailure(
        failure: ProfileHttpResult.Failure,
    ): ProfileHttpResult.Failure = when {
        failure.code in TUNNEL_ROUTE_FAILURES && !failure.detail.safeForRouteFallback ->
            failure.withCode(ProfileHttpFailure.NETWORK_RESULT_UNCERTAIN)
        failure.code == ProfileHttpFailure.NETWORK_ERROR ->
            failure.withCode(ProfileHttpFailure.TUNNEL_CONNECT_UNAVAILABLE)
        else -> failure
    }

    private fun ProfileHttpResult.Failure.withCode(code: ProfileHttpFailure): ProfileHttpResult.Failure =
        ProfileHttpResult.Failure(detail.copy(code = code))

    private fun uncertainFailure(): ProfileHttpResult.Failure = ProfileHttpResult.Failure(
        ProfileHttpFailureDetail(
            code = ProfileHttpFailure.NETWORK_RESULT_UNCERTAIN,
            phase = ProfileHttpFailurePhase.UNKNOWN,
            httpWriteStarted = true,
        ),
    )

    private fun monotonicNanosOrNull(): Long? = runCatching(monotonicNanos).getOrNull()

    private fun durationBucketSince(startedAt: Long?): TunnelRouteDurationBucket {
        val finishedAt = monotonicNanosOrNull()
        if (startedAt == null || finishedAt == null || finishedAt < startedAt) {
            return TunnelRouteDurationBucket.NOT_AVAILABLE
        }
        return TunnelRouteDurationBucket.fromElapsedNanos(finishedAt - startedAt)
    }

    private fun emit(requestId: java.util.UUID, event: TunnelRouteEvent) {
        runCatching { observer.onEvent(requestId, event) }
    }

    private companion object {
        val TUNNEL_ROUTE_FAILURES = setOf(
            ProfileHttpFailure.NETWORK_ERROR,
            ProfileHttpFailure.TUNNEL_AUTH_UNAVAILABLE,
            ProfileHttpFailure.TUNNEL_CONNECT_UNAVAILABLE,
            ProfileHttpFailure.UPSTREAM_CONNECT_UNAVAILABLE,
        )
    }
}

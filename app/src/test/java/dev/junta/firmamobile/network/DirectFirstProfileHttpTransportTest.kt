package dev.junta.firmamobile.network

import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.signing.JuntaOfvirtualTriPhaseAdapter
import java.net.URI
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectFirstProfileHttpTransportTest {
    @Test
    fun eachProvenPreHttpNetworkFailureUsesDirectThenExactlyOneTunnelAttempt() {
        for (phase in SAFE_PHASES) {
            val direct = RecordingTransport(failure(ProfileHttpFailure.NETWORK_ERROR, phase, false))
            val tunnel = RecordingTransport(success("tunnel-$phase"))
            val events = mutableListOf<TunnelRouteEvent>()
            val transport = transport(direct, tunnel, observer = TunnelRouteObserver { _, event -> events += event })

            val result = request().use { transport.post(it, ProfileHttpCancellation()) }

            assertEquals("phase=$phase", 1, direct.calls.get())
            assertEquals("phase=$phase", 1, tunnel.calls.get())
            assertEquals("phase=$phase", "tunnel-$phase", result.successBody())
            assertEquals(
                "phase=$phase",
                listOf(
                    TunnelRouteEvent(
                        route = ProfileHttpRoute.DIRECT,
                        stage = TunnelRouteStage.DIRECT_FAILED_PRE_HTTP,
                        phase = phase,
                        resultCode = ProfileHttpFailure.DIRECT_CONNECT_UNAVAILABLE,
                    ),
                    TunnelRouteEvent(
                        route = ProfileHttpRoute.SECURE_TUNNEL,
                        stage = TunnelRouteStage.TUNNEL_CONNECTING,
                    ),
                    TunnelRouteEvent(
                        route = ProfileHttpRoute.SECURE_TUNNEL,
                        stage = TunnelRouteStage.TUNNEL_ESTABLISHED,
                        durationBucket = TunnelRouteDurationBucket.UNDER_ONE_SECOND,
                    ),
                ),
                events,
            )
        }
    }

    @Test
    fun unsafeAndUnknownNetworkFailuresNeverReachTunnelAndReturnUncertainResult() {
        for (phase in UNSAFE_PHASES) {
            val direct = RecordingTransport(failure(ProfileHttpFailure.NETWORK_ERROR, phase, true))
            val tunnel = RecordingTransport(success("must-not-run"))
            val transport = transport(direct, tunnel)

            val result = request().use { transport.post(it, ProfileHttpCancellation()) }

            assertEquals("phase=$phase", 1, direct.calls.get())
            assertEquals("phase=$phase", 0, tunnel.calls.get())
            assertEquals("phase=$phase", ProfileHttpFailure.NETWORK_RESULT_UNCERTAIN, result.failureCode())
        }
    }

    @Test
    fun protocolAndHttpFailuresNeverTriggerFallbackOrLoseTheirClosedCode() {
        for (code in NON_ROUTE_FAILURES) {
            val direct = RecordingTransport(failure(code, ProfileHttpFailurePhase.READ_AFTER_HTTP_WRITE, true))
            val tunnel = RecordingTransport(success("must-not-run"))

            val result = request().use { transport(direct, tunnel).post(it, ProfileHttpCancellation()) }

            assertEquals("code=$code", 1, direct.calls.get())
            assertEquals("code=$code", 0, tunnel.calls.get())
            assertEquals("code=$code", code, result.failureCode())
        }
    }

    @Test
    fun cancellationBetweenDirectFailureEventAndAtomicTunnelBindingBlocksTunnel() {
        val cancellation = ProfileHttpCancellation()
        val direct = RecordingTransport(failure(ProfileHttpFailure.NETWORK_ERROR, ProfileHttpFailurePhase.TCP_BEFORE_HTTP_BYTES, false))
        val tunnel = RecordingTransport(success("must-not-run"))
        val events = mutableListOf<TunnelRouteEvent>()
        val observer = TunnelRouteObserver { _, event ->
            events += event
            if (event.stage == TunnelRouteStage.DIRECT_FAILED_PRE_HTTP) cancellation.cancel()
        }

        val result = request().use { transport(direct, tunnel, observer = observer).post(it, cancellation) }

        assertEquals(1, direct.calls.get())
        assertEquals(0, tunnel.calls.get())
        assertEquals(ProfileHttpFailure.DIRECT_CONNECT_UNAVAILABLE, result.failureCode())
        assertEquals(1, events.size)
        assertEquals(TunnelRouteStage.DIRECT_FAILED_PRE_HTTP, events.single().stage)
    }

    @Test
    fun directSuccessDoesNotBindTunnelAndClosesUnusedRetryCopy() {
        val direct = RecordingTransport(success("direct"))
        val tunnel = RecordingTransport(success("must-not-run"))
        val events = mutableListOf<TunnelRouteEvent>()

        val result = request().use { transport(direct, tunnel, observer = TunnelRouteObserver { _, event -> events += event }).post(it, ProfileHttpCancellation()) }

        assertEquals("direct", result.successBody())
        assertEquals(1, direct.calls.get())
        assertEquals(0, tunnel.calls.get())
        assertTrue(events.isEmpty())
    }

    @Test
    fun exactProfileEndpointPolicyAndRequestUrlAreRequiredForFallback() {
        val cases = listOf(
            Case(ProfileId("junta-andalucia"), OFVIRTUAL_ENDPOINT, requestUrl = OFVIRTUAL_ENDPOINT, policy = SecureTunnelPolicy.QA),
            Case(ProfileId("junta-ofvirtual"), JUNTA_14_ENDPOINT, requestUrl = JUNTA_14_ENDPOINT, policy = SecureTunnelPolicy.QA),
            Case(ProfileId("junta-ofvirtual"), OFVIRTUAL_ENDPOINT, requestUrl = JUNTA_14_ENDPOINT, policy = SecureTunnelPolicy.QA),
            Case(ProfileId("junta-ofvirtual"), OFVIRTUAL_ENDPOINT, requestUrl = OFVIRTUAL_ENDPOINT, policy = SecureTunnelPolicy.RELEASE),
        )
        for (case in cases) {
            val direct = RecordingTransport(failure(ProfileHttpFailure.NETWORK_ERROR, ProfileHttpFailurePhase.DNS_BEFORE_CONNECT, false))
            val tunnel = RecordingTransport(success("must-not-run"))
            val transport = DirectFirstProfileHttpTransport(
                profileId = case.profileId,
                endpoint = case.endpoint,
                policy = case.policy,
                direct = direct,
                tunnel = tunnel,
                observer = TunnelRouteObserver { _, _ -> },
            )

            val result = request(case.requestUrl).use { transport.post(it, ProfileHttpCancellation()) }

            assertEquals("case=$case", 1, direct.calls.get())
            assertEquals("case=$case", 0, tunnel.calls.get())
            assertEquals("case=$case", ProfileHttpFailure.DIRECT_CONNECT_UNAVAILABLE, result.failureCode())
        }
    }

    @Test
    fun absentTunnelKeepsDirectOnlyAndReturnsClosedDirectCode() {
        val direct = RecordingTransport(failure(ProfileHttpFailure.NETWORK_ERROR, ProfileHttpFailurePhase.TLS_BEFORE_HTTP_BYTES, false))
        val transport = transport(direct, tunnel = null)

        val result = request().use { transport.post(it, ProfileHttpCancellation()) }

        assertEquals(1, direct.calls.get())
        assertEquals(ProfileHttpFailure.DIRECT_CONNECT_UNAVAILABLE, result.failureCode())
    }

    @Test
    fun failedTunnelProducesOneTerminalEventAndNoThirdAttempt() {
        val direct = RecordingTransport(failure(ProfileHttpFailure.NETWORK_ERROR, ProfileHttpFailurePhase.TCP_BEFORE_HTTP_BYTES, false))
        val tunnel = RecordingTransport(failure(ProfileHttpFailure.NETWORK_ERROR, ProfileHttpFailurePhase.TLS_BEFORE_HTTP_BYTES, false))
        val events = mutableListOf<TunnelRouteEvent>()

        val result = request().use { transport(direct, tunnel, observer = TunnelRouteObserver { _, event -> events += event }).post(it, ProfileHttpCancellation()) }

        assertEquals(1, direct.calls.get())
        assertEquals(1, tunnel.calls.get())
        assertEquals(ProfileHttpFailure.TUNNEL_CONNECT_UNAVAILABLE, result.failureCode())
        assertEquals(
            listOf(
                TunnelRouteStage.DIRECT_FAILED_PRE_HTTP,
                TunnelRouteStage.TUNNEL_CONNECTING,
                TunnelRouteStage.TUNNEL_FAILED,
            ),
            events.map(TunnelRouteEvent::stage),
        )
        assertEquals(1, events.count { it.stage == TunnelRouteStage.TUNNEL_FAILED })
        assertEquals(ProfileHttpFailure.TUNNEL_CONNECT_UNAVAILABLE, events.last().resultCode)
    }

    @Test
    fun tunnelFailureAfterPossibleWriteIsUncertainAndNeverRetriedAgain() {
        val direct = RecordingTransport(failure(ProfileHttpFailure.NETWORK_ERROR, ProfileHttpFailurePhase.DNS_BEFORE_CONNECT, false))
        val tunnel = RecordingTransport(failure(ProfileHttpFailure.NETWORK_ERROR, ProfileHttpFailurePhase.HTTP_WRITE_STARTED, true))

        val result = request().use { transport(direct, tunnel).post(it, ProfileHttpCancellation()) }

        assertEquals(1, direct.calls.get())
        assertEquals(1, tunnel.calls.get())
        assertEquals(ProfileHttpFailure.NETWORK_RESULT_UNCERTAIN, result.failureCode())
    }

    @Test
    fun thrownTunnelExceptionIsUnknownAndThereforeReturnsUncertainResult() {
        val direct = RecordingTransport(failure(ProfileHttpFailure.NETWORK_ERROR, ProfileHttpFailurePhase.DNS_BEFORE_CONNECT, false))
        val tunnel = ProfileHttpTransport { _, _ -> throw IllegalStateException("synthetic tunnel failure") }

        val result = request().use { transport(direct, tunnel).post(it, ProfileHttpCancellation()) }

        assertEquals(1, direct.calls.get())
        assertEquals(ProfileHttpFailure.NETWORK_RESULT_UNCERTAIN, result.failureCode())
    }

    @Test
    fun explicitTunnelRouteErrorAfterPossibleWriteIsAlwaysUncertain() {
        val direct = RecordingTransport(failure(ProfileHttpFailure.NETWORK_ERROR, ProfileHttpFailurePhase.DNS_BEFORE_CONNECT, false))
        val tunnel = RecordingTransport(
            failure(ProfileHttpFailure.TUNNEL_CONNECT_UNAVAILABLE, ProfileHttpFailurePhase.HTTP_WRITE_STARTED, true),
        )

        val result = request().use { transport(direct, tunnel).post(it, ProfileHttpCancellation()) }

        assertEquals(1, tunnel.calls.get())
        assertEquals(ProfileHttpFailure.NETWORK_RESULT_UNCERTAIN, result.failureCode())
    }

    @Test
    fun explicitTunnelClosedErrorsPassThroughWithoutAThirdAttempt() {
        for (code in listOf(ProfileHttpFailure.TUNNEL_AUTH_UNAVAILABLE, ProfileHttpFailure.UPSTREAM_CONNECT_UNAVAILABLE)) {
            val direct = RecordingTransport(failure(ProfileHttpFailure.NETWORK_ERROR, ProfileHttpFailurePhase.DNS_BEFORE_CONNECT, false))
            val tunnel = RecordingTransport(failure(code, ProfileHttpFailurePhase.TCP_BEFORE_HTTP_BYTES, false))

            val result = request().use { transport(direct, tunnel).post(it, ProfileHttpCancellation()) }

            assertEquals("code=$code", 1, tunnel.calls.get())
            assertEquals("code=$code", code, result.failureCode())
        }
    }

    @Test
    fun retryOwnsIndependentBodyCopyAndPreservesInternalCorrelationOnly() {
        val wire = "op=pre&canary=body-copy".encodeToByteArray()
        val expectedWire = wire.copyOf()
        val direct = RecordingTransport(
            failure(ProfileHttpFailure.NETWORK_ERROR, ProfileHttpFailurePhase.TCP_BEFORE_HTTP_BYTES, false),
            closeRequestAfterRead = true,
        )
        val tunnel = RecordingTransport(success("ok"))
        val request = request(body = wire)
        val originalId = request.requestId

        val result = request.use { transport(direct, tunnel).post(it, ProfileHttpCancellation()) }

        assertEquals("ok", result.successBody())
        assertArrayEquals(expectedWire, direct.bodies.single())
        assertArrayEquals(expectedWire, tunnel.bodies.single())
        assertEquals(listOf(originalId), direct.requestIds)
        assertEquals(listOf(originalId), tunnel.requestIds)
        assertTrue(wire.all { it == 0.toByte() })
    }

    @Test
    fun duplicateForRetryPreservesIdButHasIndependentOwnedBody() {
        val body = "independent-owned-body".encodeToByteArray()
        val request = request(body = body)
        val duplicate = request.duplicateForRetry()
        val originalId = request.requestId

        request.close()

        assertEquals(originalId, duplicate.requestId)
        duplicate.withBody { assertEquals("independent-owned-body", it.decodeToString()) }
        duplicate.close()
        assertTrue(body.all { it == 0.toByte() })
        assertThrows(IllegalStateException::class.java) { duplicate.withBody { it.size } }
    }

    @Test
    fun routeEventsCannotContainRequestUuidUrlTokenOrArbitraryText() {
        val request = request()
        val requestId = request.requestId.toString()
        val token = "qa-token-must-not-appear"
        val event = TunnelRouteEvent(
            route = ProfileHttpRoute.SECURE_TUNNEL,
            stage = TunnelRouteStage.TUNNEL_FAILED,
            phase = ProfileHttpFailurePhase.TCP_BEFORE_HTTP_BYTES,
            resultCode = ProfileHttpFailure.TUNNEL_CONNECT_UNAVAILABLE,
        )
        val rendered = event.toString()
        val fieldNames = TunnelRouteEvent::class.java.declaredFields.map { it.name.lowercase() }

        assertFalse(rendered.contains(requestId))
        assertFalse(rendered.contains(OFVIRTUAL_ENDPOINT.toASCIIString()))
        assertFalse(rendered.contains(token))
        assertTrue(fieldNames.none { "request" in it || "uuid" in it || "url" in it || "token" in it })
        request.close()
    }

    @Test
    fun observerFailureCannotChangeTheNetworkResultOrCreateDuplicateAttempts() {
        val direct = RecordingTransport(failure(ProfileHttpFailure.NETWORK_ERROR, ProfileHttpFailurePhase.DNS_BEFORE_CONNECT, false))
        val tunnel = RecordingTransport(success("ok"))
        val observer = TunnelRouteObserver { _, _ -> throw IllegalStateException("observer failure") }

        val result = request().use { transport(direct, tunnel, observer = observer).post(it, ProfileHttpCancellation()) }

        assertEquals("ok", result.successBody())
        assertEquals(1, direct.calls.get())
        assertEquals(1, tunnel.calls.get())
    }

    @Test
    fun observerReceivesRequestIdSeparatelyWhileEventRemainsEnumOnly() {
        val direct = RecordingTransport(
            failure(ProfileHttpFailure.NETWORK_ERROR, ProfileHttpFailurePhase.DNS_BEFORE_CONNECT, false),
        )
        val tunnel = RecordingTransport(success("ok"))
        val observations = mutableListOf<Pair<UUID, TunnelRouteEvent>>()
        val observer = TunnelRouteObserver { requestId, event -> observations += requestId to event }
        val request = request()
        val expectedId = request.requestId

        request.use { transport(direct, tunnel, observer).post(it, ProfileHttpCancellation()) }

        assertEquals(3, observations.size)
        assertTrue(observations.all { it.first == expectedId })
        assertTrue(
            TunnelRouteEvent::class.java.declaredFields.none { field ->
                field.name.contains("request", ignoreCase = true) || field.type == UUID::class.java
            },
        )
    }

    @Test
    fun differentRequestsReceiveDifferentInternalIds() {
        val first = request()
        val second = request()
        try {
            assertNotEquals(first.requestId, second.requestId)
        } finally {
            first.close()
            second.close()
        }
    }

    private fun transport(
        direct: ProfileHttpTransport,
        tunnel: ProfileHttpTransport?,
        observer: TunnelRouteObserver = TunnelRouteObserver { _, _ -> },
    ) = DirectFirstProfileHttpTransport(
        profileId = ProfileId("junta-ofvirtual"),
        endpoint = OFVIRTUAL_ENDPOINT,
        policy = SecureTunnelPolicy.QA,
        direct = direct,
        tunnel = tunnel,
        observer = observer,
        monotonicNanos = { 0L },
    )

    private fun request(
        uri: URI = OFVIRTUAL_ENDPOINT,
        body: ByteArray = "op=pre&cop=sign".encodeToByteArray(),
    ): ProfileHttpRequest = ProfileHttpRequest(ValidatedNetworkUrl(uri), body)

    private fun success(body: String): ProfileHttpResult =
        ProfileHttpResult.Success(ProfileHttpResponse(body.encodeToByteArray()))

    private fun failure(
        code: ProfileHttpFailure,
        phase: ProfileHttpFailurePhase,
        httpWriteStarted: Boolean,
    ): ProfileHttpResult = ProfileHttpResult.Failure(
        ProfileHttpFailureDetail(code, phase, httpWriteStarted),
    )

    private fun ProfileHttpResult.failureCode(): ProfileHttpFailure =
        (this as ProfileHttpResult.Failure).code

    private fun ProfileHttpResult.successBody(): String =
        (this as ProfileHttpResult.Success).response.use { response ->
            response.withBody(ByteArray::decodeToString)
        }

    private class RecordingTransport(
        vararg results: ProfileHttpResult,
        private val closeRequestAfterRead: Boolean = false,
    ) : ProfileHttpTransport {
        private val results = ArrayDeque(results.toList())
        val calls = AtomicInteger()
        val bodies = mutableListOf<ByteArray>()
        val requestIds = mutableListOf<UUID>()

        override fun post(
            request: ProfileHttpRequest,
            cancellation: ProfileHttpCancellation,
        ): ProfileHttpResult {
            calls.incrementAndGet()
            requestIds += request.requestId
            bodies += request.withBody(ByteArray::copyOf)
            if (closeRequestAfterRead) request.close()
            return results.removeFirst()
        }
    }

    private data class Case(
        val profileId: ProfileId,
        val endpoint: URI,
        val requestUrl: URI,
        val policy: SecureTunnelPolicy,
    )

    private companion object {
        val OFVIRTUAL_ENDPOINT = URI(JuntaOfvirtualTriPhaseAdapter.ENDPOINT)
        val JUNTA_14_ENDPOINT = URI(SafeNetworkUrlPolicy.JUNTA_TRIPHASE_ENDPOINT)
        val SAFE_PHASES = listOf(
            ProfileHttpFailurePhase.DNS_BEFORE_CONNECT,
            ProfileHttpFailurePhase.TCP_BEFORE_HTTP_BYTES,
            ProfileHttpFailurePhase.TLS_BEFORE_HTTP_BYTES,
        )
        val UNSAFE_PHASES = listOf(
            ProfileHttpFailurePhase.HTTP_WRITE_STARTED,
            ProfileHttpFailurePhase.READ_AFTER_HTTP_WRITE,
            ProfileHttpFailurePhase.UNKNOWN,
        )
        val NON_ROUTE_FAILURES = listOf(
            ProfileHttpFailure.INVALID_ENDPOINT,
            ProfileHttpFailure.PRIVATE_ADDRESS,
            ProfileHttpFailure.REDIRECT_BLOCKED,
            ProfileHttpFailure.SESSION_EXPIRED,
            ProfileHttpFailure.CONTENT_TYPE_INVALID,
            ProfileHttpFailure.RESPONSE_TOO_LARGE,
            ProfileHttpFailure.HTTP_ERROR,
        )
    }
}

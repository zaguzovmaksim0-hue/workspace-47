package dev.junta.firmamobile.signing

import dev.junta.firmamobile.browser.NavigationId
import dev.junta.firmamobile.network.DirectFirstProfileHttpTransport
import dev.junta.firmamobile.network.NetworkUrlValidation
import dev.junta.firmamobile.network.ProfileHttpCallPhaseTracker
import dev.junta.firmamobile.network.ProfileHttpFailure
import dev.junta.firmamobile.network.ProfileHttpFailureDetail
import dev.junta.firmamobile.network.ProfileHttpFailurePhase
import dev.junta.firmamobile.network.ProfileHttpCancellation
import dev.junta.firmamobile.network.ProfileHttpRequest
import dev.junta.firmamobile.network.ProfileHttpResponse
import dev.junta.firmamobile.network.ProfileHttpResult
import dev.junta.firmamobile.network.ProfileHttpTransport
import dev.junta.firmamobile.network.SafeNetworkUrlPolicy
import dev.junta.firmamobile.network.SecureTunnelPolicy
import dev.junta.firmamobile.network.TunnelRouteObserver
import dev.junta.firmamobile.network.ValidatedNetworkUrl
import dev.junta.firmamobile.network.TrustedOrigin
import java.security.cert.X509Certificate
import java.net.URI
import java.time.Instant
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TriPhaseExecutionAdapterTest {
    @Test
    fun genericAdapterPreservesCodecWireBytesAcrossPreAndPost() = runBlocking {
        val transport = RecordingTransport("pre-response", "post-response")
        val codec = FixedWireCodec()
        val adapter = AutoFirmaTriPhaseExecutionAdapter(
            contract = contract(),
            transport = transport,
            codec = codec,
            callTimeoutMillis = 1_000,
            executor = Executor { it.run() },
        )
        val request = request()

        val prepared = adapter.prepare(request, emptyList()) as ProtocolPrepareResult.Success
        val completed = adapter.complete(
            request,
            prepared.preSign,
            LocalSignature("local-signature".encodeToByteArray()),
        ) as ProtocolCompletionResult.Success

        assertArrayEquals("pre-wire".encodeToByteArray(), transport.bodies[0])
        assertArrayEquals("post-wire".encodeToByteArray(), transport.bodies[1])
        completed.signature.use { signature ->
            signature.withBytes { assertArrayEquals("final-signature".encodeToByteArray(), it) }
        }
        assertEquals(1, codec.decoded.closeCount)
        request.close()
    }

    @Test
    fun rejectedExecutorFailsClosedAndReleasesDecodedRequest() = runBlocking {
        val codec = FixedWireCodec()
        val adapter = AutoFirmaTriPhaseExecutionAdapter(
            contract = contract(),
            transport = RecordingTransport("unused"),
            codec = codec,
            callTimeoutMillis = 1_000,
            executor = Executor { throw RejectedExecutionException("synthetic rejection") },
        )
        val request = request()

        val result = adapter.prepare(request, emptyList()) as ProtocolPrepareResult.Failure

        assertEquals(SigningErrorCode.NETWORK_RESULT_UNCERTAIN, result.code)
        assertEquals(1, codec.decoded.closeCount)
        request.close()
    }

    @Test
    fun everyMismatchedContractFieldIsRejectedBeforeCodecOrNetwork() = runBlocking {
        val mismatches = listOf(
            request(protocolId = SigningProtocolId("other-triphase-v1")),
            request(profileId = "other-profile"),
            request(profileVersion = 2),
            request(origin = TrustedOrigin("https", "other.example", 443)),
            request(algorithm = SigningAlgorithm.SHA1_WITH_RSA),
        )

        mismatches.forEach { request ->
            val codec = FixedWireCodec()
            val transport = RecordingTransport("unused")
            val adapter = AutoFirmaTriPhaseExecutionAdapter(
                contract = contract(),
                transport = transport,
                codec = codec,
                callTimeoutMillis = 1_000,
                executor = Executor { it.run() },
            )

            val result = adapter.prepare(request, emptyList()) as ProtocolPrepareResult.Failure

            assertEquals(SigningErrorCode.UNSUPPORTED_PROTOCOL, result.code)
            assertEquals(0, codec.decodeCalls)
            assertTrue(transport.bodies.isEmpty())
            request.close()
        }
    }

    @Test
    fun callerCancellationIsPropagatedInsteadOfMappedToProtocolFailure() = runBlocking {
        val interrupted = AtomicBoolean(false)
        val cancelled = CountDownLatch(1)
        val adapter = AutoFirmaTriPhaseExecutionAdapter(
            contract = contract(),
            transport = ProfileHttpTransport { _, cancellation ->
                val worker = Thread.currentThread()
                cancellation.register {
                    interrupted.set(true)
                    cancelled.countDown()
                    worker.interrupt()
                }.use {
                    try {
                        Thread.sleep(10_000)
                        error("Cancellation was not delivered")
                    } catch (_: InterruptedException) {
                        ProfileHttpResult.Failure(dev.junta.firmamobile.network.ProfileHttpFailure.NETWORK_ERROR)
                    }
                }
            },
            codec = FixedWireCodec(),
            callTimeoutMillis = 10_000,
            executor = Executor { command -> Thread(command, "tri-phase-test").start() },
        )
        val request = request()
        var propagated = false

        try {
            withTimeout(100) { adapter.prepare(request, emptyList()) }
        } catch (_: TimeoutCancellationException) {
            propagated = true
        }

        assertTrue(propagated)
        assertTrue(cancelled.await(1, TimeUnit.SECONDS))
        assertTrue(interrupted.get())
        request.close()
    }

    @Test
    fun internalDeadlineUsesCancellationSnapshotForSafePreWriteFailure() = runBlocking {
        val direct = DeadlinePhaseTransport(markHttpWriteStarted = false)
        val tunnelCalls = AtomicInteger()
        val adapter = deadlineAdapter(direct, tunnelCalls)
        val request = deadlineRequest()

        val result = adapter.prepare(request, emptyList()) as ProtocolPrepareResult.Failure

        assertEquals(SigningErrorCode.SIGNING_SERVICE_UNAVAILABLE, result.code)
        assertTrue(direct.cancelled.await(1, TimeUnit.SECONDS))
        assertEquals(1, direct.calls.get())
        assertEquals(0, tunnelCalls.get())
        request.close()
    }

    @Test
    fun deadlineAfterHttpWriteIsUncertainAndNeverStartsTunnel() = runBlocking {
        val direct = DeadlinePhaseTransport(markHttpWriteStarted = true)
        val tunnelCalls = AtomicInteger()
        val adapter = deadlineAdapter(direct, tunnelCalls)
        val request = deadlineRequest()

        val result = adapter.prepare(request, emptyList()) as ProtocolPrepareResult.Failure

        assertEquals(SigningErrorCode.NETWORK_RESULT_UNCERTAIN, result.code)
        assertTrue(direct.cancelled.await(1, TimeUnit.SECONDS))
        assertEquals(1, direct.calls.get())
        assertEquals(0, tunnelCalls.get())
        request.close()
    }

    @Test
    fun networkRequestsInheritNormalizedSigningRequestId() = runBlocking {
        val transport = RecordingTransport("pre-response", "post-response")
        val adapter = AutoFirmaTriPhaseExecutionAdapter(
            contract = contract(),
            transport = transport,
            codec = FixedWireCodec(),
            callTimeoutMillis = 1_000,
            executor = Executor { it.run() },
        )
        val request = request()

        val prepared = adapter.prepare(request, emptyList()) as ProtocolPrepareResult.Success
        val completed = adapter.complete(
            request,
            prepared.preSign,
            LocalSignature("local-signature".encodeToByteArray()),
        ) as ProtocolCompletionResult.Success
        completed.signature.close()

        assertEquals(listOf(request.requestId, request.requestId), transport.requestIds)
        request.close()
    }

    @Test
    fun codecCannotRedirectPreRequestOutsideContractEndpoint() = runBlocking {
        val transport = RecordingTransport("unused")
        val adapter = AutoFirmaTriPhaseExecutionAdapter(
            contract = contract(),
            transport = transport,
            codec = FixedWireCodec(URI("https://evil.example/triphase")),
            callTimeoutMillis = 1_000,
            executor = Executor { it.run() },
        )
        val request = request()

        val result = adapter.prepare(request, emptyList()) as ProtocolPrepareResult.Failure

        assertEquals(SigningErrorCode.ORIGIN_NOT_ALLOWED, result.code)
        assertTrue(transport.bodies.isEmpty())
        request.close()
    }

    @Test
    fun completeContractMismatchClosesOwnedInputsWithoutCodecOrNetwork() = runBlocking {
        val decoded = Decoded()
        val request = request()
        val preSign = PreSignResult(
            requestOwner = request,
            bytesToSign = "bytes-to-sign".encodeToByteArray(),
            state = State(decoded),
        )
        val signatureCleared = AtomicBoolean(false)
        val localSignature = LocalSignature(
            "local-signature".encodeToByteArray(),
            SensitiveSignatureCopyObserver { signatureCleared.set(it) },
        )
        val transport = RecordingTransport("unused")
        val codec = FixedWireCodec()
        val adapter = AutoFirmaTriPhaseExecutionAdapter(
            contract = contract().copy(profileVersion = 2),
            transport = transport,
            codec = codec,
            callTimeoutMillis = 1_000,
            executor = Executor { it.run() },
        )

        val result = adapter.complete(request, preSign, localSignature)

        assertEquals(SigningErrorCode.PAYLOAD_CHANGED, (result as ProtocolCompletionResult.Failure).code)
        assertEquals(1, decoded.closeCount)
        assertTrue(signatureCleared.get())
        assertEquals(0, codec.decodeCalls)
        assertTrue(transport.bodies.isEmpty())
        request.close()
    }

    private fun deadlineAdapter(
        direct: ProfileHttpTransport,
        tunnelCalls: AtomicInteger,
    ): AutoFirmaTriPhaseExecutionAdapter {
        val tunnel = ProfileHttpTransport { _, _ ->
            tunnelCalls.incrementAndGet()
            ProfileHttpResult.Success(ProfileHttpResponse("must-not-run".encodeToByteArray()))
        }
        val transport = DirectFirstProfileHttpTransport(
            profileId = dev.junta.firmamobile.profile.ProfileId("junta-ofvirtual"),
            endpoint = OFVIRTUAL_ENDPOINT,
            policy = SecureTunnelPolicy.QA,
            direct = direct,
            tunnel = tunnel,
            observer = TunnelRouteObserver { _, _ -> },
        )
        return AutoFirmaTriPhaseExecutionAdapter(
            contract = contract(
                profileId = "junta-ofvirtual",
                endpoint = OFVIRTUAL_ENDPOINT,
            ),
            transport = transport,
            codec = FixedWireCodec(OFVIRTUAL_ENDPOINT),
            callTimeoutMillis = 50,
            executor = Executor { command -> Thread(command, "tri-phase-deadline-test").start() },
        )
    }

    private fun deadlineRequest() = request(profileId = "junta-ofvirtual")

    private fun contract(
        profileId: String = "test-profile",
        endpoint: URI = URI(SafeNetworkUrlPolicy.JUNTA_TRIPHASE_ENDPOINT),
    ) = TriPhaseExecutionContract(
        protocolId = PROTOCOL_ID,
        profileId = profileId,
        profileVersion = 1,
        initiatorOrigins = setOf("https://service.example"),
        endpoint = endpoint,
        format = SigningFormat.CADES,
        algorithms = setOf(SigningAlgorithm.SHA256_WITH_RSA),
    )

    private fun request(
        protocolId: SigningProtocolId = PROTOCOL_ID,
        profileId: String = "test-profile",
        profileVersion: Int = 1,
        origin: TrustedOrigin = TrustedOrigin("https", "service.example", 443),
        algorithm: SigningAlgorithm = SigningAlgorithm.SHA256_WITH_RSA,
    ) = NormalizedSignRequest(
        requestId = UUID.fromString("00000000-0000-4000-8000-000000000021"),
        protocolId = protocolId,
        context = SigningContext(
            profileId = profileId,
            profileVersion = profileVersion,
            origin = origin,
            navigationId = NavigationId("00000000-0000-4000-8000-000000000022"),
            observedAt = Instant.parse("2026-07-16T00:00:00Z"),
        ),
        algorithm = algorithm,
        format = SigningFormat.CADES,
        safeDescription = "Test signature",
        payload = "payload".encodeToByteArray(),
    )

    private class FixedWireCodec(
        private val requestEndpoint: URI = URI(SafeNetworkUrlPolicy.JUNTA_TRIPHASE_ENDPOINT),
    ) : TriPhaseProtocolCodec {
        val decoded = Decoded()
        var decodeCalls = 0
        private lateinit var requestOwner: NormalizedSignRequest

        override fun decodeRequest(
            request: NormalizedSignRequest,
            certificateChain: List<X509Certificate>,
        ): TriPhaseDecodedRequest {
            decodeCalls++
            requestOwner = request
            return decoded
        }

        override fun buildPreRequest(data: TriPhaseDecodedRequest): ProfileHttpRequest =
            ProfileHttpRequest(endpoint(), "pre-wire".encodeToByteArray())

        override fun parsePreResponse(data: TriPhaseDecodedRequest, response: ByteArray): PreSignResult {
            assertArrayEquals("pre-response".encodeToByteArray(), response)
            return PreSignResult(
                requestOwner = requestOwner,
                bytesToSign = "bytes-to-sign".encodeToByteArray(),
                state = State(data as Decoded),
            )
        }

        override fun buildPostRequest(
            state: PreSignState,
            localSignature: LocalSignature,
        ): ProfileHttpRequest = ProfileHttpRequest(endpoint(), "post-wire".encodeToByteArray())

        override fun parsePostResponse(response: ByteArray): LocalSignature {
            assertArrayEquals("post-response".encodeToByteArray(), response)
            return LocalSignature("final-signature".encodeToByteArray())
        }

        private fun endpoint() = if (requestEndpoint == URI(SafeNetworkUrlPolicy.JUNTA_TRIPHASE_ENDPOINT)) {
            when (val result = SafeNetworkUrlPolicy().validateEndpoint(requestEndpoint.toASCIIString())) {
                is NetworkUrlValidation.Allowed -> result.url
                is NetworkUrlValidation.Blocked -> error("Test endpoint unexpectedly blocked")
            }
        } else {
            dev.junta.firmamobile.network.ValidatedNetworkUrl(requestEndpoint)
        }
    }

    private class Decoded : TriPhaseDecodedRequest {
        var closeCount = 0
        override fun close() {
            closeCount++
        }
    }

    private class State(private val decoded: Decoded) : PreSignState {
        override fun close() = decoded.close()
    }

    private class RecordingTransport(vararg responses: String) : ProfileHttpTransport {
        private val responses = ArrayDeque(responses.map { it.encodeToByteArray() })
        val bodies = mutableListOf<ByteArray>()
        val requestIds = mutableListOf<UUID>()

        override fun post(
            request: ProfileHttpRequest,
            cancellation: ProfileHttpCancellation,
        ): ProfileHttpResult {
            requestIds += request.requestId
            bodies += request.withBody { it.copyOf() }
            return ProfileHttpResult.Success(ProfileHttpResponse(responses.removeFirst()))
        }
    }

    private class DeadlinePhaseTransport(
        private val markHttpWriteStarted: Boolean,
    ) : ProfileHttpTransport {
        val calls = AtomicInteger()
        val cancelled = CountDownLatch(1)

        override fun post(
            request: ProfileHttpRequest,
            cancellation: ProfileHttpCancellation,
        ): ProfileHttpResult {
            calls.incrementAndGet()
            val tracker = ProfileHttpCallPhaseTracker()
            check(cancellation.beginAttempt(tracker))
            if (markHttpWriteStarted) {
                tracker.requestHeadersStart(TEST_CALL)
            }
            cancellation.register { cancelled.countDown() }.use {
                check(cancelled.await(5, TimeUnit.SECONDS))
            }
            return ProfileHttpResult.Failure(
                cancellation.snapshotFailure(ProfileHttpFailure.NETWORK_ERROR),
            )
        }
    }

    private companion object {
        val PROTOCOL_ID = SigningProtocolId("test-triphase-v1")
        val OFVIRTUAL_ENDPOINT = URI(dev.junta.firmamobile.signing.JuntaOfvirtualTriPhaseAdapter.ENDPOINT)
        val TEST_CALL = OkHttpClient().newCall(
            Request.Builder().url("https://example.com/").build(),
        )
    }
}

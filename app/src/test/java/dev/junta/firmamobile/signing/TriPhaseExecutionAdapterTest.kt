package dev.junta.firmamobile.signing

import dev.junta.firmamobile.browser.NavigationId
import dev.junta.firmamobile.network.NetworkUrlValidation
import dev.junta.firmamobile.network.ProfileHttpCancellation
import dev.junta.firmamobile.network.ProfileHttpRequest
import dev.junta.firmamobile.network.ProfileHttpResponse
import dev.junta.firmamobile.network.ProfileHttpResult
import dev.junta.firmamobile.network.ProfileHttpTransport
import dev.junta.firmamobile.network.SafeNetworkUrlPolicy
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
import kotlinx.coroutines.runBlocking
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

        assertEquals(SigningErrorCode.PROTOCOL_FAILED, result.code)
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

    private fun contract() = TriPhaseExecutionContract(
        protocolId = PROTOCOL_ID,
        profileId = "test-profile",
        profileVersion = 1,
        initiatorOrigins = setOf("https://service.example"),
        endpoint = URI(SafeNetworkUrlPolicy.JUNTA_TRIPHASE_ENDPOINT),
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

        override fun post(
            request: ProfileHttpRequest,
            cancellation: ProfileHttpCancellation,
        ): ProfileHttpResult {
            bodies += request.withBody { it.copyOf() }
            return ProfileHttpResult.Success(ProfileHttpResponse(responses.removeFirst()))
        }
    }

    private companion object {
        val PROTOCOL_ID = SigningProtocolId("test-triphase-v1")
    }
}

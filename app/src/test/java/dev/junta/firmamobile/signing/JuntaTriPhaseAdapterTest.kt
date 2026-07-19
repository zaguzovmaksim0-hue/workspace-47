package dev.junta.firmamobile.signing

import dev.junta.firmamobile.browser.NavigationId
import dev.junta.firmamobile.network.ProfileHttpResponse
import dev.junta.firmamobile.network.ProfileHttpRequest
import dev.junta.firmamobile.network.ProfileHttpCancellation
import dev.junta.firmamobile.network.ProfileHttpResult
import dev.junta.firmamobile.network.ProfileHttpTransport
import dev.junta.firmamobile.network.SafeNetworkUrlPolicy
import dev.junta.firmamobile.network.TrustedOrigin
import dev.junta.firmamobile.network.ValidatedNetworkUrl
import java.time.Instant
import java.util.ArrayDeque
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JuntaTriPhaseAdapterTest {
    @Test
    fun adapterPerformsExactPreLocalInputPostBoundaryWithoutPrivateKeySurface() = runBlocking {
        val identity = syntheticIdentity()
        val preXml = "<xml frmt=\"CAdES\" op=\"FIRMAR\"><firmas><firma Id=\"one\"><param n=\"PRE\">${Base64.getEncoder().encodeToString("pre".encodeToByteArray())}</param></firma></firmas></xml>"
        val finalBytes = "synthetic-cades".encodeToByteArray()
        val transport = QueueTransport(
            Base64.getUrlEncoder().encode(preXml.encodeToByteArray()),
            "OK NEWID=${Base64.getUrlEncoder().encodeToString(finalBytes)}".encodeToByteArray(),
        )
        val adapter = JuntaTriPhaseAdapter(transport = transport)
        val request = request()

        val prepare = adapter.prepare(request, identity.chain) as ProtocolPrepareResult.Success
        prepare.preSign.withBytesToSign { assertArrayEquals("pre".encodeToByteArray(), it) }
        val localSignature = prepare.preSign.withBytesToSign { input ->
            (JcaLocalSignatureEngine().sign(
                input,
                identity,
                SigningAlgorithm.SHA1_WITH_RSA,
            ) as LocalSignatureResult.Success).signature
        }
        val completion = adapter.complete(
            request,
            prepare.preSign,
            localSignature,
        ) as ProtocolCompletionResult.Success

        completion.signature.use { signature ->
            signature.withBytes { assertArrayEquals(finalBytes, it) }
        }
        assertEquals(2, transport.urls.size)
        assertEquals(SafeNetworkUrlPolicy.JUNTA_TRIPHASE_ENDPOINT, transport.urls[0].uri.toASCIIString())
        assertEquals(SafeNetworkUrlPolicy.JUNTA_TRIPHASE_ENDPOINT, transport.urls[1].uri.toASCIIString())
        assertTrue(transport.bodies[0].decodeToString().startsWith("op=pre&cop=sign&format=CAdES&algo=SHA1withRSA"))
        assertTrue(transport.bodies[1].decodeToString().startsWith("op=post&cop=sign&format=CAdES&algo=SHA1withRSA"))
        val sessionValue = transport.bodies[1].decodeToString()
            .split('&')
            .single { it.startsWith("session=") }
            .substringAfter('=')
        val sessionXml = Base64.getUrlDecoder().decode(sessionValue).decodeToString()
        assertTrue(sessionXml.contains("<firmas>"))
        assertTrue(!sessionXml.contains("<firmas format="))
        request.close()
    }

    @Test
    fun wrongServerUrlIsRejectedBeforeAnyNetworkCall() = runBlocking {
        val identity = syntheticIdentity()
        val transport = QueueTransport()
        val adapter = JuntaTriPhaseAdapter(transport = transport)
        val request = request("serverUrl=https://evil.example/sign\n")

        val result = adapter.prepare(request, identity.chain) as ProtocolPrepareResult.Failure

        assertEquals(SigningErrorCode.ORIGIN_NOT_ALLOWED, result.code)
        assertTrue(transport.urls.isEmpty())
        request.close()
    }

    @Test
    fun adapterCompletesProfileAllowedSha256Flow() = runBlocking {
        val identity = syntheticIdentity()
        val transport = QueueTransport(preResponse(), postResponse())
        val adapter = JuntaTriPhaseAdapter(transport = transport)
        val request = request(algorithm = SigningAlgorithm.SHA256_WITH_RSA)
        val prepared = adapter.prepare(request, identity.chain) as ProtocolPrepareResult.Success

        val completion = adapter.complete(
            request,
            prepared.preSign,
            sign(prepared.preSign, identity, SigningAlgorithm.SHA256_WITH_RSA),
        )

        (completion as ProtocolCompletionResult.Success).signature.close()
        assertTrue(transport.bodies[0].decodeToString().contains("algo=SHA256withRSA"))
        assertTrue(transport.bodies[1].decodeToString().contains("algo=SHA256withRSA"))
        request.close()
    }

    @Test
    fun preSignCanReachPostExactlyOnceEvenUnderConcurrentCompletion() = runBlocking {
        val identity = syntheticIdentity()
        val transport = QueueTransport(preResponse(), postResponse())
        val adapter = JuntaTriPhaseAdapter(transport = transport)
        val request = request()
        val preSign = (adapter.prepare(request, identity.chain) as ProtocolPrepareResult.Success).preSign
        val signatures = List(2) { sign(preSign, identity, SigningAlgorithm.SHA1_WITH_RSA) }

        val results = signatures.map { signature ->
            async { adapter.complete(request, preSign, signature) }
        }.awaitAll()

        assertEquals(1, results.count { it is ProtocolCompletionResult.Success })
        assertEquals(1, results.count { it is ProtocolCompletionResult.Failure })
        assertEquals(2, transport.urls.size)
        request.close()
    }

    @Test
    fun sameIdWithDifferentRequestOrInvalidPk1NeverPosts() = runBlocking {
        val identity = syntheticIdentity()
        val transport = QueueTransport(preResponse(), preResponse())
        val adapter = JuntaTriPhaseAdapter(transport = transport)
        val original = request()
        val preSign = (adapter.prepare(original, identity.chain) as ProtocolPrepareResult.Success).preSign
        val validSignature = sign(preSign, identity, SigningAlgorithm.SHA1_WITH_RSA)
        val substituted = request(
            algorithm = SigningAlgorithm.SHA256_WITH_RSA,
            navigationId = "00000000-0000-4000-8000-000000000099",
        )

        val substitutedResult = adapter.complete(substituted, preSign, validSignature)

        assertEquals(SigningErrorCode.PAYLOAD_CHANGED, (substitutedResult as ProtocolCompletionResult.Failure).code)
        assertEquals(1, transport.urls.size)
        original.close()
        substituted.close()

        val secondRequest = request()
        val secondPreSign = (adapter.prepare(secondRequest, identity.chain) as ProtocolPrepareResult.Success).preSign
        val invalidResult = adapter.complete(
            secondRequest,
            secondPreSign,
            LocalSignature("not-a-valid-pkcs1".encodeToByteArray()),
        )
        assertEquals(SigningErrorCode.INVALID_REQUEST, (invalidResult as ProtocolCompletionResult.Failure).code)
        assertEquals(2, transport.urls.size)
        secondRequest.close()
    }

    @Test
    fun totalDeadlineInterruptsBlockingPostWithoutRetry() = runBlocking {
        val identity = syntheticIdentity()
        val transport = TimeoutAfterPreTransport(preResponse())
        val adapter = JuntaTriPhaseAdapter(
            transport = transport,
            callTimeoutMillis = 200,
        )
        val request = request()
        val preSign = (adapter.prepare(request, identity.chain) as ProtocolPrepareResult.Success).preSign
        val signature = sign(preSign, identity, SigningAlgorithm.SHA1_WITH_RSA)

        val result = adapter.complete(request, preSign, signature)

        assertEquals(SigningErrorCode.PROTOCOL_FAILED, (result as ProtocolCompletionResult.Failure).code)
        assertEquals(2, transport.calls.get())
        assertTrue(transport.interrupted.get())
        request.close()
    }

    private fun request(
        properties: String = "filters=keyusage.digitalsignature:true;nonexpired:\nserverUrl=${SafeNetworkUrlPolicy.JUNTA_TRIPHASE_ENDPOINT}\n",
        algorithm: SigningAlgorithm = SigningAlgorithm.SHA1_WITH_RSA,
        navigationId: String = "00000000-0000-4000-8000-000000000012",
    ): NormalizedSignRequest {
        val data = "synthetic-document".encodeToByteArray()
        val payload = MiniAppletPayloadCodec.encode(data, properties)
        data.fill(0)
        return NormalizedSignRequest(
            requestId = UUID.fromString("00000000-0000-4000-8000-000000000011"),
            protocolId = JuntaTriPhaseAdapter.ID,
            context = SigningContext(
                profileId = "junta-andalucia",
                profileVersion = 1,
                origin = TrustedOrigin("https", "www.juntadeandalucia.es", 443),
                navigationId = NavigationId(navigationId),
                observedAt = Instant.parse("2026-07-12T00:00:00Z"),
            ),
            algorithm = algorithm,
            format = SigningFormat.CADES,
            safeDescription = "Autenticación con certificado",
            payload = payload,
        )
    }

    private fun sign(
        preSign: PreSignResult,
        identity: dev.junta.firmamobile.certificate.UnlockedIdentity,
        algorithm: SigningAlgorithm,
    ): LocalSignature = preSign.withBytesToSign { input ->
        (JcaLocalSignatureEngine().sign(input, identity, algorithm) as LocalSignatureResult.Success).signature
    }

    private fun preResponse(): ByteArray {
        val xml = "<xml><firmas><firma Id=\"one\"><param n=\"PRE\">${Base64.getEncoder().encodeToString("pre".encodeToByteArray())}</param></firma></firmas></xml>"
        return Base64.getUrlEncoder().encode(xml.encodeToByteArray())
    }

    private fun postResponse(): ByteArray =
        "OK NEWID=${Base64.getUrlEncoder().encodeToString("synthetic-cades".encodeToByteArray())}".encodeToByteArray()

    private class QueueTransport(
        vararg bodies: ByteArray,
    ) : ProfileHttpTransport {
        private val responses = ArrayDeque(bodies.toList())
        val urls = mutableListOf<ValidatedNetworkUrl>()
        val bodiesSeen = mutableListOf<ByteArray>()

        @Synchronized
        override fun post(
            request: ProfileHttpRequest,
            cancellation: ProfileHttpCancellation,
        ): ProfileHttpResult {
            urls += request.url
            bodiesSeen += request.withBody { it.copyOf() }
            return ProfileHttpResult.Success(ProfileHttpResponse(responses.removeFirst()))
        }

        val bodies: List<ByteArray>
            get() = bodiesSeen
    }

    private class TimeoutAfterPreTransport(
        private val preResponse: ByteArray,
    ) : ProfileHttpTransport {
        val calls = AtomicInteger()
        val interrupted = AtomicBoolean(false)

        override fun post(
            request: ProfileHttpRequest,
            cancellation: ProfileHttpCancellation,
        ): ProfileHttpResult = when (calls.incrementAndGet()) {
            1 -> ProfileHttpResult.Success(ProfileHttpResponse(preResponse))
            else -> {
                val worker = Thread.currentThread()
                cancellation.register {
                    interrupted.set(true)
                    worker.interrupt()
                }.use {
                try {
                    Thread.sleep(10_000)
                    error("deadline failed")
                } catch (_: InterruptedException) {
                    ProfileHttpResult.Failure(dev.junta.firmamobile.network.ProfileHttpFailure.NETWORK_ERROR)
                }
            }
            }
        }
    }
}

package dev.junta.firmamobile.signing

import dev.junta.firmamobile.browser.NavigationId
import dev.junta.firmamobile.network.ProfileHttpCancellation
import dev.junta.firmamobile.network.ProfileHttpRequest
import dev.junta.firmamobile.network.ProfileHttpResponse
import dev.junta.firmamobile.network.ProfileHttpResult
import dev.junta.firmamobile.network.ProfileHttpTransport
import dev.junta.firmamobile.network.TrustedOrigin
import dev.junta.firmamobile.network.ValidatedNetworkUrl
import java.time.Instant
import java.util.ArrayDeque
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnizarTriPhaseAdapterTest {
    @Test
    fun exactLegacyChallengePerformsPreLocalPostAgainstOnlyTheProfileEndpoint() = runBlocking {
        val identity = syntheticIdentity()
        val preXml = "<xml frmt=\"CAdES\" op=\"FIRMAR\"><firmas><firma Id=\"one\"><param n=\"PRE\">${Base64.getEncoder().encodeToString("pre".encodeToByteArray())}</param></firma></firmas></xml>"
        val finalBytes = "synthetic-cades".encodeToByteArray()
        val transport = QueueTransport(
            Base64.getUrlEncoder().encode(preXml.encodeToByteArray()),
            "OK NEWID=${Base64.getUrlEncoder().encodeToString(finalBytes)}".encodeToByteArray(),
        )
        val adapter = UnizarTriPhaseAdapter(transport = transport)
        val request = request()

        val prepared = adapter.prepare(request, identity.chain) as ProtocolPrepareResult.Success
        val localSignature = prepared.preSign.withBytesToSign { input ->
            (JcaLocalSignatureEngine().sign(
                input,
                identity,
                SigningAlgorithm.SHA1_WITH_RSA,
            ) as LocalSignatureResult.Success).signature
        }
        val completion = adapter.complete(
            request,
            prepared.preSign,
            localSignature,
        ) as ProtocolCompletionResult.Success

        completion.signature.use { signature ->
            signature.withBytes { assertArrayEquals(finalBytes, it) }
        }
        assertEquals(listOf(UnizarTriPhaseAdapter.ENDPOINT, UnizarTriPhaseAdapter.ENDPOINT),
            transport.urls.map { it.uri.toASCIIString() })
        assertTrue(transport.bodies[0].decodeToString().startsWith("op=pre&cop=sign&format=CAdES&algo=SHA1withRSA"))
        assertTrue(transport.bodies[1].decodeToString().startsWith("op=post&cop=sign&format=CAdES&algo=SHA1withRSA"))
        val encodedProperties = transport.bodies[0].decodeToString()
            .split('&')
            .single { it.startsWith("params=") }
            .substringAfter('=')
        val decodedProperties = Base64.getUrlDecoder().decode(encodedProperties).decodeToString()
        assertTrue(decodedProperties.contains("precalculatedHashAlgorithm=SHA1"))
        request.close()
    }

    @Test
    fun wrongOriginProtocolAlgorithmOrPropertiesAreRejectedBeforeNetwork() = runBlocking {
        val identity = syntheticIdentity()
        val cases = listOf(
            request(origin = TrustedOrigin("https", "tramita.unizar.es.evil.example", 443)),
            request(protocolId = JuntaTriPhaseAdapter.ID),
            request(algorithm = SigningAlgorithm.SHA256_WITH_RSA),
            request(properties = "mode=explicit\nserverUrl=${UnizarTriPhaseAdapter.ENDPOINT}"),
            request(challengeSize = 19),
            request(challengeSize = 21),
        )

        cases.forEach { candidate ->
            val transport = QueueTransport()
            val result = UnizarTriPhaseAdapter(transport = transport).prepare(candidate, identity.chain)
            assertTrue(result is ProtocolPrepareResult.Failure)
            assertTrue(transport.urls.isEmpty())
            candidate.close()
        }
    }

    private fun request(
        origin: TrustedOrigin = TrustedOrigin("https", "tramita.unizar.es", 443),
        protocolId: SigningProtocolId = UnizarTriPhaseAdapter.ID,
        algorithm: SigningAlgorithm = SigningAlgorithm.SHA1_WITH_RSA,
        properties: String =
            "precalculatedHashAlgorithm=SHA1\nserverUrl=${UnizarTriPhaseAdapter.ENDPOINT}",
        challengeSize: Int = 20,
    ): NormalizedSignRequest {
        val challenge = ByteArray(challengeSize) { index -> (index + 1).toByte() }
        val payload = MiniAppletPayloadCodec.encode(challenge, properties)
        challenge.fill(0)
        return NormalizedSignRequest(
            requestId = UUID.fromString("00000000-0000-4000-8000-000000000081"),
            protocolId = protocolId,
            context = SigningContext(
                profileId = "unizar-tramitador",
                profileVersion = 2,
                origin = origin,
                navigationId = NavigationId("00000000-0000-4000-8000-000000000082"),
                observedAt = Instant.parse("2026-07-18T00:00:00Z"),
            ),
            algorithm = algorithm,
            format = SigningFormat.CADES,
            safeDescription = "Acceso con certificado a la Oficina Virtual",
            payload = payload,
        )
    }

    private class QueueTransport(
        vararg responses: ByteArray,
    ) : ProfileHttpTransport {
        private val responses = ArrayDeque(responses.toList())
        val urls = mutableListOf<ValidatedNetworkUrl>()
        val bodies = mutableListOf<ByteArray>()

        override fun post(
            request: ProfileHttpRequest,
            cancellation: ProfileHttpCancellation,
        ): ProfileHttpResult {
            urls += request.url
            bodies += request.withBody { it.copyOf() }
            return ProfileHttpResult.Success(ProfileHttpResponse(responses.removeFirst()))
        }
    }
}

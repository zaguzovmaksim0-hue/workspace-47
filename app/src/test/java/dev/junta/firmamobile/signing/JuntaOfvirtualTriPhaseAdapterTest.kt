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

class JuntaOfvirtualTriPhaseAdapterTest {
    @Test
    fun exactLegacyChallengePerformsPreLocalPostAgainstOnlyTheProfileEndpoint() = runBlocking {
        val identity = syntheticIdentity()
        val preXml = "<xml frmt=\"CAdES\" op=\"FIRMAR\"><firmas><firma Id=\"one\"><param n=\"PRE\">${Base64.getEncoder().encodeToString("pre".encodeToByteArray())}</param></firma></firmas></xml>"
        val finalBytes = "synthetic-cades".encodeToByteArray()
        val transport = QueueTransport(
            Base64.getUrlEncoder().encode(preXml.encodeToByteArray()),
            "OK NEWID=${Base64.getUrlEncoder().encodeToString(finalBytes)}".encodeToByteArray(),
        )
        val adapter = JuntaOfvirtualTriPhaseAdapter(transport = transport)
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
        assertEquals(
            listOf(JuntaOfvirtualTriPhaseAdapter.ENDPOINT, JuntaOfvirtualTriPhaseAdapter.ENDPOINT),
            transport.urls.map { it.uri.toASCIIString() },
        )
        assertTrue(transport.bodies[0].decodeToString().startsWith("op=pre&cop=sign&format=CAdES&algo=SHA1withRSA"))
        assertTrue(transport.bodies[1].decodeToString().startsWith("op=post&cop=sign&format=CAdES&algo=SHA1withRSA"))
        val encodedProperties = transport.bodies[0].decodeToString()
            .split('&')
            .single { it.startsWith("params=") }
            .substringAfter('=')
        val decodedProperties = Base64.getUrlDecoder().decode(encodedProperties).decodeToString()
        val parsedProperties = java.util.Properties().apply { load(decodedProperties.reader()) }
        assertEquals(setOf("filters", "serverUrl"), parsedProperties.stringPropertyNames())
        assertEquals("keyusage.digitalsignature:true;nonexpired:", parsedProperties.getProperty("filters"))
        assertEquals(JuntaOfvirtualTriPhaseAdapter.ENDPOINT, parsedProperties.getProperty("serverUrl"))
        assertEquals(null, parsedProperties.getProperty("mode"))
        request.close()
    }

    @Test
    fun wrongOriginProtocolAlgorithmOrPropertiesAreRejectedBeforeNetwork() = runBlocking {
        val identity = syntheticIdentity()
        val cases = listOf(
            request(origin = TrustedOrigin("https", "ws072.juntadeandalucia.es.evil.example", 443)),
            request(protocolId = JuntaTriPhaseAdapter.ID),
            request(algorithm = SigningAlgorithm.SHA256_WITH_RSA),
            request(properties = "filters=keyusage.digitalsignature:true;nonexpired:\nserverUrl=https://ws072.juntadeandalucia.es/afirma-validator-miniapplet-1_5/sign/TriPhaseSignatureService"),
            request(properties = "serverUrl=${JuntaOfvirtualTriPhaseAdapter.ENDPOINT}"),
            request(properties = "filters=keyusage.digitalsignature:true\nserverUrl=${JuntaOfvirtualTriPhaseAdapter.ENDPOINT}"),
            request(properties = "filters=keyusage.digitalsignature:true;nonexpired:\nmode=explicit\nserverUrl=${JuntaOfvirtualTriPhaseAdapter.ENDPOINT}"),
            request(properties = "extra=unsupported\nfilters=keyusage.digitalsignature:true;nonexpired:\nserverUrl=${JuntaOfvirtualTriPhaseAdapter.ENDPOINT}"),
        )

        cases.forEach { candidate ->
            val transport = QueueTransport()
            val result = JuntaOfvirtualTriPhaseAdapter(transport = transport).prepare(candidate, identity.chain)
            assertTrue(result is ProtocolPrepareResult.Failure)
            assertTrue(transport.urls.isEmpty())
            candidate.close()
        }
    }

    private fun request(
        origin: TrustedOrigin = TrustedOrigin("https", "ws072.juntadeandalucia.es", 443),
        protocolId: SigningProtocolId = JuntaOfvirtualTriPhaseAdapter.ID,
        algorithm: SigningAlgorithm = SigningAlgorithm.SHA1_WITH_RSA,
        properties: String = "filters=keyusage.digitalsignature:true;nonexpired:\nserverUrl=${JuntaOfvirtualTriPhaseAdapter.ENDPOINT}",
        challenge: ByteArray = "hello world".encodeToByteArray(),
    ): NormalizedSignRequest {
        val payload = MiniAppletPayloadCodec.encode(challenge, properties)
        return NormalizedSignRequest(
            requestId = UUID.fromString("00000000-0000-4000-8000-000000000091"),
            protocolId = protocolId,
            context = SigningContext(
                profileId = "junta-ofvirtual",
                profileVersion = 1,
                origin = origin,
                navigationId = NavigationId("00000000-0000-4000-8000-000000000092"),
                observedAt = Instant.parse("2026-07-22T00:00:00Z"),
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

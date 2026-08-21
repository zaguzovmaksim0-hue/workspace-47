package dev.junta.firmamobile.signing

import dev.junta.firmamobile.browser.NavigationId
import dev.junta.firmamobile.network.ProfileHttpCancellation
import dev.junta.firmamobile.network.ProfileHttpRequest
import dev.junta.firmamobile.network.ProfileHttpResponse
import dev.junta.firmamobile.network.ProfileHttpResult
import dev.junta.firmamobile.network.ProfileHttpTransport
import dev.junta.firmamobile.network.TrustedOrigin
import java.time.Instant
import java.util.ArrayDeque
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XuntaPadesTriPhaseAdapterTest {
    @Test
    fun exactPr004aContractUsesPadesWireAndReturnsServerDocumentId() = runBlocking {
        val identity = syntheticIdentity()
        val preXml = "<xml><firmas><firma Id=\"one\"><param n=\"PRE\">${Base64.getEncoder().encodeToString("pre".encodeToByteArray())}</param></firma></firmas></xml>"
        val finalId = "server-document-id".encodeToByteArray()
        val transport = QueueTransport(
            Base64.getUrlEncoder().encode(preXml.encodeToByteArray()),
            "OK NEWID=${Base64.getUrlEncoder().encodeToString(finalId)}".encodeToByteArray(),
        )
        val adapter = XuntaPadesTriPhaseAdapter(transport = transport)
        val request = request()
        val prepared = adapter.prepare(request, identity.chain) as ProtocolPrepareResult.Success
        val localSignature = prepared.preSign.withBytesToSign { input ->
            (JcaLocalSignatureEngine().sign(input, identity, SigningAlgorithm.SHA1_WITH_RSA) as LocalSignatureResult.Success).signature
        }
        val completion = adapter.complete(request, prepared.preSign, localSignature) as ProtocolCompletionResult.Success
        completion.signature.use { it.withBytes { bytes -> assertArrayEquals(finalId, bytes) } }
        assertEquals(2, transport.bodies.size)
        assertTrue(transport.bodies[0].contains("op=pre&cop=sign&format=pades&algo=SHA1withRSA"))
        assertTrue(transport.bodies[0].contains("&doc=ZG9j&"))
        assertTrue(transport.bodies[1].contains("op=post&cop=sign&format=pades&algo=SHA1withRSA"))
        assertTrue(transport.urls.all { it == XuntaPadesTriPhaseAdapter.ENDPOINT })
        request.close()
    }

    @Test
    fun wrongDocumentOrUnobservedPropertyNeverTouchesNetwork() = runBlocking {
        val identity = syntheticIdentity()
        val transport = QueueTransport()
        val adapter = XuntaPadesTriPhaseAdapter(transport = transport)
        val wrongDoc = request(document = "other")
        assertEquals(SigningErrorCode.INVALID_REQUEST, (adapter.prepare(wrongDoc, identity.chain) as ProtocolPrepareResult.Failure).code)
        wrongDoc.close()
        val unknownProperty = request(properties = properties() + "\nvisualSignature=true")
        assertEquals(SigningErrorCode.INVALID_REQUEST, (adapter.prepare(unknownProperty, identity.chain) as ProtocolPrepareResult.Failure).code)
        unknownProperty.close()
        assertTrue(transport.bodies.isEmpty())
    }

    private fun request(
        document: String = "doc",
        properties: String = properties(),
    ): NormalizedSignRequest {
        val documentBytes = document.encodeToByteArray()
        val payload = MiniAppletPayloadCodec.encode(documentBytes, properties)
        documentBytes.fill(0)
        return NormalizedSignRequest(
            requestId = UUID.fromString("123e4567-e89b-42d3-a456-426614174000"),
            protocolId = XuntaPadesTriPhaseAdapter.ID,
            context = SigningContext(
                profileId = XuntaPadesTriPhaseAdapter.PROFILE_ID,
                profileVersion = XuntaPadesTriPhaseAdapter.PROFILE_VERSION,
                origin = TrustedOrigin("https", "sede.xunta.gal", 443),
                navigationId = NavigationId("123e4567-e89b-42d3-a456-426614174001"),
                navigationEpoch = 7,
                observedAt = Instant.parse("2026-08-18T00:00:00Z"),
                pageUrl = XuntaPadesTriPhaseAdapter.SIGNING_PAGE_URL,
            ),
            algorithm = SigningAlgorithm.SHA1_WITH_RSA,
            format = SigningFormat.PADES,
            safeDescription = XuntaPadesTriPhaseAdapter.SAFE_DESCRIPTION,
            payload = payload,
        )
    }

    private fun properties(): String = buildList {
        XuntaPadesTriPhaseAdapter.FIXED_EXTRA_PROPERTIES.forEach { (key, value) -> add("$key=$value") }
        add("filters=nonexpired")
        add("locale=gl")
        add("idBorrador=synthetic")
    }.joinToString("\n")

    private class QueueTransport(vararg responses: ByteArray) : ProfileHttpTransport {
        private val responses = ArrayDeque(responses.toList())
        val bodies = mutableListOf<String>()
        val urls = mutableListOf<String>()
        override fun post(request: ProfileHttpRequest, cancellation: ProfileHttpCancellation): ProfileHttpResult {
            urls += request.url.uri.toASCIIString()
            bodies += request.withBody { it.decodeToString() }
            return ProfileHttpResult.Success(ProfileHttpResponse(responses.removeFirst()))
        }
    }
}

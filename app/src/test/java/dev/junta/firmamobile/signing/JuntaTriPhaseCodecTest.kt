package dev.junta.firmamobile.signing

import dev.junta.firmamobile.browser.NavigationId
import dev.junta.firmamobile.network.SafeNetworkUrlPolicy
import dev.junta.firmamobile.network.TrustedOrigin
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.Properties
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JuntaTriPhaseCodecTest {
    private val codec = JuntaTriPhaseCodec()
    private val identity = syntheticIdentity()

    @Test
    fun observedSinglePreAndPostWireContractRoundTrips() {
        val request = request(
            extraProperties = "filters=keyusage.digitalsignature:true;nonexpired:\nserverUrl=${SafeNetworkUrlPolicy.JUNTA_TRIPHASE_ENDPOINT}\n",
        )
        val decoded = codec.decodeRequest(request, identity.chain)
        val preRequest = codec.buildPreRequest(decoded)
        assertEquals(SafeNetworkUrlPolicy.JUNTA_TRIPHASE_ENDPOINT, preRequest.url.uri.toASCIIString())
        val preParams = preRequest.withBody { it.decodeToString().queryParameters() }

        assertEquals("pre", preParams.getValue("op"))
        assertEquals("sign", preParams.getValue("cop"))
        assertEquals("CAdES", preParams.getValue("format"))
        assertEquals("SHA1withRSA", preParams.getValue("algo"))
        assertArrayEquals("synthetic-document".encodeToByteArray(), Base64.getUrlDecoder().decode(preParams.getValue("doc")))
        assertTrue(preParams.getValue("cert").isNotBlank())
        val serializedProperties = Properties().apply {
            load(
                String(
                    Base64.getUrlDecoder().decode(preParams.getValue("params")),
                    StandardCharsets.UTF_8,
                ).reader(),
            )
        }
        assertEquals(
            "keyusage.digitalsignature:true;nonexpired:",
            serializedProperties.getProperty("filters"),
        )
        assertFalse(serializedProperties.containsKey("serverUrl"))

        val preResponse = urlBase64(
            """
            <xml><firmas format="CAdES">
              <firma Id="one"><param n="PRE">${standardBase64("pre-one")}</param><param n="NEED_PRE">true</param><param n="NEED_DATA">true</param></firma>
            </firmas></xml>
            """.trimIndent(),
        )
        val prepared = codec.parsePreResponse(decoded, preResponse)
        prepared.withBytesToSign { assertArrayEquals("pre-one".encodeToByteArray(), it) }

        val localSignature = sign(prepared)
        val expectedPk1 = localSignature.withBytes { it.copyOf() }
        preRequest.close()
        val postState = checkNotNull(prepared.consumeState(request))
        val postRequest = codec.buildPostRequest(postState, localSignature)
        postState.close()
        assertEquals(SafeNetworkUrlPolicy.JUNTA_TRIPHASE_ENDPOINT, postRequest.url.uri.toASCIIString())
        val postParams = postRequest.withBody { it.decodeToString().queryParameters() }
        val sessionXml = String(Base64.getUrlDecoder().decode(postParams.getValue("session")), StandardCharsets.UTF_8)

        assertEquals("post", postParams.getValue("op"))
        assertArrayEquals("synthetic-document".encodeToByteArray(), Base64.getUrlDecoder().decode(postParams.getValue("doc")))
        assertTrue(sessionXml.contains(Base64.getEncoder().encodeToString(expectedPk1)))
        assertTrue(sessionXml.substringAfter("Id=\"one\"").substringBefore("</firma>").contains("n=\"PRE\""))

        val finalBytes = "synthetic-final-cades".encodeToByteArray()
        val final = codec.parsePostResponse("OK NEWID=${Base64.getUrlEncoder().encodeToString(finalBytes)}".encodeToByteArray())
        final.use { signature -> signature.withBytes { assertArrayEquals(finalBytes, it) } }
        assertThrows(IllegalStateException::class.java) { localSignature.withBytes {} }
        expectedPk1.fill(0)
        prepared.close()
        postRequest.close()
        request.close()
    }

    @Test
    fun duplicateCriticalPropertiesDoctypeMalformedAndOversizedResponsesFailClosed() {
        val duplicateServer = request(
            extraProperties = "serverUrl=${SafeNetworkUrlPolicy.JUNTA_TRIPHASE_ENDPOINT}\nserverUrl=${SafeNetworkUrlPolicy.JUNTA_TRIPHASE_ENDPOINT}\n",
        )
        assertEquals(
            TriPhaseCodecError.INVALID_REQUEST,
            assertThrows(TriPhaseCodecException::class.java) {
                codec.decodeRequest(duplicateServer, identity.chain)
            }.code,
        )
        duplicateServer.close()

        val validRequest = request(
            extraProperties = "serverUrl=${SafeNetworkUrlPolicy.JUNTA_TRIPHASE_ENDPOINT}\n",
        )
        val decoded = codec.decodeRequest(validRequest, identity.chain)
        val doctype = urlBase64("<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><xml><firmas format=\"CAdES\"><firma><param n=\"PRE\">AA==</param></firma></firmas></xml>")
        assertEquals(
            TriPhaseCodecError.RESPONSE_FORMAT_INVALID,
            assertThrows(TriPhaseCodecException::class.java) { codec.parsePreResponse(decoded, doctype) }.code,
        )
        val duplicatePre = urlBase64("<xml><firmas format=\"CAdES\"><firma><param n=\"PRE\">AA==</param><param n=\"PRE\">AA==</param></firma></firmas></xml>")
        assertEquals(
            TriPhaseCodecError.RESPONSE_FORMAT_INVALID,
            assertThrows(TriPhaseCodecException::class.java) { codec.parsePreResponse(decoded, duplicatePre) }.code,
        )
        assertEquals(
            TriPhaseCodecError.RESPONSE_TOO_LARGE,
            assertThrows(TriPhaseCodecException::class.java) {
                codec.parsePreResponse(decoded, ByteArray(JuntaTriPhaseCodec.MAX_WIRE_RESPONSE_BYTES + 1))
            }.code,
        )
        val multiplePresigns = urlBase64(
            "<xml><firmas format=\"CAdES\"><firma><param n=\"PRE\">AA==</param></firma><firma><param n=\"PRE\">AA==</param></firma></firmas></xml>",
        )
        assertEquals(
            TriPhaseCodecError.RESPONSE_FORMAT_INVALID,
            assertThrows(TriPhaseCodecException::class.java) {
                codec.parsePreResponse(decoded, multiplePresigns)
            }.code,
        )
        decoded.close()
        validRequest.close()
    }

    @Test
    fun base64DialectsPropertiesAndNeedDataStaySourceCompatibleButNarrow() {
        val request = request(
            extraProperties = "serverUrl=${SafeNetworkUrlPolicy.JUNTA_TRIPHASE_ENDPOINT}\ndocumentId=remove-me\nmode=explicit\nnote=á\n",
            data = byteArrayOf(0xfb.toByte(), 0xff.toByte()),
        )
        val decoded = codec.decodeRequest(request, identity.chain)
        val preRequest = codec.buildPreRequest(decoded)
        var ownedBody: ByteArray? = null
        val params = preRequest.withBody { body ->
            ownedBody = body
            body.decodeToString().queryParameters()
        }

        assertEquals("-_8=", params.getValue("doc"))
        assertFalse(preRequest.withBody { it.decodeToString() }.contains("%3D"))
        val roundTripped = Properties().apply {
            load(Base64.getUrlDecoder().decode(params.getValue("params")).inputStream().reader(StandardCharsets.UTF_8))
        }
        assertEquals("explicit", roundTripped.getProperty("mode"))
        assertEquals("á", roundTripped.getProperty("note"))
        assertFalse(roundTripped.containsKey("serverUrl"))
        assertFalse(roundTripped.containsKey("documentId"))
        preRequest.close()
        assertTrue(checkNotNull(ownedBody).all { it == 0.toByte() })

        val preResponse = urlBase64(
            "<xml><firmas format=\"CAdES\"><firma Id=\"one\"><param n=\"PRE\">+/8=</param><param n=\"NEED_PRE\">false</param><param n=\"NEED_DATA\">false</param></firma></firmas></xml>",
        )
        val prepared = codec.parsePreResponse(decoded, preResponse)
        prepared.withBytesToSign {
            assertArrayEquals(byteArrayOf(0xfb.toByte(), 0xff.toByte()), it)
        }
        val localSignature = sign(prepared)
        val expectedPk1 = localSignature.withBytes { it.copyOf() }
        val postState = checkNotNull(prepared.consumeState(request))
        val post = codec.buildPostRequest(
            postState,
            localSignature,
        )
        postState.close()
        val postParams = post.withBody { it.decodeToString().queryParameters() }
        val session = Base64.getUrlDecoder().decode(postParams.getValue("session")).decodeToString()
        assertTrue(
            session.contains(
                "<param n=\"PK1\">${Base64.getEncoder().encodeToString(expectedPk1)}</param>",
            ),
        )
        assertFalse(session.contains("<param n=\"PRE\">"))
        assertEquals("-_8=", postParams.getValue("doc"))
        post.close()
        expectedPk1.fill(0)
        prepared.close()
        request.close()
    }

    @Test
    fun postResponseMustUseExactSuccessPrefixAndBoundedUrlBase64() {
        listOf(
            "OK",
            "OK OTHER=AAAA",
            "ERR-01:synthetic",
            "OK NEWID=%%%",
        ).forEach { response ->
            assertEquals(
                TriPhaseCodecError.RESPONSE_FORMAT_INVALID,
                assertThrows(TriPhaseCodecException::class.java) {
                    codec.parsePostResponse(response.encodeToByteArray())
                }.code,
            )
        }
    }

    private fun request(
        extraProperties: String,
        data: ByteArray = "synthetic-document".encodeToByteArray(),
    ): NormalizedSignRequest {
        val payload = MiniAppletPayloadCodec.encode(data, extraProperties)
        data.fill(0)
        return NormalizedSignRequest(
            requestId = UUID.fromString("00000000-0000-4000-8000-000000000001"),
            protocolId = JuntaTriPhaseAdapter.ID,
            context = SigningContext(
                profileId = "junta-andalucia",
                profileVersion = 1,
                origin = TrustedOrigin("https", "www.juntadeandalucia.es", 443),
                navigationId = NavigationId("00000000-0000-4000-8000-000000000002"),
                observedAt = Instant.parse("2026-07-12T00:00:00Z"),
            ),
            algorithm = SigningAlgorithm.SHA1_WITH_RSA,
            format = SigningFormat.CADES,
            safeDescription = "Autenticación con certificado",
            payload = payload,
        )
    }

    private fun String.queryParameters(): Map<String, String> = split('&').associate { pair ->
        pair.substringBefore('=') to pair.substringAfter('=', "")
    }

    private fun standardBase64(value: String): String =
        Base64.getEncoder().encodeToString(value.encodeToByteArray())

    private fun sign(preSign: PreSignResult): LocalSignature =
        preSign.withBytesToSign { input ->
            val result = JcaLocalSignatureEngine().sign(
                input = input,
                identity = identity,
                algorithm = SigningAlgorithm.SHA1_WITH_RSA,
            )
            (result as LocalSignatureResult.Success).signature
        }

    private fun urlBase64(value: String): ByteArray =
        Base64.getUrlEncoder().encode(value.encodeToByteArray())
}

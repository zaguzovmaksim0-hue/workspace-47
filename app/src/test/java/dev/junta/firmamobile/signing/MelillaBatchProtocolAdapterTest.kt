package dev.junta.firmamobile.signing

import dev.junta.firmamobile.browser.NavigationId
import dev.junta.firmamobile.network.ProfileHttpCancellation
import dev.junta.firmamobile.network.ProfileHttpFailure
import dev.junta.firmamobile.network.ProfileHttpRequest
import dev.junta.firmamobile.network.ProfileHttpResponse
import dev.junta.firmamobile.network.ProfileHttpResult
import dev.junta.firmamobile.network.ProfileHttpTransport
import dev.junta.firmamobile.network.TrustedOrigin
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.ArrayDeque
import java.util.Base64
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test

class MelillaBatchProtocolAdapterTest {
    @Test
    fun prepareAndCompleteMatchObservedAutoFirmaJsonBatchContract() {
        val preOne = "pre-one".encodeToByteArray()
        val preTwo = "pre-two".encodeToByteArray()
        val transport = RecordingTransport(
            presignResponse(preOne, preTwo),
            FINAL_RESPONSE.encodeToByteArray(),
        )
        val identity = syntheticIdentity()
        val request = batchRequest()
        val adapter = MelillaBatchProtocolAdapter(transport)

        val prepared = (adapter.prepare(request, identity.chain) as BatchProtocolPrepareResult.Success).preSign

        assertEquals(1, transport.calls.size)
        val preCall = transport.calls.single()
        assertEquals(PRE_URL, preCall.url.toString())
        val preFields = formFields(preCall.body)
        assertEquals(setOf("json", "certs"), preFields.keys)
        assertEquals(
            identity.chain.joinToString(";") { Base64.getUrlEncoder().encodeToString(it.encoded) },
            preFields.getValue("certs"),
        )

        val batchJson = JSONObject(decodeUrlBase64Utf8(preFields.getValue("json")))
        assertEquals(
            setOf("algorithm", "format", "suboperation", "singlesigns", "stoponerror"),
            batchJson.keySet(),
        )
        assertEquals("SHA256withRSA", batchJson.getString("algorithm"))
        assertEquals("CAdES", batchJson.getString("format"))
        assertEquals("sign", batchJson.getString("suboperation"))
        assertFalse(batchJson.getBoolean("stoponerror"))

        val documents = batchJson.getJSONArray("singlesigns")
        assertEquals(2, documents.length())
        val pades = documents.getJSONObject(0)
        assertEquals(setOf("id", "datareference", "format", "extraparams"), pades.keySet())
        assertEquals("doc-1", pades.getString("id"))
        assertEquals(DATA_URL_1, pades.getString("datareference"))
        assertEquals("PAdES", pades.getString("format"))
        assertEquals(
            Base64.getEncoder().encodeToString(
                "signatureSubFilter=ETSI.CAdES.detached".encodeToByteArray(),
            ),
            pades.getString("extraparams"),
        )
        val xades = documents.getJSONObject(1)
        assertEquals(
            setOf("id", "datareference", "format", "suboperation", "extraparams"),
            xades.keySet(),
        )
        assertEquals("doc-2", xades.getString("id"))
        assertEquals(DATA_URL_2, xades.getString("datareference"))
        assertEquals("XAdES", xades.getString("format"))
        assertEquals("sign", xades.getString("suboperation"))
        assertEquals(
            Base64.getEncoder().encodeToString("mode=implicit".encodeToByteArray()),
            xades.getString("extraparams"),
        )

        assertEquals(2, prepared.inputCount)
        prepared.withInput(0) { assertArrayEquals(preOne, it) }
        prepared.withInput(1) { assertArrayEquals(preTwo, it) }

        val completed = (adapter.complete(
                request,
                prepared,
                listOf(
                    LocalSignature("pk1-one".encodeToByteArray()),
                    LocalSignature("pk1-two".encodeToByteArray()),
                ),
            ) as BatchProtocolCompletionResult.Success)
        completed.response.use { response ->
            response.withBytes {
                assertEquals(FINAL_RESPONSE, it.toString(StandardCharsets.UTF_8))
            }
        }

        assertEquals(2, transport.calls.size)
        val postFields = formFields(transport.calls[1].body)
        assertEquals(POST_URL, transport.calls[1].url.toString())
        assertEquals(setOf("json", "certs", "tridata"), postFields.keys)
        assertEquals(preFields.getValue("json"), postFields.getValue("json"))
        assertEquals(preFields.getValue("certs"), postFields.getValue("certs"))

        val signInfo = JSONObject(decodeUrlBase64Utf8(postFields.getValue("tridata")))
            .getJSONArray("signinfo")
        val firstParams = signInfo.getJSONObject(0).getJSONObject("params")
        assertEquals(
            Base64.getEncoder().encodeToString("pk1-one".encodeToByteArray()),
            firstParams.getString("PK1"),
        )
        assertEquals(Base64.getEncoder().encodeToString(preOne), firstParams.getString("PRE"))
        assertEquals("true", firstParams.getString("NEED_PRE"))
        assertEquals("preserved", firstParams.getString("OPAQUE"))
        val secondParams = signInfo.getJSONObject(1).getJSONObject("params")
        assertEquals(
            Base64.getEncoder().encodeToString("pk1-two".encodeToByteArray()),
            secondParams.getString("PK1"),
        )
        assertFalse(secondParams.has("PRE"))
        assertEquals("preserved-2", secondParams.getString("OPAQUE"))

        prepared.close()
        request.close()
    }

    @Test
    fun changedOperationBindingFailsBeforeAnyNetworkCall() {
        val transport = RecordingTransport(presignResponse("one".encodeToByteArray(), "two".encodeToByteArray()))
        val request = batchRequest(
            preSignerUrl = "https://sede.melilla.es/sta/AutofirmaLote/presign/other-operation",
        )

        val result = MelillaBatchProtocolAdapter(transport).prepare(request, syntheticIdentity().chain)

        assertEquals(SigningErrorCode.INVALID_REQUEST, (result as BatchProtocolPrepareResult.Failure).code)
        assertTrue(transport.calls.isEmpty())
        request.close()
    }

    @Test
    fun duplicateProtectedPresignKeyFailsClosedWithoutPost() {
        val duplicate = """
            {"td":{"signinfo":[{"id":"doc-1","params":{
              "PRE":"${Base64.getEncoder().encodeToString("one".encodeToByteArray())}",
              "PRE":"${Base64.getEncoder().encodeToString("two".encodeToByteArray())}"
            }}]}}
        """.trimIndent().encodeToByteArray()
        val transport = RecordingTransport(duplicate)
        val request = batchRequest(documents = listOf(defaultDocuments().first()))

        val result = MelillaBatchProtocolAdapter(transport).prepare(request, syntheticIdentity().chain)

        assertEquals(SigningErrorCode.PROTOCOL_FAILED, (result as BatchProtocolPrepareResult.Failure).code)
        assertEquals(1, transport.calls.size)
        request.close()
    }

    @Test
    fun completionRequiresExactCardinalityAndConsumesPreparedStateOnce() {
        val transport = RecordingTransport(
            presignResponse("one".encodeToByteArray(), "two".encodeToByteArray()),
            FINAL_RESPONSE.encodeToByteArray(),
        )
        val request = batchRequest()
        val adapter = MelillaBatchProtocolAdapter(transport)
        val prepared = (adapter.prepare(request, syntheticIdentity().chain) as BatchProtocolPrepareResult.Success).preSign

        val wrongCount = adapter.complete(
            request,
            prepared,
            listOf(LocalSignature("only-one".encodeToByteArray())),
        )
        assertEquals(
            SigningErrorCode.PROTOCOL_FAILED,
            (wrongCount as BatchProtocolCompletionResult.Failure).code,
        )
        assertEquals(1, transport.calls.size)

        (adapter.complete(
                request,
                prepared,
                listOf(
                    LocalSignature("pk1-one".encodeToByteArray()),
                    LocalSignature("pk1-two".encodeToByteArray()),
                ),
            ) as BatchProtocolCompletionResult.Success).response.close()
        assertEquals(2, transport.calls.size)

        val reused = adapter.complete(
            request,
            prepared,
            listOf(
                LocalSignature("replay-one".encodeToByteArray()),
                LocalSignature("replay-two".encodeToByteArray()),
            ),
        )
        assertEquals(SigningErrorCode.PROTOCOL_FAILED, (reused as BatchProtocolCompletionResult.Failure).code)
        assertEquals(2, transport.calls.size)

        prepared.close()
        request.close()
    }

    private fun batchRequest(
        preSignerUrl: String = PRE_URL,
        documents: List<NormalizedBatchSigningDocument> = defaultDocuments(),
    ): NormalizedBatchSigningRequest = NormalizedBatchSigningRequest(
        requestId = REQUEST_ID,
        protocolId = SigningProtocolId("melilla-batch-autoscript-v1"),
        context = SigningContext(
            profileId = "melilla-sede",
            profileVersion = 1,
            origin = TrustedOrigin("https", "sede.melilla.es", 443),
            navigationId = NavigationId("melilla-batch-test"),
            navigationEpoch = 7L,
            observedAt = Instant.parse("2026-08-11T10:00:00Z"),
        ),
        algorithm = SigningAlgorithm.SHA256_WITH_RSA,
        format = BatchSigningFormat.CADES,
        suboperation = "sign",
        stopOnError = false,
        operationId = "runtime-operation-1",
        preSignerUrl = preSignerUrl,
        postSignerUrl = POST_URL,
        documents = documents,
    )

    private fun defaultDocuments(): List<NormalizedBatchSigningDocument> = listOf(
        NormalizedBatchSigningDocument(
            id = "doc-1",
            dataReference = DATA_URL_1,
            format = BatchSigningFormat.PADES,
        ),
        NormalizedBatchSigningDocument(
            id = "doc-2",
            dataReference = DATA_URL_2,
            format = BatchSigningFormat.XADES,
            suboperation = "sign",
        ),
    )

    private fun presignResponse(preOne: ByteArray, preTwo: ByteArray): ByteArray = JSONObject()
        .put(
            "td",
            JSONObject().put(
                "signinfo",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("id", "doc-1")
                            .put(
                                "params",
                                JSONObject()
                                    .put("PRE", Base64.getEncoder().encodeToString(preOne))
                                    .put("NEED_PRE", "true")
                                    .put("OPAQUE", "preserved"),
                            ),
                    )
                    .put(
                        JSONObject()
                            .put("id", "doc-2")
                            .put(
                                "params",
                                JSONObject()
                                    .put("PRE", Base64.getEncoder().encodeToString(preTwo))
                                    .put("OPAQUE", "preserved-2"),
                            ),
                    ),
            ),
        )
        .toString()
        .encodeToByteArray()

    private fun formFields(body: ByteArray): Map<String, String> =
        body.toString(StandardCharsets.UTF_8)
            .split('&')
            .associate { it.substringBefore('=') to it.substringAfter('=') }

    private fun decodeUrlBase64Utf8(value: String): String =
        Base64.getUrlDecoder().decode(value).toString(StandardCharsets.UTF_8)

    private fun JSONObject.keySet(): Set<String> = buildSet {
        val names = keys()
        while (names.hasNext()) add(names.next())
    }

    private class RecordingTransport(vararg responses: ByteArray) : ProfileHttpTransport {
        data class Call(val url: URI, val body: ByteArray)

        val calls = mutableListOf<Call>()
        private val responses = ArrayDeque(responses.map(ByteArray::copyOf))

        override fun post(
            request: ProfileHttpRequest,
            cancellation: ProfileHttpCancellation,
        ): ProfileHttpResult {
            calls += Call(request.url.uri, request.withBody { it.copyOf() })
            val response = responses.pollFirst()
                ?: return ProfileHttpResult.Failure(ProfileHttpFailure.NETWORK_ERROR)
            return ProfileHttpResult.Success(ProfileHttpResponse(response))
        }
    }

    private companion object {
        val REQUEST_ID: UUID = UUID.fromString("11111111-2222-4333-8444-555555555555")
        const val PRE_URL = "https://sede.melilla.es/sta/AutofirmaLote/presign/runtime-operation-1"
        const val POST_URL = "https://sede.melilla.es/sta/AutofirmaLote/postsign/runtime-operation-1"
        const val DATA_URL_1 = "https://sede.melilla.es/sta/AutofirmaLote/getdata/runtime-operation-1/doc-1"
        const val DATA_URL_2 = "https://sede.melilla.es/sta/AutofirmaLote/getdata/runtime-operation-1/doc-2"
        const val FINAL_RESPONSE = "{\"signs\":[{\"id\":\"doc-1\",\"result\":\"DONE_AND_SAVED\"},{\"id\":\"doc-2\",\"result\":\"DONE_AND_SAVED\"}]}"
    }
}

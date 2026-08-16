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
import java.time.Instant
import java.util.ArrayDeque
import java.util.Base64
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class LugoBatchProtocolAdapterTest {
    @Test
    fun exactLugoXmlBatchUsesOfficialPrePk1PostWireContract() {
        val pre = "synthetic-pre".encodeToByteArray()
        val transport = RecordingTransport(
            triData(pre, needPre = false),
            finalResult(),
        )
        val request = request()
        val identity = syntheticIdentity()
        val adapter = LugoBatchProtocolAdapter(transportFactory = { transport })

        val prepared = (adapter.prepare(request, identity.chain) as BatchProtocolPrepareResult.Success).preSign
        assertEquals(1, transport.calls.size)
        val first = transport.calls.single()
        assertEquals(PRE_URL, first.url.toString())
        assertTrue(first.body.isEmpty())
        val preFields = queryFields(checkNotNull(first.query))
        assertEquals(setOf("xml", "certs"), preFields.keys)
        assertEquals(
            identity.chain.joinToString(";") { Base64.getUrlEncoder().encodeToString(it.encoded) },
            preFields.getValue("certs"),
        )
        val batchXml = Base64.getUrlDecoder().decode(preFields.getValue("xml")).decodeToString()
        assertTrue(batchXml.contains("algorithm=\"SHA256withRSA\""))
        assertTrue(batchXml.contains("<datasource>$HASH</datasource>"))
        assertTrue(batchXml.contains("<format>CAdES</format>"))
        assertTrue(batchXml.contains("<suboperation>sign</suboperation>"))
        assertTrue(batchXml.contains("precalculatedHashAlgorithm".encodeToByteArray().let(Base64.getEncoder()::encodeToString)))
        prepared.withInput(0) { assertEquals("synthetic-pre", it.decodeToString()) }

        val completed = adapter.complete(
            request,
            prepared,
            listOf(LocalSignature("synthetic-pk1".encodeToByteArray())),
        ) as BatchProtocolCompletionResult.Success
        completed.response.use { response ->
            response.withBytes { assertEquals(finalResult().decodeToString(), it.decodeToString()) }
        }

        assertEquals(2, transport.calls.size)
        val postFields = queryFields(checkNotNull(transport.calls[1].query))
        assertEquals(setOf("xml", "certs", "tridata"), postFields.keys)
        assertEquals(preFields.getValue("xml"), postFields.getValue("xml"))
        assertEquals(preFields.getValue("certs"), postFields.getValue("certs"))
        val postedTriData = Base64.getUrlDecoder().decode(postFields.getValue("tridata")).decodeToString()
        assertTrue(postedTriData.contains("<param n=\"PK1\">${Base64.getEncoder().encodeToString("synthetic-pk1".encodeToByteArray())}</param>"))
        assertFalse(postedTriData.contains("<param n=\"PRE\">"))
        assertTrue(postedTriData.contains("<param n=\"OPAQUE\">preserved</param>"))

        prepared.close()
        request.close()
    }

    @Test
    fun finalResultMustMatchTheSingleObservedLugoSuccessTuple() {
        for (valid in listOf("DONE_AND_SAVED", "DONE_BUT_NOT_SAVED_YET")) {
            listOf(
                "<signresults><signresult id=\"$DOCUMENT_ID\" result=\"$valid\"/></signresults>",
                "<legacyBatchResult><signresult id=\"$DOCUMENT_ID\" result=\"$valid\"/></legacyBatchResult>",
            ).forEach { responseXml ->
                assertEquals(
                    true,
                    executeWithFinal(responseXml) is BatchProtocolCompletionResult.Success,
                )
            }
        }

        val invalid = listOf(
            "<signresults><signresult id=\"foreign\" result=\"DONE_AND_SAVED\"/></signresults>",
            "<signresults><signresult id=\"$DOCUMENT_ID\" result=\"ERROR\"/></signresults>",
            "<signresults><signresult id=\"$DOCUMENT_ID\" result=\"DONE_AND_SAVED\"/><signresult id=\"other\" result=\"DONE_AND_SAVED\"/></signresults>",
            "<signresults extra=\"1\"><signresult id=\"$DOCUMENT_ID\" result=\"DONE_AND_SAVED\"/></signresults>",
            "<signresults><signresult id=\"$DOCUMENT_ID\" result=\"DONE_AND_SAVED\" extra=\"1\"/></signresults>",
            "<!DOCTYPE signresults [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><signresults><signresult id=\"$DOCUMENT_ID\" result=\"DONE_AND_SAVED\"/></signresults>",
            "not-xml",
        )
        invalid.forEach { raw ->
            val result = executeWithFinal(raw)
            assertEquals(SigningErrorCode.PROTOCOL_FAILED, (result as BatchProtocolCompletionResult.Failure).code)
        }
    }

    private fun executeWithFinal(raw: String): BatchProtocolCompletionResult {
        val request = request()
        val transport = RecordingTransport(triData("synthetic-pre".encodeToByteArray(), false), raw.encodeToByteArray())
        val adapter = LugoBatchProtocolAdapter(transportFactory = { transport })
        val prepared = (adapter.prepare(request, syntheticIdentity().chain) as BatchProtocolPrepareResult.Success).preSign
        return adapter.complete(request, prepared, listOf(LocalSignature("synthetic-pk1".encodeToByteArray()))).also {
            prepared.close()
            request.close()
        }
    }

    @Test
    fun mismatchedSessionAndForeignResultFailClosed() {
        val request = request(postSignerUrl = POST_URL.replace(SESSION, OTHER_SESSION))
        val transport = RecordingTransport(triData("pre".encodeToByteArray(), false))
        val result = LugoBatchProtocolAdapter(transportFactory = { transport }).prepare(request, syntheticIdentity().chain)
        assertEquals(SigningErrorCode.INVALID_REQUEST, (result as BatchProtocolPrepareResult.Failure).code)
        assertTrue(transport.calls.isEmpty())
        request.close()
    }

    private fun request(postSignerUrl: String = POST_URL) = NormalizedBatchSigningRequest(
        requestId = UUID.fromString("11111111-2222-4333-8444-555555555555"),
        protocolId = LugoBatchProtocolAdapter.ID,
        context = SigningContext(
            profileId = LugoBatchProtocolAdapter.PROFILE_ID,
            profileVersion = 1,
            origin = TrustedOrigin("https", "sede.deputacionlugo.org", 443),
            navigationId = NavigationId("lugo-batch-test"),
            navigationEpoch = 7L,
            observedAt = Instant.parse("2026-08-16T00:00:00Z"),
        ),
        algorithm = SigningAlgorithm.SHA256_WITH_RSA,
        format = BatchSigningFormat.CADES,
        suboperation = "sign",
        stopOnError = true,
        operationId = SESSION,
        preSignerUrl = PRE_URL,
        postSignerUrl = postSignerUrl,
        documents = listOf(
            NormalizedBatchSigningDocument(
                id = DOCUMENT_ID,
                dataReference = HASH,
                format = BatchSigningFormat.CADES,
                suboperation = "sign",
            ),
        ),
    )

    private fun triData(pre: ByteArray, needPre: Boolean): ByteArray = """
        <xml><firmas><firma Id="$DOCUMENT_ID">
          <param n="PRE">${Base64.getEncoder().encodeToString(pre)}</param>
          ${if (needPre) "<param n=\"NEED_PRE\">true</param>" else ""}
          <param n="OPAQUE">preserved</param>
        </firma></firmas></xml>
    """.trimIndent().encodeToByteArray()

    private fun finalResult(): ByteArray =
        "<signresults><signresult id=\"$DOCUMENT_ID\" result=\"DONE_AND_SAVED\"/></signresults>".encodeToByteArray()

    private fun queryFields(value: String): Map<String, String> =
        value.split('&').associate { it.substringBefore('=') to it.substringAfter('=') }

    private class RecordingTransport(vararg responses: ByteArray) : ProfileHttpTransport {
        data class Call(val url: URI, val body: ByteArray, val query: String?)
        val calls = mutableListOf<Call>()
        private val responses = ArrayDeque(responses.map(ByteArray::copyOf))
        override fun post(request: ProfileHttpRequest, cancellation: ProfileHttpCancellation): ProfileHttpResult {
            calls += Call(request.url.uri, request.withBody { it.copyOf() }, request.encodedQuery)
            val response = responses.pollFirst()
                ?: return ProfileHttpResult.Failure(ProfileHttpFailure.NETWORK_ERROR)
            return ProfileHttpResult.Success(ProfileHttpResponse(response))
        }
    }

    private companion object {
        const val SESSION = "A1B2C3D4E5F60718293A4B5C6D7E8F90"
        const val OTHER_SESSION = "B1C2D3E4F5A60718293A4B5C6D7E8F90"
        const val DOCUMENT_ID = "1786890375115325"
        const val HASH = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        const val PRE_URL = "https://sede.deputacionlugo.org/opencms/clientsigner/BatchPresigner/service/$SESSION"
        const val POST_URL = "https://sede.deputacionlugo.org/opencms/clientsigner/BatchPostsigner/service/$SESSION"
    }
}

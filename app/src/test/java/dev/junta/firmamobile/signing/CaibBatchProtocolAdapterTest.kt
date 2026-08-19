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
class CaibBatchProtocolAdapterTest {
    @Test
    fun exactCaibBatchUsesPrePk1PostContract() {
        val transport = RecordingTransport(triData("synthetic-pre".encodeToByteArray()), finalResult())
        val request = request()
        val identity = syntheticIdentity()
        val adapter = CaibBatchProtocolAdapter(transportFactory = { transport })

        val prepared = (adapter.prepare(request, identity.chain) as BatchProtocolPrepareResult.Success).preSign
        assertEquals(1, transport.calls.size)
        val preFields = fields(checkNotNull(transport.calls.single().query))
        assertEquals(setOf("xml", "certs"), preFields.keys)
        assertEquals(BATCH_B64, preFields.getValue("xml"))
        prepared.withInput(0) { assertEquals("synthetic-pre", it.decodeToString()) }

        val completed = adapter.complete(
            request,
            prepared,
            listOf(LocalSignature("synthetic-pk1".encodeToByteArray())),
        ) as BatchProtocolCompletionResult.Success
        completed.response.close()
        val postFields = fields(checkNotNull(transport.calls[1].query))
        assertEquals(setOf("xml", "certs", "tridata"), postFields.keys)
        val postedTriData = Base64.getUrlDecoder().decode(postFields.getValue("tridata")).decodeToString()
        assertTrue(postedTriData.contains("<param n=\"PK1\">${Base64.getEncoder().encodeToString("synthetic-pk1".encodeToByteArray())}</param>"))
        assertFalse(postedTriData.contains("<param n=\"PRE\">"))
        assertTrue(postedTriData.contains("<param n=\"OPAQUE\">preserved</param>"))
        prepared.close()
        request.close()
    }

    @Test
    fun foreignTokenAndInvalidFinalResultFailClosed() {
        val mismatched = request(post = POST_URL.replace(TOKEN, OTHER_TOKEN))
        val firstTransport = RecordingTransport(triData("pre".encodeToByteArray()))
        val first = CaibBatchProtocolAdapter(transportFactory = { firstTransport }).prepare(mismatched, syntheticIdentity().chain)
        assertEquals(SigningErrorCode.INVALID_REQUEST, (first as BatchProtocolPrepareResult.Failure).code)
        assertTrue(firstTransport.calls.isEmpty())
        mismatched.close()

        for (raw in listOf(
            "<signresults><signresult id=\"$SIGN_ID\" result=\"ERROR\"/></signresults>",
            "<signresults><signresult id=\"foreign\" result=\"DONE_AND_SAVED\"/></signresults>",
            "<!DOCTYPE x [<!ENTITY y SYSTEM \"file:///etc/passwd\">]><signresults><signresult id=\"$SIGN_ID\" result=\"DONE_AND_SAVED\"/></signresults>",
        )) {
            val req = request()
            val transport = RecordingTransport(triData("pre".encodeToByteArray()), raw.encodeToByteArray())
            val adapter = CaibBatchProtocolAdapter(transportFactory = { transport })
            val prepared = (adapter.prepare(req, syntheticIdentity().chain) as BatchProtocolPrepareResult.Success).preSign
            val result = adapter.complete(req, prepared, listOf(LocalSignature("pk1".encodeToByteArray())))
            assertEquals(SigningErrorCode.PROTOCOL_FAILED, (result as BatchProtocolCompletionResult.Failure).code)
            prepared.close(); req.close()
        }
    }

    private fun request(post: String = POST_URL) = NormalizedBatchSigningRequest(
        requestId = UUID.fromString("11111111-2222-4333-8444-555555555555"),
        protocolId = CaibBatchProtocolAdapter.ID,
        context = SigningContext(
            profileId = CaibBatchProtocolAdapter.PROFILE_ID,
            profileVersion = 1,
            origin = TrustedOrigin("https", "intranet.caib.es", 443),
            navigationId = NavigationId("caib-batch-test"),
            navigationEpoch = 7L,
            observedAt = Instant.parse("2026-08-18T00:00:00Z"),
        ),
        algorithm = SigningAlgorithm.SHA256_WITH_RSA,
        format = BatchSigningFormat.PADES,
        suboperation = "sign",
        stopOnError = false,
        operationId = TOKEN,
        preSignerUrl = PRE_URL,
        postSignerUrl = post,
        documents = listOf(
            NormalizedBatchSigningDocument(
                id = SIGN_ID,
                dataReference = BATCH_B64,
                format = BatchSigningFormat.PADES,
                suboperation = "sign",
            ),
        ),
    )

    private fun triData(pre: ByteArray): ByteArray = """
        <xml><firmas><firma Id="$SIGN_ID">
          <param n="PRE">${Base64.getEncoder().encodeToString(pre)}</param>
          <param n="OPAQUE">preserved</param>
        </firma></firmas></xml>
    """.trimIndent().encodeToByteArray()

    private fun finalResult(): ByteArray =
        "<signresults><signresult id=\"$SIGN_ID\" result=\"DONE_AND_SAVED\"/></signresults>".encodeToByteArray()

    private fun fields(value: String): Map<String, String> =
        value.split('&').associate { it.substringBefore('=') to it.substringAfter('=') }

    private class RecordingTransport(vararg responses: ByteArray) : ProfileHttpTransport {
        data class Call(val url: URI, val query: String?)
        val calls = mutableListOf<Call>()
        private val responses = ArrayDeque(responses.map(ByteArray::copyOf))
        override fun post(request: ProfileHttpRequest, cancellation: ProfileHttpCancellation): ProfileHttpResult {
            calls += Call(request.url.uri, request.encodedQuery)
            val response = responses.pollFirst()
                ?: return ProfileHttpResult.Failure(ProfileHttpFailure.NETWORK_ERROR)
            return ProfileHttpResult.Success(ProfileHttpResponse(response))
        }
    }

    private companion object {
        const val TOKEN = "ABCDEFGHIJKLMNOPQRSTUVWX1234"
        const val OTHER_TOKEN = "BCDEFGHIJKLMNOPQRSTUVWXY1234"
        val SIGN_ID: String = Base64.getUrlEncoder().withoutPadding().encodeToString("$TOKEN|0".encodeToByteArray())
        val BATCH_B64: String = Base64.getUrlEncoder().withoutPadding().encodeToString("synthetic-caib-batch".encodeToByteArray())
        val PRE_URL = "https://intranet.caib.es/portafibback/public/signmodule/requestPlugin/$TOKEN/-1/BatchPresigner"
        val POST_URL = "https://intranet.caib.es/portafibback/public/signmodule/requestPlugin/$TOKEN/-1/BatchPostsigner"
    }
}

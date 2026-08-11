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
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
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
class ExtremaduraBatchProtocolAdapterTest {
    @Test
    fun exactExtremaduraContractUsesItsOwnProtocolAndRuntimeOrigin() {
        val transport = RecordingTransport(presignResponse())
        val request = extremaduraRequest()

        val result = ExtremaduraBatchProtocolAdapter(transport)
            .prepare(request, syntheticIdentity().chain)

        val prepared = (result as BatchProtocolPrepareResult.Success).preSign
        assertEquals(ExtremaduraBatchProtocolAdapter.ID, request.protocolId)
        assertEquals(1, transport.calls.size)
        assertEquals(PRE_URL, transport.calls.single().url.toString())

        prepared.close()
        request.close()
    }

    @Test
    fun melillaOwnershipCannotExecuteThroughExtremaduraAdapter() {
        val transport = RecordingTransport(presignResponse())
        val request = extremaduraRequest(
            origin = TrustedOrigin("https", "sede.melilla.es", 443),
            preSignerUrl =
                "https://sede.melilla.es/sta/AutofirmaLote/presign/runtime-operation-1",
            postSignerUrl =
                "https://sede.melilla.es/sta/AutofirmaLote/postsign/runtime-operation-1",
            dataReference =
                "https://sede.melilla.es/sta/AutofirmaLote/getdata/runtime-operation-1/doc-1",
        )

        val result = ExtremaduraBatchProtocolAdapter(transport)
            .prepare(request, syntheticIdentity().chain)

        assertEquals(
            SigningErrorCode.INVALID_REQUEST,
            (result as BatchProtocolPrepareResult.Failure).code,
        )
        assertTrue(transport.calls.isEmpty())
        request.close()
    }

    private fun extremaduraRequest(
        origin: TrustedOrigin = EXTREMADURA_ORIGIN,
        preSignerUrl: String = PRE_URL,
        postSignerUrl: String = POST_URL,
        dataReference: String = DATA_URL,
    ): NormalizedBatchSigningRequest = NormalizedBatchSigningRequest(
        requestId = REQUEST_ID,
        protocolId = ExtremaduraBatchProtocolAdapter.ID,
        context = SigningContext(
            profileId = "extremadura-tramites",
            profileVersion = 1,
            origin = origin,
            navigationId = NavigationId("extremadura-batch-test"),
            navigationEpoch = 7L,
            observedAt = Instant.parse("2026-08-12T00:00:00Z"),
        ),
        algorithm = SigningAlgorithm.SHA256_WITH_RSA,
        format = BatchSigningFormat.CADES,
        suboperation = "sign",
        stopOnError = false,
        operationId = "runtime-operation-1",
        preSignerUrl = preSignerUrl,
        postSignerUrl = postSignerUrl,
        documents = listOf(
            NormalizedBatchSigningDocument(
                id = "doc-1",
                dataReference = dataReference,
                format = BatchSigningFormat.CADES,
            ),
        ),
    )

    private fun presignResponse(): ByteArray = JSONObject()
        .put(
            "td",
            JSONObject().put(
                "signinfo",
                JSONArray().put(
                    JSONObject()
                        .put("id", "doc-1")
                        .put(
                            "params",
                            JSONObject()
                                .put(
                                    "PRE",
                                    Base64.getEncoder().encodeToString("pre-one".encodeToByteArray()),
                                )
                                .put("NEED_PRE", "true"),
                        ),
                ),
            ),
        )
        .toString()
        .encodeToByteArray()

    private class RecordingTransport(vararg responses: ByteArray) : ProfileHttpTransport {
        data class Call(val url: URI)

        val calls = mutableListOf<Call>()
        private val responses = ArrayDeque(responses.map(ByteArray::copyOf))

        override fun post(
            request: ProfileHttpRequest,
            cancellation: ProfileHttpCancellation,
        ): ProfileHttpResult {
            calls += Call(request.url.uri)
            val response = responses.pollFirst()
                ?: return ProfileHttpResult.Failure(ProfileHttpFailure.NETWORK_ERROR)
            return ProfileHttpResult.Success(ProfileHttpResponse(response))
        }
    }

    private companion object {
        val REQUEST_ID: UUID = UUID.fromString("11111111-2222-4333-8444-555555555555")
        val EXTREMADURA_ORIGIN = TrustedOrigin("https", "tramites.juntaex.es", 443)
        const val PRE_URL =
            "https://tramites.juntaex.es/sta/AutofirmaLote/presign/runtime-operation-1"
        const val POST_URL =
            "https://tramites.juntaex.es/sta/AutofirmaLote/postsign/runtime-operation-1"
        const val DATA_URL =
            "https://tramites.juntaex.es/sta/AutofirmaLote/getdata/runtime-operation-1/doc-1"
    }
}

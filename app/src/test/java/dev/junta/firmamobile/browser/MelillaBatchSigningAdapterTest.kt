package dev.junta.firmamobile.browser

import dev.junta.firmamobile.network.TrustedOrigin
import dev.junta.firmamobile.profile.BuildTrustPolicy
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SiteProfileRegistry
import dev.junta.firmamobile.signing.BatchSigningFormat
import dev.junta.firmamobile.signing.BatchProtocolResponse
import dev.junta.firmamobile.signing.MelillaBatchProtocolAdapter
import dev.junta.firmamobile.signing.SigningAlgorithm
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.json.JSONObject
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
class MelillaBatchSigningAdapterTest {
    @Test
    fun convertsValidatedBridgeRequestIntoExactNormalizedSigningRequest() {
        val observedAt = Instant.parse("2026-08-11T15:00:00Z")
        val bridgeRequest = MelillaBatchBridgeRequest(
            requestId = REQUEST_ID,
            documentId = DOCUMENT_ID,
            batchPreSignerUrl = PRE_URL,
            batchPostSignerUrl = POST_URL,
            operationId = OPERATION_ID,
            algorithm = "SHA256withRSA",
            format = "CAdES",
            suboperation = "sign",
            stopOnError = false,
            documents = listOf(
                MelillaBatchDocument(
                    id = "runtime-document-1",
                    dataReference = DATA_URL_1,
                    format = "PAdES",
                ),
                MelillaBatchDocument(
                    id = "runtime-document-2",
                    dataReference = DATA_URL_2,
                    format = "XAdES",
                    suboperation = "sign",
                ),
            ),
            profileId = ProfileId(MelillaBatchBridgeAdapter.PROFILE_ID),
            sourceOrigin = TrustedOrigin("https", "sede.melilla.es", 443),
            navigationEpoch = 7L,
        )
        val adapter = MelillaBatchSigningAdapter(
            registry = SiteProfileRegistry(BuiltInSiteProfiles.catalog, BuildTrustPolicy.QA),
            clock = Clock.fixed(observedAt, ZoneOffset.UTC),
        )

        val normalized = checkNotNull(adapter.normalize(bridgeRequest))
        assertEquals(REQUEST_ID, normalized.requestId)
        assertEquals(MelillaBatchProtocolAdapter.ID, normalized.protocolId)
        assertEquals("melilla-sede", normalized.context.profileId)
        assertEquals(1, normalized.context.profileVersion)
        assertEquals(bridgeRequest.sourceOrigin, normalized.context.origin)
        assertEquals(DOCUMENT_ID.toString(), normalized.context.navigationId.value)
        assertEquals(7L, normalized.context.navigationEpoch)
        assertEquals(observedAt, normalized.context.observedAt)
        assertEquals(SigningAlgorithm.SHA256_WITH_RSA, normalized.algorithm)
        assertEquals(BatchSigningFormat.CADES, normalized.format)
        assertEquals("sign", normalized.suboperation)
        assertEquals(false, normalized.stopOnError)
        assertEquals(OPERATION_ID, normalized.operationId)
        assertEquals(PRE_URL, normalized.preSignerUrl)
        assertEquals(POST_URL, normalized.postSignerUrl)
        assertEquals(
            listOf("runtime-document-1", "runtime-document-2"),
            normalized.documents.map { it.id },
        )
        assertEquals(
            listOf(BatchSigningFormat.PADES, BatchSigningFormat.XADES),
            normalized.documents.map { it.format },
        )
        assertEquals(
            listOf(null, "sign"),
            normalized.documents.map { it.suboperation },
        )
        normalized.close()
    }

    @Test
    fun wrapsProtocolResponseIntoTheExistingOneShotBatchReplyChannel() {
        var posted: String? = null
        val channel = MelillaBatchReplyChannel(
            requestId = REQUEST_ID,
            postMessage = { posted = it },
        )
        val adapter = MelillaBatchSigningAdapter(
            registry = SiteProfileRegistry(BuiltInSiteProfiles.catalog, BuildTrustPolicy.QA),
        )
        val reply = adapter.replySink(channel)
        val response = BatchProtocolResponse("{\"resultado\":\"ok\"}".encodeToByteArray())

        assertEquals(REQUEST_ID, reply.requestId)
        assertTrue(reply.success(response))
        val envelope = JSONObject(checkNotNull(posted))
        assertEquals(MelillaBatchBridgeAdapter.BATCH_RESULT_TYPE, envelope.getString("type"))
        assertEquals(REQUEST_ID.toString(), envelope.getString("requestId"))
        assertEquals("success", envelope.getString("status"))
        assertEquals("{\"resultado\":\"ok\"}", envelope.getString("validationResponse"))
        assertFalse(reply.failure(dev.junta.firmamobile.signing.SigningErrorCode.PROTOCOL_FAILED))

        response.close()
    }

    @Test
    fun malformedUtf8ResponseStillTerminatesReplyOwnership() {
        var terminalCount = 0
        val channel = MelillaBatchReplyChannel(
            requestId = REQUEST_ID,
            postMessage = { error("malformed response must not be delivered") },
            onTerminal = { terminalCount += 1 },
        )
        val adapter = MelillaBatchSigningAdapter(
            registry = SiteProfileRegistry(BuiltInSiteProfiles.catalog, BuildTrustPolicy.QA),
        )
        val reply = adapter.replySink(channel)
        val response = BatchProtocolResponse(byteArrayOf(0xC3.toByte(), 0x28))

        assertFalse(reply.success(response))
        assertEquals(1, terminalCount)
        assertFalse(reply.abandon())

        response.close()
    }

    private companion object {
        val REQUEST_ID: UUID = UUID.fromString("123e4567-e89b-42d3-a456-426614174000")
        val DOCUMENT_ID: UUID = UUID.fromString("123e4567-e89b-42d3-a456-426614174001")
        const val OPERATION_ID = "runtime-operation-1"
        const val PRE_URL =
            "https://sede.melilla.es/sta/AutofirmaLote/presign/runtime-operation-1"
        const val POST_URL =
            "https://sede.melilla.es/sta/AutofirmaLote/postsign/runtime-operation-1"
        const val DATA_URL_1 =
            "https://sede.melilla.es/sta/AutofirmaLote/getdata/runtime-operation-1/runtime-document-1"
        const val DATA_URL_2 =
            "https://sede.melilla.es/sta/AutofirmaLote/getdata/runtime-operation-1/runtime-document-2"
    }
}

package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.signing.SigningErrorCode
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
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
class MelillaBatchBridgeAdapterTest {
    @Test
    fun dedicatedAdapterAcceptsOnlyTheObservedBoundedBatchContract() {
        val result = MelillaBatchBridgeAdapter(
            activeProfileId = { ProfileId(MelillaBatchBridgeAdapter.PROFILE_ID) },
        ).route(
            rawMessage = portalOwnedBatchEnvelope(),
            sourceOrigin = Uri.parse(MelillaBatchBridgeAdapter.SOURCE_ORIGIN),
            isMainFrame = true,
            navigationEpoch = 7,
        )

        val accepted = result as MelillaBatchBridgeRouteResult.Accepted
        assertEquals(REQUEST_ID, accepted.request.requestId.toString())
        assertEquals(DOCUMENT_ID, accepted.request.documentId.toString())
        assertEquals(7L, accepted.request.navigationEpoch)
        assertEquals("SHA256withRSA", accepted.request.algorithm)
        assertEquals("CAdES", accepted.request.format)
        assertEquals("sign", accepted.request.suboperation)
        assertFalse(accepted.request.stopOnError)
        assertEquals(1, accepted.request.documents.size)
        assertEquals("runtime-document-1", accepted.request.documents.single().id)
    }

    @Test
    fun dedicatedAdapterRejectsBatchBindingChangesBeforeAnyConsumerCanSeeIt() {
        val changed = portalOwnedBatchEnvelope().replace(
            "runtime-operation-1/runtime-document-1",
            "runtime-operation-1/runtime-document-2",
        )

        val result = MelillaBatchBridgeAdapter(
            activeProfileId = { ProfileId(MelillaBatchBridgeAdapter.PROFILE_ID) },
        ).route(
            rawMessage = changed,
            sourceOrigin = Uri.parse(MelillaBatchBridgeAdapter.SOURCE_ORIGIN),
            isMainFrame = true,
            navigationEpoch = 7,
        )

        val rejected = result as MelillaBatchBridgeRouteResult.Rejected
        assertEquals(SigningErrorCode.INVALID_REQUEST, rejected.code)
        assertEquals(REQUEST_ID, rejected.requestId?.toString())
    }

    @Test
    fun dedicatedAdapterDoesNotConsumeWrongProfileOriginFrameOrEpoch() {
        val adapter = MelillaBatchBridgeAdapter(
            activeProfileId = { ProfileId("another-profile") },
        )

        assertTrue(
            adapter.route(
                portalOwnedBatchEnvelope(),
                Uri.parse(MelillaBatchBridgeAdapter.SOURCE_ORIGIN),
                isMainFrame = true,
                navigationEpoch = 7,
            ) is MelillaBatchBridgeRouteResult.Rejected,
        )
        assertTrue(
            adapter.route(
                portalOwnedBatchEnvelope(),
                Uri.parse("https://sede.melilla.es.evil.example"),
                isMainFrame = true,
                navigationEpoch = 7,
            ) is MelillaBatchBridgeRouteResult.Rejected,
        )
        assertTrue(
            MelillaBatchBridgeAdapter(
                activeProfileId = { ProfileId(MelillaBatchBridgeAdapter.PROFILE_ID) },
            ).route(
                portalOwnedBatchEnvelope(),
                Uri.parse(MelillaBatchBridgeAdapter.SOURCE_ORIGIN),
                isMainFrame = false,
                navigationEpoch = 7,
            ) is MelillaBatchBridgeRouteResult.Rejected,
        )
        assertTrue(
            MelillaBatchBridgeAdapter(
                activeProfileId = { ProfileId(MelillaBatchBridgeAdapter.PROFILE_ID) },
            ).route(
                portalOwnedBatchEnvelope(),
                Uri.parse(MelillaBatchBridgeAdapter.SOURCE_ORIGIN),
                isMainFrame = true,
                navigationEpoch = Long.MAX_VALUE,
            ) is MelillaBatchBridgeRouteResult.Rejected,
        )
    }

    @Test
    fun dedicatedAdapterBindsMessagesToTheCurrentDocumentLifecycle() {
        var currentEpoch = 7L
        var currentDocumentId = java.util.UUID.fromString(DOCUMENT_ID)
        val adapter = MelillaBatchBridgeAdapter(
            activeProfileId = { ProfileId(MelillaBatchBridgeAdapter.PROFILE_ID) },
            currentNavigationEpoch = { currentEpoch },
            currentDocumentId = { currentDocumentId },
        )

        assertTrue(
            adapter.route(
                portalOwnedBatchEnvelope(),
                Uri.parse(MelillaBatchBridgeAdapter.SOURCE_ORIGIN),
                isMainFrame = true,
                navigationEpoch = currentEpoch,
            ) is MelillaBatchBridgeRouteResult.Accepted,
        )

        adapter.abandonAll()
        currentEpoch++
        currentDocumentId = java.util.UUID.fromString("123e4567-e89b-42d3-a456-426614174002")

        val stale = adapter.route(
            portalOwnedBatchEnvelope(),
            Uri.parse(MelillaBatchBridgeAdapter.SOURCE_ORIGIN),
            isMainFrame = true,
            navigationEpoch = currentEpoch,
        )
        assertEquals(
            SigningErrorCode.NAVIGATION_CHANGED,
            (stale as MelillaBatchBridgeRouteResult.Rejected).code,
        )
    }

    @Test
    fun batchReplyChannelReturnsTheOpaqueValidationResponseWithoutInventingSubmission() {
        var posted: String? = null
        val channel = MelillaBatchReplyChannel(
            requestId = java.util.UUID.fromString(REQUEST_ID),
            postMessage = { posted = it },
        )

        assertTrue(channel.success(validationResponse = "{\"resultado\":\"ok\"}"))

        val result = JSONObject(checkNotNull(posted))
        assertEquals("MINIAPPLET_BATCH_RESULT", result.getString("type"))
        assertEquals(REQUEST_ID, result.getString("requestId"))
        assertEquals("success", result.getString("status"))
        assertEquals("{\"resultado\":\"ok\"}", result.getString("validationResponse"))
    }

    @Test
    fun batchReplyChannelRejectsNonJsonValidationResponses() {
        var posted: String? = null
        val channel = MelillaBatchReplyChannel(
            requestId = java.util.UUID.fromString(REQUEST_ID),
            postMessage = { posted = it },
        )

        assertFalse(channel.success(validationResponse = "not-json"))
        assertEquals(null, posted)
    }

    @Test
    fun portalOwnedJsonBatchRemainsNotApplicableToTheOrdinarySingleSignAdapter() {
        val result = MiniAppletBridgeAdapter(
            activeProfileId = { ProfileId("melilla-sede") },
        ).route(
            rawMessage = portalOwnedBatchEnvelope(),
            sourceOrigin = Uri.parse("https://sede.melilla.es"),
            isMainFrame = true,
            navigationEpoch = 7,
        )

        assertSame(
            "The ordinary single-sign adapter must leave a portal-owned batch to the " +
                "dedicated composition route",
            MiniAppletBridgeRouteResult.NotApplicable,
            result,
        )
    }

    private fun portalOwnedBatchEnvelope(): String = JSONObject()
        .put("type", "MINIAPPLET_BATCH")
        .put("documentId", DOCUMENT_ID)
        .put("requestId", REQUEST_ID)
        .put(
            "batchPreSignerUrl",
            "https://sede.melilla.es/sta/AutofirmaLote/presign/runtime-operation-1",
        )
        .put(
            "batchPostSignerUrl",
            "https://sede.melilla.es/sta/AutofirmaLote/postsign/runtime-operation-1",
        )
        .put("algorithm", "SHA256withRSA")
        .put("format", "CAdES")
        .put("suboperation", "sign")
        .put("stopOnError", false)
        .put(
            "documentos",
            JSONArray().put(
                JSONObject()
                    .put("id", "runtime-document-1")
                    .put(
                        "datareference",
                        "https://sede.melilla.es/sta/AutofirmaLote/getdata/" +
                            "runtime-operation-1/runtime-document-1",
                    ),
            ),
        )
        .toString()

    private companion object {
        const val REQUEST_ID = "123e4567-e89b-42d3-a456-426614174000"
        const val DOCUMENT_ID = "123e4567-e89b-42d3-a456-426614174001"
    }
}

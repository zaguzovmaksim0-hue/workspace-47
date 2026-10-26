package dev.junta.firmamobile.browser

import dev.junta.firmamobile.network.TrustedOrigin
import dev.junta.firmamobile.profile.BuildTrustPolicy
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SiteProfileRegistry
import dev.junta.firmamobile.signing.MelillaBatchProtocolAdapter
import dev.junta.firmamobile.signing.SigningErrorCode
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
class BatchBridgeSigningRoutesTest {
    private val routes = BatchBridgeSigningRoutes(SiteProfileRegistry(BuiltInSiteProfiles.catalog, BuildTrustPolicy.QA))
    private val posted = mutableListOf<String>()
    private val channel = MelillaBatchReplyChannel(requestId = REQUEST_ID, postMessage = { posted += it })

    @Test
    fun routesExactlyTheSevenBatchBridgeProfiles() {
        assertEquals(
            setOf(
                MelillaBatchBridgeAdapter.PROFILE_ID,
                ExtremaduraBatchBridgeAdapter.PROFILE_ID,
                LaPalmaBatchBridgeAdapter.PROFILE_ID,
                HuescaBatchBridgeAdapter.PROFILE_ID,
                LugoBatchBridgeAdapter.PROFILE_ID,
                CaibBatchBridgeAdapter.PROFILE_ID,
                BurgosBatchBridgeAdapter.PROFILE_ID,
            ),
            routes.profileIds,
        )
    }

    @Test
    fun melillaRequestNormalizesThroughTheMelillaAdapterAndRepliesOnItsChannel() {
        val routed = checkNotNull(routes.dispatch(request(MelillaBatchBridgeAdapter.PROFILE_ID), channel))
        val normalized = checkNotNull(routed.normalized)
        try {
            assertEquals(MelillaBatchProtocolAdapter.ID, normalized.protocolId)
            assertEquals(REQUEST_ID, normalized.requestId)
            assertEquals(REQUEST_ID, routed.replySink.requestId)
        } finally {
            normalized.close()
        }
    }

    @Test
    fun everyKnownProfileRejectsAForeignPayloadThroughItsOwnReplySink() {
        for (profileId in routes.profileIds - MelillaBatchBridgeAdapter.PROFILE_ID) {
            posted.clear()
            val ownChannel = MelillaBatchReplyChannel(requestId = REQUEST_ID, postMessage = { posted += it })
            val routed = checkNotNull(routes.dispatch(request(profileId), ownChannel)) { profileId }
            assertNull(profileId, routed.normalized)
            assertTrue(profileId, routed.replySink.failure(SigningErrorCode.INVALID_REQUEST))
            assertEquals(profileId, "error", JSONObject(posted.single()).getString("status"))
        }
    }

    @Test
    fun unknownProfileHasNoRoute() {
        assertNull(routes.dispatch(request("unknown-portal"), channel))
        assertTrue(posted.isEmpty())
    }

    private fun request(profileId: String) = MelillaBatchBridgeRequest(
        requestId = REQUEST_ID,
        documentId = DOCUMENT_ID,
        batchPreSignerUrl = "https://sede.melilla.es/sta/AutofirmaLote/presign/runtime-operation-1",
        batchPostSignerUrl = "https://sede.melilla.es/sta/AutofirmaLote/postsign/runtime-operation-1",
        operationId = "runtime-operation-1",
        algorithm = "SHA256withRSA",
        format = "CAdES",
        suboperation = "sign",
        stopOnError = false,
        documents = listOf(
            MelillaBatchDocument(
                id = "runtime-document-1",
                dataReference = "https://sede.melilla.es/sta/AutofirmaLote/getdata/runtime-operation-1/runtime-document-1",
                format = "PAdES",
            ),
        ),
        profileId = ProfileId(profileId),
        sourceOrigin = TrustedOrigin("https", "sede.melilla.es", 443),
        navigationEpoch = 7L,
    )

    private companion object {
        val REQUEST_ID: UUID = UUID.fromString("123e4567-e89b-42d3-a456-426614174000")
        val DOCUMENT_ID: UUID = UUID.fromString("123e4567-e89b-42d3-a456-426614174001")
    }
}

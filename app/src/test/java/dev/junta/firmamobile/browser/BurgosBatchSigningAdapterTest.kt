package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.signing.BurgosBatchProtocolAdapter
import dev.junta.firmamobile.signing.SigningErrorCode
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
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
class BurgosBatchSigningAdapterTest {
    @Test
    fun exactBurgosBridgeOwnershipNormalizesToItsDistinctProtocolOnly() {
        val bridge = BurgosBatchBridgeAdapter(
            activeProfileId = { ProfileId(BurgosBatchBridgeAdapter.PROFILE_ID) },
        )
        val routed = bridge.route(
            rawMessage = burgosEnvelope(),
            sourceOrigin = Uri.parse(BurgosBatchBridgeAdapter.SOURCE_ORIGIN),
            isMainFrame = true,
            navigationEpoch = 9L,
        ) as MelillaBatchBridgeRouteResult.Accepted

        val observedAt = Instant.parse("2026-08-12T00:00:00Z")
        val normalized = checkNotNull(
            BurgosBatchSigningAdapter(
                registry = burgosRegistry(),
                clock = Clock.fixed(observedAt, ZoneOffset.UTC),
            ).normalize(routed.request),
        )
        assertEquals(BurgosBatchProtocolAdapter.ID, normalized.protocolId)
        assertEquals("diputacion-burgos-portal", normalized.context.profileId)
        assertEquals(1, normalized.context.profileVersion)
        assertEquals("registro.diputaciondeburgos.es", normalized.context.origin.host)
        assertEquals(9L, normalized.context.navigationEpoch)
        assertEquals(observedAt, normalized.context.observedAt)
        normalized.close()

        val wrongOrigin = BurgosBatchBridgeAdapter(
            activeProfileId = { ProfileId(BurgosBatchBridgeAdapter.PROFILE_ID) },
        ).route(
            rawMessage = burgosEnvelope(),
            sourceOrigin = Uri.parse(MelillaBatchBridgeAdapter.SOURCE_ORIGIN),
            isMainFrame = true,
            navigationEpoch = 9L,
        ) as MelillaBatchBridgeRouteResult.Rejected
        assertEquals(SigningErrorCode.ORIGIN_NOT_ALLOWED, wrongOrigin.code)

        val wrongProfile = BurgosBatchBridgeAdapter(
            activeProfileId = { ProfileId(MelillaBatchBridgeAdapter.PROFILE_ID) },
        ).route(
            rawMessage = burgosEnvelope(),
            sourceOrigin = Uri.parse(BurgosBatchBridgeAdapter.SOURCE_ORIGIN),
            isMainFrame = true,
            navigationEpoch = 9L,
        ) as MelillaBatchBridgeRouteResult.Rejected
        assertEquals(SigningErrorCode.PROFILE_NOT_ACTIVE, wrongProfile.code)
    }

    private fun burgosRegistry() = BuiltInSiteProfiles.qaRegistry

    private fun burgosEnvelope(): String = JSONObject()
        .put("type", "MINIAPPLET_BATCH")
        .put("documentId", DOCUMENT_ID)
        .put("requestId", REQUEST_ID)
        .put("batchPreSignerUrl", "$ORIGIN/sta/AutofirmaLote/presign/$OPERATION_ID")
        .put("batchPostSignerUrl", "$ORIGIN/sta/AutofirmaLote/postsign/$OPERATION_ID")
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
                        "$ORIGIN/sta/AutofirmaLote/getdata/$OPERATION_ID/runtime-document-1",
                    ),
            ),
        )
        .toString()

    private companion object {
        const val ORIGIN = "https://registro.diputaciondeburgos.es"
        const val OPERATION_ID = "runtime-operation-1"
        const val REQUEST_ID = "123e4567-e89b-42d3-a456-426614174010"
        const val DOCUMENT_ID = "123e4567-e89b-42d3-a456-426614174011"
    }
}

package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.signing.LugoBatchProtocolAdapter
import java.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
class LugoBatchSigningAdapterTest {
    @Test
    fun exactPublicLugoSinglePrehashBatchNormalizesToDedicatedProtocol() {
        val bridge = LugoBatchBridgeAdapter(activeProfileId = { ProfileId(LugoBatchBridgeAdapter.PROFILE_ID) })
        val routed = bridge.route(envelope(), Uri.parse(ORIGIN), true, 4L) as MelillaBatchBridgeRouteResult.Accepted
        val normalized = LugoBatchSigningAdapter(BuiltInSiteProfiles.qaRegistry).normalize(routed.request)
        requireNotNull(normalized)
        assertEquals(LugoBatchProtocolAdapter.ID, normalized.protocolId)
        assertEquals(true, normalized.stopOnError)
        assertEquals(HASH, normalized.documents.single().dataReference)
        normalized.close()

        val wrong = LugoBatchBridgeAdapter(activeProfileId = { ProfileId(LugoBatchBridgeAdapter.PROFILE_ID) })
            .route(envelope().replace(ORIGIN, "https://evil.example"), Uri.parse(ORIGIN), true, 4L)
        assertEquals(true, wrong is MelillaBatchBridgeRouteResult.Rejected)
    }

    @Test
    fun rejectsNonObservedExtraPropertiesAndMultiNodeSessionMismatch() {
        val bridge = LugoBatchBridgeAdapter(activeProfileId = { ProfileId(LugoBatchBridgeAdapter.PROFILE_ID) })
        val badProps = JSONObject(envelope()).put("extraProperties", "mode=implicit\n").toString()
        assertEquals(true, bridge.route(badProps, Uri.parse(ORIGIN), true, 4L) is MelillaBatchBridgeRouteResult.Rejected)
        val badSession = JSONObject(envelope()).put("batchPostSignerUrl", "$ORIGIN/opencms/clientsigner/BatchPostsigner/service/$OTHER_SESSION").toString()
        assertEquals(true, bridge.route(badSession, Uri.parse(ORIGIN), true, 4L) is MelillaBatchBridgeRouteResult.Rejected)
    }

    private fun envelope(): String = JSONObject()
        .put("type", LugoBatchBridgeAdapter.TYPE)
        .put("documentId", DOCUMENT_ID)
        .put("requestId", REQUEST_ID)
        .put("batchXml", batchXml())
        .put("batchPreSignerUrl", "$ORIGIN/opencms/clientsigner/BatchPresigner/service/$SESSION")
        .put("batchPostSignerUrl", "$ORIGIN/opencms/clientsigner/BatchPostsigner/service/$SESSION")
        .put("extraProperties", LugoBatchBridgeAdapter.EXTRA_PROPERTIES)
        .toString()

    private fun batchXml(): String {
        val extra = Base64.getEncoder().encodeToString(LugoBatchBridgeAdapter.EXTRA_PROPERTIES.encodeToByteArray())
        val xml = "<signbatch stoponerror=\"true\" algorithm=\"SHA256withRSA\"><singlesign Id=\"$TRANSACTION\"><datasource>$HASH</datasource><format>CAdES</format><suboperation>sign</suboperation><extraparams>$extra</extraparams><signsaver><class>es.guadaltel.framework.clientsigner.servlet.batch.util.SignSaverFile</class><config/></signsaver></singlesign></signbatch>"
        return Base64.getEncoder().encodeToString(xml.encodeToByteArray())
    }

    private companion object {
        const val ORIGIN = "https://sede.deputacionlugo.org"
        const val SESSION = "A1B2C3D4E5F60718293A4B5C6D7E8F90"
        const val OTHER_SESSION = "B1C2D3E4F5A60718293A4B5C6D7E8F90"
        const val TRANSACTION = "1786890375115325"
        const val HASH = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        const val REQUEST_ID = "123e4567-e89b-42d3-a456-426614174040"
        const val DOCUMENT_ID = "123e4567-e89b-42d3-a456-426614174041"
    }
}

package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.signing.CaibBatchProtocolAdapter
import java.util.Base64
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
class CaibBatchSigningAdapterTest {
    @Test
    fun exactObservedPortaFibBatchNormalizesToDedicatedProtocol() {
        val bridge = CaibBatchBridgeAdapter(activeProfileId = { PROFILE })
        val routed = bridge.route(envelope(), Uri.parse(ORIGIN), true, 9L) as MelillaBatchBridgeRouteResult.Accepted
        val normalized = CaibBatchSigningAdapter(BuiltInSiteProfiles.qaRegistry).normalize(routed.request)
        requireNotNull(normalized)
        assertEquals(CaibBatchProtocolAdapter.ID, normalized.protocolId)
        assertEquals(false, normalized.stopOnError)
        assertEquals(SIGN_ID, normalized.documents.single().id)
        assertEquals(BATCH_B64, normalized.documents.single().dataReference)
        normalized.close()
    }

    @Test
    fun rejectsForeignOriginMutatedPropertiesAndMismatchedEndpointToken() {
        val foreign = CaibBatchBridgeAdapter(activeProfileId = { PROFILE })
            .route(envelope(), Uri.parse("https://evil.intranet.caib.es"), true, 9L)
        assertTrue(foreign is MelillaBatchBridgeRouteResult.Rejected)

        val badProps = JSONObject(envelope()).put("extraProperties", "mode=implicit\n").toString()
        assertTrue(CaibBatchBridgeAdapter(activeProfileId = { PROFILE }).route(badProps, Uri.parse(ORIGIN), true, 9L) is MelillaBatchBridgeRouteResult.Rejected)

        val badPost = JSONObject(envelope()).put(
            "batchPostSignerUrl",
            "$ORIGIN/portafibback/public/signmodule/requestPlugin/$OTHER_TOKEN/-1/BatchPostsigner",
        ).toString()
        assertTrue(CaibBatchBridgeAdapter(activeProfileId = { PROFILE }).route(badPost, Uri.parse(ORIGIN), true, 9L) is MelillaBatchBridgeRouteResult.Rejected)
    }

    private fun envelope(): String = JSONObject()
        .put("type", CaibBatchBridgeAdapter.TYPE)
        .put("documentId", DOCUMENT_ID)
        .put("requestId", REQUEST_ID)
        .put("batchXml", BATCH_B64)
        .put("batchPreSignerUrl", "$ORIGIN/portafibback/public/signmodule/requestPlugin/$TOKEN/-1/BatchPresigner")
        .put("batchPostSignerUrl", "$ORIGIN/portafibback/public/signmodule/requestPlugin/$TOKEN/-1/BatchPostsigner")
        .put("extraProperties", CaibBatchBridgeAdapter.extraProperties(SIGN_ID))
        .toString()

    companion object {
        const val ORIGIN = CaibBatchBridgeAdapter.SOURCE_ORIGIN
        const val TOKEN = "ABCDEFGHIJKLMNOPQRSTUVWX1234"
        const val OTHER_TOKEN = "BCDEFGHIJKLMNOPQRSTUVWXY1234"
        const val REQUEST_ID = "123e4567-e89b-42d3-a456-426614174060"
        const val DOCUMENT_ID = "123e4567-e89b-42d3-a456-426614174061"
        val PROFILE = ProfileId(CaibBatchBridgeAdapter.PROFILE_ID)
        val SIGN_ID: String = Base64.getUrlEncoder().withoutPadding().encodeToString("$TOKEN|0".encodeToByteArray())
        val BATCH_B64: String = batchB64()

        private fun batchB64(): String {
            val extra = Base64.getUrlEncoder().withoutPadding().encodeToString(
                CaibBatchBridgeAdapter.extraProperties(SIGN_ID).encodeToByteArray(),
            )
            val config = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "FileName=/tmp/PluginAutofirmaBatch3380418238844326177.bin\ndebug=false".encodeToByteArray(),
            )
            val xml = "<signbatch stoponerror=\"false\" algorithm=\"SHA256withRSA\">" +
                "<singlesign Id=\"$SIGN_ID\">" +
                "<datasource>file:///app/caib/portafib/files/PASSARELADEFIRMAWEB/$TOKEN/FORM-1-1/adaptat</datasource>" +
                "<format>PAdES</format><suboperation>sign</suboperation><extraparams>$extra</extraparams>" +
                "<signsaver><class>org.fundaciobit.pluginsib.signatureweb.afirmatriphaseserver.signsaver.SignSaverFile</class>" +
                "<config>$config</config></signsaver></singlesign></signbatch>"
            return Base64.getUrlEncoder().withoutPadding().encodeToString(xml.encodeToByteArray())
        }
    }
}

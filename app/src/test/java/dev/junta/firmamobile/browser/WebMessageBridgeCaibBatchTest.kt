package dev.junta.firmamobile.browser

import android.net.Uri
import androidx.webkit.JavaScriptExecutionException
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewOutcomeReceiver
import dev.junta.firmamobile.network.TrustedOrigin
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.security.SanitizedLogger
import java.util.UUID
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
class WebMessageBridgeCaibBatchTest {
    @Test
    fun documentBindingRoutesOnlyExactCaibOrigin() {
        val accepted = mutableListOf<MelillaBatchRequest>()
        val bridge = WebMessageBridge(
            profileId = PROFILE,
            logger = SanitizedLogger(),
            onAfirmaRequest = {},
            onMelillaBatchRequest = { request, _ -> accepted += request },
            activeProfileId = { PROFILE },
            miniAppletMode = MiniAppletBridgeMode.FUNCTIONAL,
            currentNavigationEpoch = { 9L },
            currentOrigin = { TrustedOrigin("https", "intranet.caib.es", 443) },
        )

        bridge.receiveForTest(documentReadyEnvelope(), LugoBatchBridgeAdapter.SOURCE_ORIGIN)
        bridge.receiveForTest(batchEnvelope(), LugoBatchBridgeAdapter.SOURCE_ORIGIN)
        assertTrue(accepted.isEmpty())

        bridge.receiveForTest(documentReadyEnvelope(), CaibBatchBridgeAdapter.SOURCE_ORIGIN)
        bridge.receiveForTest(batchEnvelope(), CaibBatchBridgeAdapter.SOURCE_ORIGIN)
        assertEquals(1, accepted.size)
        assertEquals(CaibBatchBridgeAdapter.PROFILE_ID, accepted.single().profileId.value)
        assertEquals(CaibBatchSigningAdapterTest.SIGN_ID, accepted.single().documents.single().id)
    }

    private fun WebMessageBridge.receiveForTest(raw: String, origin: String) {
        val method = WebMessageBridge::class.java.getDeclaredMethod(
            "receive",
            WebMessageCompat::class.java,
            Uri::class.java,
            Boolean::class.javaPrimitiveType,
            JavaScriptReplyProxy::class.java,
        )
        method.isAccessible = true
        method.invoke(this, WebMessageCompat(raw), Uri.parse(origin), true, NoopReplyProxy)
    }

    private fun documentReadyEnvelope(): String = JSONObject()
        .put("type", "MINIAPPLET_DOCUMENT_READY")
        .put("documentId", DOCUMENT_ID.toString())
        .toString()

    private fun batchEnvelope(): String = JSONObject()
        .put("type", CaibBatchBridgeAdapter.TYPE)
        .put("documentId", DOCUMENT_ID.toString())
        .put("requestId", REQUEST_ID.toString())
        .put("batchXml", CaibBatchSigningAdapterTest.BATCH_B64)
        .put("batchPreSignerUrl", "${CaibBatchBridgeAdapter.SOURCE_ORIGIN}/portafibback/public/signmodule/requestPlugin/${CaibBatchSigningAdapterTest.TOKEN}/-1/BatchPresigner")
        .put("batchPostSignerUrl", "${CaibBatchBridgeAdapter.SOURCE_ORIGIN}/portafibback/public/signmodule/requestPlugin/${CaibBatchSigningAdapterTest.TOKEN}/-1/BatchPostsigner")
        .put("extraProperties", CaibBatchBridgeAdapter.extraProperties(CaibBatchSigningAdapterTest.SIGN_ID))
        .toString()

    private object NoopReplyProxy : JavaScriptReplyProxy() {
        override fun postMessage(message: String) = Unit
        override fun postMessage(message: ByteArray) = Unit
        override fun executeJavaScript(script: String, resultCallback: WebViewOutcomeReceiver<String, JavaScriptExecutionException>?) = Unit
    }

    private companion object {
        val PROFILE = ProfileId(CaibBatchBridgeAdapter.PROFILE_ID)
        val REQUEST_ID: UUID = UUID.fromString("123e4567-e89b-42d3-a456-426614174062")
        val DOCUMENT_ID: UUID = UUID.fromString("123e4567-e89b-42d3-a456-426614174063")
    }
}

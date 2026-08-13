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
import org.json.JSONArray
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
class WebMessageBridgeLaPalmaBatchTest {
    @Test
    fun exactLaPalmaDocumentBindingRoutesAndCancelsOneOwnedBatchRequest() {
        val accepted = mutableListOf<MelillaBatchRequest>()
        val cancelled = mutableListOf<UUID>()
        lateinit var pendingReply: MelillaBatchReplyChannel
        val bridge = WebMessageBridge(
            profileId = LA_PALMA_PROFILE,
            logger = SanitizedLogger(),
            onAfirmaRequest = {},
            // The ordinary router is unrelated to this batch seam and never owns these messages.
            router = WebMessageRouter(ProfileId(MelillaBatchBridgeAdapter.PROFILE_ID)),
            onMelillaBatchRequest = { request, reply ->
                accepted += request
                pendingReply = reply
            },
            onMelillaBatchCancel = cancelled::add,
            activeProfileId = { LA_PALMA_PROFILE },
            miniAppletMode = MiniAppletBridgeMode.FUNCTIONAL,
            currentNavigationEpoch = { NAVIGATION_EPOCH },
            currentOrigin = { LA_PALMA_ORIGIN },
        )

        bridge.receiveForTest(documentReadyEnvelope(), MelillaBatchBridgeAdapter.SOURCE_ORIGIN)
        bridge.receiveForTest(batchEnvelope(), MelillaBatchBridgeAdapter.SOURCE_ORIGIN)
        assertTrue("Melilla source must not acquire La Palma batch ownership", accepted.isEmpty())

        bridge.receiveForTest(documentReadyEnvelope(), LaPalmaBatchBridgeAdapter.SOURCE_ORIGIN)
        bridge.receiveForTest(batchEnvelope(), LaPalmaBatchBridgeAdapter.SOURCE_ORIGIN)

        assertEquals(1, accepted.size)
        assertEquals(LA_PALMA_PROFILE, accepted.single().profileId)
        assertEquals(LA_PALMA_ORIGIN, accepted.single().sourceOrigin)
        assertEquals(DOCUMENT_ID, accepted.single().documentId)
        assertEquals(NAVIGATION_EPOCH, accepted.single().navigationEpoch)

        bridge.receiveForTest(cancelEnvelope(), LaPalmaBatchBridgeAdapter.SOURCE_ORIGIN)
        bridge.receiveForTest(cancelEnvelope(), LaPalmaBatchBridgeAdapter.SOURCE_ORIGIN)

        assertEquals(listOf(REQUEST_ID), cancelled)
        assertFalse(pendingReply.abandon())
    }

    private fun WebMessageBridge.receiveForTest(rawMessage: String, sourceOrigin: String) {
        val method = WebMessageBridge::class.java.getDeclaredMethod(
            "receive",
            WebMessageCompat::class.java,
            Uri::class.java,
            Boolean::class.javaPrimitiveType,
            JavaScriptReplyProxy::class.java,
        )
        method.isAccessible = true
        method.invoke(
            this,
            WebMessageCompat(rawMessage),
            Uri.parse(sourceOrigin),
            true,
            NoopReplyProxy,
        )
    }

    private fun documentReadyEnvelope(): String = JSONObject()
        .put("type", "MINIAPPLET_DOCUMENT_READY")
        .put("documentId", DOCUMENT_ID.toString())
        .toString()

    private fun batchEnvelope(): String = JSONObject()
        .put("type", "MINIAPPLET_BATCH")
        .put("documentId", DOCUMENT_ID.toString())
        .put("requestId", REQUEST_ID.toString())
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
                    .put("id", DOCUMENT_EXTERNAL_ID)
                    .put(
                        "datareference",
                        "$ORIGIN/sta/AutofirmaLote/getdata/$OPERATION_ID/$DOCUMENT_EXTERNAL_ID",
                    ),
            ),
        )
        .toString()

    private fun cancelEnvelope(): String = JSONObject()
        .put("type", "MINIAPPLET_BATCH_CANCEL")
        .put("documentId", DOCUMENT_ID.toString())
        .put("requestId", REQUEST_ID.toString())
        .toString()

    private object NoopReplyProxy : JavaScriptReplyProxy() {
        override fun postMessage(message: String) = Unit

        override fun postMessage(message: ByteArray) = Unit

        override fun executeJavaScript(
            script: String,
            resultCallback: WebViewOutcomeReceiver<String, JavaScriptExecutionException>?,
        ) = Unit
    }

    private companion object {
        const val ORIGIN = "https://sedeelectronica.cabildodelapalma.es"
        const val OPERATION_ID = "runtime-operation-1"
        const val DOCUMENT_EXTERNAL_ID = "runtime-document-1"
        const val NAVIGATION_EPOCH = 11L
        val LA_PALMA_PROFILE = ProfileId(LaPalmaBatchBridgeAdapter.PROFILE_ID)
        val LA_PALMA_ORIGIN = TrustedOrigin("https", "sedeelectronica.cabildodelapalma.es", 443)
        val REQUEST_ID: UUID = UUID.fromString("123e4567-e89b-42d3-a456-426614174030")
        val DOCUMENT_ID: UUID = UUID.fromString("123e4567-e89b-42d3-a456-426614174031")
    }
}

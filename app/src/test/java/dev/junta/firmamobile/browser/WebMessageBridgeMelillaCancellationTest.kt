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
class WebMessageBridgeMelillaCancellationTest {
    @Test
    fun validatedBatchCancelNotifiesRuntimeExactlyOnceForTheOwnedRequest() {
        val cancelled = mutableListOf<UUID>()
        lateinit var pendingReply: MelillaBatchReplyChannel
        val bridge = WebMessageBridge(
            profileId = MELILLA_PROFILE,
            logger = SanitizedLogger(),
            onAfirmaRequest = {},
            onMelillaBatchRequest = { _, reply -> pendingReply = reply },
            onMelillaBatchCancel = cancelled::add,
            activeProfileId = { MELILLA_PROFILE },
            miniAppletMode = MiniAppletBridgeMode.FUNCTIONAL,
            currentNavigationEpoch = { NAVIGATION_EPOCH },
            currentOrigin = { MELILLA_ORIGIN },
            currentDocumentId = { DOCUMENT_ID },
        )

        bridge.receiveForTest(portalOwnedBatchEnvelope())
        bridge.receiveForTest(batchCancelEnvelope())
        bridge.receiveForTest(batchCancelEnvelope())

        assertEquals(listOf(REQUEST_ID), cancelled)
        assertFalse(pendingReply.abandon())
    }

    private fun WebMessageBridge.receiveForTest(rawMessage: String) {
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
            Uri.parse(MelillaBatchBridgeAdapter.SOURCE_ORIGIN),
            true,
            NoopReplyProxy,
        )
    }

    private fun portalOwnedBatchEnvelope(): String = JSONObject()
        .put("type", "MINIAPPLET_BATCH")
        .put("documentId", DOCUMENT_ID.toString())
        .put("requestId", REQUEST_ID.toString())
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

    private fun batchCancelEnvelope(): String = JSONObject()
        .put("type", "MINIAPPLET_BATCH_CANCEL")
        .put("documentId", DOCUMENT_ID.toString())
        .put("requestId", REQUEST_ID.toString())
        .toString()

    private object NoopReplyProxy : JavaScriptReplyProxy() {
        override fun postMessage(message: String) = Unit

        override fun postMessage(message: ByteArray) = Unit

        override fun executeJavaScript(
            script: String,
            resultCallback: WebViewOutcomeReceiver<String, JavaScriptExecutionException>,
        ) = Unit
    }

    private companion object {
        val MELILLA_PROFILE = ProfileId(MelillaBatchBridgeAdapter.PROFILE_ID)
        val MELILLA_ORIGIN = TrustedOrigin("https", "sede.melilla.es", 443)
        val REQUEST_ID: UUID = UUID.fromString("123e4567-e89b-42d3-a456-426614174000")
        val DOCUMENT_ID: UUID = UUID.fromString("123e4567-e89b-42d3-a456-426614174001")
        const val NAVIGATION_EPOCH = 7L
    }
}

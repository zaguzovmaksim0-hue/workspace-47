package dev.junta.firmamobile.browser

import android.net.Uri
import androidx.webkit.JavaScriptExecutionException
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewOutcomeReceiver
import dev.junta.firmamobile.network.TrustedOrigin
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.security.SanitizedLogger
import java.util.Base64
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
class WebMessageBridgeLugoBatchTest {
    @Test
    fun exactLugoDocumentBindingRoutesAndCancelsOneOwnedXmlBatchRequest() {
        val accepted = mutableListOf<MelillaBatchRequest>()
        val cancelled = mutableListOf<UUID>()
        lateinit var pendingReply: MelillaBatchReplyChannel
        val bridge = WebMessageBridge(
            profileId = LUGO_PROFILE,
            logger = SanitizedLogger(),
            onAfirmaRequest = {},
            router = WebMessageRouter(ProfileId(MelillaBatchBridgeAdapter.PROFILE_ID)),
            onMelillaBatchRequest = { request, reply ->
                accepted += request
                pendingReply = reply
            },
            onMelillaBatchCancel = cancelled::add,
            activeProfileId = { LUGO_PROFILE },
            miniAppletMode = MiniAppletBridgeMode.FUNCTIONAL,
            currentNavigationEpoch = { NAVIGATION_EPOCH },
            currentOrigin = { LUGO_ORIGIN },
        )

        bridge.receiveForTest(documentReadyEnvelope(), HuescaBatchBridgeAdapter.SOURCE_ORIGIN)
        bridge.receiveForTest(batchEnvelope(), HuescaBatchBridgeAdapter.SOURCE_ORIGIN)
        assertTrue(accepted.isEmpty())

        bridge.receiveForTest(documentReadyEnvelope(), LugoBatchBridgeAdapter.SOURCE_ORIGIN)
        bridge.receiveForTest(batchEnvelope(), LugoBatchBridgeAdapter.SOURCE_ORIGIN)

        assertEquals(1, accepted.size)
        assertEquals(LUGO_PROFILE, accepted.single().profileId)
        assertEquals(LUGO_ORIGIN, accepted.single().sourceOrigin)
        assertEquals(DOCUMENT_ID, accepted.single().documentId)
        assertEquals(TRANSACTION_ID, accepted.single().documents.single().id)
        assertEquals(NAVIGATION_EPOCH, accepted.single().navigationEpoch)

        bridge.receiveForTest(cancelEnvelope(), LugoBatchBridgeAdapter.SOURCE_ORIGIN)
        bridge.receiveForTest(cancelEnvelope(), LugoBatchBridgeAdapter.SOURCE_ORIGIN)

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
        .put("type", LugoBatchBridgeAdapter.TYPE)
        .put("documentId", DOCUMENT_ID.toString())
        .put("requestId", REQUEST_ID.toString())
        .put("batchXml", batchXml())
        .put("batchPreSignerUrl", "$ORIGIN/opencms/clientsigner/BatchPresigner/service/$SESSION")
        .put("batchPostSignerUrl", "$ORIGIN/opencms/clientsigner/BatchPostsigner/service/$SESSION")
        .put("extraProperties", LugoBatchBridgeAdapter.EXTRA_PROPERTIES)
        .toString()

    private fun cancelEnvelope(): String = JSONObject()
        .put("type", LugoBatchBridgeAdapter.CANCEL_TYPE)
        .put("documentId", DOCUMENT_ID.toString())
        .put("requestId", REQUEST_ID.toString())
        .toString()

    private fun batchXml(): String {
        val extra = Base64.getEncoder()
            .encodeToString(LugoBatchBridgeAdapter.EXTRA_PROPERTIES.encodeToByteArray())
        val xml = "<signbatch stoponerror=\"true\" algorithm=\"SHA256withRSA\">" +
            "<singlesign Id=\"$TRANSACTION_ID\"><datasource>$HASH</datasource>" +
            "<format>CAdES</format><suboperation>sign</suboperation>" +
            "<extraparams>$extra</extraparams><signsaver>" +
            "<class>es.guadaltel.framework.clientsigner.servlet.batch.util.SignSaverFile</class>" +
            "<config/></signsaver></singlesign></signbatch>"
        return Base64.getEncoder().encodeToString(xml.encodeToByteArray())
    }

    private object NoopReplyProxy : JavaScriptReplyProxy() {
        override fun postMessage(message: String) = Unit
        override fun postMessage(message: ByteArray) = Unit
        override fun executeJavaScript(
            script: String,
            resultCallback: WebViewOutcomeReceiver<String, JavaScriptExecutionException>?,
        ) = Unit
    }

    private companion object {
        const val ORIGIN = LugoBatchBridgeAdapter.SOURCE_ORIGIN
        const val SESSION = "A1B2C3D4E5F60718293A4B5C6D7E8F90"
        const val TRANSACTION_ID = "1786890375115325"
        const val HASH = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        const val NAVIGATION_EPOCH = 12L
        val LUGO_PROFILE = ProfileId(LugoBatchBridgeAdapter.PROFILE_ID)
        val LUGO_ORIGIN = TrustedOrigin("https", "sede.deputacionlugo.org", 443)
        val REQUEST_ID: UUID = UUID.fromString("123e4567-e89b-42d3-a456-426614174050")
        val DOCUMENT_ID: UUID = UUID.fromString("123e4567-e89b-42d3-a456-426614174051")
    }
}

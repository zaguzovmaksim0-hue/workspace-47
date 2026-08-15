package dev.junta.firmamobile.browser

import android.net.Uri
import androidx.webkit.JavaScriptExecutionException
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewOutcomeReceiver
import dev.junta.firmamobile.network.TrustedOrigin
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.security.SanitizedLogger
import dev.junta.firmamobile.signing.PrecalculatedHashAlgorithm
import dev.junta.firmamobile.signing.SigningErrorCode
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class WebMessageBridgeVeaMultiModeTest {
    private val veaProfile = ProfileId(VeaMultiModeBridgeAdapter.PROFILE_ID)
    private val veaOrigin = TrustedOrigin("https", "veaja.cloud.juntadeandalucia.es", 443)
    private val pageUrl = "https://veaja.cloud.juntadeandalucia.es/inicio/"
    private val documentId = UUID.fromString("12345678-1234-4234-8234-1234567890ab")
    private val requestId = UUID.randomUUID()
    private val navigationEpoch = 100L

    @Test
    fun veaDocumentReadyBindsLiveDocumentIdAndEnablesRouting() {
        val accepted = mutableListOf<VeaMultiModeBridgeRequest>()
        val cancelled = mutableListOf<UUID>()
        var liveDocumentId: UUID? = null

        val bridge = WebMessageBridge(
            profileId = veaProfile,
            logger = SanitizedLogger(),
            onAfirmaRequest = {},
            router = WebMessageRouter(veaProfile),
            onVeaMultiModeRequest = { request, _ ->
                accepted += request
            },
            onVeaMultiModeCancel = cancelled::add,
            onVeaDocumentIdChanged = { liveDocumentId = it },
            activeProfileId = { veaProfile },
            miniAppletMode = MiniAppletBridgeMode.FUNCTIONAL,
            currentNavigationEpoch = { navigationEpoch },
            currentOrigin = { veaOrigin },
            currentPageUrl = { pageUrl },
        )

        // Attempting to route multiMode sign BEFORE document ready must fail
        bridge.receiveForTest(signEnvelope(documentId), VeaMultiModeBridgeAdapter.SOURCE_ORIGIN)
        assertTrue("Request before document ready must not be accepted", accepted.isEmpty())
        assertNull("Live document ID must not be bound yet", liveDocumentId)

        // Receive VEA_DOCUMENT_READY
        bridge.receiveForTest(documentReadyEnvelope(documentId, pageUrl), VeaMultiModeBridgeAdapter.SOURCE_ORIGIN)
        assertEquals(documentId, liveDocumentId)

        // Now multiMode sign request must succeed
        bridge.receiveForTest(signEnvelope(documentId), VeaMultiModeBridgeAdapter.SOURCE_ORIGIN)
        assertEquals(1, accepted.size)
        val req = accepted.single()
        assertEquals(veaProfile, req.profileId)
        assertEquals(veaOrigin, req.sourceOrigin)
        assertEquals(documentId, req.documentId)
        assertEquals(requestId, req.requestId)
        assertEquals(PrecalculatedHashAlgorithm.SHA256, req.hashAlgorithm)
        assertEquals(1, req.hashes.size)
    }

    @Test
    fun rejectsVeaDocumentReadyFromUntrustedOriginOrMismatchedPageUrl() {
        var liveDocumentId: UUID? = null

        val bridge = WebMessageBridge(
            profileId = veaProfile,
            logger = SanitizedLogger(),
            onAfirmaRequest = {},
            router = WebMessageRouter(veaProfile),
            onVeaMultiModeRequest = { _, _ -> },
            onVeaMultiModeCancel = {},
            onVeaDocumentIdChanged = { liveDocumentId = it },
            activeProfileId = { veaProfile },
            miniAppletMode = MiniAppletBridgeMode.FUNCTIONAL,
            currentNavigationEpoch = { navigationEpoch },
            currentOrigin = { veaOrigin },
            currentPageUrl = { pageUrl },
        )

        // Wrong source origin
        bridge.receiveForTest(
            documentReadyEnvelope(documentId, pageUrl),
            "https://attacker.example.com",
        )
        assertNull(liveDocumentId)

        // Mismatched page url
        bridge.receiveForTest(
            documentReadyEnvelope(documentId, "https://veaja.cloud.juntadeandalucia.es/other"),
            VeaMultiModeBridgeAdapter.SOURCE_ORIGIN,
        )
        assertNull(liveDocumentId)
    }

    @Test
    fun routesCancelAndClearsDocumentIdOnAbandon() {
        val cancelled = mutableListOf<UUID>()
        var liveDocumentId: UUID? = null

        val bridge = WebMessageBridge(
            profileId = veaProfile,
            logger = SanitizedLogger(),
            onAfirmaRequest = {},
            router = WebMessageRouter(veaProfile),
            onVeaMultiModeRequest = { _, _ -> },
            onVeaMultiModeCancel = cancelled::add,
            onVeaDocumentIdChanged = { liveDocumentId = it },
            activeProfileId = { veaProfile },
            miniAppletMode = MiniAppletBridgeMode.FUNCTIONAL,
            currentNavigationEpoch = { navigationEpoch },
            currentOrigin = { veaOrigin },
            currentPageUrl = { pageUrl },
        )

        bridge.receiveForTest(documentReadyEnvelope(documentId, pageUrl), VeaMultiModeBridgeAdapter.SOURCE_ORIGIN)
        assertEquals(documentId, liveDocumentId)

        // Cancel envelope routes cleanly
        bridge.receiveForTest(cancelEnvelope(documentId), VeaMultiModeBridgeAdapter.SOURCE_ORIGIN)
        assertEquals(listOf(requestId), cancelled)

        // Calling abandonAllMiniAppletRequests clears live documentId
        val abandonMethod = WebMessageBridge::class.java.getDeclaredMethod("abandonAllMiniAppletRequests")
        abandonMethod.isAccessible = true
        abandonMethod.invoke(bridge)

        assertNull(liveDocumentId)
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

    private fun documentReadyEnvelope(docId: UUID, url: String): String = JSONObject()
        .put("type", "VEA_DOCUMENT_READY")
        .put("documentId", docId.toString())
        .put("pageUrl", url)
        .toString()

    private fun signEnvelope(docId: UUID): String {
        val hashHex = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        return JSONObject()
            .put("type", "MINIAPPLET_MULTIMODE_SIGN")
            .put("documentId", docId.toString())
            .put("requestId", requestId.toString())
            .put("operationArray", JSONArray(listOf("sign")))
            .put("dataArray", JSONArray(listOf(hashHex)))
            .put("originalDataArray", JSONObject.NULL)
            .put("arrayLength", 1)
            .put("algorithm", "SHA256withRSA")
            .put("format", "CADES")
            .put("extraProperties", "mode=explicit\nprecalculatedHashAlgorithm=SHA-256\nfilters=nonexpired:;signingCert;")
            .put("pageUrl", pageUrl)
            .toString()
    }

    private fun cancelEnvelope(docId: UUID): String = JSONObject()
        .put("type", "MINIAPPLET_MULTIMODE_CANCEL")
        .put("documentId", docId.toString())
        .put("requestId", requestId.toString())
        .toString()
    private object NoopReplyProxy : JavaScriptReplyProxy() {
        override fun postMessage(message: String) = Unit

        override fun postMessage(message: ByteArray) = Unit

        override fun executeJavaScript(
            script: String,
            resultCallback: WebViewOutcomeReceiver<String, JavaScriptExecutionException>?,
        ) = Unit
    }

}

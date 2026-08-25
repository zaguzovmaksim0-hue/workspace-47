package dev.junta.firmamobile.browser

import android.net.Uri
import androidx.webkit.JavaScriptExecutionException
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewOutcomeReceiver
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.security.SanitizedLogger
import java.nio.charset.StandardCharsets
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
class WebMessageBridgeEuskadiClientAuthTest {
    private val profileId = ProfileId(EuskadiClientAuthPostBridgeAdapter.PROFILE_ID)

    @Test
    fun exactEuskadiEnvelopeRoutesThroughBridgeWithoutFallingIntoGenericRouter() {
        var accepted: EuskadiClientAuthPostBridgeRequest? = null
        val adapter = EuskadiClientAuthPostBridgeAdapter(
            profileRegistry = BuiltInSiteProfiles.qaRegistry,
            activeProfileId = { profileId },
            monotonicNanos = { 1_000_000L },
        )
        val bridge = WebMessageBridge(
            profileId = profileId,
            logger = SanitizedLogger(),
            onAfirmaRequest = {},
            onEuskadiClientAuthPostRequest = { request -> accepted = request },
            router = WebMessageRouter(ProfileId("junta-andalucia")),
            activeProfileId = { profileId },
            euskadiClientAuthPostBridgeAdapter = adapter,
            currentNavigationEpoch = { 11L },
            currentPageUrl = { EuskadiClientAuthPostBridgeAdapter.SOURCE_PAGE },
        )

        bridge.receiveForTest(validEnvelope(), mainFrame = true)

        assertNotNull(accepted)
        val request = checkNotNull(accepted)
        assertEquals(profileId, request.authorized.profileId)
        assertEquals(EuskadiClientAuthPostBridgeAdapter.TARGET_URL, request.authorized.target.toASCIIString())
        try {
            assertEquals(
                "request=opaque-token&x_correlation_id=5df82aa2-af47-4f78-b67d-bd958e41ee81",
                request.postBody.toString(StandardCharsets.UTF_8),
            )
        } finally {
            request.postBody.fill(0)
        }
    }

    @Test
    fun subframeEnvelopeIsRejectedBeforeCallback() {
        var accepted: EuskadiClientAuthPostBridgeRequest? = null
        val bridge = WebMessageBridge(
            profileId = profileId,
            logger = SanitizedLogger(),
            onAfirmaRequest = {},
            onEuskadiClientAuthPostRequest = { request -> accepted = request },
            activeProfileId = { profileId },
            euskadiClientAuthPostBridgeAdapter = EuskadiClientAuthPostBridgeAdapter(
                profileRegistry = BuiltInSiteProfiles.qaRegistry,
                activeProfileId = { profileId },
                monotonicNanos = { 1_000_000L },
            ),
            currentNavigationEpoch = { 11L },
            currentPageUrl = { EuskadiClientAuthPostBridgeAdapter.SOURCE_PAGE },
        )

        bridge.receiveForTest(validEnvelope(), mainFrame = false)

        assertNull(accepted)
    }

    private fun WebMessageBridge.receiveForTest(rawMessage: String, mainFrame: Boolean) {
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
            Uri.parse(EuskadiClientAuthPostBridgeAdapter.IZENPE_ORIGIN),
            mainFrame,
            NoopReplyProxy,
        )
    }

    private fun validEnvelope(): String = JSONObject()
        .put("type", "EUSKADI_CLIENT_AUTH_POST")
        .put("profileId", EuskadiClientAuthPostBridgeAdapter.PROFILE_ID)
        .put("requestId", "9736cbef-2cf7-4d72-ae4e-c5efabc2a120")
        .put("method", "POST")
        .put("contentType", EuskadiClientAuthPostBridgeAdapter.FORM_CONTENT_TYPE)
        .put("targetUrl", EuskadiClientAuthPostBridgeAdapter.TARGET_URL)
        .put("request", "opaque-token")
        .put("x_correlation_id", "5df82aa2-af47-4f78-b67d-bd958e41ee81")
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

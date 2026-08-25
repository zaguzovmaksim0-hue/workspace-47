package dev.junta.firmamobile.browser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.junta.firmamobile.profile.ProfileId
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
class EuskadiAfirmaJavascriptShimTest {
    @Test
    fun exactEuskadiPostHookIsSeparatelyFeatureGatedAndFailClosed() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val enabled = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            euskadiClientAuthPostEnabled = true,
        )
        assertTrue(enabled.contains("const euskadiClientAuthPostEnabled = true"))
        assertTrue(enabled.contains(EuskadiClientAuthPostBridgeAdapter.SOURCE_PAGE))
        assertTrue(enabled.contains(EuskadiClientAuthPostBridgeAdapter.TARGET_URL))
        assertTrue(enabled.contains("EUSKADI_CLIENT_AUTH_POST"))
        assertTrue(enabled.contains("namedControls.length !== 2"))
        assertTrue(enabled.contains("profileId: euskadiProfileId"))
        assertTrue(enabled.contains("method: \"POST\""))
        assertTrue(enabled.contains("contentType: euskadiFormContentType"))
        assertTrue(enabled.contains("targetUrl: euskadiClientAuthTarget"))
        assertTrue(enabled.contains("x_correlation_id: correlationValue"))
        assertTrue(enabled.contains("HTMLFormElement.prototype.submit"))
        assertTrue(enabled.contains("HTMLFormElement.prototype.requestSubmit"))
        assertTrue(enabled.contains("document.addEventListener(\"submit\""))
        assertTrue(enabled.contains("if (action !== euskadiClientAuthTarget) return false"))
        assertFalse(enabled.contains("__JFM_EUSKADI_CLIENT_AUTH_POST_ENABLED__"))

        val flags = WebMessageBridge.shimCompatibilityFlags(
            profileId = ProfileId(EuskadiClientAuthPostBridgeAdapter.PROFILE_ID),
            profileActive = true,
            melillaBatchEnabled = false,
        )
        assertTrue(flags.euskadiClientAuthPost)
        assertFalse(
            WebMessageBridge.shimCompatibilityFlags(
                profileId = ProfileId(EuskadiClientAuthPostBridgeAdapter.PROFILE_ID),
                profileActive = false,
                melillaBatchEnabled = false,
            ).euskadiClientAuthPost,
        )
    }
}

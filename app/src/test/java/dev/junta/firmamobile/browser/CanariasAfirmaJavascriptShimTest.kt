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
class CanariasAfirmaJavascriptShimTest {
    @Test
    fun profileFlagIsEnabledOnlyForTheActiveCanariasProfile() {
        val enabled = WebMessageBridge.shimCompatibilityFlags(
            profileId = ProfileId("canarias-sede"),
            profileActive = true,
            melillaBatchEnabled = false,
        )
        val inactive = WebMessageBridge.shimCompatibilityFlags(
            profileId = ProfileId("canarias-sede"),
            profileActive = false,
            melillaBatchEnabled = false,
        )
        assertTrue(enabled.canarias)
        assertFalse(inactive.canarias)
        assertFalse(enabled.granCanaria)
        assertFalse(enabled.ugr)
        assertFalse(enabled.jccm)
    }

    @Test
    fun shimOwnsOnlyTheExactObservedCanariasAutoScriptTuple() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val enabled = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = true,
            canariasCompatibilityEnabled = true,
        )
        val disabled = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = true,
            canariasCompatibilityEnabled = false,
        )

        assertTrue(enabled.contains("const canariasCompatibilityEnabled = true"))
        assertTrue(disabled.contains("const canariasCompatibilityEnabled = false"))
        assertTrue(enabled.contains("https://sede.gobiernodecanarias.org/sede/identificacion"))
        assertTrue(enabled.contains("canariasChallengePattern"))
        assertTrue(enabled.contains("globalThis.atob(value)"))
        assertTrue(enabled.contains("globalThis.btoa(decoded) === value"))
        assertTrue(enabled.contains("args[1] === \"SHA1withRSA\""))
        assertTrue(enabled.contains("args[2] === \"CAdES\""))
        assertTrue(enabled.contains("format=CAdES Detached\\n"))
        assertTrue(enabled.contains("SignatureService\\n"))
        assertTrue(enabled.contains("referencesDigestMethod=http://www.w3.org/2001/04/xmlenc#sha512\\n"))
        assertTrue(enabled.contains("signingCert:true;issuer.rfc2254"))
        assertTrue(enabled.contains("if (isCanariasOrigin && !isExactCanariasCall)"))
        assertTrue(enabled.contains("!canariasCompatibilityEnabled"))
    }
}

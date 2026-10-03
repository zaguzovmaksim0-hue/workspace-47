package dev.junta.firmamobile

import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import dev.junta.firmamobile.browser.TrustedJuntaWebView
import dev.junta.firmamobile.browser.WebAuthnEngineState
import dev.junta.firmamobile.ui.WebAuthnStatusButton
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Native API/readback and capability display, not a real passkey enrollment
 * or third-party relying-party authorization test. No credential is created. */
@RunWith(AndroidJUnit4::class)
class NativeWebAuthnInstrumentedTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun browserWindowConfiguresTheNativeModeAndExposesNoJavascriptShim() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var view: TrustedJuntaWebView
            val finished = AtomicBoolean(false); val intercepted = AtomicBoolean(false)
            scenario.onActivity { activity ->
                assertNotNull(androidx.credentials.CredentialManager.create(activity))
                view = TrustedJuntaWebView(activity)
                activity.setContentView(view)
                val supported = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)
                if (supported) {
                    assertEquals(WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER, WebSettingsCompat.getWebAuthenticationSupport(view.settings))
                    assertEquals(WebAuthnEngineState.ENABLED_PROVIDER_AUTHORIZATION_UNKNOWN, view.webAuthnEngineState)
                } else assertEquals(WebAuthnEngineState.UNSUPPORTED, view.webAuthnEngineState)
                android.util.Log.i("FirmaSyntheticWebAuthn", "feature=$supported;state=${view.webAuthnEngineState};providerAuthorization=unknown")
                view.webViewClient = PublicBrowserInstrumentedTest.LocalResponseClient(android.webkit.WebViewClient(), "https://webauthn.synthetic.example/local", finished, intercepted,
                    "<html><head><title>NATIVE_AUTHN_CAPABILITY</title></head><body>Capability only</body></html>")
                view.loadUrl("https://webauthn.synthetic.example/local")
            }
            rule.waitUntil(timeoutMillis = 15_000) { finished.get() && intercepted.get() }
            val reply = AtomicReference<String?>()
            scenario.onActivity { view.evaluateJavascript("JSON.stringify({secure:isSecureContext,credentials:typeof navigator.credentials,publicKey:typeof PublicKeyCredential,get:typeof navigator.credentials?.get,origin:location.origin})") { reply.set(it) } }
            rule.waitUntil(timeoutMillis = 5_000) { reply.get() != null }
            val decoded = org.json.JSONTokener(checkNotNull(reply.get())).nextValue() as String
            val capability = org.json.JSONObject(decoded)
            assertTrue("WebAuthn must retain a real secure origin", capability.getBoolean("secure"))
            assertEquals("https://webauthn.synthetic.example", capability.getString("origin"))
            if (view.webAuthnEngineState == WebAuthnEngineState.ENABLED_PROVIDER_AUTHORIZATION_UNKNOWN) {
                assertEquals("object", capability.getString("credentials"))
                assertEquals("function", capability.getString("publicKey"))
                assertEquals("function", capability.getString("get"))
            }
            // Do not call credentials.get/create, fake success or manufacture
            // an origin on behalf of a relying party we do not control.
            scenario.onActivity { (view.parent as? android.view.ViewGroup)?.removeView(view); view.destroy() }
        }
    }

    @Test fun enabledEngineIsNotPresentedAsCredentialProviderApproval() {
        var external = 0
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.setContent { WebAuthnStatusButton(WebAuthnEngineState.ENABLED_PROVIDER_AUTHORIZATION_UNKNOWN) { external++ } } }
            rule.onNodeWithTag("webauthn-status").performClick()
            rule.onNodeWithTag("webauthn-status-dialog").assertIsDisplayed()
            rule.onNodeWithText("autorización del proveedor", substring = true).assertIsDisplayed()
            rule.runOnIdle { assertEquals(0, external) }
            rule.onNodeWithTag("webauthn-open-browser").performClick()
            rule.runOnIdle { assertEquals(1, external) }
        }
    }
    @Test fun unsupportedEngineLeavesAnExplicitExternalBrowserOption() {
        var external = 0
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.setContent { WebAuthnStatusButton(WebAuthnEngineState.UNSUPPORTED) { external++ } } }
            rule.onNodeWithTag("webauthn-status").performClick()
            rule.onNodeWithText("no anuncia soporte", substring = true).assertIsDisplayed()
            rule.onNodeWithTag("webauthn-open-browser").performClick()
            rule.runOnIdle { assertEquals(1, external) }
        }
    }
}

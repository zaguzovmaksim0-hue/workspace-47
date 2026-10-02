package dev.junta.firmamobile

import android.net.Uri
import android.webkit.ClientCertRequest
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.junta.firmamobile.security.SanitizedLogger
import dev.junta.firmamobile.signing.SigningUiState
import dev.junta.firmamobile.ui.BrowserScreen
import java.net.URI
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Reserved .example URLs and inline HTML only. No real account, certificate or submission. */
@RunWith(AndroidJUnit4::class)
class PublicBrowserInstrumentedTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val observedLog = SanitizedLogger()
    private val entry = URI("https://unprofiled.synthetic.example/form")

    @Test fun actualPublicBrowserKeepsNativeCertificateConsentButNoAutomaticKey() {
        val current = AtomicReference<WebView?>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            render(scenario, current)
            rule.onNodeWithTag("public-browsing-notice").assertIsDisplayed()
            val request = CertificateRequest()
            scenario.onActivity {
                val view = checkNotNull(current.get())
                view.webViewClient.onReceivedClientCertRequest(view, request)
            }
            awaitConsent("interactive-client-auth-unlock", scenario, current) { "ignored=${request.ignores},proceeded=${request.proceeds}" }
            rule.onNodeWithTag("interactive-client-auth-unlock").assertIsDisplayed()
            assertEquals(0, request.proceeds)
            rule.onNodeWithTag("interactive-client-auth-cancel").performClick()
            rule.runOnIdle { assertEquals(1, request.ignores); assertEquals(0, request.proceeds) }
            rule.onNodeWithTag("interactive-client-auth-unlock").assertDoesNotExist()
        }
    }

    @Test fun actualPublicBrowserAcceptsSupportedAutoFirmaRequestWithoutAProfileAndDoesNotSign() {
        val current = AtomicReference<WebView?>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            render(scenario, current)
            val view = checkNotNull(current.get())
            val invocation = Uri.parse("afirma://sign?id=PublicSynthetic123&stservlet=https%3A%2F%2Fstorage.synthetic.example%2Fput&format=CAdES&algorithm=SHA256withRSA&dat=c3ludGhldGlj")
            scenario.onActivity {
                assertTrue(view.webViewClient.shouldOverrideUrlLoading(view, object : WebResourceRequest {
                    override fun getUrl() = invocation
                    override fun isForMainFrame() = true
                    override fun isRedirect() = false
                    override fun hasGesture() = true
                    override fun getMethod() = "GET"
                    override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
                }))
            }
            awaitConsent("native-afirma-unlock", scenario, current) { "native callback invoked once" }
            rule.onNodeWithTag("native-afirma-unlock").assertIsDisplayed()
            rule.onNodeWithTag("native-afirma-confirm").assertDoesNotExist()
            // Automatic lifecycle interruption is local; do not press the
            // server-notifying cancellation button against a network endpoint.
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            rule.waitForIdle()
            rule.onNodeWithTag("native-afirma-close").performClick()
            scenario.onActivity { assertSame(view, current.get()); assertEquals("PUBLIC_SYNTHETIC_READY", view.title) }
        }
    }

    private fun render(scenario: ActivityScenario<MainActivity>, current: AtomicReference<WebView?>) {
        scenario.onActivity { activity ->
            val app = activity.application as JuntaFirmaApplication
            activity.setContent {
                BrowserScreen(
                    profileId = null, entryUrl = entry, certificateState = null,
                    logger = observedLog, signingState = SigningUiState.Idle,
                    onMiniAppletRequest = { _, _ -> error("Public browsing must not receive profile bridge signing requests") },
                    onMiniAppletCancel = {}, onConfirmSigning = {}, onCancelSigning = { _, _ -> },
                    onDismissSigningState = {}, onExitBrowser = {}, onOpenExternal = {}, onOpenOfficialAutoFirma = {},
                    onChangeCertificate = {}, onLockCertificate = {}, onClearSession = {},
                    clientCertificateIdentityProvider = { null }, clientCertPreferenceCoordinator = app.clientCertPreferenceCoordinator,
                    onWebViewChanged = { current.set(it) },
                )
            }
        }
        rule.waitUntil(timeoutMillis = 15_000) { current.get() != null }
        scenario.onActivity {
            current.get()!!.apply {
                stopLoading()
                loadDataWithBaseURL(entry.toASCIIString(),
                    "<html><head><title>PUBLIC_SYNTHETIC_READY</title></head><body><input value='local draft'></body></html>",
                    "text/html", "UTF-8", entry.toASCIIString())
            }
        }
        rule.waitUntil(timeoutMillis = 15_000) {
            var ready = false
            scenario.onActivity {
                ready = current.get()?.let { view ->
                    view.title == "PUBLIC_SYNTHETIC_READY" && view.progress == 100 &&
                        it.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                } == true
            }
            ready
        }
    }

    /** Chromium callbacks and Android dialog windows are outside Compose's
     * test clock. Observe the one issued request; never retry the callback. */
    private fun awaitConsent(
        tag: String, scenario: ActivityScenario<MainActivity>, current: AtomicReference<WebView?>,
        callbackState: () -> String,
    ) {
        var observedAtFirstCheck = false
        var first = true
        try {
            rule.waitUntil(timeoutMillis = 5_000) {
                val shown = runCatching { rule.onNodeWithTag(tag).assertIsDisplayed() }.isSuccess
                if (first) { observedAtFirstCheck = shown; first = false }
                shown
            }
        } catch (failure: Throwable) {
            var state = ""
            scenario.onActivity { activity ->
                val view = current.get()
                state = "lifecycle=${activity.lifecycle.currentState};view=${view != null};progress=${view?.progress};" +
                    "localTitle=${view?.title == "PUBLIC_SYNTHETIC_READY"};focus=${view?.hasWindowFocus()}"
            }
            throw AssertionError("Consent did not appear after one callback: $tag; $state; ${callbackState()}; " +
                observedLog.snapshot().takeLast(12).joinToString(" | "), failure)
        }
        android.util.Log.i("FirmaSyntheticConsent", "tag=$tag;visibleOnFirstObservation=$observedAtFirstCheck;${callbackState()}")
    }

    private class CertificateRequest : ClientCertRequest() {
        var proceeds = 0; var ignores = 0
        override fun getHost() = "auth.synthetic.example"
        override fun getPort() = 443
        override fun getKeyTypes() = arrayOf("RSA")
        override fun getPrincipals(): Array<Principal> = emptyArray()
        override fun proceed(key: PrivateKey, chain: Array<X509Certificate>) { proceeds++ }
        override fun ignore() { ignores++ }
        override fun cancel() { error("Do not persist denial") }
    }
}

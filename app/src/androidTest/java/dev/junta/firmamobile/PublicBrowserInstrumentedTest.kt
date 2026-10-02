package dev.junta.firmamobile

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.ClientCertRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.junta.firmamobile.browser.ClientCertPreferenceBarrierState
import dev.junta.firmamobile.security.SanitizedLogger
import dev.junta.firmamobile.signing.SigningUiState
import dev.junta.firmamobile.ui.BrowserScreen
import java.io.ByteArrayInputStream
import java.net.URI
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Reserved .example URLs and intercepted local HTML only. No real account,
 * certificate, TLS grant, remote page response or administrative submission. */
@RunWith(AndroidJUnit4::class)
class PublicBrowserInstrumentedTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val observedLog = SanitizedLogger()
    private val entry = URI("https://unprofiled.synthetic.example/form")
    private val fixturePage = entry.resolve("/__local_fixture__")

    @Test fun actualPublicBrowserKeepsNativeCertificateConsentButNoAutomaticKey() {
        val current = AtomicReference<WebView?>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            render(scenario, current)
            rule.onNodeWithTag("public-browsing-notice").assertIsDisplayed()
            val request = CertificateRequest()
            scenario.onActivity { activity ->
                val view = checkNotNull(current.get())
                val barrier = (activity.application as JuntaFirmaApplication).clientCertPreferenceCoordinator.state.value
                assertEquals(ClientCertPreferenceBarrierState.IDLE, barrier)
                android.util.Log.i("FirmaSyntheticConsent", "beforeTls;barrier=$barrier;fixture=${view.url == fixturePage.toASCIIString()}")
                view.webViewClient.onReceivedClientCertRequest(view, request)
                android.util.Log.i("FirmaSyntheticConsent", "afterTls;ignored=${request.ignores};proceeded=${request.proceeds}")
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
            // Lifecycle interruption is local; do not invoke server-notifying
            // cancellation against a real storage endpoint in this test.
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            rule.waitForIdle()
            rule.onNodeWithTag("native-afirma-close").performClick()
            scenario.onActivity { assertSame(view, current.get()); assertEquals("PUBLIC_SYNTHETIC_READY", view.title) }
        }
    }

    @Test fun actualPublicBrowserOffersPdfSigningWithoutAnExistingSiteProfile() {
        val current = AtomicReference<WebView?>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            render(scenario, current)
            val view = checkNotNull(current.get())
            val invocation = Uri.parse("afirma://sign?id=PublicPdf123&stservlet=https%3A%2F%2Fstorage.synthetic.example%2Fput&format=PAdES&algorithm=SHA256withRSA&dat=" + java.util.Base64.getUrlEncoder().encodeToString(nativeInstrumentedPdf()))
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
            rule.onNodeWithText("PDF · PAdES", substring = true).assertIsDisplayed()
            rule.onNodeWithTag("native-afirma-confirm").assertDoesNotExist()
            // Lifecycle interruption is local; do not invoke server-notifying
            // cancellation against a real storage endpoint in this test.
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
        val completedFixture = AtomicBoolean(false)
        val interceptedFixture = AtomicBoolean(false)
        var original: WebView? = null
        scenario.onActivity {
            val view = checkNotNull(current.get())
            original = view
            view.stopLoading()
            // Keep the application's actual client and every callback used by
            // these tests. Replace only the fixture's network response. A
            // loadDataWithBaseURL title is not proof of a completed navigation.
            view.webViewClient = LocalResponseClient(
                view.webViewClient, fixturePage.toASCIIString(), completedFixture, interceptedFixture,
            )
            view.loadUrl(fixturePage.toASCIIString())
        }
        try {
            rule.waitUntil(timeoutMillis = 15_000) {
                var ready = false
                scenario.onActivity { activity ->
                    val barrier = (activity.application as JuntaFirmaApplication).clientCertPreferenceCoordinator.state.value
                    ready = current.get()?.let { view ->
                        view === original && view.url == fixturePage.toASCIIString() &&
                            view.title == "PUBLIC_SYNTHETIC_READY" && view.progress == 100 &&
                            activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                            barrier == ClientCertPreferenceBarrierState.IDLE
                    } == true
                }
                ready && interceptedFixture.get() && completedFixture.get()
            }
        } catch (failure: Throwable) {
            var state = ""
            scenario.onActivity { activity ->
                val view = current.get()
                val barrier = (activity.application as JuntaFirmaApplication).clientCertPreferenceCoordinator.state.value
                state = "sameView=${view === original};lifecycle=${activity.lifecycle.currentState};" +
                    "localUrl=${view?.url == fixturePage.toASCIIString()};progress=${view?.progress};" +
                    "localTitle=${view?.title == "PUBLIC_SYNTHETIC_READY"};barrier=$barrier"
            }
            throw AssertionError("Local fixture not ready: $state;intercepted=${interceptedFixture.get()};" +
                "finished=${completedFixture.get()};" + observedLog.snapshot().takeLast(12).joinToString(" | "), failure)
        }
    }

    /** This test-only decorator does not answer certificate/signing requests.
     * All protocol, ownership and consent behavior belongs to the real client. */
    internal class LocalResponseClient(
        private val delegate: WebViewClient,
        private val fixtureUrl: String,
        private val finished: AtomicBoolean,
        private val intercepted: AtomicBoolean,
        private val html: String = "<html><head><title>PUBLIC_SYNTHETIC_READY</title></head><body><input value='local draft'></body></html>",
    ) : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val response = delegate.shouldInterceptRequest(view, request)
            if (response != null) return response
            if (request.url.toString() == fixtureUrl && request.method == "GET") {
                intercepted.set(true)
                return WebResourceResponse("text/html", "UTF-8", 200, "OK", mapOf("Cache-Control" to "no-store"),
                    ByteArrayInputStream(html.toByteArray(Charsets.UTF_8)))
            }
            // No test resource should cause outgoing network activity.
            return WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
        }
        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (url == fixtureUrl) finished.set(false)
            delegate.onPageStarted(view, url, favicon)
        }
        override fun onPageFinished(view: WebView, url: String) {
            delegate.onPageFinished(view, url)
            if (url == fixtureUrl) finished.set(true)
        }
        override fun onPageCommitVisible(view: WebView, url: String) = delegate.onPageCommitVisible(view, url)
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = delegate.shouldOverrideUrlLoading(view, request)
        override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) = delegate.onReceivedClientCertRequest(view, request)
        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) = delegate.onReceivedError(view, request, error)
        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) = delegate.onReceivedHttpError(view, request, response)
        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) = delegate.onReceivedSslError(view, handler, error)
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail) = delegate.onRenderProcessGone(view, detail)
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

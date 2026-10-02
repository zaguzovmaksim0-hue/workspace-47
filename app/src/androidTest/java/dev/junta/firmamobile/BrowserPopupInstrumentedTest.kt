package dev.junta.firmamobile

import android.graphics.Bitmap
import android.net.Uri
import android.view.MotionEvent
import android.webkit.ClientCertRequest
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.junta.firmamobile.security.SanitizedLogger
import dev.junta.firmamobile.signing.SigningUiState
import dev.junta.firmamobile.ui.BrowserScreen
import java.io.ByteArrayInputStream
import java.net.URI
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Chromium-created popup, no manufactured URL replay or remote account.
 * The only network responses are local HTML on a reserved example origin. */
@RunWith(AndroidJUnit4::class)
class BrowserPopupInstrumentedTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val origin = "https://popup.synthetic.example"
    private val logger = SanitizedLogger()
    private val callbackTrace = Collections.synchronizedList(mutableListOf<String>())

    @Test fun realWindowKeepsOpenerAndReturnsWithoutLosingTheParentDraft() {
        withBrowser { scenario, views ->
            val parent = views.first()
            assertEquals("\"edited\"", evaluate(parent, "document.getElementById('draft').value='edited'"))
            tapElement(scenario, parent, "open")
            rule.waitUntil(timeoutMillis = 15_000) { views.size == 2 }
            val child = views[1]
            ready(scenario, child, "CHILD_READY")
            rule.onNodeWithTag("browser-popup-header").assertIsDisplayed()
            assertNotSame(parent, child)
            assertEquals("true", evaluate(child, "window.opener !== null"))
            assertEquals("\"edited\"", evaluate(parent, "document.getElementById('draft').value"))
            tapElement(scenario, child, "return")
            rule.waitUntil(timeoutMillis = 10_000) {
                runCatching { rule.onNodeWithTag("browser-popup-window").assertDoesNotExist() }.isSuccess
            }
            assertEquals("\"returned\"", evaluate(parent, "document.getElementById('status').textContent"))
            assertEquals("\"edited\"", evaluate(parent, "document.getElementById('draft').value"))
            scenario.onActivity {
                assertTrue("Parent must survive return: released=${(parent as? dev.junta.firmamobile.browser.TrustedJuntaWebView)?.isNativeReleased};" +
                    "attached=${parent.isAttachedToWindow};owners=$callbackTrace", parent.isShown)
                assertEquals("PARENT_READY", parent.title)
            }
        }
    }

    @Test fun programmaticWindowWithoutUserGestureDoesNotReplaceTheParent() {
        withBrowser { scenario, views ->
            val parent = views.first()
            assertEquals("true", evaluate(parent, "window.open('/child','unsolicited')===null"))
            rule.waitForIdle()
            assertEquals(1, views.size)
            rule.onNodeWithTag("browser-popup-window").assertDoesNotExist()
            scenario.onActivity { assertEquals("PARENT_READY", parent.title) }
        }
    }

    @Test fun childCertificateRequestStillRequiresItsOwnConsentAndCloseReleasesIt() {
        withBrowser { scenario, views ->
            val parent = views.first()
            tapElement(scenario, parent, "open")
            rule.waitUntil(timeoutMillis = 15_000) { views.size == 2 }
            val child = views[1]
            ready(scenario, child, "CHILD_READY")
            val request = CertificateRequest()
            scenario.onActivity { child.webViewClient.onReceivedClientCertRequest(child, request) }
            rule.waitUntil(timeoutMillis = 5_000) {
                runCatching { rule.onNodeWithTag("interactive-client-auth-unlock").assertIsDisplayed() }.isSuccess
            }
            assertEquals(0, request.proceeds)
            rule.onNodeWithTag("interactive-client-auth-cancel").performClick()
            rule.onNodeWithTag("browser-popup-close").performClick()
            rule.waitForIdle()
            assertEquals(1, request.ignores); assertEquals(0, request.proceeds)
            scenario.onActivity {
                assertTrue("Parent must survive return: released=${(parent as? dev.junta.firmamobile.browser.TrustedJuntaWebView)?.isNativeReleased};" +
                    "attached=${parent.isAttachedToWindow};owners=$callbackTrace", parent.isShown)
                assertEquals("PARENT_READY", parent.title)
            }
        }
    }

    @Test fun aChildConsumesTheExplicitExternalReturnOnlyOnce() {
        val lease = java.util.concurrent.atomic.AtomicBoolean(false)
        val consumed = java.util.concurrent.atomic.AtomicInteger(0)
        withBrowser(mayRetain = { lease.get() }, consumeReturn = { consumed.incrementAndGet(); lease.getAndSet(false) }) { scenario, views ->
            val parent = views.first()
            tapElement(scenario, parent, "open")
            rule.waitUntil(timeoutMillis = 15_000) { views.size == 2 }
            val child = views[1]
            ready(scenario, child, "CHILD_READY")
            lease.set(true)
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            rule.waitForIdle()
            assertEquals("The hidden parent must not consume the child's return", 1, consumed.get())
            rule.onNodeWithTag("browser-popup-header").assertIsDisplayed()
            assertEquals("true", evaluate(child, "window.opener !== null"))
            rule.onNodeWithTag("browser-popup-close").performClick()
            rule.waitForIdle()
            scenario.onActivity { assertTrue(parent.isShown); assertEquals("PARENT_READY", parent.title) }
        }
    }

    private fun withBrowser(
        mayRetain: () -> Boolean = { false },
        consumeReturn: () -> Boolean = { false },
        test: (ActivityScenario<MainActivity>, List<WebView>) -> Unit,
    ) {
        val views = Collections.synchronizedList(mutableListOf<WebView>())
        val decorated = Collections.newSetFromMap(IdentityHashMap<WebView, Boolean>())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val app = activity.application as JuntaFirmaApplication
                activity.setContent {
                    BrowserScreen(
                        profileId = null, entryUrl = URI("$origin/parent"), certificateState = null,
                        logger = logger, signingState = SigningUiState.Idle,
                        onMiniAppletRequest = { _, _ -> error("No profile bridge for a public popup") },
                        onMiniAppletCancel = {}, onConfirmSigning = {}, onCancelSigning = { _, _ -> },
                        onDismissSigningState = {}, onExitBrowser = {}, onOpenExternal = {},
                        onOpenOfficialAutoFirma = {}, onChangeCertificate = {}, onLockCertificate = {},
                        onClearSession = {}, clientCertificateIdentityProvider = { null },
                        clientCertPreferenceCoordinator = app.clientCertPreferenceCoordinator,
                        mayRetainExternalReturn = mayRetain,
                        consumeExternalReturn = consumeReturn,
                        onWebViewChanged = { view ->
                            callbackTrace.add("owner=" + if (view == null) "none" else System.identityHashCode(view).toString())
                            if (view != null && decorated.add(view)) {
                                views.add(view)
                                // Queued from the factory before Chromium receives
                                // the transport. All real callbacks stay delegated.
                                view.post { view.webViewClient = FixtureClient(view.webViewClient) }
                            }
                        },
                    )
                }
            }
            rule.waitUntil(timeoutMillis = 15_000) { views.isNotEmpty() }
            val parent = views.first()
            scenario.onActivity { parent.stopLoading(); parent.loadUrl("$origin/parent") }
            ready(scenario, parent, "PARENT_READY")
            test(scenario, views)
        }
    }

    private fun ready(scenario: ActivityScenario<MainActivity>, view: WebView, title: String) {
        var nativeReadyBeforeHeader = false
        try {
            rule.waitUntil(timeoutMillis = 15_000) {
                var nativeReady = false
                scenario.onActivity {
                    nativeReady = view.title == title && view.progress == 100 && view.isShown && view.isAttachedToWindow &&
                        (view as? dev.junta.firmamobile.browser.TrustedJuntaWebView)?.isNativeReleased != true
                }
                // Chromium progress and Compose layout have separate clocks.
                // Observe both before asserting on the actual popup header;
                // do not issue a second window request or reload either page.
                val headerReady = title != "CHILD_READY" || runCatching {
                    rule.onNodeWithTag("browser-popup-header").assertIsDisplayed()
                }.isSuccess
                if (nativeReady && !headerReady) nativeReadyBeforeHeader = true
                nativeReady && headerReady
            }
        } catch (failure: Throwable) {
            throw AssertionError("Original popup did not become ready: expectedTitle=$title;" +
                "nativeReadyBeforeHeader=$nativeReadyBeforeHeader;owners=$callbackTrace;" +
                logger.snapshot().takeLast(12).joinToString("|"), failure)
        }
        android.util.Log.i("FirmaPopupUiReadiness", "expectedTitle=$title;nativeReadyBeforeHeader=$nativeReadyBeforeHeader")
    }
    private fun evaluate(view: WebView, script: String): String {
        val result = AtomicReference<String?>()
        rule.runOnIdle { view.evaluateJavascript(script) { result.set(it) } }
        try {
            rule.waitUntil(timeoutMillis = 5_000) { result.get() != null }
        } catch (failure: Throwable) {
            var state = ""
            rule.runOnIdle {
                state = "view=${System.identityHashCode(view)};attached=${view.isAttachedToWindow};shown=${view.isShown};" +
                    "released=${(view as? dev.junta.firmamobile.browser.TrustedJuntaWebView)?.isNativeReleased}"
            }
            throw AssertionError("Original page unavailable: $state;owners=$callbackTrace;" +
                logger.snapshot().takeLast(12).joinToString("|"), failure)
        }
        return checkNotNull(result.get())
    }
    private fun tapElement(scenario: ActivityScenario<MainActivity>, view: WebView, id: String) {
        val json = JSONArray(evaluate(view, "(()=>{let r=document.getElementById('$id').getBoundingClientRect();return [r.x+r.width/2,r.y+r.height/2,devicePixelRatio]})()"))
        val x = (json.getDouble(0) * json.getDouble(2)).toFloat()
        val y = (json.getDouble(1) * json.getDouble(2)).toFloat()
        scenario.onActivity {
            val now = android.os.SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
            val up = MotionEvent.obtain(now, now + 60, MotionEvent.ACTION_UP, x, y, 0)
            try { view.dispatchTouchEvent(down); view.dispatchTouchEvent(up) }
            finally { down.recycle(); up.recycle() }
        }
    }
    private inner class FixtureClient(private val original: WebViewClient) : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            original.shouldInterceptRequest(view, request)?.let { return it }
            val header = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
            val page = if (request.url.host == "popup.synthetic.example" && request.url.path == "/parent") {
                header + "<title>PARENT_READY</title></head><body><input id='draft' value='original'><button id='open' style='display:block;margin:20px;padding:20px' onclick=\"window.child=window.open('/child','child')\">Open</button><div id='status'></div><script>addEventListener('message',e=>{if(e.origin===location.origin)document.getElementById('status').textContent=e.data})</script></body></html>"
            } else if (request.url.host == "popup.synthetic.example" && request.url.path == "/child") {
                header + "<title>CHILD_READY</title></head><body><button id='return' style='margin:20px;padding:20px' onclick=\"opener.postMessage('returned',location.origin);window.close()\">Return</button></body></html>"
            } else "<html><body>Local empty resource</body></html>"
            return WebResourceResponse("text/html", "UTF-8", 200, "OK", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(page.toByteArray()))
        }
        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) = original.onPageStarted(view, url, favicon)
        override fun onPageFinished(view: WebView, url: String) = original.onPageFinished(view, url)
        override fun onPageCommitVisible(view: WebView, url: String) = original.onPageCommitVisible(view, url)
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = original.shouldOverrideUrlLoading(view, request)
        override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) = original.onReceivedClientCertRequest(view, request)
    }
    private class CertificateRequest : ClientCertRequest() {
        var proceeds = 0; var ignores = 0
        override fun getHost() = "auth.synthetic.example"
        override fun getPort() = 443
        override fun getKeyTypes() = arrayOf("RSA")
        override fun getPrincipals() = emptyArray<Principal>()
        override fun proceed(key: PrivateKey, chain: Array<X509Certificate>) { proceeds++ }
        override fun ignore() { ignores++ }
        override fun cancel() { error("No cached denial") }
    }
}

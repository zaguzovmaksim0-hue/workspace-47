package dev.junta.firmamobile

import android.content.Intent
import android.graphics.Bitmap
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
import dev.junta.firmamobile.browser.TrustedJuntaWebView
import dev.junta.firmamobile.browser.download.DocumentDownloadActivity
import dev.junta.firmamobile.browser.download.DocumentDownloadPlan
import dev.junta.firmamobile.browser.download.DocumentDownloadScreen
import dev.junta.firmamobile.browser.download.DocumentDownloadState
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentDownloadInstrumentedTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun reviewIsExplicitAndContainsNoSilentDownloadOrSaveAction() {
        var downloads = 0; var saves = 0; var closes = 0
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                DocumentDownloadScreen(plan(), DocumentDownloadState.OFFER, 0,
                    onStart = { downloads++ }, onSave = { saves++ }, onClose = { closes++ })
            } }
            rule.onNodeWithTag("download-start").assertIsDisplayed()
            rule.onNodeWithTag("download-save").assertDoesNotExist()
            rule.runOnIdle { assertEquals(0, downloads); assertEquals(0, saves) }
            rule.onNodeWithTag("download-close").performClick()
            rule.runOnIdle { assertEquals(1, closes); assertEquals(0, downloads) }
        }
    }

    @Test fun preparedDocumentOffersOnlyLocalSavingNotAnotherNetworkRequest() {
        var saves = 0
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                DocumentDownloadScreen(plan(), DocumentDownloadState.READY, 123,
                    onStart = { error("No second GET") }, onSave = { saves++ }, onClose = {})
            } }
            rule.onNodeWithTag("download-start").assertDoesNotExist()
            rule.onNodeWithTag("download-save").assertIsDisplayed().performClick()
            rule.runOnIdle { assertEquals(1, saves) }
        }
    }

    @Test fun actualDownloadActivityReceivesOnlyATicketAndCanCancelWithoutFetching() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val intent = checkNotNull(DocumentDownloadActivity.prepare(context, plan()))
        val extras = checkNotNull(intent.extras)
        assertEquals(setOf("download-ticket"), extras.keySet())
        assertTrue(checkNotNull(extras.getString("download-ticket")).matches(Regex("[a-f0-9-]{36}")))
        ActivityScenario.launch<DocumentDownloadActivity>(intent).use {
            rule.onNodeWithTag("download-start").assertIsDisplayed()
            rule.onNodeWithTag("download-progress").assertDoesNotExist()
            rule.onNodeWithTag("download-close").performClick()
        }
    }

    @Test fun actualChromiumAttachmentCallbackCreatesOneOfferWithoutFetchingAgain() {
        val offer = AtomicReference<Intent?>()
        val ready = AtomicBoolean(false)
        val responses = java.util.concurrent.atomic.AtomicInteger(0)
        lateinit var view: TrustedJuntaWebView
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                view = TrustedJuntaWebView(activity)
                activity.setContentView(view)
                view.setDocumentDownloadLauncher({ true }) { _, request ->
                    check(offer.compareAndSet(null, request)); true
                }
                view.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(v: WebView, request: WebResourceRequest): WebResourceResponse {
                        val document = request.url.path == "/attachment"
                        if (document) responses.incrementAndGet()
                        val body = if (document) "%PDF-synthetic" else
                            "<html><head><title>DOWNLOAD_FIXTURE</title></head><body><a id='file' href='/attachment'>Save</a></body></html>"
                        return WebResourceResponse(if (document) "application/pdf" else "text/html", "UTF-8", 200, "OK",
                            if (document) mapOf("Content-Disposition" to "attachment; filename=fixture.pdf", "Cache-Control" to "no-store") else emptyMap(),
                            ByteArrayInputStream(body.toByteArray()))
                    }
                    override fun onPageFinished(v: WebView, url: String) {
                        if (url.endsWith("/start")) ready.set(true)
                    }
                }
                view.loadUrl("https://download.synthetic.example/start")
            }
            rule.waitUntil(timeoutMillis = 15_000) { ready.get() }
            scenario.onActivity { view.evaluateJavascript("document.getElementById('file').click()", null) }
            rule.waitUntil(timeoutMillis = 15_000) { offer.get() != null }
            assertEquals(1, responses.get())
            assertEquals(setOf("download-ticket"), checkNotNull(offer.get()!!.extras).keySet())
            DocumentDownloadActivity.revoke(offer.get()!!)
            scenario.onActivity { (view.parent as? android.view.ViewGroup)?.removeView(view); view.destroy() }
        }
    }
    private fun plan() = checkNotNull(DocumentDownloadPlan.create(
        "https://download.synthetic.example/page", "https://download.synthetic.example/document?token=synthetic",
        "Comprobante.pdf", "application/pdf", 123, "Synthetic", "sid=not-real"))
}

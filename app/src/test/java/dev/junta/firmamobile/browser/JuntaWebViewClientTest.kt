package dev.junta.firmamobile.browser

import android.content.Context
import android.net.Uri
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.SafeBrowsingResponse
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import dev.junta.firmamobile.afirma.AfirmaRequest
import dev.junta.firmamobile.security.SanitizedLogger
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import org.robolectric.shadow.api.Shadow

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class JuntaWebViewClientTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val webView = WebView(context)
    private val callbacks = RecordingBrowserCallbacks()
    private val logger = SanitizedLogger(
        Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
    )
    private val client = JuntaWebViewClient(
        callbacks = callbacks,
        logger = logger,
        currentPageUrl = { TRUSTED_PAGE },
    )

    @Test
    fun allowedNavigationReturnsFalseWithoutCallingLoadUrlOrCallbacks() {
        val overridden = client.shouldOverrideUrlLoading(
            webView,
            request("https://ssoweb.juntadeandalucia.es/login"),
        )

        assertFalse(overridden)
        assertTrue(callbacks.events.isEmpty())
        assertTrue(shadowOf(webView).lastLoadedUrl.isNullOrEmpty())
    }

    @Test
    fun externalAndAfirmaNavigationAreConsumedByNativeCallbacks() {
        assertTrue(
            client.shouldOverrideUrlLoading(webView, request("https://example.org/help")),
        )
        assertTrue(
            client.shouldOverrideUrlLoading(
                webView,
                request("afirma://sign?algorithm=SHA256withRSA&format=CAdES&dat=abc"),
            ),
        )

        assertEquals("external:example.org", callbacks.events[0])
        assertEquals("afirma:sign", callbacks.events[1])
    }

    @Test
    fun playStoreFallbackIsConsumedAndRecordedWithoutExternalLaunch() {
        val overridden = client.shouldOverrideUrlLoading(
            webView,
            request("market://details?id=es.gob.afirma"),
        )

        assertTrue(overridden)
        assertEquals(
            listOf("blocked:PLAY_STORE_FALLBACK"),
            callbacks.events,
        )
        assertTrue(logger.exportText().contains("event=PLAY_STORE_FALLBACK_INTERCEPTED"))
    }

    @Test
    fun sslErrorsAlwaysCancelAndNeverProceed() {
        val handler = Shadow.newInstanceOf(SslErrorHandler::class.java)
        val sslError = Shadow.newInstanceOf(SslError::class.java)

        client.onReceivedSslError(webView, handler, sslError)

        val shadowHandler = shadowOf(handler)
        assertTrue(shadowHandler.wasCancelCalled())
        assertFalse(shadowHandler.wasProceedCalled())
        assertEquals(listOf("error:SSL_ERROR"), callbacks.events)
        assertTrue(logger.exportText().contains("event=SSL_ERROR_CANCELLED"))
    }

    @Test
    fun safeBrowsingHitsAlwaysReturnToSafety() {
        val response = RecordingSafeBrowsingResponse()

        client.onSafeBrowsingHit(
            webView,
            request("https://www.juntadeandalucia.es/suspicious"),
            0,
            response,
        )

        assertTrue(response.backToSafetyCalled)
        assertFalse(response.proceedCalled)
        assertFalse(response.interstitialCalled)
        assertEquals(listOf("error:SAFE_BROWSING"), callbacks.events)
        assertTrue(logger.exportText().contains("event=SAFE_BROWSING_BLOCKED"))
    }

    private fun request(rawUrl: String) = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(rawUrl)
        override fun isForMainFrame(): Boolean = true
        override fun isRedirect(): Boolean = false
        override fun hasGesture(): Boolean = true
        override fun getMethod(): String = "GET"
        override fun getRequestHeaders(): Map<String, String> = emptyMap()
    }

    private class RecordingBrowserCallbacks : BrowserNavigationCallbacks {
        val events = mutableListOf<String>()

        override fun openExternal(uri: Uri) {
            events += "external:${uri.host}"
        }

        override fun onAfirmaRequest(request: AfirmaRequest) {
            events += "afirma:${request.operation.wireName}"
        }

        override fun onNavigationBlocked(reason: NavigationBlockReason) {
            events += "blocked:${reason.name}"
        }

        override fun onBrowserError(error: BrowserErrorCode) {
            events += "error:${error.name}"
        }
    }

    private class RecordingSafeBrowsingResponse : SafeBrowsingResponse() {
        var backToSafetyCalled = false
        var proceedCalled = false
        var interstitialCalled = false

        override fun backToSafety(report: Boolean) {
            backToSafetyCalled = true
        }

        override fun proceed(report: Boolean) {
            proceedCalled = true
        }

        override fun showInterstitial(allowReporting: Boolean) {
            interstitialCalled = true
        }
    }

    private companion object {
        const val TRUSTED_PAGE = "https://www.juntadeandalucia.es/portal"
    }
}

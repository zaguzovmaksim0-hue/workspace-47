package dev.junta.firmamobile.browser

import android.content.Context
import android.net.Uri
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.junta.firmamobile.afirma.AfirmaRequest
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.security.SanitizedLogger
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JuntaWebViewClientInstrumentedTest {
    @Suppress("DEPRECATION")
    @Test
    fun allowsJuntaAndConsumesAfirmaAndPlayStoreNavigationsOnDevice() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val webView = WebView(context)
            val callbacks = RecordingCallbacks()
            val client = JuntaWebViewClient(
                callbacks = callbacks,
                logger = SanitizedLogger(),
                navigationPolicy = JuntaNavigationPolicy(ProfileId("junta-andalucia")),
                currentPageUrl = {
                    "https://www.juntadeandalucia.es/portal"
                },
            )

            assertFalse(
                client.shouldOverrideUrlLoading(
                    webView,
                    "https://sede.juntadeandalucia.es/tramite",
                ),
            )
            assertTrue(
                client.shouldOverrideUrlLoading(
                    webView,
                    "afirma://sign?algorithm=SHA256withRSA&format=CAdES&dat=YWJj",
                ),
            )
            assertTrue(
                client.shouldOverrideUrlLoading(
                    webView,
                    "market://details?id=es.gob.afirma",
                ),
            )
            assertTrue(callbacks.afirmaObserved)
            assertTrue(callbacks.playStoreBlocked)
            assertFalse(callbacks.externalOpened)
            webView.destroy()
        }
    }

    @Test
    fun rendererDeathIsRoutedWithTheExactWebViewOnDevice() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val webView = WebView(context)
            val callbacks = RecordingCallbacks()
            val client = JuntaWebViewClient(
                callbacks = callbacks,
                logger = SanitizedLogger(),
                navigationPolicy = JuntaNavigationPolicy(ProfileId("junta-andalucia")),
            )

            assertTrue(
                client.onRenderProcessGone(
                    webView,
                    RecordingRenderProcessGoneDetail(),
                ),
            )
            assertSame(webView, callbacks.rendererView)
            webView.destroy()
        }
    }

    private class RecordingCallbacks : BrowserNavigationCallbacks {
        var afirmaObserved = false
        var playStoreBlocked = false
        var externalOpened = false
        var rendererView: WebView? = null

        override fun openExternal(uri: Uri) {
            externalOpened = true
        }

        override fun onAfirmaRequest(request: AfirmaRequest) {
            afirmaObserved = true
        }

        override fun onNavigationBlocked(reason: NavigationBlockReason) {
            playStoreBlocked = reason == NavigationBlockReason.PLAY_STORE_FALLBACK
        }

        override fun onBrowserError(error: BrowserErrorCode) = Unit

        override fun onRenderProcessGone(view: WebView) {
            rendererView = view
        }
    }

    private class RecordingRenderProcessGoneDetail : RenderProcessGoneDetail() {
        override fun didCrash(): Boolean = true

        override fun rendererPriorityAtExit(): Int = 0
    }
}

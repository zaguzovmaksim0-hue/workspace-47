package dev.junta.firmamobile.browser

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class TrustedJuntaWebViewTest {
    @Test
    fun appliesHardenedSettingsAndEnablesFirstPartyWebFunctionality() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val webView = TrustedJuntaWebView(context)
        val settings = webView.settings

        assertTrue(settings.javaScriptEnabled)
        assertTrue(settings.domStorageEnabled)
        assertTrue(settings.mediaPlaybackRequiresUserGesture)
        assertFalse(settings.allowFileAccess)
        assertFalse(settings.allowContentAccess)
        assertFalse(settings.allowFileAccessFromFileURLs)
        assertFalse(settings.allowUniversalAccessFromFileURLs)
        assertFalse(settings.javaScriptCanOpenWindowsAutomatically)
        assertFalse(settings.supportMultipleWindows())
        assertTrue(settings.mixedContentMode == WebSettings.MIXED_CONTENT_NEVER_ALLOW)
        assertTrue(settings.useWideViewPort)
        assertTrue(settings.loadWithOverviewMode)

        val cookieManager = CookieManager.getInstance()
        assertTrue(cookieManager.acceptCookie())
        assertFalse(cookieManager.acceptThirdPartyCookies(webView))
        assertTrue(webView.webChromeClient is JuntaWebChromeClient)

        webView.destroy()
    }

    @Test
    fun reportsBoundedPageProgressWithoutReplacingTheHardenedChromeClient() {
        val webView = TrustedJuntaWebView(ApplicationProvider.getApplicationContext<Context>())
        var progress = -1
        webView.setPageProgressListener { progress = it }

        checkNotNull(webView.webChromeClient).onProgressChanged(webView, 42)

        assertEquals(42, progress)
        assertTrue(webView.webChromeClient is JuntaWebChromeClient)
        webView.destroy()
    }
}

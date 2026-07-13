package dev.junta.firmamobile.browser

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrustedJuntaWebViewInstrumentedTest {
    @Test
    fun appliesHardenedSettingsOnTheDeviceWebView() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val webView = TrustedJuntaWebView(context)
            val settings = webView.settings

            assertTrue(settings.javaScriptEnabled)
            assertTrue(settings.domStorageEnabled)
            assertTrue(settings.safeBrowsingEnabled)
            assertTrue(settings.mediaPlaybackRequiresUserGesture)
            assertFalse(settings.allowFileAccess)
            assertFalse(settings.allowContentAccess)
            @Suppress("DEPRECATION")
            assertFalse(settings.allowFileAccessFromFileURLs)
            @Suppress("DEPRECATION")
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
    }
}

package dev.junta.firmamobile.browser

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import dev.junta.firmamobile.BuildConfig

@SuppressLint("SetJavaScriptEnabled")
class TrustedJuntaWebView(context: Context) : WebView(context) {
    init {
        configureSettings()
        webChromeClient = JuntaWebChromeClient()
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(this@TrustedJuntaWebView, false)
        }
        setWebContentsDebuggingEnabled(BuildConfig.ENABLE_WEBVIEW_CONTENTS_DEBUGGING)
    }

    fun setPageProgressListener(listener: (Int) -> Unit) {
        webChromeClient = JuntaWebChromeClient(listener)
    }

    @Suppress("DEPRECATION")
    private fun configureSettings() {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            safeBrowsingEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            setGeolocationEnabled(false)
            mediaPlaybackRequiresUserGesture = true
        }
    }
}

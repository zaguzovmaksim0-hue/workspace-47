package dev.junta.firmamobile.browser

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.junta.firmamobile.BuildConfig

@SuppressLint("SetJavaScriptEnabled")
class TrustedJuntaWebView(context: Context) : WebView(context) {
    private val chrome = JuntaWebChromeClient()
    private var httpAuthClient: HttpAuthWebViewClient? = null

    override fun setWebViewClient(client: WebViewClient) {
        httpAuthClient?.close()
        val wrapped = HttpAuthWebViewClient(client)
        httpAuthClient = wrapped
        super.setWebViewClient(wrapped)
    }

    override fun destroy() {
        chrome.createWindow = null
        chrome.closeWindow = null
        httpAuthClient?.close()
        httpAuthClient = null
        super.destroy()
    }

    init {
        configureSettings()
        webChromeClient = chrome
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(this@TrustedJuntaWebView, false)
        }
        setWebContentsDebuggingEnabled(BuildConfig.ENABLE_WEBVIEW_CONTENTS_DEBUGGING)
    }

    /** Window requests are handled only when an owning browser host opts in. */
    fun setPopupListeners(
        create: ((WebView, Boolean, Boolean, android.os.Message) -> Boolean)?,
        close: ((WebView) -> Unit)?,
    ) {
        chrome.createWindow = create
        chrome.closeWindow = close
        settings.setSupportMultipleWindows(create != null)
    }

    fun setPageProgressListener(listener: (Int) -> Unit) {
        chrome.progressListener = listener
    }

    fun setFileChooserListener(listener: (WebView, android.webkit.ValueCallback<Array<android.net.Uri>>, android.webkit.WebChromeClient.FileChooserParams) -> Boolean) {
        chrome.fileChooser = listener
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

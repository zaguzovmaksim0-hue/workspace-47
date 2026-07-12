package dev.junta.firmamobile.browser

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.webkit.RenderProcessGoneDetail
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import dev.junta.firmamobile.afirma.AfirmaRequest
import dev.junta.firmamobile.security.DiagnosticEventCode
import dev.junta.firmamobile.security.SanitizedLogger

enum class BrowserErrorCode {
    NETWORK_ERROR,
    HTTP_ERROR,
    SSL_ERROR,
    RENDER_PROCESS_GONE,
    SAFE_BROWSING,
}

interface BrowserNavigationCallbacks {
    fun openExternal(uri: Uri)

    fun onAfirmaRequest(request: AfirmaRequest)

    fun onNavigationBlocked(reason: NavigationBlockReason)

    fun onBrowserError(error: BrowserErrorCode)

    fun onTopLevelUrlChanged(url: String) = Unit
}

class JuntaWebViewClient(
    private val callbacks: BrowserNavigationCallbacks,
    private val logger: SanitizedLogger,
    private val navigationPolicy: JuntaNavigationPolicy = JuntaNavigationPolicy(),
    private val currentPageUrl: (WebView) -> String? = { webView -> webView.url },
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean = handleNavigation(view, request.url.toString())

    @Deprecated("Legacy callback retained for old WebView implementations")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
        handleNavigation(view, url)

    private fun handleNavigation(view: WebView, targetUrl: String): Boolean =
        when (val decision = navigationPolicy.decide(targetUrl, currentPageUrl(view))) {
            NavigationDecision.AllowInWebView -> false
            is NavigationDecision.OpenExternal -> {
                logger.recordBrowserEvent(
                    DiagnosticEventCode.EXTERNAL_NAVIGATION,
                    decision.uri.host,
                )
                callbacks.openExternal(decision.uri)
                true
            }
            is NavigationDecision.HandleAfirma -> {
                logger.recordAfirmaRequest(decision.request)
                callbacks.onAfirmaRequest(decision.request)
                true
            }
            is NavigationDecision.Block -> {
                val event = if (decision.reason == NavigationBlockReason.PLAY_STORE_FALLBACK) {
                    DiagnosticEventCode.PLAY_STORE_FALLBACK_INTERCEPTED
                } else {
                    DiagnosticEventCode.NAVIGATION_BLOCKED
                }
                logger.recordBrowserEvent(event)
                callbacks.onNavigationBlocked(decision.reason)
                true
            }
        }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        callbacks.onTopLevelUrlChanged(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        callbacks.onTopLevelUrlChanged(url)
    }

    override fun onReceivedSslError(
        view: WebView,
        handler: SslErrorHandler,
        error: SslError,
    ) {
        handler.cancel()
        logger.recordBrowserEvent(DiagnosticEventCode.SSL_ERROR_CANCELLED)
        callbacks.onBrowserError(BrowserErrorCode.SSL_ERROR)
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    override fun onSafeBrowsingHit(
        view: WebView,
        request: WebResourceRequest,
        threatType: Int,
        callback: SafeBrowsingResponse,
    ) {
        callback.backToSafety(true)
        logger.recordBrowserEvent(DiagnosticEventCode.SAFE_BROWSING_BLOCKED)
        callbacks.onBrowserError(BrowserErrorCode.SAFE_BROWSING)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        if (request.isForMainFrame) {
            logger.recordBrowserEvent(DiagnosticEventCode.NETWORK_ERROR)
            callbacks.onBrowserError(BrowserErrorCode.NETWORK_ERROR)
        }
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse,
    ) {
        if (request.isForMainFrame && errorResponse.statusCode >= HTTP_ERROR_START) {
            callbacks.onBrowserError(BrowserErrorCode.HTTP_ERROR)
        }
    }

    override fun onRenderProcessGone(
        view: WebView,
        detail: RenderProcessGoneDetail,
    ): Boolean {
        callbacks.onBrowserError(BrowserErrorCode.RENDER_PROCESS_GONE)
        return true
    }

    private companion object {
        const val HTTP_ERROR_START = 400
    }
}

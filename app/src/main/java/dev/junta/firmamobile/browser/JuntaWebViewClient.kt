package dev.junta.firmamobile.browser

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.webkit.RenderProcessGoneDetail
import android.webkit.SafeBrowsingResponse
import android.webkit.ClientCertRequest
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
import dev.junta.firmamobile.profile.ProfileId

enum class BrowserErrorCode {
    NETWORK_ERROR,
    HTTP_ERROR,
    SSL_ERROR,
    RENDER_PROCESS_GONE,
    SAFE_BROWSING,
    CLIENT_CERT_PREFERENCES,
}

interface BrowserNavigationCallbacks {
    fun openExternal(uri: Uri)

    fun onAfirmaRequest(request: AfirmaRequest)

    fun onNavigationBlocked(reason: NavigationBlockReason)

    fun onBrowserError(error: BrowserErrorCode)

    fun onRenderProcessGone(view: WebView) {
        onBrowserError(BrowserErrorCode.RENDER_PROCESS_GONE)
    }

    fun onTopLevelNavigationStarted(url: String) = Unit

    fun onTopLevelUrlChanged(url: String) = Unit
}

class JuntaWebViewClient(
    private val callbacks: BrowserNavigationCallbacks,
    private val logger: SanitizedLogger,
    private val navigationPolicy: JuntaNavigationPolicy,
    private val currentPageUrl: (WebView) -> String? = { webView -> webView.url },
    private val clientAuthAuthorizer: ClientAuthNavigationAuthorizer? = null,
    private val activeProfileId: () -> ProfileId? = { null },
    private val currentNavigationEpoch: () -> Long = { 0L },
    private val onClientAuthTarget: (AuthorizedClientAuthTarget) -> Unit = {},
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean = handleNavigation(
            view,
            request.url.toString(),
            request.isForMainFrame,
            request.method,
        )

    @Deprecated("Legacy callback retained for old WebView implementations")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
        handleNavigation(view, url, false, UNKNOWN_METHOD)

    private fun handleNavigation(
        view: WebView,
        targetUrl: String,
        isModernMainFrame: Boolean,
        method: String,
    ): Boolean {
        val currentUrl = currentPageUrl(view)
        clientAuthAuthorizer?.observeTopLevelNavigation(
            activeProfileId = activeProfileId(),
            currentUrl = currentUrl,
            targetUrl = targetUrl,
            currentEpoch = currentNavigationEpoch(),
            isModernMainFrameRequest = isModernMainFrame,
        )?.let { authorized ->
            onClientAuthTarget(authorized)
            return true
        }
        return when (val decision = navigationPolicy.decide(targetUrl, currentUrl)) {
            NavigationDecision.AllowInWebView -> {
                if (isModernMainFrame) {
                    logger.recordNavigationEvent(
                        code = DiagnosticEventCode.NAVIGATION_ALLOWED,
                        rawUrl = targetUrl,
                        isMainFrame = true,
                        method = method,
                    )
                }
                false
            }
            is NavigationDecision.UpgradeToHttps -> {
                if (!isModernMainFrame || !method.equals(GET_METHOD, ignoreCase = true)) {
                    logger.recordNavigationEvent(
                        code = DiagnosticEventCode.NAVIGATION_BLOCKED,
                        rawUrl = targetUrl,
                        reason = NavigationBlockReason.INSECURE_HTTP.name,
                        isMainFrame = isModernMainFrame,
                        method = method,
                    )
                    callbacks.onNavigationBlocked(NavigationBlockReason.INSECURE_HTTP)
                } else {
                    val upgradedUrl = decision.uri.toString()
                    logger.recordNavigationEvent(
                        code = DiagnosticEventCode.NAVIGATION_ALLOWED,
                        rawUrl = upgradedUrl,
                        isMainFrame = true,
                        method = GET_METHOD,
                    )
                    view.loadUrl(upgradedUrl)
                }
                true
            }
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
                logger.recordNavigationEvent(
                    code = event,
                    rawUrl = targetUrl,
                    reason = decision.reason.name,
                    isMainFrame = isModernMainFrame,
                    method = method,
                )
                callbacks.onNavigationBlocked(decision.reason)
                true
            }
        }
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        logger.recordNavigationEvent(
            code = DiagnosticEventCode.PAGE_STARTED,
            rawUrl = url,
            isMainFrame = true,
            method = UNKNOWN_METHOD,
        )
        callbacks.onTopLevelNavigationStarted(url)
        callbacks.onTopLevelUrlChanged(url)
        clientAuthAuthorizer?.onTopLevelPageStarted(url, currentNavigationEpoch())
    }

    override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) {
        request.ignore()
    }

    override fun onPageFinished(view: WebView, url: String) {
        logger.recordNavigationEvent(
            code = DiagnosticEventCode.PAGE_FINISHED,
            rawUrl = url,
            isMainFrame = true,
            method = UNKNOWN_METHOD,
        )
        callbacks.onTopLevelUrlChanged(url)
        OfvirtualPageCompatibility.apply(view, url)
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        if (request.isForMainFrame) {
            logger.recordNavigationEvent(
                code = DiagnosticEventCode.NETWORK_REQUEST,
                rawUrl = request.url.toString(),
                isMainFrame = true,
                method = request.method,
            )
        }
        return null
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
        callbacks.onRenderProcessGone(view)
        return true
    }

    private companion object {
        const val HTTP_ERROR_START = 400
        const val UNKNOWN_METHOD = "UNKNOWN"
        const val GET_METHOD = "GET"
    }
}

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
import dev.junta.firmamobile.profile.ClientAuthTransitionMode
import dev.junta.firmamobile.profile.HttpMethod
import dev.junta.firmamobile.profile.ProfileId
import java.net.URI
import java.util.concurrent.atomic.AtomicReference

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

    fun openOfficialAutoFirma(uri: Uri)

    fun onAfirmaRequest(request: AfirmaRequest)

    fun onNavigationBlocked(reason: NavigationBlockReason)

    fun onBrowserError(error: BrowserErrorCode)

    fun onRenderProcessGone(view: WebView) {
        onBrowserError(BrowserErrorCode.RENDER_PROCESS_GONE)
    }

    fun onTopLevelNavigationStarted(url: String) = Unit

    fun onTopLevelUrlChanged(url: String) = Unit

    fun onTopLevelPageFinished(url: String) = Unit
}

class JuntaWebViewClient(
    private val callbacks: BrowserNavigationCallbacks,
    private val logger: SanitizedLogger,
    private val navigationPolicy: JuntaNavigationPolicy,
    private val currentPageUrl: (WebView) -> String? = { webView -> webView.url },
    private val isActiveWebView: (WebView) -> Boolean = { true },
    private val clientAuthAuthorizer: ClientAuthNavigationAuthorizer? = null,
    private val activeProfileId: () -> ProfileId? = { null },
    private val currentNavigationEpoch: () -> Long = { 0L },
    private val onClientAuthTarget: (AuthorizedClientAuthTarget) -> Unit = {},
    private val onInPlaceClientAuthChallenge: (AuthorizedClientAuthTarget, ClientCertRequest) -> Unit = { _, request ->
        request.ignore()
    },
) : WebViewClient() {
    private val observedTopLevelUrl = AtomicReference<String?>(null)
    private val pendingInPlaceClientAuth = AtomicReference<PendingInPlaceClientAuth?>(null)
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
        if (!isCurrentWebView(view)) return true
        val currentUrl = currentPageUrl(view)
        val currentProfileId = activeProfileId()
        if (currentProfileId?.value != EuskadiClientAuthPostBridgeAdapter.PROFILE_ID) {
            clientAuthAuthorizer?.observeTopLevelNavigation(
                activeProfileId = currentProfileId,
                currentUrl = currentUrl,
                targetUrl = targetUrl,
                currentEpoch = currentNavigationEpoch(),
                isModernMainFrameRequest = isModernMainFrame,
            )?.let { authorized ->
                onClientAuthTarget(authorized)
                return true
            }
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
            is NavigationDecision.OpenOfficialAutoFirma -> {
                if (!isModernMainFrame || !method.equals(GET_METHOD, ignoreCase = true)) {
                    logger.recordNavigationEvent(
                        code = DiagnosticEventCode.NAVIGATION_BLOCKED,
                        rawUrl = targetUrl,
                        reason = NavigationBlockReason.UNSUPPORTED_EXTERNAL_INTENT.name,
                        isMainFrame = isModernMainFrame,
                        method = method,
                    )
                } else {
                    logger.recordBrowserEvent(DiagnosticEventCode.EXTERNAL_NAVIGATION, "AutoFirma")
                    callbacks.openOfficialAutoFirma(decision.uri)
                }
                true
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
                    if (isModernMainFrame) {
                        callbacks.onNavigationBlocked(NavigationBlockReason.INSECURE_HTTP)
                    }
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
                if (!isModernMainFrame) {
                    logger.recordNavigationEvent(
                        code = DiagnosticEventCode.NAVIGATION_BLOCKED,
                        rawUrl = targetUrl,
                        reason = NavigationBlockReason.UNTRUSTED_EXTERNAL_NAVIGATION.name,
                        isMainFrame = false,
                        method = method,
                    )
                } else {
                    logger.recordBrowserEvent(
                        DiagnosticEventCode.EXTERNAL_NAVIGATION,
                        decision.uri.host,
                    )
                    callbacks.openExternal(decision.uri)
                }
                true
            }
            is NavigationDecision.HandleAfirma -> {
                if (!isModernMainFrame) {
                    logger.recordNavigationEvent(
                        code = DiagnosticEventCode.NAVIGATION_BLOCKED,
                        rawUrl = targetUrl,
                        reason = NavigationBlockReason.UNTRUSTED_AFIRMA_ORIGIN.name,
                        isMainFrame = false,
                        method = method,
                    )
                } else {
                    logger.recordAfirmaRequest(decision.request)
                    callbacks.onAfirmaRequest(decision.request)
                }
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
                if (isModernMainFrame) {
                    callbacks.onNavigationBlocked(decision.reason)
                }
                true
            }
        }
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        if (!isCurrentWebView(view)) return
        observedTopLevelUrl.set(url)
        val inPlaceTargetStart = pendingInPlaceClientAuth.get()?.let { pending ->
            if (url == pending.authorized.target.toASCIIString()) {
                pending
            } else {
                pendingInPlaceClientAuth.compareAndSet(pending, null)
                null
            }
        }
        recordVeaAuthReturnDiagnostic(url)
        logger.recordNavigationEvent(
            code = DiagnosticEventCode.PAGE_STARTED,
            rawUrl = url,
            isMainFrame = true,
            method = UNKNOWN_METHOD,
        )
        callbacks.onTopLevelNavigationStarted(url)
        callbacks.onTopLevelUrlChanged(url)
        inPlaceTargetStart?.let { pending ->
            val current = pendingInPlaceClientAuth.get()
            if (current === pending &&
                pending.authorized.profileId == activeProfileId() &&
                !pending.authorized.isExpiredOrInvalid()
            ) {
                pendingInPlaceClientAuth.compareAndSet(
                    pending,
                    pending.copy(navigationEpoch = currentNavigationEpoch()),
                )
            }
        }
        clientAuthAuthorizer?.onTopLevelPageStarted(url, currentNavigationEpoch())
    }

    internal fun armConfirmedInPlaceClientAuth(
        authorized: AuthorizedClientAuthTarget,
        navigationEpoch: Long,
    ): Boolean {
        if (authorized.policy.transitionMode != ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE ||
            authorized.policy.requestMethod != HttpMethod.GET ||
            authorized.isExpiredOrInvalid()
        ) {
            return false
        }
        pendingInPlaceClientAuth.set(PendingInPlaceClientAuth(authorized, navigationEpoch))
        return true
    }

    override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) {
        if (!isCurrentWebView(view)) {
            request.ignore()
            return
        }
        val pending = pendingInPlaceClientAuth.getAndSet(null)
        if (pending == null || pending.navigationEpoch != currentNavigationEpoch() ||
            pending.authorized.profileId != activeProfileId() || pending.authorized.isExpiredOrInvalid() ||
            !request.host.equals(pending.authorized.target.host, ignoreCase = true) ||
            request.port != pending.authorized.policy.requestPort
        ) {
            request.ignore()
            return
        }
        onInPlaceClientAuthChallenge(pending.authorized, request)
    }

    override fun onPageFinished(view: WebView, url: String) {
        if (!isCurrentWebView(view)) return
        observedTopLevelUrl.set(url)
        logger.recordNavigationEvent(
            code = DiagnosticEventCode.PAGE_FINISHED,
            rawUrl = url,
            isMainFrame = true,
            method = UNKNOWN_METHOD,
        )
        callbacks.onTopLevelUrlChanged(url)
        callbacks.onTopLevelPageFinished(url)
        OfvirtualPageCompatibility.apply(view, url)
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        if (!isCurrentWebView(view)) return null
        if (request.isForMainFrame) {
            recordVeaAuthReturnDiagnostic(request.url.toString())
            logger.recordNavigationEvent(
                code = DiagnosticEventCode.NETWORK_REQUEST,
                rawUrl = request.url.toString(),
                isMainFrame = true,
                method = request.method,
            )
            clientAuthAuthorizer?.observeTopLevelResourceRequest(
                activeProfileId = activeProfileId(),
                currentUrl = observedTopLevelUrl.get(),
                targetUrl = request.url.toString(),
                method = request.method,
                currentEpoch = currentNavigationEpoch(),
                isMainFrameRequest = true,
            )?.let { authorized ->
                pendingInPlaceClientAuth.set(
                    PendingInPlaceClientAuth(authorized, currentNavigationEpoch()),
                )
            }
        }
        return null
    }

    override fun onReceivedSslError(
        view: WebView,
        handler: SslErrorHandler,
        error: SslError,
    ) {
        handler.cancel()
        if (isCurrentWebView(view)) {
            logger.recordBrowserEvent(DiagnosticEventCode.SSL_ERROR_CANCELLED)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    override fun onSafeBrowsingHit(
        view: WebView,
        request: WebResourceRequest,
        threatType: Int,
        callback: SafeBrowsingResponse,
    ) {
        callback.backToSafety(true)
        if (isCurrentWebView(view)) {
            logger.recordBrowserEvent(DiagnosticEventCode.SAFE_BROWSING_BLOCKED)
            if (request.isForMainFrame) callbacks.onBrowserError(BrowserErrorCode.SAFE_BROWSING)
        }
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        if (request.isForMainFrame && isCurrentWebView(view)) {
            logger.recordBrowserEvent(DiagnosticEventCode.NETWORK_ERROR)
            callbacks.onBrowserError(BrowserErrorCode.NETWORK_ERROR)
        }
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse,
    ) {
        if (request.isForMainFrame && errorResponse.statusCode >= HTTP_ERROR_START &&
            isCurrentWebView(view)
        ) {
            callbacks.onBrowserError(BrowserErrorCode.HTTP_ERROR)
        }
    }

    override fun onRenderProcessGone(
        view: WebView,
        detail: RenderProcessGoneDetail,
    ): Boolean {
        if (isCurrentWebView(view)) callbacks.onRenderProcessGone(view)
        return true
    }

    private fun recordVeaAuthReturnDiagnostic(rawUrl: String) {
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return
        if (!uri.scheme.equals("https", ignoreCase = true)) return
        val host = uri.host?.lowercase() ?: return
        val names = uri.rawQuery
            ?.split('&')
            ?.mapNotNull { part -> part.substringBefore('=').takeIf(String::isNotEmpty) }
            ?.toSet()
            .orEmpty()
        val stage = when {
            host == "api-veaja.cloud.juntadeandalucia.es" &&
                uri.rawPath == "/auth/returnLogin" && names == setOf("resCode") ->
                "vea-auth-return-rescode"

            host == "api-veaja.cloud.juntadeandalucia.es" &&
                uri.rawPath == "/auth/returnLogin" && names.isNotEmpty() ->
                "vea-auth-return-shape-" + names.sorted().joinToString("-")

            host == "veaja.cloud.juntadeandalucia.es" &&
                uri.rawPath == "/authFacade" && names == setOf("token", "redirectUrl") ->
                "vea-auth-success"

            host == "veaja.cloud.juntadeandalucia.es" &&
                uri.rawPath == "/authFacade" && names == setOf("error", "redirectUrl") ->
                "vea-auth-error"

            else -> null
        } ?: return
        logger.recordPortalCallback(stage = stage, host = host)
    }

    private fun isCurrentWebView(view: WebView): Boolean = try {
        isActiveWebView(view)
    } catch (_: Exception) {
        false
    }

    private data class PendingInPlaceClientAuth(
        val authorized: AuthorizedClientAuthTarget,
        val navigationEpoch: Long,
    )

    private companion object {
        const val HTTP_ERROR_START = 400
        const val UNKNOWN_METHOD = "UNKNOWN"
        const val GET_METHOD = "GET"
    }
}

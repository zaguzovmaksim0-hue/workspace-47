package dev.junta.firmamobile.browser

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.junta.firmamobile.network.JuntaOriginPolicy
import dev.junta.firmamobile.security.DiagnosticEventCode
import dev.junta.firmamobile.security.SanitizedLogger

class ProtocolProbeActivity : ComponentActivity() {
    private lateinit var webView: TrustedJuntaWebView
    private lateinit var statusView: TextView
    private var bridgeAttachment: WebMessageBridgeAttachment? = null
    private var probeListenerAttached = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        statusView = TextView(this).apply {
            text = STATUS_STARTING
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        webView = TrustedJuntaWebView(this)
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    statusView,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
                addView(
                    webView,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f,
                    ),
                )
            },
        )

        val logger = SanitizedLogger()
        val recorder = ProtocolObservationRecorder(logger) { safeText ->
            statusView.text = safeText.ifBlank { STATUS_WAITING }
        }
        if (!attachOriginRestrictedProbe(recorder)) {
            statusView.text = STATUS_INCOMPATIBLE
            return
        }

        webView.webViewClient = ProtocolProbeWebViewClient(
            recorder = recorder,
            logger = logger,
            onSafeStatus = { safeStatus ->
                statusView.text = recorder.exportText().ifBlank { safeStatus }
            },
        )
        bridgeAttachment = WebMessageBridge(
            logger = logger,
            onAfirmaRequest = { request ->
                recorder.recordBranch(ObservedRuntimeBranch.AFIRMA, request.origin.host)
            },
        ).attach(webView)
        if (bridgeAttachment?.listenerAttached != true ||
            bridgeAttachment?.documentStartScriptAttached != true
        ) {
            statusView.text = STATUS_INCOMPATIBLE
            return
        }
        webView.loadUrl(JuntaOriginPolicy.START_URL)
    }

    private fun attachOriginRestrictedProbe(recorder: ProtocolObservationRecorder): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        ) {
            return false
        }
        WebViewCompat.addWebMessageListener(
            webView,
            PROBE_BRIDGE_NAME,
            JuntaOriginPolicy.webMessageOriginRules,
        ) { _, message, sourceOrigin, isMainFrame, _ ->
            if (message.type == WebMessageCompat.TYPE_STRING) {
                message.data?.let { rawMessage ->
                    recorder.recordMessage(rawMessage, sourceOrigin, isMainFrame)
                }
            }
        }
        probeListenerAttached = true
        return true
    }

    override fun onDestroy() {
        bridgeAttachment?.close()
        bridgeAttachment = null
        if (probeListenerAttached) {
            WebViewCompat.removeWebMessageListener(webView, PROBE_BRIDGE_NAME)
            probeListenerAttached = false
        }
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val PROBE_BRIDGE_NAME = "JuntaFirmaProbe"
        private const val STATUS_STARTING = "Preparando observación segura…"
        private const val STATUS_WAITING = "Esperando una llamada MiniApplet…"
        private const val STATUS_INCOMPATIBLE = "WebView incompatible con observación segura."
    }
}

private class ProtocolProbeWebViewClient(
    private val recorder: ProtocolObservationRecorder,
    private val logger: SanitizedLogger,
    private val onSafeStatus: (String) -> Unit,
    private val navigationPolicy: JuntaNavigationPolicy = JuntaNavigationPolicy(),
) : WebViewClient() {
    @Volatile
    private var topLevelOriginHost: String = INVALID_HOST

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        handleNavigation(view, request.url.toString())

    @Deprecated("Legacy callback retained for WebView compatibility")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
        handleNavigation(view, url)

    private fun handleNavigation(view: WebView, targetUrl: String): Boolean {
        val scheme = try {
            Uri.parse(targetUrl).scheme?.lowercase()
        } catch (_: Exception) {
            null
        }
        val branch = when (scheme) {
            "afirma" -> ObservedRuntimeBranch.AFIRMA
            "intent" -> ObservedRuntimeBranch.INTENT
            "ws", "wss" -> ObservedRuntimeBranch.WEBSOCKET
            else -> null
        }
        branch?.let { recorder.recordBranch(it, topLevelOriginHost) }

        return when (val decision = navigationPolicy.decide(targetUrl, view.url)) {
            NavigationDecision.AllowInWebView -> false
            is NavigationDecision.HandleAfirma -> {
                logger.recordAfirmaRequest(decision.request)
                true
            }
            is NavigationDecision.OpenExternal -> {
                logger.recordBrowserEvent(DiagnosticEventCode.NAVIGATION_BLOCKED)
                true
            }
            is NavigationDecision.Block -> {
                val event = if (decision.reason == NavigationBlockReason.PLAY_STORE_FALLBACK) {
                    DiagnosticEventCode.PLAY_STORE_FALLBACK_INTERCEPTED
                } else {
                    DiagnosticEventCode.NAVIGATION_BLOCKED
                }
                logger.recordBrowserEvent(event)
                true
            }
        }
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val uri = request.url
        if (uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(TRIPHASE_HOST, ignoreCase = true) &&
            uri.path?.endsWith(TRIPHASE_PATH_SUFFIX) == true
        ) {
            recorder.recordBranch(ObservedRuntimeBranch.DIRECT_NETWORK, topLevelOriginHost)
        }
        return null
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        topLevelOriginHost = trustedHostFor(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        topLevelOriginHost = trustedHostFor(url)
        onSafeStatus("Página Junta cargada. Esperando una llamada MiniApplet…")
    }

    override fun onReceivedSslError(
        view: WebView,
        handler: SslErrorHandler,
        error: SslError,
    ) {
        handler.cancel()
        logger.recordBrowserEvent(DiagnosticEventCode.SSL_ERROR_CANCELLED)
        onSafeStatus("Error TLS cancelado.")
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        if (request.isForMainFrame) {
            logger.recordBrowserEvent(DiagnosticEventCode.NETWORK_ERROR)
            onSafeStatus("Error de red del portal.")
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
        logger.recordBrowserEvent(DiagnosticEventCode.SAFE_BROWSING_BLOCKED)
        onSafeStatus("Safe Browsing bloqueó la página.")
    }

    private fun trustedHostFor(url: String): String = try {
        url
            .let(Uri::parse)
            ?.let(JuntaOriginPolicy::originFor)
            ?.host
            ?: INVALID_HOST
    } catch (_: Exception) {
        INVALID_HOST
    }

    private companion object {
        const val INVALID_HOST = "invalid"
        const val TRIPHASE_HOST = "ws024.juntadeandalucia.es"
        const val TRIPHASE_PATH_SUFFIX = "/TriPhaseSignatureService"
    }
}

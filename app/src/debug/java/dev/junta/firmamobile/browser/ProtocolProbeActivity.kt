package dev.junta.firmamobile.browser

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.view.ViewGroup
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.junta.firmamobile.R
import dev.junta.firmamobile.network.JuntaOriginPolicy
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.network.TrustedOrigin
import dev.junta.firmamobile.security.DiagnosticEventCode
import dev.junta.firmamobile.security.SanitizedLogger
import dev.junta.firmamobile.ui.BrowserAddressPresentation
import dev.junta.firmamobile.ui.BrowserWindowInsetsPolicy
import org.json.JSONTokener
import org.json.JSONObject

class ProtocolProbeActivity : ComponentActivity() {
    private lateinit var webView: TrustedJuntaWebView
    private lateinit var statusView: TextView
    private lateinit var hostView: TextView
    private lateinit var urlDetailsView: TextView
    private lateinit var detailsButton: Button
    private var bridgeAttachment: WebMessageBridgeAttachment? = null
    private var probeListenerAttached = false
    private var detailsExpanded = false
    private var currentUrl = JuntaOriginPolicy.START_URL
    internal lateinit var protocolRecorderForTesting: ProtocolObservationRecorder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        statusView = TextView(this).apply {
            id = R.id.protocol_probe_status
            text = STATUS_STARTING
            maxHeight = dp(PROBE_STATUS_MAX_HEIGHT_DP)
            isVerticalScrollBarEnabled = true
            movementMethod = ScrollingMovementMethod.getInstance()
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        hostView = TextView(this).apply {
            text = BrowserAddressPresentation.hostOf(currentUrl)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(16), dp(8), dp(8), dp(8))
        }
        detailsButton = Button(this).apply {
            text = getString(R.string.browser_probe_details)
            setOnClickListener { toggleDetails() }
        }
        urlDetailsView = TextView(this).apply {
            visibility = View.GONE
            maxLines = 1
            setHorizontallyScrolling(true)
            setTextIsSelectable(true)
            setPadding(dp(16), dp(4), dp(16), dp(8))
        }
        webView = TrustedJuntaWebView(this).apply {
            id = R.id.protocol_probe_webview
        }
        val root = LinearLayout(this).apply {
            id = R.id.protocol_probe_root
            orientation = LinearLayout.VERTICAL
            addView(
                LinearLayout(this@ProtocolProbeActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(
                        hostView,
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                    )
                    addView(
                        detailsButton,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ),
                    )
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                urlDetailsView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
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
        }
        BrowserWindowInsetsPolicy.install(root)
        setContentView(root)

        val logger = SanitizedLogger()
        val recorder = ProtocolObservationRecorder(
            logger = logger,
            onUpdated = { safeText ->
                statusView.text = safeText.ifBlank { STATUS_WAITING }
            },
        )
        protocolRecorderForTesting = recorder
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
            onUrlChanged = ::updatePageUrl,
        )
        bridgeAttachment = WebMessageBridge(
            profileId = JUNTA_PROFILE,
            logger = logger,
            onAfirmaRequest = {},
            miniAppletMode = MiniAppletBridgeMode.OBSERVATION,
        ).attach(webView)
        if (bridgeAttachment?.listenerAttached != true ||
            bridgeAttachment?.documentStartScriptAttached != true
        ) {
            statusView.text = STATUS_INCOMPATIBLE
            return
        }
        if (!intent.getBooleanExtra(EXTRA_SKIP_INITIAL_LOAD, false)) {
            webView.loadUrl(JuntaOriginPolicy.START_URL)
        }
    }

    private fun toggleDetails() {
        detailsExpanded = !detailsExpanded
        if (detailsExpanded) {
            urlDetailsView.text = currentUrl
            urlDetailsView.visibility = View.VISIBLE
            detailsButton.text = getString(R.string.browser_probe_hide_details)
        } else {
            urlDetailsView.text = ""
            urlDetailsView.visibility = View.GONE
            detailsButton.text = getString(R.string.browser_probe_details)
        }
    }

    private fun updatePageUrl(url: String) {
        currentUrl = url
        hostView.text = BrowserAddressPresentation.hostOf(url)
        if (detailsExpanded) urlDetailsView.text = url
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
            JuntaOriginPolicy.webMessageOriginRules(JUNTA_PROFILE),
        ) { _, message, sourceOrigin, isMainFrame, _ ->
            if (message.type == WebMessageCompat.TYPE_STRING) {
                val rawMessage = message.data
                if (rawMessage != null) {
                    recorder.recordMessage(rawMessage, sourceOrigin, isMainFrame)
                } else {
                    recorder.rejectInvalidMessage(sourceOrigin, isMainFrame)
                }
            } else {
                recorder.rejectInvalidMessage(sourceOrigin, isMainFrame)
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
        const val EXTRA_SKIP_INITIAL_LOAD =
            "dev.junta.firmamobile.debug.SKIP_PROTOCOL_PROBE_INITIAL_LOAD"
        private const val STATUS_STARTING = "Preparando observación segura…"
        private const val STATUS_WAITING = "Esperando una llamada MiniApplet…"
        private const val STATUS_INCOMPATIBLE = "WebView incompatible con observación segura."
        private val JUNTA_PROFILE = ProfileId("junta-andalucia")
        private const val PROBE_STATUS_MAX_HEIGHT_DP = 144
    }
}

private class ProtocolProbeWebViewClient(
    private val recorder: ProtocolObservationRecorder,
    private val logger: SanitizedLogger,
    private val onSafeStatus: (String) -> Unit,
    private val onUrlChanged: (String) -> Unit,
    private val navigationPolicy: JuntaNavigationPolicy = JuntaNavigationPolicy(
        ProfileId("junta-andalucia"),
    ),
) : WebViewClient() {
    @Volatile
    private var topLevelOriginHost: String = INVALID_HOST
    private var currentDocumentGeneration = 0L

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        handleNavigation(view, request.url.toString(), request.isForMainFrame)

    @Deprecated("Legacy callback retained for WebView compatibility")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
        handleNavigation(view, url, isForMainFrame = false)

    private fun handleNavigation(
        view: WebView,
        targetUrl: String,
        isForMainFrame: Boolean,
    ): Boolean {
        val branch = try {
            when (Uri.parse(targetUrl).scheme?.lowercase()) {
                "afirma" -> ObservedRuntimeBranch.AFIRMA
                "intent" -> ObservedRuntimeBranch.INTENT
                "ws", "wss" -> ObservedRuntimeBranch.WEBSOCKET
                else -> null
            }
        } catch (_: Exception) {
            null
        }
        if (branch != null && branch in NATIVE_CORRELATION_BRANCHES) {
            recorder.recordNavigationBranch(
                branch = branch,
                originHost = topLevelOriginHost,
                isMainFrame = isForMainFrame,
            )
        }
        val decision = navigationPolicy.decide(targetUrl, view.url)
        if ((branch == null || branch !in NATIVE_CORRELATION_BRANCHES) &&
            decision !is NavigationDecision.AllowInWebView
        ) {
            recorder.rejectNavigationTransition(
                originHost = topLevelOriginHost,
                isMainFrame = isForMainFrame,
            )
        }
        return when (decision) {
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

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        currentDocumentGeneration = recorder.beginDocument()
        topLevelOriginHost = trustedHostFor(url)
        onUrlChanged(url)
    }

    override fun onPageCommitVisible(view: WebView, url: String) {
        activateCurrentProbeDocument(view)
    }

    override fun onPageFinished(view: WebView, url: String) {
        topLevelOriginHost = trustedHostFor(url)
        onUrlChanged(url)
        activateCurrentProbeDocument(view)
        onSafeStatus("Página Junta cargada. Esperando una llamada MiniApplet…")
    }

    private fun activateCurrentProbeDocument(view: WebView) {
        val generation = currentDocumentGeneration
        if (generation <= 0L) return
        view.evaluateJavascript(PROBE_DOCUMENT_BINDING_EXPRESSION) { encodedValue ->
            if (generation != currentDocumentGeneration) return@evaluateJavascript
            val binding = parseDocumentBinding(encodedValue)
            if (binding == null) {
                recorder.rejectCurrentDocument(generation)
            } else {
                val activated = recorder.activateDocument(
                    binding.documentId,
                    binding.origin.host,
                    generation,
                )
                if (activated) topLevelOriginHost = binding.origin.host
            }
        }
    }

    private fun parseDocumentBinding(encodedValue: String): ProbeDocumentBinding? = try {
        val serialized = JSONTokener(encodedValue).nextValue() as? String ?: return null
        val json = JSONObject(serialized)
        if (json.length() != PROBE_DOCUMENT_BINDING_KEYS.size ||
            PROBE_DOCUMENT_BINDING_KEYS.any { !json.has(it) }
        ) {
            return null
        }
        val documentId = json.opt(DOCUMENT_ID_FIELD) as? String ?: return null
        val serializedOrigin = json.opt(ORIGIN_FIELD) as? String ?: return null
        val origin = JuntaOriginPolicy.originFor(Uri.parse(serializedOrigin)) ?: return null
        ProbeDocumentBinding(documentId, origin)
    } catch (_: Exception) {
        null
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
        const val DOCUMENT_ID_FIELD = "documentId"
        const val ORIGIN_FIELD = "origin"
        const val PROBE_DOCUMENT_BINDING_EXPRESSION =
            "JSON.stringify({documentId:window.__jfmProbeDocumentId||null," +
                "origin:window.location.origin})"
        val PROBE_DOCUMENT_BINDING_KEYS = setOf(DOCUMENT_ID_FIELD, ORIGIN_FIELD)
        val NATIVE_CORRELATION_BRANCHES = setOf(
            ObservedRuntimeBranch.AFIRMA,
            ObservedRuntimeBranch.INTENT,
        )
    }
}

private data class ProbeDocumentBinding(
    val documentId: String,
    val origin: TrustedOrigin,
)

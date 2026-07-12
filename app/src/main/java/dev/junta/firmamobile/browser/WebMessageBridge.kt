package dev.junta.firmamobile.browser

import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.ScriptHandler
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.junta.firmamobile.afirma.AfirmaRequest
import dev.junta.firmamobile.network.JuntaOriginPolicy
import dev.junta.firmamobile.security.DiagnosticEventCode
import dev.junta.firmamobile.security.SanitizedLogger

class WebMessageBridge(
    private val logger: SanitizedLogger,
    private val onAfirmaRequest: (AfirmaRequest) -> Unit,
    private val router: WebMessageRouter = WebMessageRouter(),
) {
    fun attach(webView: WebView): WebMessageBridgeAttachment {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            logger.recordBrowserEvent(DiagnosticEventCode.WEB_MESSAGE_FEATURE_UNAVAILABLE)
            return WebMessageBridgeAttachment(webView = webView)
        }

        WebViewCompat.addWebMessageListener(
            webView,
            BRIDGE_NAME,
            JuntaOriginPolicy.webMessageOriginRules,
        ) { _, message, sourceOrigin, isMainFrame, replyProxy ->
            receive(message, sourceOrigin, isMainFrame, replyProxy)
        }

        val scriptHandler = if (
            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        ) {
            WebViewCompat.addDocumentStartJavaScript(
                webView,
                AfirmaJavascriptShim.load(webView.context),
                JuntaOriginPolicy.webMessageOriginRules,
            )
        } else {
            logger.recordBrowserEvent(DiagnosticEventCode.DOCUMENT_START_SCRIPT_UNAVAILABLE)
            null
        }

        return WebMessageBridgeAttachment(
            webView = webView,
            listenerAttached = true,
            scriptHandler = scriptHandler,
        )
    }

    private fun receive(
        message: WebMessageCompat,
        sourceOrigin: android.net.Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy,
    ) {
        val rawMessage = message.data
        if (message.type != WebMessageCompat.TYPE_STRING || rawMessage == null) {
            logger.recordBrowserEvent(DiagnosticEventCode.WEB_MESSAGE_REJECTED)
            return
        }

        when (
            val result = router.route(
                rawMessage = rawMessage,
                sourceOrigin = sourceOrigin,
                isMainFrame = isMainFrame,
            )
        ) {
            is WebMessageRouteResult.Accepted -> {
                logger.recordAfirmaRequest(result.request)
                val delivered = runCatching { onAfirmaRequest(result.request) }.isSuccess
                if (delivered) {
                    reply(
                        replyProxy = replyProxy,
                        requestId = result.requestId,
                        status = WebMessageReplyStatus.ACCEPTED,
                    )
                } else {
                    logger.recordBrowserEvent(DiagnosticEventCode.WEB_MESSAGE_REJECTED)
                    reply(
                        replyProxy = replyProxy,
                        requestId = result.requestId,
                        status = WebMessageReplyStatus.REJECTED,
                        errorCode = ERROR_NATIVE_HANDLER_FAILURE,
                    )
                }
            }
            is WebMessageRouteResult.Rejected -> {
                logger.recordBrowserEvent(DiagnosticEventCode.WEB_MESSAGE_REJECTED)
                result.requestId?.let { requestId ->
                    reply(
                        replyProxy = replyProxy,
                        requestId = requestId,
                        status = WebMessageReplyStatus.REJECTED,
                        errorCode = result.errorCode,
                    )
                }
            }
        }
    }

    private fun reply(
        replyProxy: JavaScriptReplyProxy,
        requestId: String,
        status: WebMessageReplyStatus,
        errorCode: String? = null,
    ) {
        runCatching {
            replyProxy.postMessage(
                WebMessageProtocol.replyJson(
                    requestId = requestId,
                    status = status,
                    errorCode = errorCode,
                ),
            )
        }.onFailure {
            logger.recordBrowserEvent(DiagnosticEventCode.WEB_MESSAGE_REJECTED)
        }
    }

    companion object {
        const val BRIDGE_NAME = "JuntaFirmaMobile"
        private const val ERROR_NATIVE_HANDLER_FAILURE = "NATIVE_HANDLER_FAILURE"
    }
}

class WebMessageBridgeAttachment internal constructor(
    private val webView: WebView,
    val listenerAttached: Boolean = false,
    private val scriptHandler: ScriptHandler? = null,
) : AutoCloseable {
    val documentStartScriptAttached: Boolean
        get() = scriptHandler != null

    private var closed = false

    override fun close() {
        if (closed) return
        closed = true
        scriptHandler?.remove()
        if (listenerAttached) {
            WebViewCompat.removeWebMessageListener(webView, WebMessageBridge.BRIDGE_NAME)
        }
    }
}

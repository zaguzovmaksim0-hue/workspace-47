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
import dev.junta.firmamobile.signing.SigningErrorCode
import java.util.UUID

class WebMessageBridge(
    private val logger: SanitizedLogger,
    private val onAfirmaRequest: (AfirmaRequest) -> Unit,
    private val onMiniAppletRequest: ((MiniAppletBridgeRequest, MiniAppletReplyChannel) -> Unit)? =
        null,
    private val onMiniAppletCancel: (UUID) -> Unit = {},
    private val router: WebMessageRouter = WebMessageRouter(),
    private val miniAppletAdapter: MiniAppletBridgeAdapter = MiniAppletBridgeAdapter(),
    private val miniAppletMode: MiniAppletBridgeMode = MiniAppletBridgeMode.OBSERVATION,
) {
    private val replyRegistry = MiniAppletReplyRegistry()

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
                AfirmaJavascriptShim.load(webView.context, miniAppletMode),
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
            abandonPending = ::abandonAllMiniAppletRequests,
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
            val directResult = miniAppletAdapter.route(
                rawMessage = rawMessage,
                sourceOrigin = sourceOrigin,
                isMainFrame = isMainFrame,
            )
        ) {
            is MiniAppletBridgeRouteResult.Accepted -> {
                receiveMiniAppletRequest(directResult.request, replyProxy)
                return
            }
            is MiniAppletBridgeRouteResult.Rejected -> {
                logger.recordBrowserEvent(DiagnosticEventCode.WEB_MESSAGE_REJECTED)
                directResult.requestId?.let { requestId ->
                    MiniAppletReplyChannel(
                        requestId = requestId,
                        postMessage = replyProxy::postMessage,
                    ).failure(directResult.code)
                }
                return
            }
            is MiniAppletBridgeRouteResult.Cancelled -> {
                if (replyRegistry.abandon(
                        directResult.requestId,
                        directResult.navigationId,
                    )
                ) {
                    runCatching { onMiniAppletCancel(directResult.requestId) }
                }
                return
            }
            MiniAppletBridgeRouteResult.NotApplicable -> Unit
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

    private fun receiveMiniAppletRequest(
        request: MiniAppletBridgeRequest,
        replyProxy: JavaScriptReplyProxy,
    ) {
        if (miniAppletMode != MiniAppletBridgeMode.FUNCTIONAL) {
            request.normalized.close()
            MiniAppletReplyChannel(
                requestId = request.normalized.requestId,
                postMessage = replyProxy::postMessage,
            ).failure(SigningErrorCode.UNSUPPORTED_PROTOCOL)
            return
        }
        val reply = replyRegistry.create(
            requestId = request.normalized.requestId,
            navigationId = request.normalized.context.navigationId,
            postMessage = replyProxy::postMessage,
        )
        if (reply == null) {
            request.normalized.close()
            MiniAppletReplyChannel(
                requestId = request.normalized.requestId,
                postMessage = replyProxy::postMessage,
            ).failure(SigningErrorCode.PROTOCOL_FAILED)
            return
        }
        val handler = onMiniAppletRequest
        if (handler == null) {
            request.normalized.close()
            reply.failure(SigningErrorCode.UNSUPPORTED_PROTOCOL)
            return
        }
        runCatching { handler(request, reply) }.onFailure {
            request.normalized.close()
            logger.recordBrowserEvent(DiagnosticEventCode.WEB_MESSAGE_REJECTED)
            reply.failure(SigningErrorCode.PROTOCOL_FAILED)
        }
    }

    private fun abandonAllMiniAppletRequests() {
        replyRegistry.abandonAll().forEach { requestId ->
            runCatching { onMiniAppletCancel(requestId) }
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
    private val abandonPending: () -> Unit = {},
) : AutoCloseable {
    val documentStartScriptAttached: Boolean
        get() = scriptHandler != null

    private var closed = false

    fun abandonMiniAppletRequests() {
        abandonPending()
    }

    override fun close() {
        if (closed) return
        closed = true
        abandonPending()
        scriptHandler?.remove()
        if (listenerAttached) {
            WebViewCompat.removeWebMessageListener(webView, WebMessageBridge.BRIDGE_NAME)
        }
    }
}

internal class MiniAppletReplyRegistry {
    private val pending = linkedMapOf<UUID, PendingReply>()
    private val seen = linkedSetOf<UUID>()

    @Synchronized
    fun create(
        requestId: UUID,
        navigationId: NavigationId,
        postMessage: (String) -> Unit,
    ): MiniAppletReplyChannel? {
        if (requestId in seen || pending.isNotEmpty() || seen.size >= MAX_SEEN_REQUESTS) {
            return null
        }
        lateinit var channel: MiniAppletReplyChannel
        channel = MiniAppletReplyChannel(
            requestId = requestId,
            postMessage = postMessage,
            onTerminal = { remove(requestId, channel) },
        )
        pending[requestId] = PendingReply(navigationId, channel)
        seen += requestId
        return channel
    }

    @Synchronized
    fun abandon(requestId: UUID, navigationId: NavigationId): Boolean {
        val pendingReply = pending[requestId]
            ?.takeIf { it.navigationId == navigationId }
            ?: return false
        return pendingReply.channel.abandon()
    }

    fun abandonAll(): List<UUID> {
        val replies = synchronized(this) { pending.toMap() }
        return replies.mapNotNull { (requestId, reply) ->
            requestId.takeIf { reply.channel.abandon() }
        }
    }

    @Synchronized
    private fun remove(requestId: UUID, channel: MiniAppletReplyChannel) {
        if (pending[requestId]?.channel === channel) pending.remove(requestId)
    }

    private companion object {
        const val MAX_SEEN_REQUESTS = 64
    }

    private data class PendingReply(
        val navigationId: NavigationId,
        val channel: MiniAppletReplyChannel,
    )
}

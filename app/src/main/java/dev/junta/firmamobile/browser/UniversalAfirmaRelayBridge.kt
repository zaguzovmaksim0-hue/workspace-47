package dev.junta.firmamobile.browser

import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.ScriptHandler
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.junta.firmamobile.R
import dev.junta.firmamobile.afirma.servlet.AfirmaRelayStore
import dev.junta.firmamobile.afirma.servlet.AfirmaResultTransport
import dev.junta.firmamobile.afirma.servlet.AfirmaServletInvocation
import dev.junta.firmamobile.afirma.servlet.AfirmaServletTransport
import dev.junta.firmamobile.afirma.servlet.RelayResultTransport
import dev.junta.firmamobile.afirma.servlet.relayServletOrigin
import dev.junta.firmamobile.afirma.servlet.AfirmaServletInvocationParser
import dev.junta.firmamobile.afirma.servlet.relayScopeFor
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

internal suspend fun prepareRelayResultTransport(
    store: AfirmaRelayStore,
    scope: AfirmaRelayStore.Scope?,
    invocation: AfirmaServletInvocation,
): AfirmaResultTransport {
    if (scope == null) return AfirmaServletTransport()
    val servletOrigin = relayServletOrigin(invocation.storageUrl)
    if (servletOrigin != null && store.isBlocked(scope, servletOrigin)) {
        store.expectResult(scope, invocation.sessionId)
    }
    return RelayResultTransport(store, scope, AfirmaServletTransport())
}

internal interface UniversalAfirmaRelayOperations {
    suspend fun markBlocked(scope: AfirmaRelayStore.Scope, servletOrigin: String): Boolean

    suspend fun poll(
        scope: AfirmaRelayStore.Scope,
        sessionId: String,
    ): AfirmaRelayStore.PollResult

    suspend fun putUpload(scope: AfirmaRelayStore.Scope, fileId: String, data: String): Boolean
}

internal class AfirmaRelayStoreOperations(
    private val store: AfirmaRelayStore,
) : UniversalAfirmaRelayOperations {
    override suspend fun markBlocked(
        scope: AfirmaRelayStore.Scope,
        servletOrigin: String,
    ): Boolean = store.markBlocked(scope, servletOrigin)

    override suspend fun poll(
        scope: AfirmaRelayStore.Scope,
        sessionId: String,
    ): AfirmaRelayStore.PollResult = store.poll(scope, sessionId)

    override suspend fun putUpload(
        scope: AfirmaRelayStore.Scope,
        fileId: String,
        data: String,
    ): Boolean = store.putUpload(scope, fileId, data)
}

internal class UniversalAfirmaRelayHandler(
    private val operations: UniversalAfirmaRelayOperations,
    private val currentEpoch: () -> Long,
) {
    suspend fun handle(
        rawMessage: String,
        sourceOrigin: String,
        isMainFrame: Boolean,
        pageUrl: String,
        epoch: Long,
    ): String? = route(
        rawMessage = rawMessage,
        sourceOrigin = sourceOrigin,
        isMainFrame = isMainFrame,
        pageUrl = pageUrl,
        epoch = epoch,
    )?.json()

    private suspend fun route(
        rawMessage: String,
        sourceOrigin: String,
        isMainFrame: Boolean,
        pageUrl: String,
        epoch: Long,
    ): RelayReply? = runCatching {
        require(currentEpoch() == epoch)
        val scope = relayScopeFor(epoch, pageUrl) ?: error("invalid_page_scope")
        val origin = httpsOrigin(sourceOrigin) ?: error("invalid_source_origin")
        require(isMainFrame && origin == scope.pageOrigin)
        require(rawMessage.toByteArray(Charsets.UTF_8).size <= MAX_MESSAGE_BYTES)
        val request = parseRequest(rawMessage) ?: error("invalid_relay_request")
        operations.markBlocked(scope, request.servlet)
        val reply = when (request.op) {
            RelayOperation.CHECK -> RelayReply(request.rid, REPLY_OK)
            RelayOperation.GET -> when (val result = operations.poll(scope, request.id!!)) {
                is AfirmaRelayStore.PollResult.Ready ->
                    RelayReply(request.rid, REPLY_READY, result.text)
                AfirmaRelayStore.PollResult.Wait -> RelayReply(request.rid, REPLY_WAIT)
                AfirmaRelayStore.PollResult.Pass -> RelayReply(request.rid, REPLY_PASS)
            }
            RelayOperation.PUT -> RelayReply(
                request.rid,
                putStatus(scope, request),
            )
        }
        require(currentEpoch() == epoch)
        reply
    }.getOrNull()

    private fun parseRequest(rawMessage: String): RelayRequest? = runCatching {
        val json = JSONObject(rawMessage)
        val version = json.opt("v")
        require(version is Int && version == PROTOCOL_VERSION)
        val ridValue = json.opt("rid")
        require(ridValue is Int || ridValue is Long)
        val rid = (ridValue as Number).toLong()
        require(rid in MIN_RID..MAX_RID)
        val opName = json.optString("op")
        val op = RelayOperation.entries.singleOrNull { it.wireName == opName }
        require(op != null)
        val servlet = httpsOrigin(json.optString("servlet"))
        require(servlet != null)
        val id = if (op == RelayOperation.CHECK) null else json.optString("id")
        if (op != RelayOperation.CHECK) {
            require(id != null && AfirmaServletInvocationParser.SESSION_ID.matches(id))
        }
        val dat = if (op == RelayOperation.PUT) {
            require(json.has("dat"))
            json.optString("dat")
        } else {
            null
        }
        val datOversized = dat?.toByteArray(Charsets.UTF_8)?.size?.let { it > MAX_DATA_BYTES } == true
        RelayRequest(
            rid,
            checkNotNull(op),
            checkNotNull(servlet),
            id,
            if (datOversized) null else dat,
            datOversized,
        )
    }.getOrNull()

    private suspend fun putStatus(
        scope: AfirmaRelayStore.Scope,
        request: RelayRequest,
    ): String = when {
        request.datOversized -> REPLY_PASS
        else -> {
            val accepted = operations.putUpload(scope, request.id!!, request.dat!!)
            if (accepted) REPLY_OK else REPLY_PASS
        }
    }

    private fun httpsOrigin(value: String): String? = runCatching {
        val uri = URI(value)
        require(uri.scheme.equals("https", ignoreCase = true))
        val host = checkNotNull(uri.host)
        require(!uri.isOpaque && uri.rawUserInfo == null &&
            uri.rawQuery == null && uri.rawFragment == null)
        require(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/")
        require(uri.port == -1 || uri.port in MIN_PORT..MAX_PORT)
        val normalizedHost = host.lowercase(Locale.ROOT)
        when (uri.port) {
            -1, DEFAULT_HTTPS_PORT -> "https://$normalizedHost"
            else -> "https://$normalizedHost:${uri.port}"
        }
    }.getOrNull()

    private data class RelayRequest(
        val rid: Long,
        val op: RelayOperation,
        val servlet: String,
        val id: String?,
        val dat: String?,
        val datOversized: Boolean,
    )

    private class RelayReply(
        private val rid: Long,
        private val status: String,
        private val text: String? = null,
    ) {
        fun json(): String = JSONObject().apply {
            put("v", PROTOCOL_VERSION)
            put("rid", rid)
            put("status", status)
            text?.let { put("text", it) }
        }.toString()
    }

    private enum class RelayOperation(val wireName: String) {
        CHECK("check"),
        GET("get"),
        PUT("put"),
    }

    private companion object {
        const val PROTOCOL_VERSION = 1
        const val MIN_RID = 1L
        const val MAX_RID = 9_007_199_254_740_991L
        const val MAX_MESSAGE_BYTES = 8L * 1024L * 1024L + 1024L
        const val MAX_DATA_BYTES = 8L * 1024L * 1024L
        const val DEFAULT_HTTPS_PORT = 443
        const val MIN_PORT = 1
        const val MAX_PORT = 65535
        const val REPLY_OK = "ok"
        const val REPLY_READY = "ready"
        const val REPLY_WAIT = "wait"
        const val REPLY_PASS = "pass"
    }
}

internal class UniversalAfirmaRelayBridge(
    private val scope: CoroutineScope,
    private val store: AfirmaRelayStore,
    private val currentEpoch: () -> Long,
) {
    private val handler = UniversalAfirmaRelayHandler(
        operations = AfirmaRelayStoreOperations(store),
        currentEpoch = currentEpoch,
    )

    fun attach(webView: WebView): AutoCloseable {
        val listenerSupported =
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
        val scriptSupported =
            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        if (!listenerSupported || !scriptSupported) {
            return AutoCloseable {}
        }
        val script = webView.context.resources.openRawResource(R.raw.afirma_relay).use {
            it.readBytes().toString(Charsets.UTF_8)
        }
        WebViewCompat.addWebMessageListener(
            webView,
            BRIDGE_NAME,
            ORIGIN_RULES,
        ) { _, message, sourceOrigin, isMainFrame, replyProxy ->
            receive(message, sourceOrigin, isMainFrame, webView, replyProxy)
        }
        val scriptHandler = WebViewCompat.addDocumentStartJavaScript(
            webView,
            script,
            ORIGIN_RULES,
        )
        return UniversalAfirmaRelayBridgeAttachment(webView, scriptHandler)
    }

    private fun receive(
        message: WebMessageCompat,
        sourceOrigin: android.net.Uri,
        isMainFrame: Boolean,
        webView: WebView,
        replyProxy: JavaScriptReplyProxy,
    ) {
        val rawMessage = message.data
        if (message.type != WebMessageCompat.TYPE_STRING || rawMessage == null) return
        val epoch = currentEpoch()
        val pageUrl = webView.url.orEmpty()
        scope.launch {
            val reply = runCatching {
                handler.handle(
                    rawMessage = rawMessage,
                    sourceOrigin = sourceOrigin.toString(),
                    isMainFrame = isMainFrame,
                    pageUrl = pageUrl,
                    epoch = epoch,
                )
            }.getOrNull()
            reply?.let { text -> runCatching { replyProxy.postMessage(text) } }
        }
    }

    private companion object {
        val ORIGIN_RULES = setOf("*")
    }
}

private const val BRIDGE_NAME = "FirmaMobileAfirmaRelay"

private class UniversalAfirmaRelayBridgeAttachment(
    private val webView: WebView,
    private val scriptHandler: ScriptHandler,
) : AutoCloseable {
    private var closed = false

    override fun close() {
        if (closed) return
        closed = true
        scriptHandler.remove()
        WebViewCompat.removeWebMessageListener(webView, BRIDGE_NAME)
    }
}

package dev.junta.firmamobile.browser

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.junta.firmamobile.R
import dev.junta.firmamobile.afirma.servlet.NativeAfirmaLimits

/** Offers parent-owned AutoScript iframe requests through the existing consent flow. */
internal class VerifiedAfirmaFrameBridge(
    private val currentEpoch: () -> Long,
    private val isCurrent: (WebView) -> Boolean,
    private val onInvocation: (WebView, String, String) -> Boolean,
    private val onRejected: () -> Unit,
) {
    fun attach(webView: WebView): AutoCloseable {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        ) return AutoCloseable {}
        val source = webView.context.resources.openRawResource(R.raw.afirma_frame).use {
            it.readBytes().toString(Charsets.UTF_8)
        }
        val script = "(" + source + ")(" + NativeAfirmaLimits.URI_CHARS + ");"
        return AfirmaFrameAttachment(webView, currentEpoch, isCurrent, onInvocation, onRejected).attach(script)
    }
}

private class AfirmaFrameAttachment(
    private val webView: WebView,
    private val currentEpoch: () -> Long,
    private val isCurrent: (WebView) -> Boolean,
    private val onInvocation: (WebView, String, String) -> Boolean,
    private val onRejected: () -> Unit,
) : AutoCloseable {
    private val handler = Handler(Looper.getMainLooper())
    private var closed = false
    private var listenerAttached = false
    private var scriptHandler: ScriptHandler? = null
    private var pending: AfirmaFrameDocument? = null
    private var timeout: Runnable? = null

    fun attach(script: String): AutoCloseable {
        try {
            WebViewCompat.addWebMessageListener(webView, BRIDGE_NAME, ORIGIN_RULES) {
                    owner, message, sourceOrigin, isMainFrame, _ ->
                receive(owner, message, sourceOrigin, isMainFrame)
            }
            listenerAttached = true
            scriptHandler = WebViewCompat.addDocumentStartJavaScript(webView, script, ORIGIN_RULES)
        } catch (_: Exception) {
            close()
        }
        return this
    }

    private fun receive(owner: WebView, message: WebMessageCompat, origin: Uri, isMainFrame: Boolean) {
        if (!ownsWebView(owner) || message.type != WebMessageCompat.TYPE_STRING) return
        val request = message.data?.let(VerifiedAfirmaFrameSource::parseMessage) ?: return
        val document = AfirmaFrameDocument(owner.url.orEmpty(), currentEpoch(), request.documentId)
        if (VerifiedAfirmaFrameSource.permitsSource(origin.toString(), isMainFrame, document)) {
            val uri = NativeAfirmaNavigation.extract(request.uri)
            if (uri == null || pending?.navigationEpoch == document.navigationEpoch) {
                onRejected()
            } else {
                verifyCurrentDocument(document, uri)
            }
        }
    }

    private fun verifyCurrentDocument(document: AfirmaFrameDocument, uri: String) {
        abandonVerification()
        pending = document
        val expiry = Runnable {
            if (pending === document) {
                abandonVerification()
                if (owns(document)) onRejected()
            }
        }
        timeout = expiry
        handler.postDelayed(expiry, VERIFICATION_TIMEOUT_MILLIS)
        try {
            webView.evaluateJavascript(DOCUMENT_ID_PROBE) { result -> finishVerification(document, uri, result) }
        } catch (_: Exception) {
            if (pending === document) {
                abandonVerification()
                if (owns(document)) onRejected()
            }
        }
    }

    private fun finishVerification(document: AfirmaFrameDocument, uri: String, result: String?) {
        if (pending !== document) return
        abandonVerification()
        if (!owns(document)) return
        val id = VerifiedAfirmaFrameSource.documentIdFromJavascript(result)
        if (id != null) {
            val actual = AfirmaFrameDocument(webView.url.orEmpty(), currentEpoch(), id)
            if (VerifiedAfirmaFrameSource.isCurrent(document, actual) &&
                !onInvocation(webView, uri, document.pageUrl)
            ) onRejected()
        }
    }

    private fun ownsWebView(owner: WebView): Boolean = !closed && owner === webView && isCurrent(owner)

    private fun owns(document: AfirmaFrameDocument): Boolean =
        !closed && isCurrent(webView) && currentEpoch() == document.navigationEpoch

    private fun abandonVerification() {
        timeout?.let(handler::removeCallbacks)
        timeout = null
        pending = null
    }

    override fun close() {
        if (closed) return
        closed = true
        abandonVerification()
        scriptHandler?.remove()
        scriptHandler = null
        if (listenerAttached) WebViewCompat.removeWebMessageListener(webView, BRIDGE_NAME)
        listenerAttached = false
    }

    private companion object {
        const val BRIDGE_NAME = "FirmaMobileFrameAfirma"
        const val DOCUMENT_ID_PROBE = "window.FirmaMobileFrameDocumentId || null"
        const val VERIFICATION_TIMEOUT_MILLIS = 5_000L
        val ORIGIN_RULES = setOf("*")
    }
}

package dev.junta.firmamobile.browser

import android.os.Message
import android.webkit.WebView

/** Completes exactly one Chromium window handoff, never by replaying a URL. */
class BrowserPopupTransport private constructor(
    private var parent: WebView?,
    private var message: Message?,
    private var transport: WebView.WebViewTransport?,
    private var canAttach: (() -> Boolean)?,
) {
    var attached: Boolean = false
        private set

    fun attach(child: WebView): Boolean {
        val pending = message ?: return false
        val native = transport ?: return false
        if (child === parent || !child.isAttachedToWindow ||
            (child.url != null && child.url != "about:blank") || pending.obj !== native
        ) return false
        if (!runCatching { canAttach?.invoke() == true }.getOrDefault(false) || message !== pending) return false
        releaseReferences()
        return try {
            native.webView = child
            pending.sendToTarget()
            attached = true
            true
        } catch (_: Exception) {
            // Sending may have crossed the platform boundary: no second send.
            false
        }
    }

    fun cancel() {
        val pending = message ?: return
        val native = transport
        releaseReferences()
        if (native == null || pending.obj !== native) return
        runCatching { native.webView = null; pending.sendToTarget() }
    }

    private fun releaseReferences() {
        parent = null; message = null; transport = null; canAttach = null
    }

    companion object {
        fun create(parent: WebView, message: Message, canAttach: () -> Boolean): BrowserPopupTransport? {
            val native = message.obj as? WebView.WebViewTransport ?: return null
            if (message.target == null) return null
            return BrowserPopupTransport(parent, message, native, canAttach)
        }
    }
}

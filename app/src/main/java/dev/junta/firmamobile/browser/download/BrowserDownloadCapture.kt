package dev.junta.firmamobile.browser.download

import android.content.Intent
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.Toast
import dev.junta.firmamobile.R
import dev.junta.firmamobile.browser.PublicBrowserAddress

/** UI-thread offer handling; request evidence itself is synchronized because
 * WebView resource observation runs on an IO thread. No request is replayed here. */
internal class BrowserDownloadCapture(private val view: WebView) {
    private val evidence = DownloadGetEvidence()
    private var launcher: ((WebView, Intent) -> Boolean)? = null
    private var canRespond: () -> Boolean = { false }
    private var closed = false

    fun configure(allowed: () -> Boolean, launch: ((WebView, Intent) -> Boolean)?) {
        canRespond = allowed; launcher = launch
        view.setDownloadListener { url, userAgent, disposition, mimeType, length ->
            offer(url, userAgent, disposition, mimeType, length)
        }
    }
    fun observe(request: WebResourceRequest) {
        try { evidence.record(request.url.toString(), request.method, request.isForMainFrame) }
        catch (_: Exception) { evidence.clear() }
    }
    fun navigationStarted(url: String) { evidence.navigationStarted(url) }
    fun clear() { evidence.clear() }
    fun close() {
        closed = true; evidence.clear(); launcher = null; canRespond = { false }
        view.setDownloadListener(null)
    }
    internal fun offer(url: String?, userAgent: String?, disposition: String?, mimeType: String?, length: Long): Boolean {
        val launch = launcher ?: return false
        if (closed || !view.isAttachedToWindow || !view.isShown || !view.hasWindowFocus() || !canRespond()) return false
        val raw = url ?: return false
        if (!evidence.consume(raw)) { unsupported(); return false }
        val page = view.url ?: return false
        val pageUri = PublicBrowserAddress.parse(page)
        val target = PublicBrowserAddress.parse(raw)
        val cookie = if (pageUri != null && target != null && pageUri.host.equals(target.host, true)) {
            runCatching { CookieManager.getInstance().getCookie(raw) }.getOrNull()
        } else null
        val guessedName = runCatching { URLUtil.guessFileName(raw, disposition, mimeType) }.getOrNull()
        val plan = DocumentDownloadPlan.create(page, raw, guessedName, mimeType, length, userAgent, cookie)
        if (plan == null) { unsupported(); return false }
        val intent = DocumentDownloadActivity.prepare(view.context, plan) ?: return false
        val accepted = runCatching { launch(view, intent) }.getOrDefault(false)
        if (!accepted) DocumentDownloadActivity.revoke(intent)
        return accepted
    }
    private fun unsupported() {
        Toast.makeText(view.context, R.string.document_download_unsupported, Toast.LENGTH_LONG).show()
    }
}

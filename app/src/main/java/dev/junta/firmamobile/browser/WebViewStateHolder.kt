package dev.junta.firmamobile.browser

import android.os.Bundle
import android.webkit.WebView
import dev.junta.firmamobile.network.JuntaOriginPolicy

internal interface WebViewStateTarget {
    fun restoreState(state: Bundle): Boolean

    fun saveState(state: Bundle): Boolean

    fun loadUrl(url: String)
}

class WebViewStateHolder(savedInstanceState: Bundle?) {
    private var pendingHistory: Bundle? = savedInstanceState?.getBundle(STATE_KEY)

    fun restoreOrLoad(webView: WebView): Boolean = restoreOrLoad(AndroidTarget(webView))

    internal fun restoreOrLoad(target: WebViewStateTarget): Boolean {
        val history = pendingHistory.also { pendingHistory = null }
        if (history != null && target.restoreState(history)) return true

        target.loadUrl(JuntaOriginPolicy.START_URL)
        return false
    }

    fun save(webView: WebView, outState: Bundle) = save(AndroidTarget(webView), outState)

    internal fun save(target: WebViewStateTarget, outState: Bundle) {
        val history = Bundle()
        if (target.saveState(history)) {
            outState.putBundle(STATE_KEY, history)
        }
    }

    fun capture(webView: WebView): Boolean = capture(AndroidTarget(webView))

    internal fun capture(target: WebViewStateTarget): Boolean {
        val history = Bundle()
        return if (target.saveState(history)) {
            pendingHistory = history
            true
        } else {
            false
        }
    }

    fun clear() {
        pendingHistory = null
    }

    private class AndroidTarget(private val webView: WebView) : WebViewStateTarget {
        override fun restoreState(state: Bundle): Boolean = webView.restoreState(state) != null

        override fun saveState(state: Bundle): Boolean = webView.saveState(state) != null

        override fun loadUrl(url: String) {
            webView.loadUrl(url)
        }
    }

    companion object {
        internal const val STATE_KEY = "junta_webview_history"
    }
}

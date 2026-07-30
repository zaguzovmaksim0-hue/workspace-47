package dev.junta.firmamobile.browser

import android.webkit.WebView

fun interface ClientCertPreferenceClearer {
    fun clear(onCleared: () -> Unit)
}

internal object AndroidClientCertPreferenceClearer : ClientCertPreferenceClearer {
    override fun clear(onCleared: () -> Unit) {
        WebView.clearClientCertPreferences(onCleared)
    }
}

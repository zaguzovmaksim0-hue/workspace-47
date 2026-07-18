package dev.junta.firmamobile.browser

import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView

class JuntaWebChromeClient(
    private val onProgressChanged: (Int) -> Unit = {},
) : WebChromeClient() {
    override fun onProgressChanged(view: WebView, newProgress: Int) {
        onProgressChanged(newProgress.coerceIn(0, 100))
    }

    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: android.os.Message,
    ): Boolean = false

    override fun onPermissionRequest(request: PermissionRequest) {
        request.deny()
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String,
        callback: GeolocationPermissions.Callback,
    ) {
        callback.invoke(origin, false, false)
    }
}

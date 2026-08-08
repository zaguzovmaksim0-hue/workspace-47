package dev.junta.firmamobile.browser

import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
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

    override fun onJsAlert(
        view: WebView,
        url: String,
        message: String,
        result: JsResult,
    ): Boolean {
        result.confirm()
        return true
    }

    override fun onJsBeforeUnload(
        view: WebView,
        url: String,
        message: String,
        result: JsResult,
    ): Boolean {
        result.confirm()
        return true
    }

    override fun onJsConfirm(
        view: WebView,
        url: String,
        message: String,
        result: JsResult,
    ): Boolean {
        result.cancel()
        return true
    }

    override fun onJsPrompt(
        view: WebView,
        url: String,
        message: String,
        defaultValue: String,
        result: JsPromptResult,
    ): Boolean {
        result.cancel()
        return true
    }

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

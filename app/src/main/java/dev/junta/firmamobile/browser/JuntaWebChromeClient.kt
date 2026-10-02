package dev.junta.firmamobile.browser

import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView

class JuntaWebChromeClient(
    var progressListener: (Int) -> Unit = {},
) : WebChromeClient() {
    var fileChooser: ((WebView, android.webkit.ValueCallback<Array<android.net.Uri>>, FileChooserParams) -> Boolean)? = null

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: android.webkit.ValueCallback<Array<android.net.Uri>>,
        fileChooserParams: FileChooserParams,
    ): Boolean = fileChooser?.invoke(webView, filePathCallback, fileChooserParams) ?: false

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        progressListener(newProgress.coerceIn(0, 100))
    }

    var createWindow: ((WebView, Boolean, Boolean, android.os.Message) -> Boolean)? = null
    var closeWindow: ((WebView) -> Unit)? = null

    override fun onCloseWindow(window: WebView) { closeWindow?.invoke(window) }

    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: android.os.Message,
    ): Boolean = createWindow?.invoke(view, isDialog, isUserGesture, resultMsg) ?: false

    override fun onJsAlert(
        view: WebView,
        url: String,
        message: String,
        result: JsResult,
    ): Boolean = false

    override fun onJsBeforeUnload(
        view: WebView,
        url: String,
        message: String,
        result: JsResult,
    ): Boolean = false

    override fun onJsConfirm(
        view: WebView,
        url: String,
        message: String,
        result: JsResult,
    ): Boolean = false

    override fun onJsPrompt(
        view: WebView,
        url: String,
        message: String,
        defaultValue: String,
        result: JsPromptResult,
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

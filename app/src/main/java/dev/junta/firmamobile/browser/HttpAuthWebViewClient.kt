package dev.junta.firmamobile.browser

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.webkit.ClientCertRequest
import android.webkit.HttpAuthHandler
import android.webkit.RenderProcessGoneDetail
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import dev.junta.firmamobile.R

/** A per-client adapter; every non-HTTP-auth callback stays with the actual
 * browser client. Replacing the client or destroying its view ends pending auth. */
internal class HttpAuthWebViewClient(
    private val delegate: WebViewClient,
    private val observeDownloadRequest: (WebResourceRequest) -> Unit = {},
    private val downloadNavigation: (String) -> Unit = {},
) : WebViewClient() {
    private var epoch = 0L
    private var closed = false
    private var owner: WebView? = null
    private var hostActivity: Activity? = null
    private var lifecycle: Lifecycle? = null
    private var activeRequestId: Any? = null
    private var dialog: AlertDialog? = null
    private var username: EditText? = null
    private var password: EditText? = null
    private val main = Handler(Looper.getMainLooper())
    private val expiry = Runnable { controller.tick() }
    private val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_DESTROY) controller.invalidate()
    }
    private val detached = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = Unit
        override fun onViewDetachedFromWindow(view: View) { controller.invalidate() }
    }
    private val controller: BrowserHttpAuthController<WebView> = BrowserHttpAuthController(
        isCurrent = { view, generation -> !closed && owner === view && epoch == generation },
        canRespond = {
            !closed && owner?.isAttachedToWindow == true && owner?.isShown == true &&
                lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true &&
                hostActivity?.isFinishing == false && hostActivity?.isDestroyed == false
        },
        onPrompt = { prompt -> if (prompt == null) hideDialog() else showDialog(prompt) },
    )

    override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) {
        request(view, host, realm, object : HttpAuthReply {
            override fun proceed(username: String, password: String) = handler.proceed(username, password)
            override fun cancel() = handler.cancel()
        }, handler)
    }

    /** Platform-adapter boundary also used by synthetic UI tests. It does not
     * accept stored credentials or manufacture an authenticated request. */
    internal fun request(view: WebView, host: String, realm: String, answer: HttpAuthReply, identity: Any = answer): Boolean {
        if (activeRequestId === identity) return controller.hasPending
        if (closed || controller.hasPending || !view.hasWindowFocus()) {
            runCatching { answer.cancel() }; return false
        }
        val activity = findActivity(view.context)
        val life = (activity as? LifecycleOwner)?.lifecycle
        val page = view.url
        if (activity == null || life == null || page == null || !life.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            runCatching { answer.cancel() }; return false
        }
        owner = view; hostActivity = activity; lifecycle = life; activeRequestId = identity
        life.addObserver(observer); view.addOnAttachStateChangeListener(detached)
        val reply = object : HttpAuthReply {
            private var answered = false
            override fun proceed(username: String, password: String) {
                if (answered) return
                answered = true
                try { answer.proceed(username, password) } finally { release(identity) }
            }
            override fun cancel() {
                if (answered) return
                answered = true
                try { answer.cancel() } finally { release(identity) }
            }
        }
        val accepted = controller.offer(view, epoch, page, host, realm, reply)
        if (accepted) main.postDelayed(expiry, 120_000L)
        return accepted
    }

    private fun showDialog(prompt: BrowserHttpAuthPrompt) {
        val activity = checkNotNull(hostActivity)
        val spacing = (16 * activity.resources.displayMetrics.density).toInt()
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(spacing, spacing, spacing, spacing)
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            isSaveEnabled = false
        }
        fun label(value: String) { column.addView(TextView(activity).apply { text = value }) }
        label(activity.getString(R.string.http_auth_server, prompt.scope.server))
        label(activity.getString(R.string.http_auth_page, prompt.scope.pageOrigin))
        if (prompt.scope.realm.isNotEmpty()) label(activity.getString(R.string.http_auth_realm, prompt.scope.realm))
        label(activity.getString(R.string.http_auth_explanation))
        val user = EditText(activity).apply {
            hint = activity.getString(R.string.http_auth_username)
            contentDescription = hint; tag = "http-auth-username"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            isSingleLine = true; isSaveEnabled = false
        }
        val secret = EditText(activity).apply {
            hint = activity.getString(R.string.http_auth_password)
            contentDescription = hint; tag = "http-auth-password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            isSingleLine = true; isSaveEnabled = false
        }
        username = user; password = secret; column.addView(user); column.addView(secret)
        val window = AlertDialog.Builder(activity)
            .setTitle(R.string.http_auth_title)
            .setView(ScrollView(activity).apply { addView(column) })
            .setNegativeButton(R.string.http_auth_cancel) { _, _ -> controller.cancel(prompt.token) }
            .setPositiveButton(R.string.http_auth_confirm, null)
            .create()
        dialog = window
        window.setOnCancelListener { controller.cancel(prompt.token) }
        window.setOnDismissListener {
            user.text.clear(); secret.text.clear()
            controller.cancel(prompt.token)
        }
        window.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.show()
        window.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (window.window?.decorView?.hasWindowFocus() != true) return@setOnClickListener
            val userValue = user.text.toString(); val secretValue = secret.text.toString()
            if (!BrowserHttpAuthController.validCredentials(userValue, secretValue)) {
                user.error = activity.getString(R.string.http_auth_invalid_input)
            } else {
                controller.confirm(prompt.token, userValue, secretValue)
            }
        }
    }

    private fun hideDialog() {
        val shown = dialog; dialog = null
        username?.text?.clear(); password?.text?.clear(); username = null; password = null
        shown?.dismiss()
    }
    private fun release(expected: Any? = null) {
        if (expected != null && activeRequestId !== expected) return
        main.removeCallbacks(expiry)
        lifecycle?.removeObserver(observer)
        owner?.removeOnAttachStateChangeListener(detached)
        lifecycle = null; hostActivity = null; owner = null; activeRequestId = null
        hideDialog()
    }
    fun close() { closed = true; controller.close(); release() }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        controller.invalidate(); epoch++; downloadNavigation(url); delegate.onPageStarted(view, url, favicon)
    }
    override fun onPageFinished(view: WebView, url: String) = delegate.onPageFinished(view, url)
    override fun onPageCommitVisible(view: WebView, url: String) = delegate.onPageCommitVisible(view, url)
    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) = delegate.doUpdateVisitedHistory(view, url, isReload)
    override fun onLoadResource(view: WebView, url: String) = delegate.onLoadResource(view, url)
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = delegate.shouldOverrideUrlLoading(view, request)
    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView, url: String) = delegate.shouldOverrideUrlLoading(view, url)
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        observeDownloadRequest(request)
        return delegate.shouldInterceptRequest(view, request)
    }
    @Suppress("DEPRECATION")
    override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? = delegate.shouldInterceptRequest(view, url)
    override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) = delegate.onReceivedClientCertRequest(view, request)
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        controller.invalidate(); delegate.onReceivedSslError(view, handler, error)
    }
    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) controller.invalidate()
        delegate.onReceivedError(view, request, error)
    }
    @Suppress("DEPRECATION")
    override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
        controller.invalidate(); delegate.onReceivedError(view, errorCode, description, failingUrl)
    }
    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) = delegate.onReceivedHttpError(view, request, response)
    override fun onFormResubmission(view: WebView, dontResend: Message, resend: Message) = delegate.onFormResubmission(view, dontResend, resend)
    override fun onScaleChanged(view: WebView, oldScale: Float, newScale: Float) = delegate.onScaleChanged(view, oldScale, newScale)
    override fun shouldOverrideKeyEvent(view: WebView, event: KeyEvent) = delegate.shouldOverrideKeyEvent(view, event)
    override fun onUnhandledKeyEvent(view: WebView, event: KeyEvent) = delegate.onUnhandledKeyEvent(view, event)
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        close(); return delegate.onRenderProcessGone(view, detail)
    }
    @RequiresApi(27)
    override fun onSafeBrowsingHit(view: WebView, request: WebResourceRequest, threatType: Int, callback: SafeBrowsingResponse) {
        controller.invalidate(); delegate.onSafeBrowsingHit(view, request, threatType, callback)
    }
    override fun onReceivedLoginRequest(view: WebView, realm: String, account: String?, args: String) = delegate.onReceivedLoginRequest(view, realm, account, args)

    private fun findActivity(initial: Context): Activity? {
        var context = initial
        repeat(16) {
            if (context is Activity) return context as Activity
            val next = (context as? ContextWrapper)?.baseContext ?: return null
            if (next === context) return null
            context = next
        }
        return null
    }
}

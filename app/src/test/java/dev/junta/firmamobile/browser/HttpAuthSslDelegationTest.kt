package dev.junta.firmamobile.browser

import android.content.Context
import android.net.Uri
import android.net.http.SslCertificate
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadow.api.Shadow

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class HttpAuthSslDelegationTest {
    @Test
    fun sslErrorsReachOriginalDelegateAndAreCancelled() {
        val view = WebView(ApplicationProvider.getApplicationContext<Context>())
        val handler = Shadow.newInstanceOf(SslErrorHandler::class.java)
        val cert = SslCertificate("CN=synthetic.invalid", "CN=synthetic.invalid", "", "")
        val error = SslError(SslError.SSL_UNTRUSTED, cert, "https://synthetic.example/")
        var calls = 0
        val delegate = object : WebViewClient() {
            override fun onReceivedSslError(v: WebView, h: SslErrorHandler, e: SslError) {
                calls++
                assertSame(view, v)
                assertSame(handler, h)
                assertSame(error, e)
                h.cancel()
            }
        }
        val client = HttpAuthWebViewClient(delegate)
        try {
            client.onReceivedSslError(view, handler, error)
            assertEquals(1, calls)
            assertTrue(shadowOf(handler).wasCancelCalled())
            assertFalse(shadowOf(handler).wasProceedCalled())
        } finally {
            client.close()
            view.destroy()
        }
    }

    @Test
    fun formResubmissionDelegatesMessagesWithoutSendingThem() {
        val view = WebView(ApplicationProvider.getApplicationContext<Context>())
        var replies = 0
        val replyTarget = Handler(Looper.getMainLooper()) { replies++; true }
        val dontResend = Message.obtain().apply { target = replyTarget }
        val resend = Message.obtain().apply { target = replyTarget }
        var calls = 0
        val delegate = object : WebViewClient() {
            override fun onFormResubmission(v: WebView, dont: Message, send: Message) {
                calls++
                assertSame(view, v)
                assertSame(dontResend, dont)
                assertSame(resend, send)
            }
        }
        val client = HttpAuthWebViewClient(delegate)
        try {
            client.onFormResubmission(view, dontResend, resend)
            assertEquals(1, calls)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, calls)
            assertEquals(0, replies)
        } finally {
            client.close()
            view.destroy()
        }
    }

    @Test
    fun closedClientStillDelegatesCustomSchemeRequest() {
        val view = WebView(ApplicationProvider.getApplicationContext<Context>())
        val uri = Uri.parse("afirma://sign?dat=eA==")
        val headers = mutableMapOf<String, String>()
        val request = object : WebResourceRequest {
            override fun getUrl(): Uri = uri
            override fun isForMainFrame(): Boolean = true
            override fun getMethod(): String = "GET"
            override fun hasGesture(): Boolean = true
            override fun isRedirect(): Boolean = false
            override fun getRequestHeaders(): MutableMap<String, String> = headers
        }
        var calls = 0
        val delegate = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                calls++
                assertSame(view, v)
                assertSame(request, r)
                return true
            }
        }
        val client = HttpAuthWebViewClient(delegate)
        try {
            client.close()
            assertTrue(client.shouldOverrideUrlLoading(view, request))
            assertEquals(1, calls)
        } finally {
            view.destroy()
        }
    }
}
package dev.junta.firmamobile.browser

import android.content.Context
import android.net.Uri
import android.net.http.SslError
import android.webkit.ClientCertRequest
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class HttpAuthDelegationTest {
    @Test fun wrapperPreservesTheActualNavigationAndResourceDecisions() {
        val view = WebView(ApplicationProvider.getApplicationContext<Context>())
        val request = Request("https://public.example/page")
        val response = WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(byteArrayOf(1)))
        var navigations = 0; var starts = 0; var finishes = 0; var intercepts = 0
        val delegate = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean { assertSame(request, r); navigations++; return true }
            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse { assertSame(request, r); intercepts++; return response }
            override fun onPageStarted(v: WebView, url: String, favicon: android.graphics.Bitmap?) { starts++ }
            override fun onPageFinished(v: WebView, url: String) { finishes++ }
        }
        val client = HttpAuthWebViewClient(delegate)
        assertTrue(client.shouldOverrideUrlLoading(view, request))
        assertSame(response, client.shouldInterceptRequest(view, request))
        client.onPageStarted(view, request.url.toString(), null)
        client.onPageFinished(view, request.url.toString())
        assertEquals(listOf(1, 1, 1, 1), listOf(navigations, starts, finishes, intercepts))
        client.close(); view.destroy()
    }

    @Test fun clientCertificateChallengeStillBelongsToTheExistingDelegate() {
        val view = WebView(ApplicationProvider.getApplicationContext<Context>())
        var calls = 0; var ignored = 0
        val request = object : ClientCertRequest() {
            override fun getHost() = "auth.synthetic.example"
            override fun getPort() = 443
            override fun getKeyTypes() = arrayOf("RSA")
            override fun getPrincipals() = emptyArray<Principal>()
            override fun proceed(key: PrivateKey, chain: Array<X509Certificate>) { error("No key in delegation test") }
            override fun ignore() { ignored++ }
            override fun cancel() { error("No cached denial") }
        }
        val client = HttpAuthWebViewClient(object : WebViewClient() {
            override fun onReceivedClientCertRequest(v: WebView, r: ClientCertRequest) { assertSame(request, r); calls++; r.ignore() }
        })
        client.onReceivedClientCertRequest(view, request)
        assertEquals(1, calls); assertEquals(1, ignored)
        client.close(); view.destroy()
    }

    @Test fun trustedViewInstallsAndReplacesTheAdapterWithoutChangingItsRestrictions() {
        val view = TrustedJuntaWebView(ApplicationProvider.getApplicationContext<Context>())
        view.webViewClient = WebViewClient()
        val first = view.webViewClient
        assertTrue(first is HttpAuthWebViewClient)
        view.webViewClient = WebViewClient()
        assertTrue(view.webViewClient is HttpAuthWebViewClient)
        assertNotSame(first, view.webViewClient)
        assertFalse(view.settings.allowFileAccess)
        assertFalse(view.settings.allowContentAccess)
        assertEquals(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW, view.settings.mixedContentMode)
        view.destroy()
    }

    private class Request(private val address: String) : WebResourceRequest {
        override fun getUrl() = Uri.parse(address)
        override fun isForMainFrame() = true
        override fun isRedirect() = false
        override fun hasGesture() = true
        override fun getMethod() = "GET"
        override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
    }
}

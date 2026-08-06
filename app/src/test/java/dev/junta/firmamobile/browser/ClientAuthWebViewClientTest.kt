package dev.junta.firmamobile.browser

import android.content.Context
import android.net.Uri
import android.webkit.ClientCertRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import dev.junta.firmamobile.afirma.AfirmaRequest
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.signing.nonExportableSyntheticIdentity
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.time.Clock
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
class ClientAuthWebViewClientTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val webView = WebView(context)
    private val synthetic = nonExportableSyntheticIdentity()
    private val clock = Clock.fixed(synthetic.identity.summary.validFrom.plusSeconds(60), ZoneOffset.UTC)

    @Test
    fun initialAuthorizedPageStartDoesNotInvalidateGrantBeforeClientCertificateRequest() {
        val epoch = AtomicInteger(9)
        val clears = AtomicInteger()
        val callbacks = RecordingCallbacks { epoch.incrementAndGet() }
        val client = client(epoch, callbacks, clears)

        client.onPageStarted(webView, TARGET, null)
        val request = RecordingRequest()
        client.onReceivedClientCertRequest(webView, request)

        assertEquals(9, epoch.get())
        assertEquals(listOf("url"), callbacks.events)
        assertEquals(1, request.proceeds)
        assertEquals(0, request.ignores)
        assertEquals(0, synthetic.encodedReads.get())
        assertEquals(0, clears.get())

        client.abandon()
        assertEquals(1, clears.get())

        client.abandon()
        assertEquals(1, clears.get())
    }

    @Suppress("DEPRECATION")
    @Test
    fun staleClientTlsWebViewRejectsCertificateAndCannotMutateActiveBrowser() {
        val epoch = AtomicInteger(9)
        val clears = AtomicInteger()
        val callbacks = RecordingCallbacks { epoch.incrementAndGet() }
        var active = false
        val client = client(
            epoch = epoch,
            callbacks = callbacks,
            clears = clears,
            isActiveWebView = { active },
        )

        val request = RecordingRequest()
        client.onReceivedClientCertRequest(webView, request)
        assertTrue(client.shouldOverrideUrlLoading(webView, "https://example.org/"))
        client.onPageStarted(webView, TARGET, null)
        client.onPageFinished(webView, TARGET)

        assertEquals(0, request.proceeds)
        assertEquals(1, request.ignores)
        assertEquals(1, clears.get())
        assertTrue(callbacks.events.isEmpty())

        active = true
        val secondRequest = RecordingRequest()
        client.onReceivedClientCertRequest(webView, secondRequest)
        assertEquals(0, secondRequest.proceeds)
        assertEquals(1, secondRequest.ignores)
        assertEquals(1, clears.get())
    }

    @Test
    fun rendererDeathAbandonsTheOneShotClientTlsGrant() {
        val epoch = AtomicInteger(20)
        val clears = AtomicInteger()
        val callbacks = RecordingCallbacks { epoch.incrementAndGet() }
        val client = client(epoch, callbacks, clears)
        client.onPageStarted(webView, TARGET, null)

        assertTrue(
            client.onRenderProcessGone(
                webView,
                RecordingRenderProcessGoneDetail(),
            ),
        )
        assertEquals(1, clears.get())

        val request = RecordingRequest()
        client.onReceivedClientCertRequest(webView, request)
        assertEquals(1, request.ignores)
        assertTrue(callbacks.events.contains("renderer"))
        assertEquals(1, clears.get())
    }

    @Test
    fun subsequentNavigationAbandonsGrantAndOffOriginMainFrameIsBlocked() {
        val epoch = AtomicInteger(12)
        val clears = AtomicInteger()
        val callbacks = RecordingCallbacks { epoch.incrementAndGet() }
        val client = client(epoch, callbacks, clears)
        client.onPageStarted(webView, TARGET, null)

        client.onPageStarted(webView, RETURN, null)
        assertEquals(1, clears.get())

        val request = RecordingRequest()
        client.onReceivedClientCertRequest(webView, request)

        assertEquals(13, epoch.get())
        assertEquals(1, request.ignores)
        assertTrue(client.shouldOverrideUrlLoading(webView, "https://example.org/"))
        assertFalse(client.shouldOverrideUrlLoading(webView, RETURN))
        assertTrue(callbacks.events.contains("blocked:INVALID_URL"))
    }

    private fun client(
        epoch: AtomicInteger,
        callbacks: BrowserNavigationCallbacks,
        clears: AtomicInteger = AtomicInteger(),
        isActiveWebView: (WebView) -> Boolean = { true },
    ): ClientAuthWebViewClient {
        val authorized = authorized()
        val grant = ClientAuthGrant(authorized, epoch.get().toLong())
        val handler = ClientAuthRequestHandler(
            grant = grant,
            identityProvider = { synthetic.identity },
            currentNavigationEpoch = { epoch.get().toLong() },
            clearClientCertPreferences = { clears.incrementAndGet() },
            clock = clock,
        )
        return ClientAuthWebViewClient(
            grant = grant,
            requestHandler = handler,
            callbacks = callbacks,
            isActiveWebView = isActiveWebView,
        )
    }

    private fun authorized(): AuthorizedClientAuthTarget {
        val authorizer = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry)
        authorizer.observeTopLevelNavigation(PROFILE, INDEX, SOURCE, 4, true)
        authorizer.onTopLevelPageStarted(SOURCE, 5)
        return checkNotNull(authorizer.observeTopLevelNavigation(PROFILE, SOURCE, TARGET, 5, true))
    }

    private class RecordingCallbacks(private val onStart: () -> Unit) : BrowserNavigationCallbacks {
        val events = mutableListOf<String>()
        override fun openExternal(uri: Uri) = Unit
        override fun onAfirmaRequest(request: AfirmaRequest) = Unit
        override fun onNavigationBlocked(reason: NavigationBlockReason) {
            events += "blocked:${reason.name}"
        }
        override fun onBrowserError(error: BrowserErrorCode) = Unit
        override fun onRenderProcessGone(view: WebView) {
            events += "renderer"
        }
        override fun onTopLevelNavigationStarted(url: String) {
            events += "start"
            onStart()
        }
        override fun onTopLevelUrlChanged(url: String) {
            events += "url"
        }
    }

    private class RecordingRenderProcessGoneDetail : RenderProcessGoneDetail() {
        override fun didCrash(): Boolean = true

        override fun rendererPriorityAtExit(): Int = 0
    }

    private class RecordingRequest : ClientCertRequest() {
        var proceeds = 0
        var ignores = 0
        override fun getHost(): String = "ws235.juntadeandalucia.es"
        override fun getPort(): Int = 443
        override fun getKeyTypes(): Array<String> = arrayOf("RSA")
        override fun getPrincipals(): Array<Principal> = emptyArray()
        override fun proceed(privateKey: PrivateKey, chain: Array<X509Certificate>) {
            proceeds++
        }
        override fun ignore() {
            ignores++
        }
        override fun cancel() = Unit
    }

    private companion object {
        val PROFILE = ProfileId("carne-joven-andalucia")
        const val INDEX = "https://ws104.juntadeandalucia.es/carneJoven/cjservlet/portal/index.jsp"
        const val SOURCE =
            "https://ws104.juntadeandalucia.es/carneJoven/servlet/CallAuthenticationServlet"
        const val RETURN =
            "https://ws104.juntadeandalucia.es/carneJoven/servlet/ReturnAuthenticationServlet"
        const val TARGET =
            "https://ws235.juntadeandalucia.es/authenticationFacade?action=validateCert&ticketId=synthetic-ticket&appId=IAJ.CARNETJOVEN&webSessionId=synthetic-session&comeBackURL=aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ%3D"
    }
}

package dev.junta.firmamobile.browser

import android.content.Context
import android.net.Uri
import android.webkit.ClientCertRequest
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.junta.firmamobile.afirma.AfirmaRequest
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.security.SanitizedLogger
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClientAuthWebViewInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun normalClientIgnoresDirectClientCertificateRequest() {
        instrumentation.runOnMainSync {
            val webView = TrustedJuntaWebView(context)
            val request = RecordingClientCertRequest()
            val client = JuntaWebViewClient(
                callbacks = RecordingCallbacks(),
                logger = SanitizedLogger(clock),
            )

            client.onReceivedClientCertRequest(webView, request)

            assertEquals(0, request.proceeds)
            assertEquals(1, request.ignores)
            webView.destroy()
        }
    }

    @Test
    fun dedicatedInitialTargetDoesNotAdvanceNavigationAndForeignMainFrameIsBlocked() {
        instrumentation.runOnMainSync {
            val webView = TrustedJuntaWebView(context)
            val callbacks = RecordingCallbacks()
            val preferencesCleared = AtomicInteger()
            val grant = grant()
            val client = ClientAuthWebViewClient(
                grant = grant,
                requestHandler = requestHandler(grant, preferencesCleared),
                callbacks = callbacks,
            )
            webView.webViewClient = client

            client.onPageStarted(webView, TARGET, null)

            assertEquals(0, callbacks.navigationStarts)
            assertEquals(listOf(TARGET), callbacks.changedUrls)
            assertEquals(0, preferencesCleared.get())
            assertFalse(client.shouldOverrideUrlLoading(webView, mainFrameRequest(TARGET)))
            assertTrue(
                client.shouldOverrideUrlLoading(
                    webView,
                    mainFrameRequest("https://ws235.juntadeandalucia.es.evil.example/"),
                ),
            )
            assertEquals(listOf(NavigationBlockReason.INVALID_URL), callbacks.blockedReasons)
            assertEquals(1, preferencesCleared.get())
            webView.destroy()
        }
    }

    @Test
    fun dedicatedConstructionDoesNotExposeTheWebMessageBridge() {
        val visualStateReached = CountDownLatch(1)
        val javascriptResult = arrayOfNulls<String>(1)
        lateinit var webView: TrustedJuntaWebView

        instrumentation.runOnMainSync {
            val grant = grant()
            webView = TrustedJuntaWebView(context).also { view ->
                view.webViewClient = ClientAuthWebViewClient(
                    grant = grant,
                    requestHandler = requestHandler(grant),
                    callbacks = RecordingCallbacks(),
                )
                view.loadDataWithBaseURL(
                    TARGET,
                    "<html><body>dedicated-client-auth</body></html>",
                    "text/html",
                    "UTF-8",
                    null,
                )
                view.postVisualStateCallback(
                    1L,
                    object : WebView.VisualStateCallback() {
                        override fun onComplete(requestId: Long) {
                            view.evaluateJavascript(
                                "typeof globalThis['${WebMessageBridge.BRIDGE_NAME}']",
                            ) { result ->
                                javascriptResult[0] = result
                                visualStateReached.countDown()
                            }
                        }
                    },
                )
            }
        }

        assertTrue(visualStateReached.await(10, TimeUnit.SECONDS))
        assertEquals("\"undefined\"", javascriptResult[0])
        instrumentation.runOnMainSync { webView.destroy() }
    }

    private fun grant(): ClientAuthGrant {
        val authorizer = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, clock)
        authorizer.observeTopLevelNavigation(PROFILE, INDEX, SOURCE, 4, true)
        authorizer.onTopLevelPageStarted(SOURCE, 5)
        val authorized = checkNotNull(
            authorizer.observeTopLevelNavigation(PROFILE, SOURCE, TARGET, 5, true),
        )
        return ClientAuthGrant(authorized, navigationEpoch = 6)
    }

    private fun requestHandler(
        grant: ClientAuthGrant,
        preferencesCleared: AtomicInteger = AtomicInteger(),
    ) = ClientAuthRequestHandler(
        grant = grant,
        identityProvider = { null },
        currentNavigationEpoch = { grant.navigationEpoch },
        clearClientCertPreferences = { preferencesCleared.incrementAndGet() },
        clock = clock,
    )

    private fun mainFrameRequest(rawUrl: String) = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(rawUrl)
        override fun isForMainFrame(): Boolean = true
        override fun isRedirect(): Boolean = true
        override fun hasGesture(): Boolean = false
        override fun getMethod(): String = "GET"
        override fun getRequestHeaders(): Map<String, String> = emptyMap()
    }

    private class RecordingCallbacks : BrowserNavigationCallbacks {
        var navigationStarts = 0
        val changedUrls = mutableListOf<String>()
        val blockedReasons = mutableListOf<NavigationBlockReason>()

        override fun openExternal(uri: Uri) = Unit

        override fun onAfirmaRequest(request: AfirmaRequest) = Unit

        override fun onNavigationBlocked(reason: NavigationBlockReason) {
            blockedReasons += reason
        }

        override fun onBrowserError(error: BrowserErrorCode) = Unit

        override fun onTopLevelNavigationStarted(url: String) {
            navigationStarts++
        }

        override fun onTopLevelUrlChanged(url: String) {
            changedUrls += url
        }
    }

    private class RecordingClientCertRequest : ClientCertRequest() {
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
        const val INDEX =
            "https://ws104.juntadeandalucia.es/carneJoven/cjservlet/portal/index.jsp"
        const val SOURCE =
            "https://ws104.juntadeandalucia.es/carneJoven/servlet/CallAuthenticationServlet"
        const val TARGET =
            "https://ws235.juntadeandalucia.es/authenticationFacade" +
                "?action=validateCert&ticketId=synthetic-ticket" +
                "&appId=IAJ.CARNETJOVEN&webSessionId=synthetic-session" +
                "&comeBackURL=https%3A%2F%2Fws104.juntadeandalucia.es%2FcarneJoven" +
                "%2Fservlet%2FReturnAuthenticationServlet"
    }
}

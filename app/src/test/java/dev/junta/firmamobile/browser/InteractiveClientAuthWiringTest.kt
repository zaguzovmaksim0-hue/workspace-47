package dev.junta.firmamobile.browser

import android.content.Context
import android.net.Uri
import android.net.http.SslCertificate
import android.net.http.SslError
import android.webkit.ClientCertRequest
import android.webkit.SslErrorHandler
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import dev.junta.firmamobile.afirma.AfirmaRequest
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.security.SanitizedLogger
import dev.junta.firmamobile.signing.nonExportableSyntheticIdentity
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.time.Clock
import java.time.ZoneOffset
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
class InteractiveClientAuthWiringTest {
    @Test fun platformCallbackFromUnknownHostReachesConsentWithoutProfilePromotionOrReload() {
        val f = Fixture()
        f.client.onReceivedClientCertRequest(f.view, f.request)
        assertNotNull(f.prompt)
        assertEquals(0, f.request.proceeds)
        assertTrue(f.controller.confirm(f.prompt!!.token))
        assertEquals(1, f.request.proceeds)
        assertNull(f.activeProfile)
        assertEquals(0, f.synthetic.encodedReads.get())
        assertNull(f.view.url)
        f.view.destroy()
    }

    @Test fun aClientWithoutInteractiveDelegateStillDeniesUnknownRequests() {
        val f = Fixture()
        val client = JuntaWebViewClient(f.callbacks, SanitizedLogger(), JuntaNavigationPolicy(ProfileId("junta-andalucia")))
        client.onReceivedClientCertRequest(f.view, f.request)
        assertEquals(1, f.request.ignores); assertNull(f.prompt)
        f.view.destroy()
    }

    @Test fun staleWebViewIsRejectedBeforeTheInteractiveDelegate() {
        val f = Fixture(); f.current = false
        f.client.onReceivedClientCertRequest(f.view, f.request)
        assertEquals(1, f.request.ignores); assertNull(f.prompt)
        f.view.destroy()
    }

    @Test fun topLevelNavigationInvalidatesPendingCallbackWithoutSendingACertificate() {
        val f = Fixture(); f.client.onReceivedClientCertRequest(f.view, f.request)
        val oldToken = f.prompt!!.token
        f.client.onPageStarted(f.view, "https://other.example/", null)
        assertFalse(f.controller.confirm(oldToken)); assertEquals(1, f.request.ignores)
        assertEquals(0, f.request.proceeds)
        f.view.destroy()
    }

    @Test fun sslFailureCancelsTheTlsErrorAndPendingInteractiveRequest() {
        val f = Fixture(); f.client.onReceivedClientCertRequest(f.view, f.request)
        var canceled = 0; var proceeded = 0
        val handler = object : SslErrorHandler() {
            override fun cancel() { canceled++ }
            override fun proceed() { proceeded++ }
        }
        val cert = SslCertificate("CN=synthetic.invalid", "CN=synthetic.invalid", "", "")
        f.client.onReceivedSslError(f.view, handler,
            SslError(SslError.SSL_UNTRUSTED, cert, "https://auth.unknown.example/"))
        assertEquals(1, canceled); assertEquals(0, proceeded)
        assertEquals(1, f.request.ignores); assertEquals(0, f.request.proceeds)
        assertNull(f.prompt)
        f.view.destroy()
    }

    private class Fixture {
        val view = WebView(ApplicationProvider.getApplicationContext<Context>())
        val synthetic = nonExportableSyntheticIdentity()
        var epoch = 1L
        var current = true
        val activeProfile: ProfileId? = null
        var prompt: InteractiveClientAuthPrompt? = null
        val request = Request()
        val controller = InteractiveClientAuthController<WebView>(
            isCurrent = { owner, expected -> current && owner === view && expected == epoch },
            identityProvider = { synthetic.identity }, canRespond = { true },
            clearClientCertPreferences = {}, onPrompt = { prompt = it },
            scheduler = ClientCertPreferenceTimeoutScheduler { _, _ -> ClientCertPreferenceTimeoutHandle {} },
            clock = Clock.fixed(synthetic.identity.summary.validFrom.plusSeconds(60), ZoneOffset.UTC),
        )
        val callbacks = object : BrowserNavigationCallbacks {
            override fun openExternal(uri: Uri) = Unit
            override fun openOfficialAutoFirma(uri: Uri) = Unit
            override fun onAfirmaRequest(request: AfirmaRequest) = Unit
            override fun onNavigationBlocked(reason: NavigationBlockReason) = Unit
            override fun onBrowserError(error: BrowserErrorCode) { controller.cancelPending() }
            override fun onTopLevelNavigationStarted(url: String) { epoch++; controller.cancelPending() }
        }
        val client = JuntaWebViewClient(
            callbacks, SanitizedLogger(), JuntaNavigationPolicy(ProfileId("junta-andalucia")),
            currentPageUrl = { "https://portal.example/" }, isActiveWebView = { current && it === view },
            activeProfileId = { activeProfile }, currentNavigationEpoch = { epoch },
            onInteractiveClientAuthChallenge = { owner, request ->
                controller.offer(owner, epoch, "https://portal.example/", request)
            },
        )
    }

    private class Request : ClientCertRequest() {
        var proceeds = 0; var ignores = 0
        override fun getHost() = "auth.unknown.example"
        override fun getPort() = 443
        override fun getKeyTypes() = arrayOf("RSA")
        override fun getPrincipals(): Array<Principal> = emptyArray()
        override fun proceed(key: PrivateKey, chain: Array<X509Certificate>) { proceeds++ }
        override fun ignore() { ignores++ }
        override fun cancel() { error("Denial must not be cached") }
    }
}

package dev.junta.firmamobile.browser

import android.content.Context
import android.net.Uri
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.SafeBrowsingResponse
import android.webkit.RenderProcessGoneDetail
import android.webkit.ClientCertRequest
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebView
import android.webkit.ValueCallback
import androidx.test.core.app.ApplicationProvider
import dev.junta.firmamobile.afirma.AfirmaRequest
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.security.SanitizedLogger
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import org.robolectric.shadow.api.Shadow

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class JuntaWebViewClientTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val webView = WebView(context)
    private val callbacks = RecordingBrowserCallbacks()
    private val logger = SanitizedLogger(
        Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
    )
    private val client = JuntaWebViewClient(
        callbacks = callbacks,
        logger = logger,
        navigationPolicy = JuntaNavigationPolicy(ProfileId("junta-andalucia")),
        currentPageUrl = { TRUSTED_PAGE },
    )

    @Test
    fun allowedNavigationReturnsFalseWithoutCallingLoadUrlOrCallbacks() {
        val overridden = client.shouldOverrideUrlLoading(
            webView,
            request("https://ssoweb.juntadeandalucia.es/login"),
        )

        assertFalse(overridden)
        assertTrue(callbacks.events.isEmpty())
        assertTrue(shadowOf(webView).lastLoadedUrl.isNullOrEmpty())
    }

    @Test
    fun crossProfileAndHttpNavigationAreBlocked() {
        assertTrue(
            client.shouldOverrideUrlLoading(
                webView,
                request("https://reg.redsara.es/es/"),
            ),
        )
        assertTrue(
            client.shouldOverrideUrlLoading(
                webView,
                request("http://example.org/help"),
            ),
        )

        assertEquals(
            listOf(
                "blocked:CROSS_PROFILE_NAVIGATION",
                "blocked:INSECURE_HTTP",
            ),
            callbacks.events,
        )
    }

    @Test
    fun exactOfvirtualMainFrameGetDowngradeIsReloadedAsHttps() {
        val ofvirtualCallbacks = RecordingBrowserCallbacks()
        val ofvirtualLogger = SanitizedLogger(
            Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
        )
        val ofvirtualClient = JuntaWebViewClient(
            callbacks = ofvirtualCallbacks,
            logger = ofvirtualLogger,
            navigationPolicy = JuntaNavigationPolicy(ProfileId("junta-ofvirtual")),
            currentPageUrl = {
                "https://ws072.juntadeandalucia.es/ofvirtual/auth/signInAutcertjs"
            },
        )
        val target = "http://ws072.juntadeandalucia.es/ofvirtual/auth/legacyReturn?state=ok#done"

        val overridden = ofvirtualClient.shouldOverrideUrlLoading(webView, request(target))

        assertTrue(overridden)
        assertEquals(
            "https://ws072.juntadeandalucia.es/ofvirtual/auth/legacyReturn?state=ok#done",
            shadowOf(webView).lastLoadedUrl,
        )
        assertTrue(ofvirtualCallbacks.events.isEmpty())
        assertTrue(ofvirtualLogger.exportText().contains("event=NAVIGATION_ALLOWED"))
        assertTrue(ofvirtualLogger.exportText().contains("method=GET"))
    }

    @Test
    fun ofvirtualPostAndSubframeHttpDowngradesRemainBlocked() {
        val ofvirtualCallbacks = RecordingBrowserCallbacks()
        val ofvirtualClient = JuntaWebViewClient(
            callbacks = ofvirtualCallbacks,
            logger = logger,
            navigationPolicy = JuntaNavigationPolicy(ProfileId("junta-ofvirtual")),
            currentPageUrl = {
                "https://ws072.juntadeandalucia.es/ofvirtual/auth/signInAutcertjs"
            },
        )
        val target = "http://ws072.juntadeandalucia.es/ofvirtual/auth/legacyReturn"

        assertTrue(ofvirtualClient.shouldOverrideUrlLoading(webView, request(target, method = "POST")))
        assertTrue(ofvirtualClient.shouldOverrideUrlLoading(webView, subframeRequest(target)))

        assertEquals(
            listOf("blocked:INSECURE_HTTP"),
            ofvirtualCallbacks.events,
        )
        assertTrue(shadowOf(webView).lastLoadedUrl.isNullOrEmpty())
    }

    @Test
    @Suppress("DEPRECATION")
    fun subframeAndLegacyBlockedNavigationCannotReachApplicationCallback() {
        val frameCallbacks = RecordingBrowserCallbacks()
        val frameLogger = SanitizedLogger(
            Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
        )
        val frameClient = JuntaWebViewClient(
            callbacks = frameCallbacks,
            logger = frameLogger,
            navigationPolicy = JuntaNavigationPolicy(ProfileId("junta-ofvirtual")),
            currentPageUrl = {
                "https://ws072.juntadeandalucia.es/ofvirtual/auth/signInAutcertjs"
            },
        )
        val insecure =
            "http://ws072.juntadeandalucia.es/ofvirtual/auth/legacyReturn?token=frame-secret"
        val crossProfile = "https://reg.redsara.es/es/?token=cross-frame-secret#fragment"
        val unsupported = "custom-scheme://host/path?token=scheme-frame-secret"
        val legacy = "https://reg.redsara.es/es/?token=legacy-frame-secret"

        assertTrue(frameClient.shouldOverrideUrlLoading(webView, subframeRequest(insecure)))
        assertTrue(frameClient.shouldOverrideUrlLoading(webView, subframeRequest(crossProfile)))
        assertTrue(frameClient.shouldOverrideUrlLoading(webView, subframeRequest(unsupported)))
        assertTrue(frameClient.shouldOverrideUrlLoading(webView, legacy))

        assertEquals(emptyList<String>(), frameCallbacks.events)
        assertTrue(shadowOf(webView).lastLoadedUrl.isNullOrEmpty())
        val exported = frameLogger.exportText()
        assertTrue(exported.contains("reason=INSECURE_HTTP"))
        assertTrue(exported.contains("reason=CROSS_PROFILE_NAVIGATION"))
        assertTrue(exported.contains("reason=UNSUPPORTED_SCHEME"))
        assertTrue(exported.contains("main_frame=false"))
        assertFalse(exported.contains("frame-secret"))
    }

    @Test
    fun staleWebViewCannotDeliverNavigationOrLifecycleCallbacks() {
        var active = true
        val staleCallbacks = RecordingBrowserCallbacks()
        val staleClient = JuntaWebViewClient(
            callbacks = staleCallbacks,
            logger = logger,
            navigationPolicy = JuntaNavigationPolicy(ProfileId("junta-andalucia")),
            currentPageUrl = { TRUSTED_PAGE },
            isActiveWebView = { active },
        )
        active = false

        assertTrue(
            staleClient.shouldOverrideUrlLoading(
                webView,
                request("https://example.org/help"),
            ),
        )
        assertTrue(
            staleClient.shouldOverrideUrlLoading(
                webView,
                request("afirma://sign?algorithm=SHA256withRSA&format=CAdES&dat=abc"),
            ),
        )
        staleClient.onPageStarted(webView, TRUSTED_PAGE, null)
        staleClient.onPageFinished(webView, TRUSTED_PAGE)

        val handler = Shadow.newInstanceOf(SslErrorHandler::class.java)
        val sslError = Shadow.newInstanceOf(SslError::class.java)
        staleClient.onReceivedSslError(webView, handler, sslError)

        val safeBrowsing = RecordingSafeBrowsingResponse()
        staleClient.onSafeBrowsingHit(
            webView,
            request("https://www.juntadeandalucia.es/suspicious"),
            0,
            safeBrowsing,
        )

        assertTrue(staleCallbacks.events.isEmpty())
        assertTrue(shadowOf(handler).wasCancelCalled())
        assertFalse(shadowOf(handler).wasProceedCalled())
        assertTrue(safeBrowsing.backToSafetyCalled)
        assertFalse(safeBrowsing.proceedCalled)
    }

    @Test
    fun staleWebViewSafetyErrorsFailClosedWithoutRecordingDiagnostics() {
        val staleLogger = SanitizedLogger(
            Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
        )
        var active = true
        val staleCallbacks = RecordingBrowserCallbacks()
        val staleClient = JuntaWebViewClient(
            callbacks = staleCallbacks,
            logger = staleLogger,
            navigationPolicy = JuntaNavigationPolicy(ProfileId("junta-andalucia")),
            currentPageUrl = { TRUSTED_PAGE },
            isActiveWebView = { active },
        )
        active = false
        val safeBrowsing = RecordingSafeBrowsingResponse()

        staleClient.onSafeBrowsingHit(
            webView,
            request("https://www.juntadeandalucia.es/suspicious"),
            0,
            safeBrowsing,
        )
        staleClient.onReceivedError(
            webView,
            request(TRUSTED_PAGE),
            Shadow.newInstanceOf(WebResourceError::class.java),
        )

        assertTrue(safeBrowsing.backToSafetyCalled)
        assertFalse(safeBrowsing.proceedCalled)
        assertTrue(staleCallbacks.events.isEmpty())
        assertEquals("", staleLogger.exportText())
    }

    @Test
    fun staleWebViewCancelsSslErrorWithoutRecordingDiagnostic() {
        val staleLogger = SanitizedLogger(
            Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
        )
        var active = true
        val staleClient = JuntaWebViewClient(
            callbacks = RecordingBrowserCallbacks(),
            logger = staleLogger,
            navigationPolicy = JuntaNavigationPolicy(ProfileId("junta-andalucia")),
            currentPageUrl = { TRUSTED_PAGE },
            isActiveWebView = { active },
        )
        active = false
        val handler = Shadow.newInstanceOf(SslErrorHandler::class.java)
        val sslError = Shadow.newInstanceOf(SslError::class.java)

        staleClient.onReceivedSslError(webView, handler, sslError)

        assertTrue(shadowOf(handler).wasCancelCalled())
        assertFalse(shadowOf(handler).wasProceedCalled())
        assertEquals("", staleLogger.exportText())
    }

    @Test
    fun staleWebViewDoesNotRecordNetworkRequestDiagnostics() {
        val staleLogger = SanitizedLogger(
            Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
        )
        var active = true
        val staleClient = JuntaWebViewClient(
            callbacks = RecordingBrowserCallbacks(),
            logger = staleLogger,
            navigationPolicy = JuntaNavigationPolicy(ProfileId("junta-andalucia")),
            currentPageUrl = { TRUSTED_PAGE },
            isActiveWebView = { active },
        )
        active = false

        staleClient.shouldInterceptRequest(
            webView,
            request(
                "https://ws072.juntadeandalucia.es/ofvirtual/auth/signInAutcertjs?firmaB64=stale-secret",
                method = "POST",
            ),
        )

        assertEquals("", staleLogger.exportText())
    }

    @Test
    fun activeViewPredicateFailureIsFailClosed() {
        val staleCallbacks = RecordingBrowserCallbacks()
        val staleClient = JuntaWebViewClient(
            callbacks = staleCallbacks,
            logger = logger,
            navigationPolicy = JuntaNavigationPolicy(ProfileId("junta-andalucia")),
            currentPageUrl = { TRUSTED_PAGE },
            isActiveWebView = { error("ownership probe failed") },
        )

        assertTrue(
            staleClient.shouldOverrideUrlLoading(
                webView,
                request("https://example.org/help"),
            ),
        )
        assertTrue(staleCallbacks.events.isEmpty())
    }

    @Test
    fun externalAndAfirmaNavigationAreConsumedByNativeCallbacks() {
        assertTrue(
            client.shouldOverrideUrlLoading(webView, request("https://example.org/help")),
        )
        assertTrue(
            client.shouldOverrideUrlLoading(
                webView,
                request("afirma://sign?algorithm=SHA256withRSA&format=CAdES&dat=abc"),
            ),
        )
        assertTrue(
            client.shouldOverrideUrlLoading(
                webView,
                request(
                    "intent://sign?algorithm=SHA256withRSA&format=CAdES&dat=abc" +
                        "#Intent;scheme=afirma;package=es.gob.afirma;end",
                ),
            ),
        )

        assertEquals("external:example.org", callbacks.events[0])
        assertEquals("afirma:sign", callbacks.events[1])
        assertEquals("afirma:sign", callbacks.events[2])
    }

    @Test
    @Suppress("DEPRECATION")
    fun subframeAndLegacyExternalHttpsCannotReachNativeHandoff() {
        val frameCallbacks = RecordingBrowserCallbacks()
        val frameLogger = SanitizedLogger(
            Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
        )
        val frameClient = JuntaWebViewClient(
            callbacks = frameCallbacks,
            logger = frameLogger,
            navigationPolicy = JuntaNavigationPolicy(ProfileId("junta-andalucia")),
            currentPageUrl = { TRUSTED_PAGE },
        )
        val external = "https://example.org/help?token=external-frame-secret#fragment"

        assertTrue(frameClient.shouldOverrideUrlLoading(webView, subframeRequest(external)))
        assertTrue(frameClient.shouldOverrideUrlLoading(webView, external))

        assertEquals(emptyList<String>(), frameCallbacks.events)
        val exported = frameLogger.exportText()
        assertTrue(exported.contains("reason=UNTRUSTED_EXTERNAL_NAVIGATION"))
        assertTrue(exported.contains("main_frame=false"))
        assertFalse(exported.contains("event=EXTERNAL_NAVIGATION"))
        assertFalse(exported.contains("external-frame-secret"))
    }

    @Test
    fun subframeExternalIntentFallbackCannotReachNativeHandoff() {
        val fallbackIntent =
            "intent://scan/#Intent;scheme=zxing;" +
                "S.browser_fallback_url=https%3A%2F%2Fexample.org%2Fhelp;end"
        val mainCallbacks = RecordingBrowserCallbacks()
        val mainClient = JuntaWebViewClient(
            callbacks = mainCallbacks,
            logger = SanitizedLogger(
                Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
            ),
            navigationPolicy = JuntaNavigationPolicy(ProfileId("junta-andalucia")),
            currentPageUrl = { TRUSTED_PAGE },
        )
        val frameCallbacks = RecordingBrowserCallbacks()
        val frameLogger = SanitizedLogger(
            Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
        )
        val frameClient = JuntaWebViewClient(
            callbacks = frameCallbacks,
            logger = frameLogger,
            navigationPolicy = JuntaNavigationPolicy(ProfileId("junta-andalucia")),
            currentPageUrl = { TRUSTED_PAGE },
        )

        assertTrue(mainClient.shouldOverrideUrlLoading(webView, request(fallbackIntent)))
        assertEquals(listOf("external:example.org"), mainCallbacks.events)

        assertTrue(frameClient.shouldOverrideUrlLoading(webView, subframeRequest(fallbackIntent)))
        assertEquals(emptyList<String>(), frameCallbacks.events)
        val exported = frameLogger.exportText()
        assertTrue(exported.contains("reason=UNTRUSTED_EXTERNAL_NAVIGATION"))
        assertTrue(exported.contains("main_frame=false"))
        assertFalse(exported.contains("event=EXTERNAL_NAVIGATION"))
    }

    @Test
    fun subframeAfirmaAndEmbeddedIntentCannotReachNativeCallbacks() {
        val frameCallbacks = RecordingBrowserCallbacks()
        val frameClient = JuntaWebViewClient(
            callbacks = frameCallbacks,
            logger = logger,
            navigationPolicy = JuntaNavigationPolicy(ProfileId("junta-andalucia")),
            currentPageUrl = { TRUSTED_PAGE },
        )
        val afirma = "afirma://sign?algorithm=SHA256withRSA&format=CAdES&dat=abc"
        val embeddedIntent =
            "intent://sign?algorithm=SHA256withRSA&format=CAdES&dat=abc" +
                "#Intent;scheme=afirma;package=es.gob.afirma;end"

        assertTrue(frameClient.shouldOverrideUrlLoading(webView, subframeRequest(afirma)))
        assertTrue(
            frameClient.shouldOverrideUrlLoading(webView, subframeRequest(embeddedIntent)),
        )

        assertEquals(emptyList<String>(), frameCallbacks.events)
        val exported = logger.exportText()
        assertTrue(exported.contains("reason=UNTRUSTED_AFIRMA_ORIGIN"))
        assertTrue(exported.contains("main_frame=false"))
        assertFalse(exported.contains("dat=abc"))
    }

    @Test
    @Suppress("DEPRECATION")
    fun legacyAfirmaCallbackCannotReachNativeCallbacks() {
        val legacyCallbacks = RecordingBrowserCallbacks()
        val legacyClient = JuntaWebViewClient(
            callbacks = legacyCallbacks,
            logger = logger,
            navigationPolicy = JuntaNavigationPolicy(ProfileId("junta-andalucia")),
            currentPageUrl = { TRUSTED_PAGE },
        )
        val afirma = "afirma://sign?algorithm=SHA256withRSA&format=CAdES&dat=abc"

        assertTrue(legacyClient.shouldOverrideUrlLoading(webView, afirma))

        assertEquals(emptyList<String>(), legacyCallbacks.events)
    }

    @Test
    fun playStoreFallbackIsConsumedAndRecordedWithoutExternalLaunch() {
        val overridden = client.shouldOverrideUrlLoading(
            webView,
            request("market://details?id=es.gob.afirma"),
        )

        assertTrue(overridden)
        assertEquals(
            listOf("blocked:PLAY_STORE_FALLBACK"),
            callbacks.events,
        )
        assertTrue(logger.exportText().contains("event=PLAY_STORE_FALLBACK_INTERCEPTED"))
    }

    @Test
    fun blockedAndMainFramePostRequestsRecordSafeNavigationMetadata() {
        val secret = "certificate-secret-canary"
        val target =
            "https://reg.redsara.es/es/continue?certificate=$secret#fragment"

        assertTrue(client.shouldOverrideUrlLoading(webView, request(target, method = "POST")))
        client.shouldInterceptRequest(
            webView,
            request(
                "https://ws072.juntadeandalucia.es/ofvirtual/auth/signInAutcertjs?firmaB64=$secret",
                method = "POST",
            ),
        )

        val exported = logger.exportText()
        assertTrue(exported.contains("event=NAVIGATION_BLOCKED"))
        assertTrue(exported.contains("reason=CROSS_PROFILE_NAVIGATION"))
        assertTrue(exported.contains("event=NETWORK_REQUEST"))
        assertTrue(exported.contains("host=ws072.juntadeandalucia.es"))
        assertTrue(exported.contains("method=POST"))
        assertFalse(exported.contains(secret))
        assertFalse(exported.contains("certificate="))
        assertFalse(exported.contains("firmaB64="))
    }

    @Test
    fun sslErrorsAlwaysCancelWithoutUnownedApplicationError() {
        val handler = Shadow.newInstanceOf(SslErrorHandler::class.java)
        val sslError = Shadow.newInstanceOf(SslError::class.java)

        client.onReceivedSslError(webView, handler, sslError)

        val shadowHandler = shadowOf(handler)
        assertTrue(shadowHandler.wasCancelCalled())
        assertFalse(shadowHandler.wasProceedCalled())
        assertEquals(emptyList<String>(), callbacks.events)
        assertTrue(logger.exportText().contains("event=SSL_ERROR_CANCELLED"))
    }

    @Test
    fun normalWebViewAlwaysIgnoresClientCertificateRequests() {
        val request = RecordingClientCertRequest()

        client.onReceivedClientCertRequest(webView, request)

        assertEquals(1, request.ignores)
        assertEquals(0, request.proceeds)
        assertEquals(0, request.cancels)
    }

    @Test
    fun safeBrowsingHitsAlwaysReturnToSafety() {
        val response = RecordingSafeBrowsingResponse()

        client.onSafeBrowsingHit(
            webView,
            request("https://www.juntadeandalucia.es/suspicious"),
            0,
            response,
        )

        assertTrue(response.backToSafetyCalled)
        assertFalse(response.proceedCalled)
        assertFalse(response.interstitialCalled)
        assertEquals(listOf("error:SAFE_BROWSING"), callbacks.events)
        assertTrue(logger.exportText().contains("event=SAFE_BROWSING_BLOCKED"))

        callbacks.events.clear()
        val subframeResponse = RecordingSafeBrowsingResponse()
        client.onSafeBrowsingHit(
            webView,
            subframeRequest("https://www.juntadeandalucia.es/suspicious-frame"),
            0,
            subframeResponse,
        )

        assertTrue(subframeResponse.backToSafetyCalled)
        assertFalse(subframeResponse.proceedCalled)
        assertFalse(subframeResponse.interstitialCalled)
        assertEquals(emptyList<String>(), callbacks.events)
    }

    @Test
    fun rendererDeathIsAcknowledgedForTheExactAffectedWebView() {
        val detail = RecordingRenderProcessGoneDetail(didCrashValue = true)

        assertTrue(client.onRenderProcessGone(webView, detail))

        assertSame(webView, callbacks.rendererView)
        assertEquals(listOf("renderer"), callbacks.events)
    }

    @Test
    fun ofvirtualPageFinishInjectsMenuCompatibilityWithoutTouchingPortalData() {
        val recordingWebView = RecordingJavascriptWebView(context)
        val ofvirtualClient = JuntaWebViewClient(
            callbacks = callbacks,
            logger = logger,
            navigationPolicy = JuntaNavigationPolicy(ProfileId("junta-ofvirtual")),
            currentPageUrl = { OFVIRTUAL_PAGE },
        )

        ofvirtualClient.onPageFinished(recordingWebView, OFVIRTUAL_PAGE)

        val script = recordingWebView.evaluatedScripts.single()
        assertTrue(script.contains("MenÃº"))
        assertTrue(script.contains("Menú"))
        assertTrue(script.contains("data-toggle=\"collapse\""))
        assertTrue(script.contains("classList.toggle('show'"))
        assertFalse(script.contains("firmaB64"))
        assertFalse(script.contains("certificadoB64"))
        assertFalse(script.contains("document.cookie"))
    }

    @Test
    fun topLevelPageLifecycleUpdatesAddressWithoutLoggingTheUrl() {
        val rawUrl =
            "https://www.juntadeandalucia.es/path?secret-canary=value#fragment"

        client.onPageStarted(webView, rawUrl, null)
        client.onPageFinished(webView, rawUrl)

        assertEquals(
            listOf("start:$rawUrl", "url:$rawUrl", "url:$rawUrl"),
            callbacks.events,
        )
        assertFalse(logger.exportText().contains("secret-canary"))
    }

    @Test
    fun carneJovenAuthorizedTransitionStaysInWebViewAndUnauthorizedWs235IsBlockedFailClosed() {
        var capturedAuthorizedTarget: AuthorizedClientAuthTarget? = null
        val carneJovenCallbacks = RecordingBrowserCallbacks()
        val authorizer = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry)
        val carneJovenClient = JuntaWebViewClient(
            callbacks = carneJovenCallbacks,
            logger = logger,
            navigationPolicy = JuntaNavigationPolicy(ProfileId("carne-joven-andalucia")),
            currentPageUrl = { "https://ws104.juntadeandalucia.es/carneJoven/servlet/CallAuthenticationServlet" },
            clientAuthAuthorizer = authorizer,
            activeProfileId = { ProfileId("carne-joven-andalucia") },
            currentNavigationEpoch = { 1L },
            onClientAuthTarget = { capturedAuthorizedTarget = it },
        )

        authorizer.observeTopLevelNavigation(
            activeProfileId = ProfileId("carne-joven-andalucia"),
            currentUrl = "https://ws104.juntadeandalucia.es/carneJoven/cjservlet/portal/index.jsp",
            targetUrl = "https://ws104.juntadeandalucia.es/carneJoven/servlet/CallAuthenticationServlet",
            currentEpoch = 0L,
            isModernMainFrameRequest = true,
        )
        authorizer.onTopLevelPageStarted(
            "https://ws104.juntadeandalucia.es/carneJoven/servlet/CallAuthenticationServlet",
            1L,
        )

        val ws235Target =
            "https://ws235.juntadeandalucia.es/authenticationFacade?action=validateCert&appId=IAJ.CARNETJOVEN&ticketId=synthetic-ticket&webSessionId=synthetic-session&comeBackURL=aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ%3D"
        val authorizedResult = carneJovenClient.shouldOverrideUrlLoading(webView, request(ws235Target))

        assertTrue(authorizedResult)
        assertEquals("ws235.juntadeandalucia.es", capturedAuthorizedTarget?.target?.host)
        assertTrue(carneJovenCallbacks.events.isEmpty())

        val unauthorizedCallbacks = RecordingBrowserCallbacks()
        val unauthorizedClient = JuntaWebViewClient(
            callbacks = unauthorizedCallbacks,
            logger = logger,
            navigationPolicy = JuntaNavigationPolicy(ProfileId("carne-joven-andalucia")),
            currentPageUrl = { "https://ws104.juntadeandalucia.es/carneJoven/cjservlet/portal/index.jsp" },
            clientAuthAuthorizer = authorizer,
            activeProfileId = { ProfileId("carne-joven-andalucia") },
            currentNavigationEpoch = { 2L },
        )
        val unauthorizedResult = unauthorizedClient.shouldOverrideUrlLoading(webView, request(ws235Target))

        assertTrue(unauthorizedResult)
        assertEquals(listOf("blocked:CROSS_PROFILE_NAVIGATION"), unauthorizedCallbacks.events)
    }

    @Test
    fun subframeAndLegacyRequestsDoNotTriggerClientAuthAndUnauthorizedWs235IsBlockedFailClosed() {
        var capturedAuthorizedTarget: AuthorizedClientAuthTarget? = null
        val carneJovenCallbacks = RecordingBrowserCallbacks()
        val authorizer = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry)
        val carneJovenClient = JuntaWebViewClient(
            callbacks = carneJovenCallbacks,
            logger = logger,
            navigationPolicy = JuntaNavigationPolicy(ProfileId("carne-joven-andalucia")),
            currentPageUrl = { "https://ws104.juntadeandalucia.es/carneJoven/servlet/CallAuthenticationServlet" },
            clientAuthAuthorizer = authorizer,
            activeProfileId = { ProfileId("carne-joven-andalucia") },
            currentNavigationEpoch = { 1L },
            onClientAuthTarget = { capturedAuthorizedTarget = it },
        )

        authorizer.observeTopLevelNavigation(
            activeProfileId = ProfileId("carne-joven-andalucia"),
            currentUrl = "https://ws104.juntadeandalucia.es/carneJoven/cjservlet/portal/index.jsp",
            targetUrl = "https://ws104.juntadeandalucia.es/carneJoven/servlet/CallAuthenticationServlet",
            currentEpoch = 0L,
            isModernMainFrameRequest = true,
        )
        authorizer.onTopLevelPageStarted(
            "https://ws104.juntadeandalucia.es/carneJoven/servlet/CallAuthenticationServlet",
            1L,
        )

        val ws235Target =
            "https://ws235.juntadeandalucia.es/authenticationFacade?action=validateCert&appId=IAJ.CARNETJOVEN&ticketId=synthetic-ticket&webSessionId=synthetic-session&comeBackURL=aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ%3D"

        val subframeResult = carneJovenClient.shouldOverrideUrlLoading(webView, subframeRequest(ws235Target))
        assertTrue(subframeResult)
        assertEquals(null, capturedAuthorizedTarget)
        assertEquals(emptyList<String>(), carneJovenCallbacks.events)

        val legacyCallbacks = RecordingBrowserCallbacks()
        val legacyClient = JuntaWebViewClient(
            callbacks = legacyCallbacks,
            logger = logger,
            navigationPolicy = JuntaNavigationPolicy(ProfileId("carne-joven-andalucia")),
            currentPageUrl = { "https://ws104.juntadeandalucia.es/carneJoven/cjservlet/portal/index.jsp" },
            clientAuthAuthorizer = authorizer,
            activeProfileId = { ProfileId("carne-joven-andalucia") },
            currentNavigationEpoch = { 2L },
        )

        @Suppress("DEPRECATION")
        val legacyResult = legacyClient.shouldOverrideUrlLoading(webView, ws235Target)
        assertTrue(legacyResult)
        assertEquals(emptyList<String>(), legacyCallbacks.events)
    }

    private fun request(rawUrl: String, method: String = "GET") = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(rawUrl)
        override fun isForMainFrame(): Boolean = true
        override fun isRedirect(): Boolean = false
        override fun hasGesture(): Boolean = true
        override fun getMethod(): String = method
        override fun getRequestHeaders(): Map<String, String> = emptyMap()
    }

    private fun subframeRequest(rawUrl: String) = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(rawUrl)
        override fun isForMainFrame(): Boolean = false
        override fun isRedirect(): Boolean = false
        override fun hasGesture(): Boolean = true
        override fun getMethod(): String = "GET"
        override fun getRequestHeaders(): Map<String, String> = emptyMap()
    }

    private class RecordingJavascriptWebView(context: Context) : WebView(context) {
        val evaluatedScripts = mutableListOf<String>()

        override fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
            evaluatedScripts += script
            resultCallback?.onReceiveValue("null")
        }
    }

    private class RecordingBrowserCallbacks : BrowserNavigationCallbacks {
        val events = mutableListOf<String>()
        var rendererView: WebView? = null

        override fun openExternal(uri: Uri) {
            events += "external:${uri.host}"
        }

        override fun onAfirmaRequest(request: AfirmaRequest) {
            events += "afirma:${request.operation.wireName}"
        }

        override fun onNavigationBlocked(reason: NavigationBlockReason) {
            events += "blocked:${reason.name}"
        }

        override fun onBrowserError(error: BrowserErrorCode) {
            events += "error:${error.name}"
        }

        override fun onRenderProcessGone(view: WebView) {
            rendererView = view
            events += "renderer"
        }

        override fun onTopLevelUrlChanged(url: String) {
            events += "url:$url"
        }

        override fun onTopLevelNavigationStarted(url: String) {
            events += "start:$url"
        }
    }

    private class RecordingRenderProcessGoneDetail(
        private val didCrashValue: Boolean,
    ) : RenderProcessGoneDetail() {
        override fun didCrash(): Boolean = didCrashValue

        override fun rendererPriorityAtExit(): Int = 0
    }

    private class RecordingSafeBrowsingResponse : SafeBrowsingResponse() {
        var backToSafetyCalled = false
        var proceedCalled = false
        var interstitialCalled = false

        override fun backToSafety(report: Boolean) {
            backToSafetyCalled = true
        }

        override fun proceed(report: Boolean) {
            proceedCalled = true
        }

        override fun showInterstitial(allowReporting: Boolean) {
            interstitialCalled = true
        }
    }

    private class RecordingClientCertRequest : ClientCertRequest() {
        var ignores = 0
        var proceeds = 0
        var cancels = 0
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
        override fun cancel() {
            cancels++
        }
    }

    private companion object {
        const val TRUSTED_PAGE = "https://www.juntadeandalucia.es/portal"
        const val OFVIRTUAL_PAGE = "https://ws072.juntadeandalucia.es/ofvirtual/ovMisTramites/index"
    }
}

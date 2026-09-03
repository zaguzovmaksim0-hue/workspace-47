package dev.junta.firmamobile.browser

import android.content.Context
import android.net.Uri
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.SafeBrowsingResponse
import android.webkit.RenderProcessGoneDetail
import android.webkit.ClientCertRequest
import android.webkit.WebResourceRequest
import android.webkit.TestWebResourceError
import android.webkit.WebView
import android.webkit.WebViewClient
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
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
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
    fun staleWebViewSafeBrowsingFailsClosedWithoutRecordingDiagnostic() {
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

        assertTrue(safeBrowsing.backToSafetyCalled)
        assertFalse(safeBrowsing.proceedCalled)
        assertTrue(staleCallbacks.events.isEmpty())
        assertEquals("", staleLogger.exportText())
    }

    @Test
    fun staleWebViewMainFrameNetworkErrorDoesNotRecordDiagnostic() {
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

        staleClient.onReceivedError(
            webView,
            request(TRUSTED_PAGE),
            TestWebResourceError(-2, "synthetic network failure"),
        )

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
    fun seguridadSocialOfficialAutoFirmaHandoffIsBlockedInsideTheApp() {
        val sedessCallbacks = RecordingBrowserCallbacks()
        val sedessClient = JuntaWebViewClient(
            callbacks = sedessCallbacks,
            logger = logger,
            navigationPolicy = JuntaNavigationPolicy(ProfileId("seguridad-social-sede-autofirma")),
            currentPageUrl = { SEGURIDAD_SOCIAL_RETURN_PAGE },
        )
        val target =
            "intent://sign?algorithm=SHA256withRSA&format=CAdES&dat=YWJj" +
                "#Intent;scheme=afirma;package=es.gob.afirma;end"

        assertTrue(sedessClient.shouldOverrideUrlLoading(webView, request(target)))

        assertEquals(
            listOf("blocked:UNSUPPORTED_EXTERNAL_INTENT"),
            sedessCallbacks.events,
        )
    }

    @Test
    fun seguridadSocialOfficialAutoFirmaHandoffRejectsSubframesAndPost() {
        val sedessCallbacks = RecordingBrowserCallbacks()
        val sedessClient = JuntaWebViewClient(
            callbacks = sedessCallbacks,
            logger = logger,
            navigationPolicy = JuntaNavigationPolicy(ProfileId("seguridad-social-sede-autofirma")),
            currentPageUrl = { SEGURIDAD_SOCIAL_RETURN_PAGE },
        )
        val target = "afirma://sign?algorithm=SHA256withRSA&format=CAdES&dat=YWJj"

        assertTrue(sedessClient.shouldOverrideUrlLoading(webView, subframeRequest(target)))
        assertEquals(emptyList<String>(), sedessCallbacks.events)

        assertTrue(sedessClient.shouldOverrideUrlLoading(webView, request(target, method = "POST")))
        assertEquals(
            listOf("blocked:UNSUPPORTED_EXTERNAL_INTENT"),
            sedessCallbacks.events,
        )
    }

    @Test
    fun externalHttpsIsBlockedWhileInAppAfirmaNavigationUsesNativeBridge() {
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

        assertEquals("blocked:UNTRUSTED_EXTERNAL_NAVIGATION", callbacks.events[0])
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
        assertEquals(
            listOf("blocked:UNTRUSTED_EXTERNAL_NAVIGATION"),
            mainCallbacks.events,
        )

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
    fun tarragonaExactTopLevelPostArmsOnlyTheMatchingInPlaceClientTlsChallenge() {
        val profileId = ProfileId("diputacion-tarragona-sede")
        val target = "https://cert.valid.aoc.cat/o/oauth2/cert"
        var captured: AuthorizedClientAuthTarget? = null
        var capturedRequest: ClientCertRequest? = null
        val tarragonaClient = JuntaWebViewClient(
            callbacks = RecordingBrowserCallbacks(),
            logger = logger,
            navigationPolicy = JuntaNavigationPolicy(profileId),
            clientAuthAuthorizer = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry),
            activeProfileId = { profileId },
            currentNavigationEpoch = { 200L },
            onInPlaceClientAuthChallenge = { authorized, request ->
                captured = authorized
                capturedRequest = request
            },
        )

        tarragonaClient.onPageStarted(webView, TARRAGONA_VALID_SOURCE, null)
        assertNull(tarragonaClient.shouldInterceptRequest(webView, request(target, method = "POST")))
        val clientCert = RecordingClientCertRequest(requestHost = "cert.valid.aoc.cat")
        tarragonaClient.onReceivedClientCertRequest(webView, clientCert)

        assertEquals(profileId, captured?.profileId)
        assertEquals(target, captured?.target?.toASCIIString())
        assertSame(clientCert, capturedRequest)
        assertEquals(0, clientCert.ignores)
    }

    @Test
    fun malagaExactTopLevelPostArmsTheInPlaceClaveClientTlsChallenge() {
        val profileId = ProfileId("diputacion-malaga-instancia-general")
        var captured: AuthorizedClientAuthTarget? = null
        var capturedRequest: ClientCertRequest? = null
        val malagaClient = JuntaWebViewClient(
            callbacks = RecordingBrowserCallbacks(),
            logger = logger,
            navigationPolicy = JuntaNavigationPolicy(profileId),
            clientAuthAuthorizer = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry),
            activeProfileId = { profileId },
            currentNavigationEpoch = { 205L },
            onInPlaceClientAuthChallenge = { authorized, request ->
                captured = authorized
                capturedRequest = request
            },
        )

        malagaClient.onPageStarted(webView, MALAGA_CLAVE_SERVICE_REDIRECT, null)
        assertNull(malagaClient.shouldInterceptRequest(webView, request(MALAGA_CLAVE_CERT_TARGET, method = "POST")))
        val clientCert = RecordingClientCertRequest(requestHost = "pasarela-ident.clave.gob.es")
        malagaClient.onReceivedClientCertRequest(webView, clientCert)

        assertEquals(profileId, captured?.profileId)
        assertEquals(MALAGA_CLAVE_CERT_TARGET, captured?.target?.toASCIIString())
        assertSame(clientCert, capturedRequest)
        assertEquals(0, clientCert.ignores)
    }

    @Test
    fun malagaGetWrongTargetAndUnarmedClientCertificateChallengeFailClosed() {
        val profileId = ProfileId("diputacion-malaga-instancia-general")
        val scenarios = listOf(
            "GET" to MALAGA_CLAVE_CERT_TARGET,
            "POST" to MALAGA_CLAVE_CERT_TARGET.replace("/AuthenticateCitizen", "/other"),
            "POST" to "$MALAGA_CLAVE_CERT_TARGET?extra=1",
        )
        scenarios.forEachIndexed { index, (method, target) ->
            var callbackCount = 0
            val malagaClient = JuntaWebViewClient(
                callbacks = RecordingBrowserCallbacks(),
                logger = logger,
                navigationPolicy = JuntaNavigationPolicy(profileId),
                clientAuthAuthorizer = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry),
                activeProfileId = { profileId },
                currentNavigationEpoch = { 206L + index },
                onInPlaceClientAuthChallenge = { _, _ -> callbackCount++ },
            )
            malagaClient.onPageStarted(webView, MALAGA_CLAVE_SERVICE_REDIRECT, null)
            assertNull(malagaClient.shouldInterceptRequest(webView, request(target, method = method)))
            val clientCert = RecordingClientCertRequest(requestHost = "pasarela-ident.clave.gob.es")
            malagaClient.onReceivedClientCertRequest(webView, clientCert)
            assertEquals(0, callbackCount)
            assertEquals(1, clientCert.ignores)
        }
    }

    @Test
    fun tarragonaGetWrongTargetAndUnarmedClientCertificateChallengeFailClosed() {
        val profileId = ProfileId("diputacion-tarragona-sede")
        val scenarios = listOf(
            "GET" to "https://cert.valid.aoc.cat/o/oauth2/cert",
            "POST" to "https://cert.valid.aoc.cat/o/oauth2/other",
            "POST" to "https://cert.valid.aoc.cat/o/oauth2/cert?extra=1",
        )

        scenarios.forEachIndexed { index, (method, target) ->
            var callbackCount = 0
            val tarragonaClient = JuntaWebViewClient(
                callbacks = RecordingBrowserCallbacks(),
                logger = logger,
                navigationPolicy = JuntaNavigationPolicy(profileId),
                clientAuthAuthorizer = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry),
                activeProfileId = { profileId },
                currentNavigationEpoch = { 210L + index },
                onInPlaceClientAuthChallenge = { _, _ -> callbackCount++ },
            )
            tarragonaClient.onPageStarted(webView, TARRAGONA_VALID_SOURCE, null)
            assertNull(tarragonaClient.shouldInterceptRequest(webView, request(target, method = method)))
            val clientCert = RecordingClientCertRequest(requestHost = "cert.valid.aoc.cat")
            tarragonaClient.onReceivedClientCertRequest(webView, clientCert)
            assertEquals(0, callbackCount)
            assertEquals(1, clientCert.ignores)
        }
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
    fun euskadiClientAuthTargetCannotBypassPostBridgeAsNormalNavigation() {
        listOf("GET", "POST").forEach { method ->
            var capturedAuthorizedTarget: AuthorizedClientAuthTarget? = null
            val euskadiCallbacks = RecordingBrowserCallbacks()
            val euskadiClient = JuntaWebViewClient(
                callbacks = euskadiCallbacks,
                logger = logger,
                navigationPolicy = JuntaNavigationPolicy(
                    ProfileId(EuskadiClientAuthPostBridgeAdapter.PROFILE_ID),
                ),
                currentPageUrl = { EuskadiClientAuthPostBridgeAdapter.SOURCE_PAGE },
                clientAuthAuthorizer = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry),
                activeProfileId = { ProfileId(EuskadiClientAuthPostBridgeAdapter.PROFILE_ID) },
                currentNavigationEpoch = { 7L },
                onClientAuthTarget = { capturedAuthorizedTarget = it },
            )

            val overridden = euskadiClient.shouldOverrideUrlLoading(
                webView,
                request(EuskadiClientAuthPostBridgeAdapter.TARGET_URL, method),
            )

            assertTrue(overridden)
            assertEquals(null, capturedAuthorizedTarget)
            assertEquals(
                listOf("blocked:CROSS_PROFILE_NAVIGATION"),
                euskadiCallbacks.events,
            )
        }
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

    @Test
    fun veaExactGetRedirectStopsBeforeTlsAndRequestsUserConfirmation() {
        val profileId = ProfileId("junta-andalucia-vea-peg")
        val authorizer = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry)
        val source = veaSourceUrl()
        val target = veaTargetUrl()
        var captured: AuthorizedClientAuthTarget? = null
        val veaCallbacks = RecordingBrowserCallbacks()
        val veaClient = JuntaWebViewClient(
            callbacks = veaCallbacks,
            logger = logger,
            navigationPolicy = JuntaNavigationPolicy(profileId, BuiltInSiteProfiles.qaRegistry),
            currentPageUrl = { VEA_AUTH_FACADE },
            clientAuthAuthorizer = authorizer,
            activeProfileId = { profileId },
            currentNavigationEpoch = { 20L },
            onClientAuthTarget = { captured = it },
        )

        assertFalse(veaClient.shouldOverrideUrlLoading(webView, request(source)))

        // /auth/login is only a server-side redirect hop. The ws235 TLS request is
        // stopped so BrowserScreen can ask the user before any private-key use.
        assertTrue(veaClient.shouldOverrideUrlLoading(webView, request(target)))

        assertEquals(profileId, captured?.profileId)
        assertEquals(target, captured?.target?.toASCIIString())
        assertTrue(veaCallbacks.events.none { it.startsWith("external:") })
    }

    @Test
    fun veaConfirmedTargetCanBeArmedInPlaceAfterUserConfirmation() {
        val profileId = ProfileId("junta-andalucia-vea-peg")
        val source = veaSourceUrl()
        val confirmed = confirmedVeaTarget(40L)
        var epoch = 40L
        var challengeCount = 0
        val inPlaceClient = JuntaWebViewClient(
            callbacks = EpochAdvancingBrowserCallbacks { epoch++ },
            logger = logger,
            navigationPolicy = JuntaNavigationPolicy(profileId, BuiltInSiteProfiles.qaRegistry),
            clientAuthAuthorizer = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry),
            activeProfileId = { profileId },
            currentNavigationEpoch = { epoch },
            onInPlaceClientAuthChallenge = { authorized, _ ->
                assertEquals(confirmed.profileId, authorized.profileId)
                assertEquals(confirmed.source, authorized.source)
                challengeCount++
            },
        )
        assertTrue(inPlaceClient.armConfirmedInPlaceClientAuth(confirmed, epoch))

        // Chromium may observe the main-frame request before onPageStarted. The
        // exact source start must rebind the preconfirmed state to the new epoch.
        inPlaceClient.shouldInterceptRequest(webView, request(source))
        inPlaceClient.onPageStarted(webView, source, null)
        val refreshedTarget = veaTargetUrl()
            .replace("ticketId=synthetic-ticket", "ticketId=synthetic-ticket-2")
            .replace("webSessionId=synthetic-session", "webSessionId=synthetic-session-2")
        assertFalse(inPlaceClient.shouldOverrideUrlLoading(webView, request(refreshedTarget)))
        inPlaceClient.onPageStarted(webView, refreshedTarget, null)
        val request = RecordingClientCertRequest()
        inPlaceClient.onReceivedClientCertRequest(webView, request)

        assertEquals(42L, epoch)
        assertEquals(1, challengeCount)
        assertEquals(0, request.ignores)
    }

    @Test
    fun carneJovenConfirmedRedirectRefreshesTicketInsideOriginalWebView() {
        val profileId = ProfileId("carne-joven-andalucia")
        val index = "https://ws104.juntadeandalucia.es/carneJoven/cjservlet/portal/index.jsp"
        val source = "https://ws104.juntadeandalucia.es/carneJoven/servlet/CallAuthenticationServlet"
        val target =
            "https://ws235.juntadeandalucia.es/authenticationFacade" +
                "?action=validateCert&appId=IAJ.CARNETJOVEN" +
                "&ticketId=synthetic-ticket&webSessionId=synthetic-session" +
                "&comeBackURL=aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ%3D"
        val authorizer = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry)
        authorizer.observeTopLevelNavigation(profileId, index, source, 69L, true)
        authorizer.onTopLevelPageStarted(source, 70L)
        val confirmed = checkNotNull(
            authorizer.observeTopLevelNavigation(profileId, source, target, 70L, true),
        ).refreshedAfterUserConfirmation()
        var epoch = 70L
        var challenge: AuthorizedClientAuthTarget? = null
        val inPlaceClient = JuntaWebViewClient(
            callbacks = EpochAdvancingBrowserCallbacks { epoch++ },
            logger = logger,
            navigationPolicy = JuntaNavigationPolicy(profileId, BuiltInSiteProfiles.qaRegistry),
            clientAuthAuthorizer = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry),
            activeProfileId = { profileId },
            currentNavigationEpoch = { epoch },
            onInPlaceClientAuthChallenge = { authorized, _ -> challenge = authorized },
        )

        assertTrue(inPlaceClient.armConfirmedInPlaceClientAuth(confirmed, epoch))
        inPlaceClient.shouldInterceptRequest(webView, request(source))
        inPlaceClient.onPageStarted(webView, source, null)
        val refreshedTarget = target
            .replace("ticketId=synthetic-ticket", "ticketId=synthetic-ticket-2")
            .replace("webSessionId=synthetic-session", "webSessionId=synthetic-session-2")
        assertFalse(inPlaceClient.shouldOverrideUrlLoading(webView, request(refreshedTarget)))
        inPlaceClient.onPageStarted(webView, refreshedTarget, null)
        val request = RecordingClientCertRequest()
        inPlaceClient.onReceivedClientCertRequest(webView, request)

        assertEquals(refreshedTarget, challenge?.target?.toASCIIString())
        assertEquals(0, request.ignores)
    }

    @Test
    fun carneJovenPreTlsTimeoutRetriesConfirmedFlowWithoutRetainingOldGrant() {
        val profileId = ProfileId("carne-joven-andalucia")
        val source = CARNE_JOVEN_SOURCE
        val confirmed = confirmedCarneJovenTarget(70L)
        val callbacks = RecordingBrowserCallbacks()
        var retryTarget: AuthorizedClientAuthTarget? = null
        var retryCount = 0
        val inPlaceClient = JuntaWebViewClient(
            callbacks = callbacks,
            logger = logger,
            navigationPolicy = JuntaNavigationPolicy(profileId, BuiltInSiteProfiles.qaRegistry),
            clientAuthAuthorizer = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry),
            activeProfileId = { profileId },
            currentNavigationEpoch = { 70L },
            onPreconfirmedClientAuthNetworkTimeout = { authorized ->
                retryTarget = authorized
                retryCount++
                true
            },
        )
        assertTrue(inPlaceClient.armConfirmedInPlaceClientAuth(confirmed, 70L))
        inPlaceClient.shouldInterceptRequest(webView, request(source))
        inPlaceClient.onPageStarted(webView, source, null)
        val refreshedTarget = carneJovenTargetUrl(
            ticket = "synthetic-ticket-retry",
            session = "synthetic-session-retry",
        )
        assertFalse(inPlaceClient.shouldOverrideUrlLoading(webView, request(refreshedTarget)))
        inPlaceClient.onPageStarted(webView, refreshedTarget, null)

        inPlaceClient.onReceivedError(
            webView,
            request(refreshedTarget),
            TestWebResourceError(WebViewClient.ERROR_TIMEOUT, "synthetic connect timeout"),
        )

        assertEquals(1, retryCount)
        assertEquals(refreshedTarget, retryTarget?.target?.toASCIIString())
        assertFalse(callbacks.events.contains("error:NETWORK_ERROR"))
        assertTrue(logger.exportText().contains("client-auth-pre-tls-timeout-retry"))

        // The failed target was consumed before retry; a late challenge cannot reuse it.
        val lateRequest = RecordingClientCertRequest()
        inPlaceClient.onReceivedClientCertRequest(webView, lateRequest)
        assertEquals(1, lateRequest.ignores)
    }

    @Test
    fun carneJovenChromiumConnectionTimedOutMappedAsConnectErrorStillRetries() {
        val profileId = ProfileId("carne-joven-andalucia")
        val confirmed = confirmedCarneJovenTarget(70L)
        val callbacks = RecordingBrowserCallbacks()
        var retryCount = 0
        val inPlaceClient = JuntaWebViewClient(
            callbacks = callbacks,
            logger = logger,
            navigationPolicy = JuntaNavigationPolicy(profileId, BuiltInSiteProfiles.qaRegistry),
            clientAuthAuthorizer = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry),
            activeProfileId = { profileId },
            currentNavigationEpoch = { 70L },
            onPreconfirmedClientAuthNetworkTimeout = {
                retryCount++
                true
            },
        )
        assertTrue(inPlaceClient.armConfirmedInPlaceClientAuth(confirmed, 70L))
        inPlaceClient.shouldInterceptRequest(webView, request(CARNE_JOVEN_SOURCE))
        inPlaceClient.onPageStarted(webView, CARNE_JOVEN_SOURCE, null)
        val target = carneJovenTargetUrl(
            ticket = "synthetic-ticket-connect-timeout",
            session = "synthetic-session-connect-timeout",
        )
        assertFalse(inPlaceClient.shouldOverrideUrlLoading(webView, request(target)))
        inPlaceClient.onPageStarted(webView, target, null)

        inPlaceClient.onReceivedError(
            webView,
            request(target),
            TestWebResourceError(
                WebViewClient.ERROR_CONNECT,
                "net::ERR_CONNECTION_TIMED_OUT",
            ),
        )

        assertEquals(1, retryCount)
        assertFalse(callbacks.events.contains("error:NETWORK_ERROR"))
    }

    @Test
    fun carneJovenChromiumConnectionRefusedDoesNotRetry() {
        val profileId = ProfileId("carne-joven-andalucia")
        val confirmed = confirmedCarneJovenTarget(70L)
        val callbacks = RecordingBrowserCallbacks()
        var retryCount = 0
        val inPlaceClient = JuntaWebViewClient(
            callbacks = callbacks,
            logger = logger,
            navigationPolicy = JuntaNavigationPolicy(profileId, BuiltInSiteProfiles.qaRegistry),
            clientAuthAuthorizer = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry),
            activeProfileId = { profileId },
            currentNavigationEpoch = { 70L },
            onPreconfirmedClientAuthNetworkTimeout = {
                retryCount++
                true
            },
        )
        assertTrue(inPlaceClient.armConfirmedInPlaceClientAuth(confirmed, 70L))
        inPlaceClient.shouldInterceptRequest(webView, request(CARNE_JOVEN_SOURCE))
        inPlaceClient.onPageStarted(webView, CARNE_JOVEN_SOURCE, null)
        val target = carneJovenTargetUrl(
            ticket = "synthetic-ticket-refused",
            session = "synthetic-session-refused",
        )
        assertFalse(inPlaceClient.shouldOverrideUrlLoading(webView, request(target)))
        inPlaceClient.onPageStarted(webView, target, null)

        inPlaceClient.onReceivedError(
            webView,
            request(target),
            TestWebResourceError(
                WebViewClient.ERROR_CONNECT,
                "net::ERR_CONNECTION_REFUSED",
            ),
        )

        assertEquals(0, retryCount)
        assertTrue(callbacks.events.contains("error:NETWORK_ERROR"))
    }

    @Test
    fun carneJovenTimeoutAfterClientCertChallengeNeverAutoRetries() {
        val profileId = ProfileId("carne-joven-andalucia")
        val confirmed = confirmedCarneJovenTarget(70L)
        val callbacks = RecordingBrowserCallbacks()
        var retryCount = 0
        var challengeCount = 0
        val inPlaceClient = JuntaWebViewClient(
            callbacks = callbacks,
            logger = logger,
            navigationPolicy = JuntaNavigationPolicy(profileId, BuiltInSiteProfiles.qaRegistry),
            clientAuthAuthorizer = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry),
            activeProfileId = { profileId },
            currentNavigationEpoch = { 70L },
            onPreconfirmedClientAuthNetworkTimeout = {
                retryCount++
                true
            },
            onInPlaceClientAuthChallenge = { _, _ -> challengeCount++ },
        )
        assertTrue(inPlaceClient.armConfirmedInPlaceClientAuth(confirmed, 70L))
        inPlaceClient.shouldInterceptRequest(webView, request(CARNE_JOVEN_SOURCE))
        inPlaceClient.onPageStarted(webView, CARNE_JOVEN_SOURCE, null)
        val target = carneJovenTargetUrl(
            ticket = "synthetic-ticket-post-cert",
            session = "synthetic-session-post-cert",
        )
        assertFalse(inPlaceClient.shouldOverrideUrlLoading(webView, request(target)))
        inPlaceClient.onPageStarted(webView, target, null)
        inPlaceClient.onReceivedClientCertRequest(webView, RecordingClientCertRequest())
        assertEquals(1, challengeCount)

        inPlaceClient.onReceivedError(
            webView,
            request(target),
            TestWebResourceError(WebViewClient.ERROR_TIMEOUT, "synthetic post-cert timeout"),
        )

        assertEquals(0, retryCount)
        assertTrue(callbacks.events.contains("error:NETWORK_ERROR"))
    }

    @Test
    fun carneJovenNonTimeoutNetworkFailureDoesNotRetryAndClearsPendingGrant() {
        val profileId = ProfileId("carne-joven-andalucia")
        val confirmed = confirmedCarneJovenTarget(70L)
        val callbacks = RecordingBrowserCallbacks()
        var retryCount = 0
        val inPlaceClient = JuntaWebViewClient(
            callbacks = callbacks,
            logger = logger,
            navigationPolicy = JuntaNavigationPolicy(profileId, BuiltInSiteProfiles.qaRegistry),
            clientAuthAuthorizer = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry),
            activeProfileId = { profileId },
            currentNavigationEpoch = { 70L },
            onPreconfirmedClientAuthNetworkTimeout = {
                retryCount++
                true
            },
        )
        assertTrue(inPlaceClient.armConfirmedInPlaceClientAuth(confirmed, 70L))
        inPlaceClient.shouldInterceptRequest(webView, request(CARNE_JOVEN_SOURCE))
        inPlaceClient.onPageStarted(webView, CARNE_JOVEN_SOURCE, null)
        val target = carneJovenTargetUrl(
            ticket = "synthetic-ticket-connect-error",
            session = "synthetic-session-connect-error",
        )
        assertFalse(inPlaceClient.shouldOverrideUrlLoading(webView, request(target)))
        inPlaceClient.onPageStarted(webView, target, null)

        inPlaceClient.onReceivedError(
            webView,
            request(target),
            TestWebResourceError(WebViewClient.ERROR_CONNECT, "synthetic connect failure"),
        )

        assertEquals(0, retryCount)
        assertTrue(callbacks.events.contains("error:NETWORK_ERROR"))
        val lateRequest = RecordingClientCertRequest()
        inPlaceClient.onReceivedClientCertRequest(webView, lateRequest)
        assertEquals(1, lateRequest.ignores)
    }

    @Test
    fun carneJovenRetryControllerIsExactBoundedAndResettable() {
        val authorized = confirmedCarneJovenTarget(70L)
        val controller = CarneJovenPreTlsRetryController(maxRetries = 2)

        assertEquals(java.net.URI(CARNE_JOVEN_SOURCE), controller.nextSource(authorized))
        assertEquals(java.net.URI(CARNE_JOVEN_SOURCE), controller.nextSource(authorized))
        assertNull(controller.nextSource(authorized))

        controller.reset()
        assertEquals(java.net.URI(CARNE_JOVEN_SOURCE), controller.nextSource(authorized))
        controller.reset()
        assertNull(
            controller.nextSource(
                authorized.copy(profileId = ProfileId("junta-andalucia-vea-peg")),
            ),
        )
        assertNull(
            controller.nextSource(
                authorized.copy(
                    target = java.net.URI(
                        "https://ws235.juntadeandalucia.es/not-authenticationFacade",
                    ),
                ),
            ),
        )
    }

    @Test
    fun veaPreconfirmedSourceAlsoRebindsWhenPageStartedPrecedesIntercept() {
        val profileId = ProfileId("junta-andalucia-vea-peg")
        val source = veaSourceUrl()
        val confirmed = confirmedVeaTarget(50L)
        var epoch = 50L
        var challengeCount = 0
        val inPlaceClient = JuntaWebViewClient(
            callbacks = EpochAdvancingBrowserCallbacks { epoch++ },
            logger = logger,
            navigationPolicy = JuntaNavigationPolicy(profileId, BuiltInSiteProfiles.qaRegistry),
            clientAuthAuthorizer = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry),
            activeProfileId = { profileId },
            currentNavigationEpoch = { epoch },
            onInPlaceClientAuthChallenge = { _, _ -> challengeCount++ },
        )
        assertTrue(inPlaceClient.armConfirmedInPlaceClientAuth(confirmed, epoch))

        // WebView callback order is not a security assumption: the reverse order
        // must reach the same exact source/epoch-bound state.
        inPlaceClient.onPageStarted(webView, source, null)
        inPlaceClient.shouldInterceptRequest(webView, request(source))
        val refreshedTarget = veaTargetUrl()
            .replace("ticketId=synthetic-ticket", "ticketId=synthetic-ticket-3")
            .replace("webSessionId=synthetic-session", "webSessionId=synthetic-session-3")
        assertFalse(inPlaceClient.shouldOverrideUrlLoading(webView, request(refreshedTarget)))
        inPlaceClient.onPageStarted(webView, refreshedTarget, null)
        val request = RecordingClientCertRequest()
        inPlaceClient.onReceivedClientCertRequest(webView, request)

        assertEquals(52L, epoch)
        assertEquals(1, challengeCount)
        assertEquals(0, request.ignores)
    }

    @Test
    fun veaPreconfirmedTargetFailsClosedAfterUnrelatedEpochChange() {
        val profileId = ProfileId("junta-andalucia-vea-peg")
        val source = veaSourceUrl()
        val confirmed = confirmedVeaTarget(60L)
        var epoch = 60L
        var challengeCount = 0
        val inPlaceClient = JuntaWebViewClient(
            callbacks = RecordingBrowserCallbacks(),
            logger = logger,
            navigationPolicy = JuntaNavigationPolicy(profileId, BuiltInSiteProfiles.qaRegistry),
            clientAuthAuthorizer = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry),
            activeProfileId = { profileId },
            currentNavigationEpoch = { epoch },
            onInPlaceClientAuthChallenge = { _, _ -> challengeCount++ },
        )
        assertTrue(inPlaceClient.armConfirmedInPlaceClientAuth(confirmed, epoch))
        inPlaceClient.shouldInterceptRequest(webView, request(source))

        epoch++ // An unrelated navigation invalidates the preconfirmed source epoch.
        inPlaceClient.shouldOverrideUrlLoading(webView, request(veaTargetUrl()))
        val request = RecordingClientCertRequest()
        inPlaceClient.onReceivedClientCertRequest(webView, request)

        assertEquals(0, challengeCount)
        assertEquals(1, request.ignores)
    }

    @Test
    fun veaObservedReturnLoginShapeRequiresLiveConfirmedClientAuthFlow() {
        val profileId = ProfileId("junta-andalucia-vea-peg")
        val returnUrl = "$VEA_API_RETURN?appId=CHIE.VEA&resCode=synthetic-result" +
            "&ticketId=synthetic-ticket&webSessionId=synthetic-session"
        val staticClient = JuntaWebViewClient(
            callbacks = RecordingBrowserCallbacks(),
            logger = logger,
            navigationPolicy = JuntaNavigationPolicy(profileId, BuiltInSiteProfiles.qaRegistry),
            activeProfileId = { profileId },
        )
        assertTrue(staticClient.shouldOverrideUrlLoading(webView, request(returnUrl)))

        val liveClient = JuntaWebViewClient(
            callbacks = RecordingBrowserCallbacks(),
            logger = logger,
            navigationPolicy = JuntaNavigationPolicy(profileId, BuiltInSiteProfiles.qaRegistry),
            activeProfileId = { profileId },
            isConfirmedClientAuthReturnUrl = { it == returnUrl },
        )
        assertFalse(liveClient.shouldOverrideUrlLoading(webView, request(returnUrl)))
    }

    @Test
    fun veaObservedClusterContinuationRequiresLiveSessionBindingAndArmsOnlyExactChallenge() {
        val profileId = ProfileId("junta-andalucia-vea-peg")
        val confirmed = confirmedVeaTarget(70L)
        val exactContinuation = confirmed.copy(target = java.net.URI(veaClusterContinuationUrl()))
        val staticClient = JuntaWebViewClient(
            callbacks = RecordingBrowserCallbacks(),
            logger = logger,
            navigationPolicy = JuntaNavigationPolicy(profileId, BuiltInSiteProfiles.qaRegistry),
            activeProfileId = { profileId },
            currentNavigationEpoch = { 70L },
        )
        assertTrue(staticClient.shouldOverrideUrlLoading(webView, request(veaClusterContinuationUrl())))

        var challenge: AuthorizedClientAuthTarget? = null
        val liveClient = JuntaWebViewClient(
            callbacks = RecordingBrowserCallbacks(),
            logger = logger,
            navigationPolicy = JuntaNavigationPolicy(profileId, BuiltInSiteProfiles.qaRegistry),
            activeProfileId = { profileId },
            currentNavigationEpoch = { 70L },
            resolveConfirmedClientAuthContinuationUrl = { raw ->
                exactContinuation.takeIf { raw == veaClusterContinuationUrl() }
            },
            onInPlaceClientAuthChallenge = { authorized, _ -> challenge = authorized },
        )
        assertFalse(liveClient.shouldOverrideUrlLoading(webView, request(veaClusterContinuationUrl())))
        liveClient.onPageStarted(webView, veaClusterContinuationUrl(), null)
        val clientCert = RecordingClientCertRequest(requestHost = "ws235-4.juntadeandalucia.es")
        liveClient.onReceivedClientCertRequest(webView, clientCert)
        assertEquals(exactContinuation, challenge)
        assertEquals(0, clientCert.ignores)

        val nearMiss = veaClusterContinuationUrl()
            .replace("ticketId=synthetic-ticket", "ticketId=other-ticket")
        assertTrue(liveClient.shouldOverrideUrlLoading(webView, request(nearMiss)))
    }

    @Test
    fun veaClientCertChallengeIsNotArmedByDirectOrNearMissNavigation() {
        val profileId = ProfileId("junta-andalucia-vea-peg")
        val authorizer = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry)
        var currentUrl = VEA_START
        val veaClient = JuntaWebViewClient(
            callbacks = RecordingBrowserCallbacks(),
            logger = logger,
            navigationPolicy = JuntaNavigationPolicy(profileId, BuiltInSiteProfiles.qaRegistry),
            currentPageUrl = { currentUrl },
            clientAuthAuthorizer = authorizer,
            activeProfileId = { profileId },
            currentNavigationEpoch = { 30L },
        )
        val directTarget = veaTargetUrl()

        assertTrue(veaClient.shouldOverrideUrlLoading(webView, request(directTarget)))
        veaClient.shouldInterceptRequest(webView, request(directTarget))
        val request = RecordingClientCertRequest()
        veaClient.onReceivedClientCertRequest(webView, request)
        assertEquals(1, request.ignores)

        currentUrl = veaSourceUrl().replace("modoAcceso=afirma", "modoAcceso=clave")
        veaClient.shouldInterceptRequest(webView, request(directTarget))
        val nearMissRequest = RecordingClientCertRequest()
        veaClient.onReceivedClientCertRequest(webView, nearMissRequest)
        assertEquals(1, nearMissRequest.ignores)
    }

    private fun confirmedCarneJovenTarget(epoch: Long): AuthorizedClientAuthTarget {
        val profileId = ProfileId("carne-joven-andalucia")
        val authorizer = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry)
        authorizer.observeTopLevelNavigation(
            profileId,
            CARNE_JOVEN_INDEX,
            CARNE_JOVEN_SOURCE,
            epoch - 1,
            true,
        )
        authorizer.onTopLevelPageStarted(CARNE_JOVEN_SOURCE, epoch)
        return checkNotNull(
            authorizer.observeTopLevelNavigation(
                profileId,
                CARNE_JOVEN_SOURCE,
                carneJovenTargetUrl(),
                epoch,
                true,
            ),
        ).refreshedAfterUserConfirmation()
    }

    private fun carneJovenTargetUrl(
        ticket: String = "synthetic-ticket",
        session: String = "synthetic-session",
    ): String =
        "https://ws235.juntadeandalucia.es/authenticationFacade" +
            "?action=validateCert&appId=IAJ.CARNETJOVEN" +
            "&ticketId=$ticket&webSessionId=$session" +
            "&comeBackURL=aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ%3D"

    private fun confirmedVeaTarget(epoch: Long): AuthorizedClientAuthTarget {
        val profileId = ProfileId("junta-andalucia-vea-peg")
        var captured: AuthorizedClientAuthTarget? = null
        val captureClient = JuntaWebViewClient(
            callbacks = RecordingBrowserCallbacks(),
            logger = logger,
            navigationPolicy = JuntaNavigationPolicy(profileId, BuiltInSiteProfiles.qaRegistry),
            currentPageUrl = { VEA_AUTH_FACADE },
            clientAuthAuthorizer = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry),
            activeProfileId = { profileId },
            currentNavigationEpoch = { epoch },
            onClientAuthTarget = { captured = it },
        )
        assertFalse(captureClient.shouldOverrideUrlLoading(webView, request(veaSourceUrl())))
        assertTrue(captureClient.shouldOverrideUrlLoading(webView, request(veaTargetUrl())))
        return checkNotNull(captured).refreshedAfterUserConfirmation()
    }

    private fun veaSourceUrl(): String {
        val redirect = "$VEA_START?iniciarSolicitud=true&procedureId=123&versionId=456"
        return "$VEA_API_LOGIN?modoAcceso=afirma" +
            "&comeBackUrl=${encode(base64(VEA_AUTH_FACADE))}" +
            "&redirectUrl=${encode(base64(redirect))}" +
            "&codigoProcedimiento=PEG_VEA"
    }

    private fun veaTargetUrl(): String =
        "https://ws235.juntadeandalucia.es/authenticationFacade" +
            "?action=validateCert&appId=CHIE.VEA" +
            "&comeBackURL=${encode(base64(VEA_API_RETURN))}" +
            "&ticketId=synthetic-ticket&webSessionId=synthetic-session"

    private fun veaClusterContinuationUrl(): String =
        "https://ws235-4.juntadeandalucia.es/authenticationFacade/5" +
            "?action=validateCert&appId=CHIE.VEA" +
            "&comeBackURL=${encode(base64(VEA_API_RETURN))}" +
            "&ticketId=synthetic-ticket&webSessionId=synthetic-session"

    private fun base64(value: String): String = Base64.getEncoder().encodeToString(value.toByteArray())
    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

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

    private class EpochAdvancingBrowserCallbacks(
        private val advanceEpoch: () -> Unit,
    ) : BrowserNavigationCallbacks {
        override fun openExternal(uri: Uri) = Unit
        override fun openOfficialAutoFirma(uri: Uri) = Unit
        override fun onAfirmaRequest(request: AfirmaRequest) = Unit
        override fun onNavigationBlocked(reason: NavigationBlockReason) = Unit
        override fun onBrowserError(error: BrowserErrorCode) = Unit
        override fun onTopLevelNavigationStarted(url: String) = advanceEpoch()
    }

    private class RecordingBrowserCallbacks : BrowserNavigationCallbacks {
        val events = mutableListOf<String>()
        var rendererView: WebView? = null

        override fun openExternal(uri: Uri) {
            events += "external:${uri.host}"
        }

        override fun openOfficialAutoFirma(uri: Uri) {
            events += "official-autofirma:${uri.host}"
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

    private class RecordingClientCertRequest(
        private val requestHost: String = "ws235.juntadeandalucia.es",
        private val requestPort: Int = 443,
        private val requestKeyTypes: Array<String> = arrayOf("RSA"),
    ) : ClientCertRequest() {
        var ignores = 0
        var proceeds = 0
        var cancels = 0
        override fun getHost(): String = requestHost
        override fun getPort(): Int = requestPort
        override fun getKeyTypes(): Array<String> = requestKeyTypes
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
        const val CARNE_JOVEN_INDEX =
            "https://ws104.juntadeandalucia.es/carneJoven/cjservlet/portal/index.jsp"
        const val CARNE_JOVEN_SOURCE =
            "https://ws104.juntadeandalucia.es/carneJoven/servlet/CallAuthenticationServlet"
        const val VEA_ORIGIN = "https://veaja.cloud.juntadeandalucia.es"
        const val VEA_START = "$VEA_ORIGIN/inicio/procedimiento-detalle/PEG_VEA"
        const val VEA_AUTH_FACADE = "$VEA_ORIGIN/authFacade"
        const val VEA_API_LOGIN = "https://api-veaja.cloud.juntadeandalucia.es/auth/login"
        const val VEA_API_RETURN = "https://api-veaja.cloud.juntadeandalucia.es/auth/returnLogin"
        const val MALAGA_CLAVE_SERVICE_REDIRECT =
            "https://pasarela.clave.gob.es/Proxy2/ServiceRedirect"
        const val MALAGA_CLAVE_CERT_TARGET =
            "https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen"
        const val TARRAGONA_VALID_SOURCE =
            "https://valid.aoc.cat/o/oauth2/auth?response_type=code&client_id=valid.dipta.cat&" +
                "redirect_uri=https%3A%2F%2Fegovern.altanet.org%2Fvalid%2Fcode&" +
                "scope=autenticacio_usuari&state=synthetic-state&access_type=online&approval_prompt=auto"
        const val SEGURIDAD_SOCIAL_RETURN_PAGE =
            "https://sede.seg-social.gob.es/wps/myportal/sede/!ut/p/z1/portal-state/" +
                "?A=&N3=&idApp=826" +
                "&idContenido=a061f401-c3ed-426e-9428-82bd9198c223" +
                "&idPagina=com.ss.sede.RegistroElectronicoDeApoderamiento"
        const val TRUSTED_PAGE = "https://www.juntadeandalucia.es/portal"
        const val OFVIRTUAL_PAGE = "https://ws072.juntadeandalucia.es/ofvirtual/ovMisTramites/index"
    }
}

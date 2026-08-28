package dev.junta.firmamobile.browser

import dev.junta.firmamobile.afirma.AfirmaOperation
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import org.junit.Assert.assertEquals
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
class JuntaNavigationPolicyTest {
    private val policy = JuntaNavigationPolicy(ProfileId("junta-andalucia"))
    private val trustedPage =
        "https://www.juntadeandalucia.es/empleoformacionytrabajoautonomo/ovorion/"

    @Test
    fun keepsOnlySelectedProfileHttpsNavigationInsideWebView() {
        val decision = policy.decide(
            "https://ssoweb.juntadeandalucia.es/login?continue=1",
            trustedPage,
        )

        assertEquals(NavigationDecision.AllowInWebView, decision)
    }

    @Test
    fun blocksCrossProfileNavigationInsteadOfRebindingTheWebView() {
        val decision = policy.decide("https://reg.redsara.es/es/", trustedPage)

        assertEquals(
            NavigationBlockReason.CROSS_PROFILE_NAVIGATION,
            (decision as NavigationDecision.Block).reason,
        )
    }

    @Test
    fun blocksThirdPartyHttpsInsideTheAppAndBlocksHttpDowngrades() {
        val https = policy.decide("https://example.org/help", trustedPage)
            as NavigationDecision.Block
        assertEquals(NavigationBlockReason.UNTRUSTED_EXTERNAL_NAVIGATION, https.reason)

        val http = policy.decide("http://example.org/help", trustedPage)
            as NavigationDecision.Block
        assertEquals(NavigationBlockReason.INSECURE_HTTP, http.reason)
    }

    @Test
    fun upgradesOnlyExactOfvirtualLegacyHttpDowngradeToHttps() {
        val ofvirtualPolicy = JuntaNavigationPolicy(ProfileId("junta-ofvirtual"))
        val current = "https://ws072.juntadeandalucia.es/ofvirtual/auth/signInAutcertjs"
        val target = "http://ws072.juntadeandalucia.es/ofvirtual/auth/legacyReturn?state=ok#done"

        val decision = ofvirtualPolicy.decide(target, current) as NavigationDecision.UpgradeToHttps

        assertEquals(
            "https://ws072.juntadeandalucia.es/ofvirtual/auth/legacyReturn?state=ok#done",
            decision.uri.toString(),
        )
    }

    @Test
    fun rejectsEveryOtherHttpNavigationIncludingNearMisses() {
        val ofvirtualPolicy = JuntaNavigationPolicy(ProfileId("junta-ofvirtual"))
        val current = "https://ws072.juntadeandalucia.es/ofvirtual/auth/signInAutcertjs"
        val blockedTargets = listOf(
            "http://ws072.juntadeandalucia.es/outside/legacyReturn",
            "http://user@ws072.juntadeandalucia.es/ofvirtual/auth/legacyReturn",
            "http://ws072.juntadeandalucia.es:8080/ofvirtual/auth/legacyReturn",
            "http://ws072.juntadeandalucia.es.evil.example/ofvirtual/auth/legacyReturn",
            "http://example.org/ofvirtual/auth/legacyReturn",
        )

        blockedTargets.forEach { target ->
            val decision = ofvirtualPolicy.decide(target, current) as NavigationDecision.Block
            assertEquals(target, NavigationBlockReason.INSECURE_HTTP, decision.reason)
        }
        val untrustedCurrent = ofvirtualPolicy.decide(
            "http://ws072.juntadeandalucia.es/ofvirtual/auth/legacyReturn",
            "https://evil.example/ofvirtual/auth/signInAutcertjs",
        ) as NavigationDecision.Block
        assertEquals(NavigationBlockReason.INSECURE_HTTP, untrustedCurrent.reason)
    }

    @Test
    fun interceptsAfirmaOnlyFromTheSelectedProfilesSigningOrigin() {
        val raw = "afirma://sign?algorithm=SHA256withRSA&format=CAdES&dat=abc"
        val accepted = policy.decide(raw, trustedPage) as NavigationDecision.HandleAfirma
        assertEquals(AfirmaOperation.SIGN, accepted.request.operation)

        listOf(
            "https://evil.example/",
            "https://reg.redsara.es/es/",
            "https://sede.juntadeandalucia.es/path",
        ).forEach { page ->
            val blocked = policy.decide(raw, page) as NavigationDecision.Block
            assertEquals(NavigationBlockReason.UNTRUSTED_AFIRMA_ORIGIN, blocked.reason)
        }
    }

    @Test
    fun extractsEmbeddedAfirmaIntentBeforeConsideringExternalPackages() {
        val decision = policy.decide(
            "intent://sign?algorithm=SHA256withRSA&format=CAdES&dat=abc" +
                "#Intent;scheme=afirma;package=es.gob.afirma;end",
            trustedPage,
        ) as NavigationDecision.HandleAfirma

        assertEquals(AfirmaOperation.SIGN, decision.request.operation)
    }

    @Test
    fun blocksAutoFirmaMarketAndGooglePlayFallbacks() {
        val urls = listOf(
            "market://details?id=es.gob.afirma",
            "https://play.google.com/store/apps/details?id=es.gob.afirma",
            "intent://details?id=es.gob.afirma" +
                "#Intent;scheme=market;package=com.android.vending;end",
            "intent://scan/#Intent;scheme=zxing;" +
                "S.browser_fallback_url=https%3A%2F%2Fplay.google.com%2Fstore%2Fapps%2Fdetails%3Fid%3Des.gob.afirma;end",
        )

        urls.forEach { url ->
            val decision = policy.decide(url, trustedPage) as NavigationDecision.Block
            assertEquals(url, NavigationBlockReason.PLAY_STORE_FALLBACK, decision.reason)
        }
    }

    @Test
    fun failsClosedForUnknownSchemesAndUnvalidatedIntentTargets() {
        val unknown = policy.decide("javascript:alert(1)", trustedPage)
        assertEquals(
            NavigationBlockReason.UNSUPPORTED_SCHEME,
            (unknown as NavigationDecision.Block).reason,
        )

        val intent = policy.decide(
            "intent://pay#Intent;scheme=custom-pay;package=com.example.pay;end",
            trustedPage,
        )
        assertTrue(intent is NavigationDecision.Block)
        assertEquals(
            NavigationBlockReason.UNSUPPORTED_EXTERNAL_INTENT,
            (intent as NavigationDecision.Block).reason,
        )
    }

    @Test
    fun blocksClientAuthRequestOriginFailClosedInsteadOfOpeningExternalBrowser() {
        val carneJovenPolicy = JuntaNavigationPolicy(ProfileId("carne-joven-andalucia"))
        val carneJovenPage = "https://ws104.juntadeandalucia.es/carneJoven/cjservlet/portal/index.jsp"
        val ws235Target = "https://ws235.juntadeandalucia.es/authenticationFacade?action=validateCert&appId=IAJ.CARNETJOVEN&ticketId=123&webSessionId=456&comeBackURL=aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ%3D"

        val decision = carneJovenPolicy.decide(ws235Target, carneJovenPage)
        assertTrue(decision is NavigationDecision.Block)
        assertEquals(
            NavigationBlockReason.CROSS_PROFILE_NAVIGATION,
            (decision as NavigationDecision.Block).reason,
        )
    }

    @Test
    fun euskadiClientAuthTargetIsNeverNormalBrowserNavigation() {
        val euskadiPolicy = JuntaNavigationPolicy(ProfileId("euskadi-sede-electronica"))
        val source = "https://eidas.izenpe.com/trustedx-authserver/izenpe/authentication"
        val target = "https://eidas2.izenpe.com/cert-authn-external-validation/authenticate"

        val decision = euskadiPolicy.decide(target, source) as NavigationDecision.Block
        assertEquals(NavigationBlockReason.CROSS_PROFILE_NAVIGATION, decision.reason)
    }

    @Test
    fun veaCertificateAuthNavigationStaysInWebViewOnlyForExactBoundedContract() {
        val vea = JuntaNavigationPolicy(
            ProfileId("junta-andalucia-vea-peg"),
            BuiltInSiteProfiles.qaRegistry,
        )
        val source = veaSource()
        val target = veaTarget()

        assertEquals(
            NavigationDecision.AllowInWebView,
            vea.decide(source, VEA_AUTH_FACADE),
        )
        assertEquals(
            NavigationDecision.AllowInWebView,
            vea.decide(target, source),
        )
        assertEquals(
            NavigationDecision.AllowInWebView,
            vea.decide("$VEA_API_RETURN?resCode=1", target),
        )
        assertEquals(
            NavigationDecision.AllowInWebView,
            vea.decide(VEA_API_END, "$VEA_API_RETURN?resCode=1"),
        )

        val directTarget = vea.decide(target, VEA_START) as NavigationDecision.Block
        assertEquals(NavigationBlockReason.CROSS_PROFILE_NAVIGATION, directTarget.reason)
    }

    @Test
    fun veaCertificateAuthNavigationRejectsApiAndRedirectNearMisses() {
        val vea = JuntaNavigationPolicy(
            ProfileId("junta-andalucia-vea-peg"),
            BuiltInSiteProfiles.qaRegistry,
        )
        val invalidSources = listOf(
            veaSource(redirect = "https://evil.example/?iniciarSolicitud=true&procedureId=1&versionId=2"),
            veaSource(redirect = "$VEA_START?iniciarSolicitud=false&procedureId=1&versionId=2"),
            veaSource(redirect = "$VEA_START?iniciarSolicitud=true&procedureId=1&versionId=2&extra=1"),
            veaSource().replace("modoAcceso=afirma", "modoAcceso=clave"),
        )
        invalidSources.forEach { source ->
            assertTrue(source, vea.decide(source, VEA_AUTH_FACADE) !is NavigationDecision.AllowInWebView)
        }
        listOf(
            "$VEA_API_RETURN?resCode=1&extra=1",
            "$VEA_API_END?extra=1",
            "https://api-veaja.cloud.juntadeandalucia.es/auth/other",
        ).forEach { target ->
            assertTrue(target, vea.decide(target, VEA_AUTH_FACADE) !is NavigationDecision.AllowInWebView)
        }
        listOf(
            veaTarget(appId = "IAJ.CARNETJOVEN"),
            veaTarget(callback = "https://evil.example/return"),
            veaTarget() + "&extra=1",
        ).forEach { target ->
            val blocked = vea.decide(target, veaSource()) as NavigationDecision.Block
            assertEquals(target, NavigationBlockReason.CROSS_PROFILE_NAVIGATION, blocked.reason)
        }
    }

    private fun veaSource(
        redirect: String = "$VEA_START?iniciarSolicitud=true&procedureId=123&versionId=456",
    ): String = "$VEA_API_LOGIN?modoAcceso=afirma" +
        "&comeBackUrl=${urlEncode(base64(VEA_AUTH_FACADE))}" +
        "&redirectUrl=${urlEncode(base64(redirect))}" +
        "&codigoProcedimiento=PEG_VEA"

    private fun veaTarget(
        appId: String = "CHIE.VEA",
        callback: String = VEA_API_RETURN,
    ): String = "https://ws235.juntadeandalucia.es/authenticationFacade" +
        "?action=validateCert&appId=$appId" +
        "&comeBackURL=${urlEncode(base64(callback))}" +
        "&ticketId=synthetic-ticket&webSessionId=synthetic-session"

    private fun base64(value: String): String = Base64.getEncoder().encodeToString(value.toByteArray())
    private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    @Test
    fun exactOriginMatcherRequiresHttpsSchemeCanonicalHostAndEffectivePort() {
        val carneJovenPolicy = JuntaNavigationPolicy(ProfileId("carne-joven-andalucia"))
        val carneJovenPage = "https://ws104.juntadeandalucia.es/carneJoven/cjservlet/portal/index.jsp"

        val uppercaseTarget = "https://WS235.JUNTADEANDALUCIA.ES:443/authenticationFacade?action=test"
        val uppercaseDecision = carneJovenPolicy.decide(uppercaseTarget, carneJovenPage)
        assertTrue(uppercaseDecision is NavigationDecision.Block)
        assertEquals(
            NavigationBlockReason.CROSS_PROFILE_NAVIGATION,
            (uppercaseDecision as NavigationDecision.Block).reason,
        )

        val httpTarget = "http://ws235.juntadeandalucia.es/authenticationFacade"
        val httpDecision = carneJovenPolicy.decide(httpTarget, carneJovenPage)
        assertTrue(httpDecision is NavigationDecision.Block)
        assertEquals(
            NavigationBlockReason.INSECURE_HTTP,
            (httpDecision as NavigationDecision.Block).reason,
        )

        val portTarget = "https://ws235.juntadeandalucia.es:8443/authenticationFacade"
        val portDecision = carneJovenPolicy.decide(portTarget, carneJovenPage)
        assertTrue(portDecision is NavigationDecision.Block)
        assertEquals(
            NavigationBlockReason.INVALID_URL,
            (portDecision as NavigationDecision.Block).reason,
        )

        val userInfoTarget = "https://user:pass@ws235.juntadeandalucia.es/authenticationFacade"
        val userInfoDecision = carneJovenPolicy.decide(userInfoTarget, carneJovenPage)
        assertTrue(userInfoDecision is NavigationDecision.Block)
        assertEquals(
            NavigationBlockReason.INVALID_URL,
            (userInfoDecision as NavigationDecision.Block).reason,
        )
    }
    private companion object {
        const val VEA_ORIGIN = "https://veaja.cloud.juntadeandalucia.es"
        const val VEA_START = "$VEA_ORIGIN/inicio/procedimiento-detalle/PEG_VEA"
        const val VEA_AUTH_FACADE = "$VEA_ORIGIN/authFacade"
        const val VEA_API_LOGIN = "https://api-veaja.cloud.juntadeandalucia.es/auth/login"
        const val VEA_API_RETURN = "https://api-veaja.cloud.juntadeandalucia.es/auth/returnLogin"
        const val VEA_API_END = "https://api-veaja.cloud.juntadeandalucia.es/auth/endLogin"
    }

}

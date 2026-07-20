package dev.junta.firmamobile.browser

import dev.junta.firmamobile.afirma.AfirmaOperation
import dev.junta.firmamobile.profile.ProfileId
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
    fun routesThirdPartyHttpsExternallyButBlocksHttpDowngrades() {
        val https = policy.decide("https://example.org/help", trustedPage)
            as NavigationDecision.OpenExternal
        assertEquals("https://example.org/help", https.uri.toString())

        val http = policy.decide("http://example.org/help", trustedPage)
            as NavigationDecision.Block
        assertEquals(NavigationBlockReason.INSECURE_HTTP, http.reason)
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
}

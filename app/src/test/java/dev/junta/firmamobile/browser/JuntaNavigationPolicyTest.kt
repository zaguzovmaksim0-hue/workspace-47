package dev.junta.firmamobile.browser

import dev.junta.firmamobile.afirma.AfirmaOperation
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
    private val policy = JuntaNavigationPolicy()
    private val trustedPage =
        "https://www.juntadeandalucia.es/empleoformacionytrabajoautonomo/ovorion/"

    @Test
    fun keepsAllowedHttpsNavigationInsideWebView() {
        val decision = policy.decide(
            "https://ssoweb.juntadeandalucia.es/login?continue=1",
            trustedPage,
        )

        assertEquals(NavigationDecision.AllowInWebView, decision)
    }

    @Test
    fun routesThirdPartyHttpAndHttpsToExternalBrowser() {
        listOf("https://example.org/help", "http://example.org/help").forEach { url ->
            val decision = policy.decide(url, trustedPage) as NavigationDecision.OpenExternal
            assertEquals(url, decision.uri.toString())
        }
    }

    @Test
    fun interceptsAfirmaOnlyFromATrustedCurrentPage() {
        val raw = "afirma://sign?algorithm=SHA256withRSA&format=CAdES&dat=abc"
        val accepted = policy.decide(raw, trustedPage) as NavigationDecision.HandleAfirma
        assertEquals(AfirmaOperation.SIGN, accepted.request.operation)

        val blocked = policy.decide(raw, "https://evil.example/") as NavigationDecision.Block
        assertEquals(NavigationBlockReason.UNTRUSTED_AFIRMA_ORIGIN, blocked.reason)
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

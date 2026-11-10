package dev.junta.firmamobile.browser

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
// URL admission uses no TLS connection or provider.
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class UniversalAfirmaNavigationPolicyTest {
    private val policy = JuntaNavigationPolicy(null)
    private val page = "https://portal.synthetic.example/form"

    @Test
    fun publicHttpsAfirmaSignCannotGrantProfileSigningAuthority() {
        val raw = "afirma://sign?algorithm=SHA256withRSA&format=CAdES&dat=c3ludGhldGlj"
        val decision = policy.decide(raw, page)
        assertEquals(NavigationDecision.Block(NavigationBlockReason.UNSUPPORTED_SCHEME), decision)
    }

    @Test
    fun publicHttpsAutoFirmaIntentCannotGrantProfileSigningAuthority() {
        val raw = "intent://sign?algorithm=SHA256withRSA&format=PAdEStri&dat=c3ludGhldGlj#Intent;scheme=afirma;package=es.gob.afirma;end"
        val decision = policy.decide(raw, page)
        assertEquals(NavigationDecision.Block(NavigationBlockReason.UNSUPPORTED_SCHEME), decision)
    }

    @Test
    fun crossOriginCallbackCannotEscapeTheVisiblePageOrigin() {
        val raw = "afirma://sign?algorithm=SHA256withRSA&format=CAdES&dat=c3ludGhldGlj&rtservlet=https%3A%2F%2Fattacker.synthetic.example%2Fcallback"
        val decision = policy.decide(raw, page)
        assertEquals(NavigationDecision.Block(NavigationBlockReason.INVALID_AFIRMA_URI), decision)
    }

    @Test
    fun websocketLegacyTransportIsRejected() {
        val decision = policy.decide("afirma://websocket?channel=test", page)
        assertEquals(NavigationDecision.Block(NavigationBlockReason.INVALID_AFIRMA_URI), decision)
    }

    @Test
    fun injectedIntentComponentIsRejected() {
        val raw = "intent://sign?algorithm=SHA256withRSA&format=CAdES&dat=c3ludGhldGlj#Intent;scheme=afirma;package=es.gob.afirma;component=es.gob.afirma/.MainActivity;end"
        val decision = policy.decide(raw, page)
        assertEquals(NavigationDecision.Block(NavigationBlockReason.UNSUPPORTED_EXTERNAL_INTENT), decision)
    }

    @Test
    fun signWithoutRequiredProtocolParametersIsRejected() {
        val decision = policy.decide("afirma://sign?dat=c3ludGhldGlj", page)
        assertEquals(NavigationDecision.Block(NavigationBlockReason.INVALID_AFIRMA_URI), decision)
    }

    @Test
    fun implicitDefaultHttpsPortStillRequiresNativeBridge() {
        val raw = "afirma://sign?algorithm=SHA256withRSA&format=CAdES&dat=c3ludGhldGlj"
        val decision = policy.decide(raw, page)
        assertEquals(NavigationDecision.Block(NavigationBlockReason.UNSUPPORTED_SCHEME), decision)
    }

    @Test
    fun explicitDefaultHttpsPortStillRequiresNativeBridge() {
        val raw = "afirma://sign?algorithm=SHA256withRSA&format=CAdES&dat=c3ludGhldGlj"
        val decision = policy.decide(raw, "https://portal.synthetic.example:443/form")
        assertEquals(NavigationDecision.Block(NavigationBlockReason.UNSUPPORTED_SCHEME), decision)
    }

    @Test
    fun crossOriginPublicSignerEndpointStillRequiresNativeBridge() {
        val raw = "afirma://sign?algorithm=SHA256withRSA&format=CAdES&dat=c3ludGhldGlj" +
            "&rtservlet=https%3A%2F%2Fafirma.redsara.es%2Fafirma-signature-storage%2FStorageService"
        val decision = policy.decide(raw, page)
        assertEquals(NavigationDecision.Block(NavigationBlockReason.UNSUPPORTED_SCHEME), decision)
    }

    @Test
    fun privateNetworkSignerEndpointRemainsBlocked() {
        for (endpoint in listOf(
            "https://10.0.0.8/StorageService",
            "https://192.168.1.8/StorageService",
        )) {
            val raw = "afirma://sign?algorithm=SHA256withRSA&format=CAdES&dat=c3ludGhldGlj" +
                "&rtservlet=" + java.net.URLEncoder.encode(endpoint, "UTF-8")
            assertEquals(
                NavigationDecision.Block(NavigationBlockReason.INVALID_AFIRMA_URI),
                policy.decide(raw, page),
            )
        }
    }

    @Test
    fun localhostSignerEndpointRemainsBlocked() {
        val raw = "afirma://sign?algorithm=SHA256withRSA&format=CAdES&dat=c3ludGhldGlj" +
            "&rtservlet=https%3A%2F%2Flocalhost%2FStorageService"
        assertEquals(
            NavigationDecision.Block(NavigationBlockReason.INVALID_AFIRMA_URI),
            policy.decide(raw, page),
        )
    }

    @Test
    fun localAndPrivateSourcePagesCannotObtainUniversalAfirmaHandling() {
        val raw = "afirma://sign?algorithm=SHA256withRSA&format=CAdES&dat=c3ludGhldGlj"
        for (source in listOf(
            "https://localhost/form",
            "https://127.0.0.1/form",
            "https://192.168.1.1/form",
            "https://10.0.0.8/form",
            "https://host.local/form",
            "https://printer/form",
        )) {
            assertEquals(
                "Accepted untrusted source: $source",
                NavigationDecision.Block(NavigationBlockReason.UNTRUSTED_AFIRMA_ORIGIN),
                policy.decide(raw, source),
            )
        }
    }

    @Test
    fun nonHttpsPageCannotObtainUniversalAfirmaHandling() {
        val decision = policy.decide(
            "afirma://sign?algorithm=SHA256withRSA&format=CAdES&dat=c3ludGhldGlj",
            "http://portal.synthetic.example/form",
        )
        assertEquals(NavigationDecision.Block(NavigationBlockReason.UNTRUSTED_AFIRMA_ORIGIN), decision)
    }
}

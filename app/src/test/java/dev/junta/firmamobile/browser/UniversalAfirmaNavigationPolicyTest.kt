package dev.junta.firmamobile.browser

import dev.junta.firmamobile.afirma.AfirmaOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class UniversalAfirmaNavigationPolicyTest {
    private val policy = JuntaNavigationPolicy(null)
    private val page = "https://portal.synthetic.example/form"

    @Test
    fun publicHttpsAfirmaSignIsHandledWithoutAProfile() {
        val raw = "afirma://sign?algorithm=SHA256withRSA&format=CAdES&dat=c3ludGhldGlj"
        val decision = policy.decide(raw, page)
        assertEquals(NavigationDecision.HandleAfirma::class, decision::class)
        assertEquals(AfirmaOperation.SIGN, (decision as NavigationDecision.HandleAfirma).request.operation)
    }

    @Test
    fun publicHttpsAutoFirmaIntentIsHandledWithoutAProfile() {
        val raw = "intent://sign?algorithm=SHA256withRSA&format=PAdEStri&dat=c3ludGhldGlj#Intent;scheme=afirma;package=es.gob.afirma;end"
        val decision = policy.decide(raw, page)
        assertTrue(decision is NavigationDecision.HandleAfirma)
        assertEquals(AfirmaOperation.SIGN, (decision as NavigationDecision.HandleAfirma).request.operation)
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
    fun implicitDefaultHttpsPortIsAcceptedForUniversalAfirma() {
        val raw = "afirma://sign?algorithm=SHA256withRSA&format=CAdES&dat=c3ludGhldGlj"
        val decision = policy.decide(raw, page)
        assertTrue(decision is NavigationDecision.HandleAfirma)
        val request = (decision as NavigationDecision.HandleAfirma).request
        assertEquals(raw, request.rawUri)
        assertEquals("portal.synthetic.example", request.origin.host)
        assertEquals(443, request.origin.port)
    }

    @Test
    fun explicitDefaultHttpsPortIsAcceptedAndRawUriIsPreserved() {
        val raw = "afirma://sign?algorithm=SHA256withRSA&format=CAdES&dat=c3ludGhldGlj"
        val decision = policy.decide(raw, "https://portal.synthetic.example:443/form")
        assertTrue(decision is NavigationDecision.HandleAfirma)
        val request = (decision as NavigationDecision.HandleAfirma).request
        assertEquals(raw, request.rawUri)
        assertEquals("portal.synthetic.example", request.origin.host)
        assertEquals(443, request.origin.port)
    }

    @Test
    fun crossOriginPublicSignerEndpointIsAcceptedWithoutHostCatalog() {
        val raw = "afirma://sign?algorithm=SHA256withRSA&format=CAdES&dat=c3ludGhldGlj" +
            "&rtservlet=https%3A%2F%2Fafirma.redsara.es%2Fafirma-signature-storage%2FStorageService"
        val decision = policy.decide(raw, page)
        assertTrue("Expected universal AutoFirma handling: $decision", decision is NavigationDecision.HandleAfirma)
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
    fun nonHttpsPageCannotObtainUniversalAfirmaHandling() {
        val decision = policy.decide(
            "afirma://sign?algorithm=SHA256withRSA&format=CAdES&dat=c3ludGhldGlj",
            "http://portal.synthetic.example/form",
        )
        assertEquals(NavigationDecision.Block(NavigationBlockReason.UNTRUSTED_AFIRMA_ORIGIN), decision)
    }
}

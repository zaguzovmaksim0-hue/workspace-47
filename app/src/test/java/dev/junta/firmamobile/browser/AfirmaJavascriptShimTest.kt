package dev.junta.firmamobile.browser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
class AfirmaJavascriptShimTest {
    @Test
    fun shimOnlyForwardsAfirmaOrIntentUrisThroughTheNamedWebMessageObject() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val script = AfirmaJavascriptShim.load(context)

        assertTrue(script.contains("window.JuntaFirmaMobile"))
        assertTrue(script.contains("bridge.postMessage"))
        assertTrue(script.contains("AFIRMA_URI"))
        assertTrue(script.contains("window.open"))
        assertTrue(script.contains("afirma:"))
        assertTrue(script.contains("intent:"))
        assertTrue(script.length <= AfirmaJavascriptShim.MAX_SCRIPT_CHARS)
    }

    @Test
    fun shimDoesNotExposeSecretsOrHardcodePortalCallbacks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val script = AfirmaJavascriptShim.load(context)

        assertFalse(script.contains("addJavascriptInterface"))
        assertFalse(script.contains("getPrivateKey"))
        assertFalse(script.contains("readFile"))
        assertFalse(script.contains("sendHttpRequest"))
        assertFalse(script.contains("saveSignatureAuthCallback"))
        assertFalse(script.contains("ws024"))
    }

    @Test
    fun shimObservesMiniAppletCallsAndBlocksLoopbackWebSocketsWithoutCallbacks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val script = AfirmaJavascriptShim.load(context)

        assertTrue(script.contains("window.JuntaFirmaProbe"))
        assertTrue(script.contains("MiniApplet"))
        assertTrue(script.contains("cargarMiniApplet"))
        assertTrue(script.contains("MINIAPPLET_OBSERVATION"))
        assertTrue(script.contains("RUNTIME_BRANCH_OBSERVATION"))
        assertTrue(script.contains("MINIAPPLET_CALL_END"))
        assertTrue(script.contains("probeDocumentId"))
        assertTrue(script.contains("documentId: probeDocumentId"))
        assertTrue(script.contains("activeProbeRequestId"))
        assertTrue(script.contains("requestId: activeProbeRequestId"))
        assertTrue(script.contains("tryObserveMiniAppletCall"))
        assertTrue(script.contains("finally"))
        assertTrue(script.contains("window.WebSocket"))
        assertTrue(script.contains("Reflect.apply"))
        assertTrue(script.contains("window.top !== window"))
        assertTrue(script.contains("btnacceso"))
        assertTrue(script.contains("signInAutcertjs"))
        assertFalse(script.contains("querySelector(\"input[type=button]"))
        assertFalse(script.contains("saveSignatureAuthCallback"))
        assertFalse(script.contains("showLogCallback"))
    }
}

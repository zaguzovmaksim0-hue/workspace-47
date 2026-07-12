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
}

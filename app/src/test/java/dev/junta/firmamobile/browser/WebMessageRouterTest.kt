package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.afirma.AfirmaOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
class WebMessageRouterTest {
    private val router = WebMessageRouter()

    @Test
    fun routesTrustedMainFrameAfirmaMessageUsingTheActualSourceOrigin() {
        val result = router.route(
            rawMessage = message(
                "afirma://sign?algorithm=SHA256withRSA&format=CAdES&dat=YWJj",
            ),
            sourceOrigin = TRUSTED_ORIGIN,
            isMainFrame = true,
        ) as WebMessageRouteResult.Accepted

        assertEquals(REQUEST_ID, result.requestId)
        assertEquals(AfirmaOperation.SIGN, result.request.operation)
        assertEquals("www.juntadeandalucia.es", result.request.origin.host)
    }

    @Test
    fun rejectsMessagesFromSubframesAndKeepsAValidIdForTheReply() {
        val result = router.route(
            rawMessage = message("afirma://selectcert"),
            sourceOrigin = TRUSTED_ORIGIN,
            isMainFrame = false,
        ) as WebMessageRouteResult.Rejected

        assertEquals(REQUEST_ID, result.requestId)
        assertEquals("NOT_MAIN_FRAME", result.errorCode)
    }

    @Test
    fun rejectsUntrustedOriginsWithoutReflectingAnUnvalidatedRequestId() {
        val result = router.route(
            rawMessage = message("afirma://selectcert"),
            sourceOrigin = Uri.parse("https://evil.example"),
            isMainFrame = true,
        ) as WebMessageRouteResult.Rejected

        assertNull(result.requestId)
        assertEquals("UNTRUSTED_ORIGIN", result.errorCode)
    }

    @Test
    fun blocksIntentPlayStoreFallbackInsteadOfLaunchingAutoFirma() {
        val result = router.route(
            rawMessage = message(
                "intent://details#Intent;scheme=market;package=es.gob.afirma;end",
            ),
            sourceOrigin = TRUSTED_ORIGIN,
            isMainFrame = true,
        ) as WebMessageRouteResult.Rejected

        assertEquals(REQUEST_ID, result.requestId)
        assertEquals("PLAY_STORE_FALLBACK", result.errorCode)
    }

    private fun message(uri: String): String =
        """{"type":"AFIRMA_URI","requestId":"$REQUEST_ID","uri":"$uri"}"""

    private companion object {
        const val REQUEST_ID = "123e4567-e89b-12d3-a456-426614174000"
        val TRUSTED_ORIGIN: Uri = Uri.parse("https://www.juntadeandalucia.es")
    }
}

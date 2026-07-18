package dev.junta.firmamobile.ui

import android.content.Context
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import dev.junta.firmamobile.profile.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
class BrowserUrlSanitizerTest {
    @Test
    fun queryFragmentAndEphemeralCanariesNeverReachDisplayedUrl() {
        val sanitized = safeBrowserDisplayUrl(
            "https://ws235.juntadeandalucia.es/authenticationFacade" +
                "?ticketId=ephemeral-canary&webSessionId=session-canary#fragment-canary",
        )

        assertEquals("https://ws235.juntadeandalucia.es/authenticationFacade", sanitized)
        assertFalse(sanitized.contains("canary"))
    }

    @Test
    fun unsafeOrMalformedValuesCollapseToAnOriginlessHttpsPlaceholder() {
        listOf(
            "javascript:alert(1)",
            "https://",
            "not a url",
        ).forEach { raw -> assertEquals("https://", safeBrowserDisplayUrl(raw)) }
    }

    @Test
    fun dedicatedWebViewIsNeverExposedOrCapturedForPersistence() {
        val webView = WebView(ApplicationProvider.getApplicationContext<Context>())

        assertNull(browserWebViewForPersistence(webView, dedicated = true))
        assertSame(webView, browserWebViewForPersistence(webView, dedicated = false))
        assertFalse(shouldCaptureBrowserState(discardHistory = false, dedicated = true))
        assertFalse(shouldCaptureBrowserState(discardHistory = true, dedicated = false))
    }

    @Test
    fun exactClientTlsInitiatorRestoresItsProfileButRequestOriginDoesNot() {
        assertEquals(
            ProfileId("carne-joven-andalucia"),
            initiatorProfileForUrl(
                "https://ws104.juntadeandalucia.es/carneJoven/cjservlet/portal/index.jsp",
            ),
        )
        assertNull(
            initiatorProfileForUrl("https://ws235.juntadeandalucia.es/authenticationFacade"),
        )
    }

    @Test
    fun selectedProfileContourNeverAdoptsAnotherImplementedOrigin() {
        val junta = ProfileId("junta-andalucia")

        assertTrue(
            urlBelongsToSelectedProfile(
                "https://www.juntadeandalucia.es/empleoformacionytrabajoautonomo/",
                junta,
            ),
        )
        assertTrue(
            urlBelongsToSelectedProfile("https://ssoweb.juntadeandalucia.es/redirect", junta),
        )
        assertFalse(urlBelongsToSelectedProfile("https://reg.redsara.es/es/", junta))
        assertFalse(urlBelongsToSelectedProfile("https://unknown.example/", junta))
    }
}

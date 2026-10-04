package dev.junta.firmamobile.browser

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import dev.junta.firmamobile.afirma.AfirmaRequest
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.security.SanitizedLogger
import org.junit.Assert.*
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
class ExternalAppNavigationTest {
    @Test fun actualClientOffersOnlyTheCurrentMainFrameUserGet() = withFixture { f ->
        assertTrue(f.client.shouldOverrideUrlLoading(f.view, Request()))
        assertEquals(1, f.offers); assertEquals("sampleauth://login?state=abc", f.target!!.uri.toString())
        assertEquals(0, f.legacyExternalCalls)
    }
    @Test fun iframeScriptRedirectPostAndLegacyCallbacksCannotOfferAnApp() = withFixture { f ->
        for (request in listOf(Request(main = false), Request(gesture = false), Request(redirect = true), Request(methodName = "POST"))) {
            assertTrue(f.client.shouldOverrideUrlLoading(f.view, request))
        }
        @Suppress("DEPRECATION")
        val blocked = f.client.shouldOverrideUrlLoading(f.view, "sampleauth://login")
        assertTrue(blocked); assertEquals(0, f.offers); assertEquals(0, f.legacyExternalCalls)
    }
    @Test fun hiddenOrReplacedParentCannotOfferAnExternalAction() = withFixture { f ->
        f.view.shown = false; f.client.shouldOverrideUrlLoading(f.view, Request())
        f.view.shown = true; f.view.focused = false; f.client.shouldOverrideUrlLoading(f.view, Request())
        f.view.focused = true; f.active = false; f.client.shouldOverrideUrlLoading(f.view, Request())
        assertEquals(0, f.offers)
    }
    @Test fun genericHandlerDoesNotConsumeNativeAfirmaOrNormalHttps() = withFixture { f ->
        f.client.shouldOverrideUrlLoading(f.view, Request("afirma://sign?dat=eA=="))
        assertEquals(1, f.nativeCalls); assertEquals(0, f.offers)
        assertFalse(f.client.shouldOverrideUrlLoading(f.view, Request("https://next.example/login")))
        assertEquals(0, f.offers)
    }
    @Test fun unsafeSourceAndRejectedPageIntentRemainBlocked() = withFixture { f ->
        f.page = "file:///private"
        assertTrue(f.client.shouldOverrideUrlLoading(f.view, Request()))
        f.page = "https://source.example/"
        assertTrue(f.client.shouldOverrideUrlLoading(f.view, Request("intent://login#Intent;scheme=sampleauth;component=dev.junta.firmamobile/.MainActivity;end")))
        assertEquals(0, f.offers)
    }
    @Test fun admissionFailureDoesNotStartASecondFallback() = withFixture { f ->
        f.accept = false
        assertTrue(f.client.shouldOverrideUrlLoading(f.view, Request("intent://login#Intent;scheme=sampleauth;S.browser_fallback_url=https%3A%2F%2Ffallback.example;end")))
        assertEquals(1, f.offers); assertEquals(0, f.legacyExternalCalls)
    }
    private class FakeView(context: Context) : WebView(context) {
        var shown = true; var focused = true
        override fun isShown() = shown
        override fun hasWindowFocus() = focused
    }
    private class Fixture {
        val view = FakeView(ApplicationProvider.getApplicationContext())
        var active = true; var page = "https://source.example/page?private=not-shown"
        var offers = 0; var legacyExternalCalls = 0; var nativeCalls = 0; var accept = true
        var target: ExternalAppLink? = null
        val client = JuntaWebViewClient(
            callbacks = object : BrowserNavigationCallbacks {
                override fun openExternal(uri: Uri) { legacyExternalCalls++ }
                override fun openOfficialAutoFirma(uri: Uri) { legacyExternalCalls++ }
                override fun onAfirmaRequest(request: AfirmaRequest) = Unit
                override fun onNavigationBlocked(reason: NavigationBlockReason) = Unit
                override fun onBrowserError(error: BrowserErrorCode) = Unit
            },
            logger = SanitizedLogger(), navigationPolicy = JuntaNavigationPolicy(null, BuiltInSiteProfiles.qaRegistry),
            currentPageUrl = { page }, isActiveWebView = { active },
            onNativeAfirmaInvocation = { _, _, _ -> nativeCalls++; true },
            onExternalAppRequest = { owner, link -> assertSame(view, owner); offers++; target = link; accept },
        )
    }
    private class Request(
        private val url: String = "sampleauth://login?state=abc", private val main: Boolean = true,
        private val gesture: Boolean = true, private val redirect: Boolean = false, private val methodName: String = "GET",
    ) : WebResourceRequest {
        override fun getUrl() = Uri.parse(url)
        override fun isForMainFrame() = main
        override fun isRedirect() = redirect
        override fun hasGesture() = gesture
        override fun getMethod() = methodName
        override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
    }
    private fun withFixture(test: (Fixture) -> Unit) { val f = Fixture(); try { test(f) } finally { f.view.destroy() } }
}

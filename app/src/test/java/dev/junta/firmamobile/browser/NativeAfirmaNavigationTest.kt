package dev.junta.firmamobile.browser

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import dev.junta.firmamobile.afirma.AfirmaRequest
import dev.junta.firmamobile.afirma.servlet.AfirmaServletInvocationParser
import dev.junta.firmamobile.afirma.servlet.AfirmaServletParseResult
import dev.junta.firmamobile.profile.ProfileId
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
class NativeAfirmaNavigationTest {
    private val payload = "selectcert?id=Session123&stservlet=https%3A%2F%2Fstore.example%2FStorageService&key=12345678"

    @Test fun standardAndroidIntentBecomesDataNotAnExecutableIntent() {
        assertEquals("afirma://$payload", NativeAfirmaNavigation.extract(
            "intent://$payload#Intent;scheme=afirma;package=es.gob.afirma;end"))
        assertEquals("afirma://$payload", NativeAfirmaNavigation.extract("afirma://$payload"))
    }

    @Test fun arbitraryHandlerComponentFallbackAndMalformedUrisAreRefused() {
        val invalid = listOf(
            "intent://$payload#Intent;scheme=afirma;package=malicious.app;end",
            "intent://$payload#Intent;scheme=afirma;package=es.gob.afirma;component=es.gob.afirma/.Fake;end",
            "intent://$payload#Intent;scheme=afirma;package=es.gob.afirma;S.browser_fallback_url=https%3A%2F%2Fevil.example;end",
            "afirma://user@sign?x=1", "afirma://sign:443?x=1", "afirma://sign/path?x=1", "afirma://sign?x=1#fragment",
            "afirma://sign?x=1\n", "javascript:afirma://sign", "afirma://unsupported-op?x=1",
        )
        invalid.forEach { assertNull(it, NativeAfirmaNavigation.extract(it)) }
    }

    @Test fun modernTopLevelInvocationReachesGenericHandlerWithoutAProfileRecipe() {
        fixture { f ->
            assertTrue(f.client.shouldOverrideUrlLoading(f.view, Request("afirma://$payload")))
            assertEquals(listOf("afirma://$payload" to "https://unconfigured.example/form"), f.invocations)
            assertEquals(0, f.oldRequests)
            assertNull(f.view.url)
        }
    }

    @Test fun androidIntentVariantUsesTheSameGenericHandler() {
        fixture { f ->
            f.client.shouldOverrideUrlLoading(f.view, Request("intent://$payload#Intent;scheme=afirma;package=es.gob.afirma;end"))
            assertEquals("afirma://$payload", f.invocations.single().first)
        }
    }

    @Test fun subframePostLegacyAndDestroyedViewCannotTriggerGenericConsent() {
        fixture { f ->
            f.client.shouldOverrideUrlLoading(f.view, Request("afirma://$payload", mainFrame = false))
            f.client.shouldOverrideUrlLoading(f.view, Request("afirma://$payload", method = "POST"))
            @Suppress("DEPRECATION")
            f.client.shouldOverrideUrlLoading(f.view, "afirma://$payload")
            f.active = false
            f.client.shouldOverrideUrlLoading(f.view, Request("afirma://$payload"))
            assertTrue(f.invocations.isEmpty())
        }
    }

    @Test fun rejectingDelegateDoesNotTurnUnknownOriginIntoTrustedSigning() {
        fixture { f ->
            f.accept = false
            assertTrue(f.client.shouldOverrideUrlLoading(f.view, Request("afirma://$payload")))
            assertEquals(0, f.oldRequests)
            assertTrue(NavigationBlockReason.UNTRUSTED_AFIRMA_ORIGIN in f.blocks)
        }
    }

    @Test fun coSignUsesTheNativeDataHandlerAndRetainsMainFrameRestrictions() {
        val target = "cosign?id=Session123&stservlet=https%3A%2F%2Fstore.example%2Fput&format=PAdES&algorithm=SHA256withRSA&dat=JVBERi0="
        val uri = "afirma://$target"
        assertEquals(uri, NativeAfirmaNavigation.extract(uri))
        assertEquals(uri, NativeAfirmaNavigation.extract("intent://$target#Intent;scheme=afirma;package=es.gob.afirma;end"))
        assertNull(NativeAfirmaNavigation.extract(uri.replace("cosign", "unsupported-op")))
        fixture { f ->
            f.client.shouldOverrideUrlLoading(f.view, Request(uri, mainFrame = false))
            f.client.shouldOverrideUrlLoading(f.view, Request(uri, method = "POST"))
            assertTrue(f.invocations.isEmpty())
            assertTrue(f.client.shouldOverrideUrlLoading(f.view, Request(uri)))
            assertEquals(listOf(uri to "https://unconfigured.example/form"), f.invocations)
            assertEquals(0, f.oldRequests)
        }
    }

    @Test fun batchAndCounterSignAreDataRoutesWithTheSameTopLevelRestrictions() {
        for (operation in listOf("batch", "countersign")) {
            val target = "afirma://$operation?id=Session123&dat=YWJj"
            assertEquals(target, NativeAfirmaNavigation.extract(target))
            fixture { f ->
                f.client.shouldOverrideUrlLoading(f.view, Request(target, mainFrame = false))
                f.client.shouldOverrideUrlLoading(f.view, Request(target, method = "POST"))
                assertTrue(f.invocations.isEmpty())
                assertTrue(f.client.shouldOverrideUrlLoading(f.view, Request(target)))
                assertEquals(listOf(target to "https://unconfigured.example/form"), f.invocations)
                assertEquals(0, f.oldRequests)
            }
        }
    }

    @Test fun desktopWebsocketIsRecognizedOnlyForTheNativeUnsupportedResult() {
        val target = "websocket?ports=63117&idsession=Session123"
        val raw = "afirma://$target"
        assertEquals(raw, NativeAfirmaNavigation.extract(raw))
        assertEquals(raw, NativeAfirmaNavigation.extract(
            "intent://$target#Intent;scheme=afirma;package=es.gob.afirma;end"))
        val parsed = AfirmaServletInvocationParser.parse(raw, "https://unconfigured.example/form")
        assertTrue(parsed.toString(), parsed is AfirmaServletParseResult.Unsupported)
        assertEquals("desktop_websocket_not_implemented", (parsed as AfirmaServletParseResult.Unsupported).reason)
        fixture { f ->
            f.client.shouldOverrideUrlLoading(f.view, Request(raw, mainFrame = false))
            f.client.shouldOverrideUrlLoading(f.view, Request(raw, method = "POST"))
            assertTrue(f.invocations.isEmpty())
            assertTrue(f.client.shouldOverrideUrlLoading(f.view, Request(raw)))
            assertEquals(listOf(raw to "https://unconfigured.example/form"), f.invocations)
            assertEquals(0, f.oldRequests)
        }
    }

    @Test fun desktopRefusalDoesNotAdmitUnknownOperationsOrRelaxIntentAndUriGuards() {
        val invalid = listOf(
            "afirma://websocket2?ports=63117", "afirma://unknown?ports=63117",
            "afirma://user@websocket?ports=63117", "afirma://websocket:443?ports=63117",
            "afirma://websocket/path?ports=63117", "afirma://websocket?ports=63117#fragment",
            "intent://websocket?ports=63117#Intent;scheme=afirma;package=other.app;end",
            "intent://websocket?ports=63117#Intent;scheme=afirma;package=es.gob.afirma;component=es.gob.afirma/.Fake;end",
            "intent://websocket?ports=63117#Intent;scheme=afirma;package=es.gob.afirma;S.browser_fallback_url=https%3A%2F%2Fother.example;end",
        )
        invalid.forEach { assertNull(it, NativeAfirmaNavigation.extract(it)) }
        val unknown = AfirmaServletInvocationParser.parse("afirma://unknown?x=1", "https://unconfigured.example/")
        assertTrue(unknown is AfirmaServletParseResult.Unsupported)
        assertEquals("operation_not_implemented", (unknown as AfirmaServletParseResult.Unsupported).reason)
        assertTrue(AfirmaServletInvocationParser.parse("afirma://websocket/path?x=1", "https://unconfigured.example/")
            is AfirmaServletParseResult.Invalid)
    }

    private class Fixture {
        val view = WebView(ApplicationProvider.getApplicationContext<Context>())
        var active = true; var accept = true; var oldRequests = 0
        val invocations = mutableListOf<Pair<String, String>>()
        val blocks = mutableListOf<NavigationBlockReason>()
        val client = JuntaWebViewClient(
            callbacks = object : BrowserNavigationCallbacks {
                override fun openExternal(uri: Uri) = Unit
                override fun openOfficialAutoFirma(uri: Uri) = Unit
                override fun onAfirmaRequest(request: AfirmaRequest) { oldRequests++ }
                override fun onNavigationBlocked(reason: NavigationBlockReason) { blocks += reason }
                override fun onBrowserError(error: BrowserErrorCode) = Unit
            },
            logger = SanitizedLogger(), navigationPolicy = JuntaNavigationPolicy(ProfileId("junta-andalucia")),
            currentPageUrl = { "https://unconfigured.example/form" }, isActiveWebView = { active && it === view },
            onNativeAfirmaInvocation = { _, uri, page -> invocations += uri to page; accept },
        )
    }
    private fun fixture(block: (Fixture) -> Unit) { val f = Fixture(); try { block(f) } finally { f.view.destroy() } }
    private class Request(private val target: String, private val mainFrame: Boolean = true, private val method: String = "GET") : WebResourceRequest {
        override fun getUrl() = Uri.parse(target)
        override fun isForMainFrame() = mainFrame
        override fun isRedirect() = false
        override fun hasGesture() = true
        override fun getMethod() = method
        override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
    }
}

package dev.junta.firmamobile

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.semantics.SemanticsActions
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import dev.junta.firmamobile.browser.ClientCertPreferenceBarrierState
import dev.junta.firmamobile.catalog.PortalCatalogRepository
import dev.junta.firmamobile.catalog.PublicPortalCatalogParser
import dev.junta.firmamobile.catalog.PortalOpenTarget
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import java.net.URI
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real catalog callbacks and local HTML; initial portal traffic is trapped on loopback.
 * No certificate, account, live signing or broker requests. */
@RunWith(AndroidJUnit4::class)
class UniversalCatalogBrowserInstrumentedTest {
    @get:Rule val rule = createEmptyComposeRule()
    private var networkSink: ServerSocket? = null

    @Before fun trapInitialPortalTraffic() {
        assertTrue("Synthetic launch tests require WebView proxy override", WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE))
        val sink = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        networkSink = sink
        val applied = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ProxyController.getInstance().setProxyOverride(
                ProxyConfig.Builder().addProxyRule("127.0.0.1:${sink.localPort}").build(),
                { it.run() }, { applied.countDown() },
            )
        }
        assertTrue(applied.await(5, TimeUnit.SECONDS))
    }

    @After fun clearTestProxy() {
        val sink = networkSink ?: return
        try {
            val cleared = CountDownLatch(1)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                ProxyController.getInstance().clearProxyOverride({ it.run() }, { cleared.countDown() })
            }
            assertTrue(cleared.await(5, TimeUnit.SECONDS))
        } finally {
            sink.close()
        }
    }

    @Test fun aReviewedPortalUsesItsProfileOnTheOrdinaryTap() = exercise("age-reg-redsara", alias = false)
    @Test fun anAliasUsesItsProfileAndPublishedLaunchOnTheOrdinaryTap() = exercise("age-pag-reg", alias = true)
    @Test fun manualUniversalRecoveryKeepsNativeConsentWithoutTheProfile() =
        exercise("age-pag-reg", alias = true, universal = true)
    @Test fun aUniversalRedirectToAProfileStartNeverOffersOrAdoptsThatProfile() =
        exercise("age-pag-reg", alias = true, universal = true, redirect = true)

    private fun exercise(portalId: String, alias: Boolean, universal: Boolean = false, redirect: Boolean = false) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var activityContext: android.content.Context? = null
            scenario.onActivity { activityContext = it }
            val strings = checkNotNull(activityContext).resources
            var target: URI? = null; var searchName = ""
            scenario.onActivity { activity ->
                val catalog = activity.resources.openRawResource(R.raw.public_portal_catalog_v1).bufferedReader().use {
                    PublicPortalCatalogParser.parse(it.readText())
                }
                val repository = PortalCatalogRepository(BuiltInSiteProfiles.runtimeRegistry, BuiltInSiteProfiles.catalog, catalog)
                val item = repository.portals().single { it.portalId.value == portalId }
                val metadata = catalog.entries.single { it.portalId == item.portalId }
                assertNotNull("A reviewed profile must exist for this regression to test the changed default", repository.resolveLaunch(item))
                assertEquals(alias, metadata.launchUrl != null)
                assertEquals(PortalOpenTarget.InApp(checkNotNull(repository.resolveLaunch(item))), repository.resolveOpenTarget(item))
                target = repository.resolveUniversalOpenTarget(item)!!.entryUrl
                assertEquals(metadata.launchUrl ?: metadata.entryUrl, target)
                searchName = metadata.displayName
            }
            rule.waitUntil(timeoutMillis = 15_000) {
                rule.onAllNodes(androidx.compose.ui.test.hasText(strings.getString(R.string.browse_without_certificate))).fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithText(strings.getString(R.string.browse_without_certificate)).performScrollTo().performClick()
            rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("catalog-universal-afirma"))
            rule.onNodeWithTag("catalog-universal-afirma").assertIsDisplayed()
            // The search field is a lazy header and may be composed out after scrolling to a card.
            rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("public-web-address"))
            rule.onNodeWithTag("public-web-address").performTextReplacement(searchName)
            rule.onNodeWithTag("catalog-open-profile-$portalId").assertDoesNotExist()
            // The search filters asynchronously; keep scrolling until the filtered card is composed.
            rule.waitUntil(timeoutMillis = 15_000) {
                runCatching { rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("catalog-open-$portalId")) }
                rule.onAllNodes(hasTestTag("catalog-open-$portalId")).fetchSemanticsNodes().isNotEmpty()
            }
            if (universal) {
                val options = rule.onNodeWithTag("catalog-card-$portalId").fetchSemanticsNode().config[SemanticsActions.CustomActions]
                rule.runOnIdle { check(options.single().action()) }
                rule.onNodeWithTag("catalog-open-universal-$portalId").assertIsDisplayed().performClick()
            } else {
                rule.onNodeWithTag("catalog-open-$portalId").performClick()
            }
            rule.onNodeWithTag("public-browsing-notice").assertDoesNotExist()
            rule.onNodeWithContentDescription(strings.getString(R.string.browser_action_more)).performClick()
            rule.onNodeWithText(strings.getString(R.string.browser_site_information)).performClick()
            if (universal) rule.onNodeWithTag("public-browsing-notice").assertIsDisplayed()
            else rule.onNodeWithTag("public-browsing-notice").assertDoesNotExist()
            rule.onNodeWithTag("browser-site-information-close").performClick()
            var current: WebView? = null
            rule.waitUntil(timeoutMillis = 15_000) { scenario.onActivity { current = findWebView(it.window.decorView) }; current != null }
            val view = checkNotNull(current); val destination = checkNotNull(target)
            lateinit var originalClient: android.webkit.WebViewClient
            val fixture = URI("https", null, destination.host, -1, "/__firmamobile_universal_fixture__", null, null).toASCIIString()
            val completed = AtomicBoolean(false); val intercepted = AtomicBoolean(false)
            scenario.onActivity {
                view.stopLoading()
                originalClient = view.webViewClient
                view.webViewClient = PublicBrowserInstrumentedTest.LocalResponseClient(originalClient, fixture, completed, intercepted,
                    "<html><head><title>UNIVERSAL_AFIRMA_READY</title></head><body><input id='draft' value='retained'></body></html>")
                view.loadUrl(fixture)
            }
            rule.waitUntil(timeoutMillis = 15_000) {
                var ready = false
                scenario.onActivity { activity ->
                    ready = view.url == fixture && view.title == "UNIVERSAL_AFIRMA_READY" && view.progress == 100 &&
                        view.isShown && view.hasWindowFocus() && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                        (activity.application as JuntaFirmaApplication).clientCertPreferenceCoordinator.state.value == ClientCertPreferenceBarrierState.IDLE
                }
                ready && completed.get() && intercepted.get()
            }
            if (!universal) return@use
            if (redirect) {
                completed.set(false); intercepted.set(false)
                scenario.onActivity {
                    view.webViewClient = PublicBrowserInstrumentedTest.LocalResponseClient(originalClient, destination.toASCIIString(), completed, intercepted,
                        "<html><head><title>REDIRECT_READY</title></head><body>Local reviewed start</body></html>")
                    view.evaluateJavascript("location.replace(${org.json.JSONObject.quote(destination.toASCIIString())})", null)
                }
                rule.waitUntil(timeoutMillis = 15_000) {
                    var ready = false
                    scenario.onActivity { ready = view.url == destination.toASCIIString() && view.title == "REDIRECT_READY" }
                    ready && completed.get() && intercepted.get()
                }
                assertEquals("\"undefined\"", evaluate(view, "typeof window.JuntaFirmaMobile"))
                rule.onNodeWithContentDescription(strings.getString(R.string.browser_action_more)).performClick()
                rule.onNodeWithText(strings.getString(R.string.browser_site_information)).performClick()
                rule.onNodeWithTag("public-browsing-notice").assertIsDisplayed()
                rule.onNodeWithTag("browser-site-information-close").performClick()
                offer(view, scenario, "afirma://unsupported")
                rule.onNodeWithText(strings.getString(R.string.native_afirma_unsupported)).assertIsDisplayed()
                rule.onNodeWithText("Modo específico del portal").assertDoesNotExist()
                scenario.onActivity { assertSame(view, findWebView(it.window.decorView)) }
                return@use
            }
            assertEquals("\"undefined\"", evaluate(view, "typeof window.JuntaFirmaMobile"))
            val request = "afirma://sign?id=Universal123&stservlet=https%3A%2F%2Fstorage.synthetic.example%2Fput&format=CAdES&algorithm=SHA256withRSA&dat=QUJDRA%3D%3D"
            offer(view, scenario, request)
            rule.onNodeWithTag("native-afirma-consent").assertIsDisplayed()
            rule.onNodeWithTag("native-afirma-unlock").assertIsDisplayed()
            rule.onNodeWithTag("native-afirma-confirm").assertDoesNotExist()
            assertEquals("\"retained\"", evaluate(view, "document.getElementById('draft').value"))
            scenario.onActivity { assertSame(view, findWebView(it.window.decorView)) }
            // Closing the Activity invalidates locally; never press server
            // cancellation, unlock, sign or send against an actual service.
        }
    }
    private fun offer(view: WebView, scenario: ActivityScenario<MainActivity>, request: String) {
        scenario.onActivity {
            assertTrue(view.webViewClient.shouldOverrideUrlLoading(view, object : WebResourceRequest {
                override fun getUrl() = Uri.parse(request)
                override fun isForMainFrame() = true
                override fun isRedirect() = false
                override fun hasGesture() = true
                override fun getMethod() = "GET"
                override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
            }))
        }
    }
    private fun evaluate(view: WebView, script: String): String {
        val result = AtomicReference<String?>()
        rule.runOnIdle { view.evaluateJavascript(script) { result.set(it) } }
        rule.waitUntil(timeoutMillis = 5_000) { result.get() != null }
        return checkNotNull(result.get())
    }
    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findWebView(view.getChildAt(i))?.let { return it }
        return null
    }
}

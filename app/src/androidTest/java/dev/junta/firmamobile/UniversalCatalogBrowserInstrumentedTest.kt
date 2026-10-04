package dev.junta.firmamobile

import androidx.compose.ui.test.onNodeWithContentDescription
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
import dev.junta.firmamobile.browser.ClientCertPreferenceBarrierState
import dev.junta.firmamobile.catalog.PortalCatalogRepository
import dev.junta.firmamobile.catalog.PublicPortalCatalogParser
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real catalog primary button -> ordinary browser -> existing native consent.
 * Local HTML and synthetic protocol bytes; no certificate/key/network signing. */
@RunWith(AndroidJUnit4::class)
class UniversalCatalogBrowserInstrumentedTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun aPortalWithAReviewedProfileUsesTheCommonEngineByDefault() = exercise("age-reg-redsara", alias = false)
    @Test fun anAliasUsesItsBundledDestinationWithoutInheritingTheProfile() = exercise("age-pag-reg", alias = true)

    private fun exercise(portalId: String, alias: Boolean) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var target: URI? = null; var searchName = ""; var searchLabel = ""
            scenario.onActivity { activity ->
                val catalog = activity.resources.openRawResource(R.raw.public_portal_catalog_v1).bufferedReader().use {
                    PublicPortalCatalogParser.parse(it.readText())
                }
                val repository = PortalCatalogRepository(BuiltInSiteProfiles.qaRegistry, BuiltInSiteProfiles.catalog, catalog)
                val item = repository.portals().single { it.portalId.value == portalId }
                val metadata = catalog.entries.single { it.portalId == item.portalId }
                assertNotNull("A reviewed profile must exist for this regression to test the changed default", repository.resolveLaunch(item))
                assertEquals(alias, metadata.launchUrl != null)
                target = repository.resolveUniversalOpenTarget(item)!!.entryUrl
                assertEquals(metadata.launchUrl ?: metadata.entryUrl, target)
                searchName = metadata.displayName; searchLabel = activity.getString(R.string.catalog_search_label)
            }
            rule.waitUntil(timeoutMillis = 15_000) {
                rule.onAllNodes(androidx.compose.ui.test.hasText("Explorar sedes sin desbloquear el certificado")).fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithText("Explorar sedes sin desbloquear el certificado").performScrollTo().performClick()
            rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("catalog-universal-afirma"))
            rule.onNodeWithTag("catalog-universal-afirma").assertIsDisplayed()
            rule.onNodeWithText(searchLabel).performTextReplacement(searchName)
            // A reviewed profile exists in metadata, but is no longer another
            // visible primary action on every user-facing card.
            rule.onNodeWithTag("catalog-open-profile-$portalId").assertDoesNotExist()
            rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("catalog-open-$portalId"))
            rule.onNodeWithTag("catalog-open-$portalId").performClick()
            rule.onNodeWithTag("public-browsing-notice").assertDoesNotExist()
            rule.onNodeWithContentDescription("Más opciones").performClick()
            rule.onNodeWithText("Información del sitio").performClick()
            rule.onNodeWithTag("public-browsing-notice").assertIsDisplayed()
            rule.onNodeWithTag("browser-site-information-close").performClick()
            var current: WebView? = null
            rule.waitUntil(timeoutMillis = 15_000) { scenario.onActivity { current = findWebView(it.window.decorView) }; current != null }
            val view = checkNotNull(current); val destination = checkNotNull(target)
            val fixture = URI("https", null, destination.host, -1, "/__firmamobile_universal_fixture__", null, null).toASCIIString()
            val completed = AtomicBoolean(false); val intercepted = AtomicBoolean(false)
            scenario.onActivity {
                view.stopLoading()
                view.webViewClient = PublicBrowserInstrumentedTest.LocalResponseClient(view.webViewClient, fixture, completed, intercepted,
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
            assertEquals("\"undefined\"", evaluate(view, "typeof window.JuntaFirmaMobile"))
            val request = "afirma://sign?id=Universal123&stservlet=https%3A%2F%2Fstorage.synthetic.example%2Fput&format=CAdES&algorithm=SHA256withRSA&dat=QUJDRA%3D%3D"
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
            rule.onNodeWithTag("native-afirma-consent").assertIsDisplayed()
            rule.onNodeWithTag("native-afirma-unlock").assertIsDisplayed()
            rule.onNodeWithTag("native-afirma-confirm").assertDoesNotExist()
            assertEquals("\"retained\"", evaluate(view, "document.getElementById('draft').value"))
            scenario.onActivity { assertSame(view, findWebView(it.window.decorView)) }
            // Closing the Activity invalidates locally; never press server
            // cancellation, unlock, sign or send against an actual service.
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

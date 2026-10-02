package dev.junta.firmamobile

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
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
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real MainActivity routing and platform launch call, intercepted by an
 * Instrumentation monitor before another application actually opens. */
@RunWith(AndroidJUnit4::class)
class ExternalAppHandoffInstrumentedTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val target = "sampleauth://login?state=a%2Fb&token=synthetic-only"

    @Test fun theOriginalUserRequestLaunchesOnlyAfterExplicitConfirmation() = withBrowser { scenario, view, launches ->
        offer(scenario, view, target)
        rule.onNodeWithTag("external-app-dialog").assertIsDisplayed()
        assertTrue(launches.isEmpty())
        rule.onNodeWithTag("external-app-open").performClick()
        rule.waitForIdle()
        assertEquals(1, launches.size)
        val intent = launches.single()
        assertEquals(target, intent.dataString); assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(setOf(Intent.CATEGORY_BROWSABLE), intent.categories)
        assertNull(intent.component); assertNull(intent.selector); assertNull(intent.clipData); assertNull(intent.extras)
        assertEquals(0, intent.flags)
        scenario.onActivity { assertSame(view, findWebView(it.window.decorView)); assertEquals("EXTERNAL_LOCAL_READY", view.title) }
    }

    @Test fun fallbackRequiresItsOwnButtonAndIsNotPassedAsAnAppExtra() = withBrowser { scenario, view, launches ->
        offer(scenario, view, "intent://login#Intent;scheme=sampleauth;S.browser_fallback_url=https%3A%2F%2Ffallback.synthetic.example%2Fcontinue;end")
        rule.onNodeWithTag("external-app-dialog").assertIsDisplayed()
        assertTrue(launches.isEmpty())
        rule.onNodeWithTag("external-app-web").performClick()
        rule.waitForIdle(); assertEquals(1, launches.size)
        assertEquals("https://fallback.synthetic.example/continue", launches.single().dataString)
        assertNull(launches.single().extras); assertNull(launches.single().`package`)
    }

    @Test fun cancellationAndNavigationNeverLaunchTheOldLink() = withBrowser { scenario, view, launches ->
        offer(scenario, view, target)
        rule.onNodeWithTag("external-app-cancel").performClick()
        rule.onNodeWithTag("external-app-dialog").assertDoesNotExist()
        assertTrue(launches.isEmpty())
        offer(scenario, view, target)
        rule.onNodeWithTag("external-app-dialog").assertIsDisplayed()
        scenario.onActivity { view.webViewClient.onPageStarted(view, "https://external.synthetic.example/next", null) }
        rule.waitForIdle()
        rule.onNodeWithTag("external-app-dialog").assertDoesNotExist()
        assertTrue(launches.isEmpty())
    }

    private fun offer(scenario: ActivityScenario<MainActivity>, view: WebView, raw: String) {
        scenario.onActivity {
            assertTrue(view.webViewClient.shouldOverrideUrlLoading(view, object : WebResourceRequest {
                override fun getUrl() = Uri.parse(raw)
                override fun isForMainFrame() = true
                override fun isRedirect() = false
                override fun hasGesture() = true
                override fun getMethod() = "GET"
                override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
            }))
        }
        rule.waitUntil(timeoutMillis = 5_000) { runCatching { rule.onNodeWithTag("external-app-dialog").assertIsDisplayed() }.isSuccess }
    }

    private fun withBrowser(test: (ActivityScenario<MainActivity>, WebView, List<Intent>) -> Unit) {
        val launches = Collections.synchronizedList(mutableListOf<Intent>())
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.data?.scheme == "sampleauth" || intent.data?.host == "fallback.synthetic.example") {
                    launches.add(Intent(intent))
                    return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
                }
                return null
            }
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.addMonitor(monitor)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                rule.waitUntil(timeoutMillis = 15_000) {
                    rule.onAllNodes(androidx.compose.ui.test.hasText("Explorar sedes sin desbloquear el certificado")).fetchSemanticsNodes().isNotEmpty()
                }
                rule.onNodeWithText("Explorar sedes sin desbloquear el certificado").performScrollTo().performClick()
                rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("catalog-open-public-web"))
                rule.onNodeWithTag("catalog-open-public-web").performClick()
                rule.onNodeWithTag("public-web-address").performTextReplacement("https://external.synthetic.example/")
                rule.onNodeWithTag("public-web-open-confirm").performClick()
                var current: WebView? = null
                rule.waitUntil(timeoutMillis = 15_000) { scenario.onActivity { current = findWebView(it.window.decorView) }; current != null }
                val view = checkNotNull(current); val finished = AtomicBoolean(false); val intercepted = AtomicBoolean(false)
                val local = "https://external.synthetic.example/fixture"
                scenario.onActivity {
                    view.stopLoading()
                    view.webViewClient = PublicBrowserInstrumentedTest.LocalResponseClient(view.webViewClient, local, finished, intercepted,
                        "<html><head><title>EXTERNAL_LOCAL_READY</title></head><body><input value='retained draft'></body></html>")
                    view.loadUrl(local)
                }
                rule.waitUntil(timeoutMillis = 15_000) {
                    var ready = false
                    scenario.onActivity { ready = view.url == local && view.title == "EXTERNAL_LOCAL_READY" && view.progress == 100 && view.hasWindowFocus() }
                    ready && finished.get() && intercepted.get()
                }
                test(scenario, view, launches)
            }
        } finally { instrumentation.removeMonitor(monitor) }
    }
    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findWebView(view.getChildAt(i))?.let { return it }
        return null
    }
}

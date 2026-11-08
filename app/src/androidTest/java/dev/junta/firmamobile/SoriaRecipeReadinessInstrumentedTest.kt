package dev.junta.firmamobile

import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SoriaRecipeReadinessInstrumentedTest {
    private val probe = RealE2eInstrumentedTest()
    @Test
    fun delayedHandlerIsReadyBeforeTheOnlyCertificateClick() = withFixture(true) { scenario, view, target, navigated ->
        click(scenario)
        assertTrue(navigated.await(5, TimeUnit.SECONDS))
        assertEquals(TARGET, target.get())
        assertEquals("1", clicks(scenario, view))
    }

    @Test
    fun missingHandlerTimesOutWithoutClickOrNavigation() = withFixture(false) { scenario, view, target, _ ->
        val failure = runCatching { click(scenario) }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals("REAL_E2E_RECIPE_TARGET_TIMEOUT", failure?.message)
        assertEquals("0", clicks(scenario, view))
        assertNull(target.get())
    }

    private fun click(scenario: ActivityScenario<MainActivity>) = probe.clickExactButton(
        scenario = scenario,
        expectedCurrentUrl = SOURCE,
        expectedElementId = "b_certificado",
        expectedLabel = "ACCEDER",
        expectedAriaLabel = null,
        expectedOnClick = "pulsarCertificado();",
        expectedHandlerName = "pulsarCertificado",
        waitForExpectedUrl = true,
    )

    private fun clicks(scenario: ActivityScenario<MainActivity>, view: WebView): String? {
        val latch = CountDownLatch(1)
        val count = AtomicReference<String?>()
        scenario.onActivity {
            view.evaluateJavascript("window.fixtureClicks") { count.set(it); latch.countDown() }
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        return count.get()
    }

    private fun withFixture(
        delayedHandler: Boolean,
        test: (ActivityScenario<MainActivity>, WebView, AtomicReference<String?>, CountDownLatch) -> Unit,
    ) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val finished = CountDownLatch(1)
            val navigated = CountDownLatch(1)
            val target = AtomicReference<String?>()
            val field = MainActivity::class.java.getDeclaredField("currentWebView").apply { isAccessible = true }
            var previous: WebView? = null
            lateinit var view: WebView
            scenario.onActivity { activity ->
                (activity.application as JuntaFirmaApplication).sanitizedLogger.clear()
                previous = field.get(activity) as? WebView
                view = WebView(activity).apply {
                    settings.javaScriptEnabled = true
                    settings.blockNetworkLoads = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            if (url == SOURCE) finished.countDown()
                        }

                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            if (request.isForMainFrame) { target.set(request.url.toString()); navigated.countDown() }
                            return true
                        }
                    }
                }
                field.set(activity, view)
                val lateScript = if (delayedHandler) {
                    "setTimeout(() => { window.pulsarCertificado = () => { location.href = '$TARGET'; }; }, 1200);"
                } else ""
                view.loadDataWithBaseURL(
                    SOURCE,
                    """
                    <button id="b_certificado" type="button" onclick="pulsarCertificado();">ACCEDER</button>
                    <button id="b_certificado" type="button" onclick="pulsarClave();">ACCEDER</button>
                    <script>
                    window.fixtureClicks = 0;
                    document.addEventListener('click', () => { window.fixtureClicks++; }, true);
                    $lateScript
                    </script>
                    """.trimIndent(),
                    "text/html", "UTF-8", null,
                )
            }
            try {
                assertTrue(finished.await(10, TimeUnit.SECONDS))
                test(scenario, view, target, navigated)
            } finally {
                scenario.onActivity { activity ->
                    field.set(activity, previous)
                    view.stopLoading()
                    view.destroy()
                }
            }
        }
    }

    private companion object {
        const val SOURCE = "https://soria-fixture.invalid/login"
        const val TARGET = "https://soria-fixture.invalid/certificate?opcion=ssl"
    }
}

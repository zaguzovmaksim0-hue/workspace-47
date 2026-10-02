package dev.junta.firmamobile

import android.webkit.WebViewClient
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.junta.firmamobile.browser.HttpAuthReply
import dev.junta.firmamobile.browser.HttpAuthWebViewClient
import dev.junta.firmamobile.browser.TrustedJuntaWebView
import org.hamcrest.Matchers.`is`
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic platform-adapter boundary; never a real account or server reply.
 * The controller, Android dialog, lifecycle and installed WebView wrapper are real. */
@RunWith(AndroidJUnit4::class)
class HttpAuthBrowserInstrumentedTest {
    @Test fun credentialsAreForwardedOnlyAfterTheExplicitDialogConfirmation() {
        withBrowser { scenario, view ->
            val reply = Reply()
            scenario.onActivity { assertTrue((view.webViewClient as HttpAuthWebViewClient).request(view, "auth.synthetic.example", "Synthetic area", reply)) }
            onView(withTagValue(`is`("http-auth-username"))).perform(replaceText("synthetic-user"))
            onView(withTagValue(`is`("http-auth-password"))).perform(replaceText("not-a-real-password"))
            scenario.onActivity { assertEquals(0, reply.proceeds); assertEquals(0, reply.cancels) }
            onView(withText("Continuar")).perform(click())
            scenario.onActivity {
                assertEquals(1, reply.proceeds); assertEquals(0, reply.cancels)
                assertEquals("synthetic-user" to "not-a-real-password", reply.value)
            }
        }
    }

    @Test fun backgroundingCancelsWithoutForwardingTypedCredentials() {
        withBrowser { scenario, view ->
            val reply = Reply()
            scenario.onActivity { assertTrue((view.webViewClient as HttpAuthWebViewClient).request(view, "auth.synthetic.example", "", reply)) }
            onView(withTagValue(`is`("http-auth-password"))).perform(replaceText("local-test-text"))
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            scenario.onActivity { assertEquals(0, reply.proceeds); assertEquals(1, reply.cancels) }
        }
    }

    @Test fun replacementClientAndNavigationCannotReuseTheOldPrompt() {
        withBrowser { scenario, view ->
            val reply = Reply()
            scenario.onActivity {
                assertTrue((view.webViewClient as HttpAuthWebViewClient).request(view, "auth.synthetic.example", "", reply))
                view.webViewClient = WebViewClient()
                assertEquals(1, reply.cancels); assertEquals(0, reply.proceeds)
                view.webViewClient.onPageStarted(view, "https://auth.synthetic.example/changed", null)
                assertEquals(1, reply.cancels)
            }
        }
    }

    @Test fun aDifferentHostCannotDisplayARequestInTheCurrentPageContext() {
        withBrowser { scenario, view ->
            val reply = Reply()
            scenario.onActivity {
                assertFalse((view.webViewClient as HttpAuthWebViewClient).request(view, "other.synthetic.example", "", reply))
                assertEquals(1, reply.cancels); assertEquals(0, reply.proceeds)
            }
        }
    }

    private fun withBrowser(test: (ActivityScenario<MainActivity>, TrustedJuntaWebView) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var view: TrustedJuntaWebView
            scenario.onActivity { activity ->
                view = TrustedJuntaWebView(activity)
                activity.setContentView(view)
                view.webViewClient = WebViewClient()
                view.loadDataWithBaseURL("https://auth.synthetic.example/local", "<html><head><title>LOCAL_HTTP_AUTH_FIXTURE</title></head><body>Local</body></html>", "text/html", "UTF-8", "https://auth.synthetic.example/local")
            }
            val end = android.os.SystemClock.uptimeMillis() + 15_000L
            var ready = false
            while (!ready && android.os.SystemClock.uptimeMillis() < end) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { ready = view.isAttachedToWindow && view.hasWindowFocus() && view.progress == 100 && view.title == "LOCAL_HTTP_AUTH_FIXTURE" }
                if (!ready) Thread.sleep(25)
            }
            assertTrue("Local WebView did not become visible", ready)
            try { test(scenario, view) }
            finally { scenario.onActivity { view.destroy() } }
        }
    }

    private class Reply : HttpAuthReply {
        var proceeds = 0; var cancels = 0; var value: Pair<String, String>? = null
        override fun proceed(username: String, password: String) { proceeds++; value = username to password }
        override fun cancel() { cancels++ }
    }
}

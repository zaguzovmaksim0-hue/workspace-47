package dev.junta.firmamobile

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.junta.firmamobile.ui.ExternalAppConsentDialog
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ExternalAppConsentDialogInstrumentedTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun noAutomaticActionThenAppCallbackOnce() = withDialog(FALLBACK_HOST) { calls ->
        compose.onNodeWithTag("external-app-dialog").assertExists()
        compose.onNodeWithText(SOURCE_HOST, substring = true).assertExists()
        compose.onNodeWithText(SCHEME, substring = true).assertExists()
        compose.onNodeWithText(PACKAGE_NAME, substring = true).assertExists()
        assertCalls(calls)
        compose.onNodeWithTag("external-app-open").performClick()
        assertCalls(calls, app = 1)
    }

    @Test
    fun webCallbackOnlyAfterExplicitClick() = withDialog(FALLBACK_HOST) { calls ->
        compose.onNodeWithText(FALLBACK_HOST, substring = true).assertExists()
        assertCalls(calls)
        compose.onNodeWithTag("external-app-web").performClick()
        assertCalls(calls, web = 1)
    }

    @Test
    fun nullFallbackHasNoWebButtonAndCancelAndBackCallCancel() = withDialog(null) { calls ->
        compose.onNodeWithTag("external-app-web").assertDoesNotExist()
        assertCalls(calls)
        compose.onNodeWithTag("external-app-cancel").performClick()
        assertCalls(calls, cancel = 1)
        // The test parent keeps the dialog mounted to exercise its back callback.
        pressBack()
        compose.waitForIdle()
        assertCalls(calls, cancel = 2)
    }

    private fun withDialog(fallbackHost: String?, check: (Calls) -> Unit) {
        val calls = Calls()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    MaterialTheme {
                        ExternalAppConsentDialog(
                            sourceHost = SOURCE_HOST,
                            scheme = SCHEME,
                            requestedPackage = PACKAGE_NAME,
                            fallbackHost = fallbackHost,
                            onOpenApp = { calls.app.incrementAndGet() },
                            onOpenWeb = { calls.web.incrementAndGet() },
                            onCancel = { calls.cancel.incrementAndGet() },
                        )
                    }
                }
            }
            compose.waitForIdle()
            check(calls)
        }
    }

    private fun assertCalls(calls: Calls, app: Int = 0, web: Int = 0, cancel: Int = 0) {
        compose.runOnIdle {
            assertEquals(app, calls.app.get())
            assertEquals(web, calls.web.get())
            assertEquals(cancel, calls.cancel.get())
        }
    }

    private class Calls {
        val app = AtomicInteger()
        val web = AtomicInteger()
        val cancel = AtomicInteger()
    }

    private companion object {
        const val SOURCE_HOST = "source.example"
        const val SCHEME = "sampleapp"
        const val PACKAGE_NAME = "dev.example.synthetic"
        const val FALLBACK_HOST = "fallback.example"
    }
}

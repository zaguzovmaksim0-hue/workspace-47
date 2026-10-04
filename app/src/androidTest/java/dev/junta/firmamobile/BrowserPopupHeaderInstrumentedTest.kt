// BrowserPopupHeaderInstrumentedTest.kt
package dev.junta.firmamobile

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.junta.firmamobile.ui.BrowserPopupHeader
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class BrowserPopupHeaderInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun explicitCloseInvokesCallbackOnlyOnButton() {
        val closeCalls = AtomicInteger(0)
        lateinit var title: String

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                title = activity.getString(R.string.browser_popup_title)
                activity.setContent {
                    MaterialTheme {
                        BrowserPopupHeader(
                            onClose = { closeCalls.incrementAndGet() },
                        )
                    }
                }
            }

            composeRule.runOnIdle {
                assertEquals(0, closeCalls.get())
            }
            composeRule.onNodeWithTag("browser-popup-header")
                .assertIsDisplayed()
                .performTouchInput { click(Offset(1f, 1f)) }
            composeRule.onNodeWithText(title, useUnmergedTree = true)
                .assertIsDisplayed()
                .performTouchInput { click() }
            composeRule.runOnIdle {
                assertEquals(0, closeCalls.get())
            }

            composeRule.onNodeWithTag("browser-popup-close")
                .assertIsDisplayed()
                .assertHasClickAction()
                .performClick()
            composeRule.runOnIdle {
                assertEquals(1, closeCalls.get())
            }
        }
    }

    @Test
    fun largeFontAtOnePointFiveKeepsTextAndButtonVisible() {
        lateinit var title: String
        lateinit var closeText: String

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                title = activity.getString(R.string.browser_popup_title)
                closeText = activity.getString(R.string.browser_popup_close)
                activity.setContent {
                    val density = LocalDensity.current
                    CompositionLocalProvider(
                        LocalDensity provides Density(
                            density = density.density,
                            fontScale = 1.5f,
                        ),
                    ) {
                        MaterialTheme {
                            BrowserPopupHeader(onClose = {})
                        }
                    }
                }
            }

            composeRule.onNodeWithTag("browser-popup-header")
                .assertIsDisplayed()
            composeRule.onNodeWithText(title, useUnmergedTree = true)
                .assertIsDisplayed()
            composeRule.onNodeWithText(closeText, useUnmergedTree = true)
                .assertIsDisplayed()
            composeRule.onNodeWithTag("browser-popup-close")
                .assertIsDisplayed()
                .assertHasClickAction()
        }
    }
}

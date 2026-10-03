package dev.junta.firmamobile

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.junta.firmamobile.ui.PublicBrowsingNotice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic UI only; no certificate material or network is used. */
@RunWith(AndroidJUnit4::class)
class PublicBrowsingNoticeInstrumentedTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun noticeAndLocalizedTitleAndExplanationAreDisplayed() {
        withNotice { title, explanation ->
            rule.onNodeWithTag("public-browsing-notice").assertIsDisplayed()
            rule.onNodeWithText(title).assertIsDisplayed()
            rule.onNodeWithText(explanation).assertIsDisplayed()
        }
    }

    @Test fun noticeExposesNoSigningOrUnlockAction() {
        withNotice { _, _ ->
            rule.onNodeWithTag("public-browsing-notice").assertIsDisplayed()
            rule.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(0)
        }
    }

    @Test fun titleAndExplanationAreDisplayedAtFontScaleOneAndAHalf() {
        withNotice(fontScale = 1.5f) { title, explanation ->
            rule.onNodeWithTag("public-browsing-notice").assertIsDisplayed()
            rule.onNodeWithText(title).assertIsDisplayed()
            rule.onNodeWithText(explanation).assertIsDisplayed()
        }
    }

    private fun withNotice(
        fontScale: Float? = null,
        assertions: (String, String) -> Unit
    ) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var title: String
            lateinit var explanation: String
            scenario.onActivity { activity ->
                title = activity.getString(R.string.public_browsing_title)
                explanation = activity.getString(R.string.public_browsing_explanation)
                activity.setContent {
                    val density = LocalDensity.current
                    CompositionLocalProvider(
                        LocalDensity provides Density(
                            density = density.density,
                            fontScale = fontScale ?: density.fontScale
                        )
                    ) {
                        MaterialTheme {
                            PublicBrowsingNotice()
                        }
                    }
                }
            }
            assertions(title, explanation)
        }
    }
}

package dev.junta.firmamobile

import android.view.WindowManager.LayoutParams.FLAG_SECURE
import android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
import android.view.WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLaunchTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    @Test
    fun showsRequiredFirstRunCopy() {
        TestCertificateDependencies.install().use {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
var activityContext: android.content.Context? = null
scenario.onActivity { activityContext = it }
val strings = checkNotNull(activityContext).resources
                // Catalog startup is asynchronous and is not a Compose idling resource.
                rule.waitUntil(timeoutMillis = 15_000) {
                    runCatching { rule.onNodeWithText(strings.getString(R.string.app_name)).assertIsDisplayed() }.isSuccess
                }
                rule.onNodeWithText(strings.getString(R.string.app_name)).assertIsDisplayed()
                rule.onNodeWithText(strings.getString(R.string.unofficial_disclosure)).assertIsDisplayed()
                rule.onNodeWithText(strings.getString(R.string.certificate_title)).assertIsDisplayed()
                rule.onNodeWithText(strings.getString(R.string.certificate_copy)).assertIsDisplayed()
                rule.onNodeWithText(strings.getString(R.string.privacy_copy))
                    .assertIsDisplayed()
                rule.onNodeWithText(strings.getString(R.string.select_certificate)).assertIsDisplayed()
                scenario.onActivity { activity ->
                    check(activity.window.attributes.flags and FLAG_SECURE == 0)
                    check(
                        activity.window.attributes.softInputMode and
                            SOFT_INPUT_MASK_ADJUST == SOFT_INPUT_ADJUST_RESIZE,
                    )
                }
            }
        }
    }
}

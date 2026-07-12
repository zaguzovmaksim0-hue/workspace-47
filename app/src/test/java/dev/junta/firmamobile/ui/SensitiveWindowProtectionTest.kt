package dev.junta.firmamobile.ui

import android.view.WindowManager.LayoutParams.FLAG_SECURE
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class SensitiveWindowProtectionTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun realWindowFlagCanBeEnabledAndCleared() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java)
            .setup()
            .get()

        WindowSecureFlagPolicy.apply(activity.window, sensitive = true)
        assertTrue(activity.window.attributes.flags and FLAG_SECURE != 0)

        WindowSecureFlagPolicy.apply(activity.window, sensitive = false)
        assertEquals(0, activity.window.attributes.flags and FLAG_SECURE)
    }

    @Test
    fun sensitiveEffectClearsProtectionWhenItLeavesComposition() {
        val visible = mutableStateOf(true)
        val events = mutableListOf<Boolean>()
        rule.setContent {
            if (visible.value) {
                SensitiveWindowProtection(enabled = true) { events += it }
            }
        }
        rule.runOnIdle {
            assertEquals(true, events.last())
            visible.value = false
        }

        rule.runOnIdle {
            assertEquals(false, events.last())
        }
    }
}

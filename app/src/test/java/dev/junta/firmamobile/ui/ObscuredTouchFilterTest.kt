package dev.junta.firmamobile.ui

import android.view.MotionEvent
import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalView
import dev.junta.firmamobile.testing.createComposeRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class ObscuredTouchFilterTest {
    @get:Rule val rule = createComposeRule()

    @Test fun obscuredTouchesAreRejectedAndOrdinaryTouchesRemainAllowed() {
        val view = View(RuntimeEnvironment.getApplication())
        rule.setContent {
            CompositionLocalProvider(LocalView provides view) { FilterObscuredTouches() }
        }
        rule.runOnIdle {
            assertFalse(acceptsTouch(view, MotionEvent.FLAG_WINDOW_IS_OBSCURED))
            assertTrue(acceptsTouch(view, 0))
        }
    }

    @Test fun disposalRestoresThePreviousFilterForEachView() {
        val first = View(RuntimeEnvironment.getApplication())
        val second = View(RuntimeEnvironment.getApplication()).apply { filterTouchesWhenObscured = true }
        val current = mutableStateOf(first)
        val visible = mutableStateOf(true)
        rule.setContent {
            if (visible.value) {
                CompositionLocalProvider(LocalView provides current.value) { FilterObscuredTouches() }
            }
        }
        rule.runOnIdle {
            assertTrue(first.filterTouchesWhenObscured)
            current.value = second
        }
        rule.runOnIdle {
            assertFalse(first.filterTouchesWhenObscured)
            assertTrue(second.filterTouchesWhenObscured)
            visible.value = false
        }
        rule.runOnIdle { assertTrue(second.filterTouchesWhenObscured) }
    }

    private fun acceptsTouch(view: View, flags: Int): Boolean {
        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 1,
            arrayOf(MotionEvent.PointerProperties().apply { id = 0 }),
            arrayOf(MotionEvent.PointerCoords().apply { x = 1f; y = 1f }),
            0, 0, 1f, 1f, 0, 0, 0, flags)
        return try { view.onFilterTouchEventForSecurity(event) } finally { event.recycle() }
    }
}

package dev.junta.firmamobile.ui

import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertEquals
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
class BrowserWindowInsetsTest {
    @Test
    fun nativePolicyUsesMaxImeAndNavigationInsetWithoutAccumulation() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java)
            .setup()
            .get()
        val root = FrameLayout(activity).apply {
            setPadding(3, 5, 7, 11)
        }
        activity.setContentView(root)
        BrowserWindowInsetsPolicy.install(root)
        BrowserWindowInsetsPolicy.install(root)
        val threeButtonNavigation = WindowInsetsCompat.Builder()
            .setInsets(
                WindowInsetsCompat.Type.statusBars(),
                Insets.of(0, 32, 0, 0),
            )
            .setVisible(WindowInsetsCompat.Type.statusBars(), true)
            .setInsets(
                WindowInsetsCompat.Type.navigationBars(),
                Insets.of(1, 0, 2, 96),
            )
            .setVisible(WindowInsetsCompat.Type.navigationBars(), true)
            .setVisible(WindowInsetsCompat.Type.ime(), false)
            .build()
        val keyboard = WindowInsetsCompat.Builder(threeButtonNavigation)
            .setInsets(
                WindowInsetsCompat.Type.ime(),
                Insets.of(0, 0, 0, 320),
            )
            .setVisible(WindowInsetsCompat.Type.ime(), true)
            .build()

        ViewCompat.dispatchApplyWindowInsets(root, threeButtonNavigation)

        assertEquals(4, root.paddingLeft)
        assertEquals(37, root.paddingTop)
        assertEquals(9, root.paddingRight)
        assertEquals(107, root.paddingBottom)

        ViewCompat.dispatchApplyWindowInsets(root, keyboard)

        assertEquals(4, root.paddingLeft)
        assertEquals(37, root.paddingTop)
        assertEquals(9, root.paddingRight)
        assertEquals(331, root.paddingBottom)

        ViewCompat.dispatchApplyWindowInsets(root, threeButtonNavigation)

        assertEquals(4, root.paddingLeft)
        assertEquals(37, root.paddingTop)
        assertEquals(9, root.paddingRight)
        assertEquals(107, root.paddingBottom)

        val gesturalNavigation = WindowInsetsCompat.Builder()
            .setInsets(
                WindowInsetsCompat.Type.navigationBars(),
                Insets.of(0, 0, 0, 24),
            )
            .setVisible(WindowInsetsCompat.Type.navigationBars(), true)
            .setVisible(WindowInsetsCompat.Type.ime(), false)
            .build()
        ViewCompat.dispatchApplyWindowInsets(root, gesturalNavigation)
        ViewCompat.dispatchApplyWindowInsets(root, gesturalNavigation)

        assertEquals(35, root.paddingBottom)
    }
}

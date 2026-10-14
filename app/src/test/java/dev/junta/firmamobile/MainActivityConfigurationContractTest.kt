package dev.junta.firmamobile

import android.content.ComponentName
import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class MainActivityConfigurationContractTest {
    @Test
    fun rotationResizeAndNightModeKeepTheBrowserActivity() {
        val context = RuntimeEnvironment.getApplication()
        val info = context.packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java),
            0,
        )

        // Recreation would reset the destination to the certificate screen and drop
        // the open portal, because WebView state is never restored from a Bundle.
        assertEquals(HANDLED_WITHOUT_RECREATION, info.configChanges and HANDLED_WITHOUT_RECREATION)
    }

    private companion object {
        const val HANDLED_WITHOUT_RECREATION =
            ActivityInfo.CONFIG_ORIENTATION or
                ActivityInfo.CONFIG_SCREEN_SIZE or
                ActivityInfo.CONFIG_SCREEN_LAYOUT or
                ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE or
                ActivityInfo.CONFIG_KEYBOARD or
                ActivityInfo.CONFIG_KEYBOARD_HIDDEN or
                ActivityInfo.CONFIG_NAVIGATION or
                ActivityInfo.CONFIG_UI_MODE
    }
}

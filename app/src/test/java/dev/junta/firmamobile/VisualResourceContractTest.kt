package dev.junta.firmamobile

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
class VisualResourceContractTest {
    @Test
    fun launcherAndHomeArtworkExposeCompleteAndroidResourceContract() {
        val context = RuntimeEnvironment.getApplication()
        val resources = context.resources
        val packageName = context.packageName
        val applicationInfo = context.packageManager.getApplicationInfo(
            packageName,
            ApplicationInfoFlags,
        )

        assertEquals(R.mipmap.ic_launcher, applicationInfo.icon)
        assertNotEquals(
            0,
            resources.getIdentifier("ic_launcher_background", "drawable", packageName),
        )
        assertNotEquals(
            0,
            resources.getIdentifier("ic_launcher_round", "mipmap", packageName),
        )
        assertNotEquals(
            0,
            resources.getIdentifier("jfm_home_background", "drawable", packageName),
        )
    }

    private companion object {
        const val ApplicationInfoFlags = 0
    }
}

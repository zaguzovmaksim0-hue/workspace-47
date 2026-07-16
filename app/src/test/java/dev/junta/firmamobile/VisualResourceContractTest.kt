package dev.junta.firmamobile

import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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
        assertEquals("Junta Firma", applicationInfo.loadLabel(context.packageManager).toString())
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
            resources.getIdentifier("ic_launcher_monochrome", "drawable", packageName),
        )
        assertNotEquals(
            0,
            resources.getIdentifier("jfm_home_background", "drawable", packageName),
        )
        assertEquals(0, resources.getIdentifier("navigation_history", "string", packageName))

        val foreground = (resources.getDrawable(
            R.drawable.ic_launcher_foreground,
            context.theme,
        ) as BitmapDrawable).bitmap
        val foregroundBounds = foreground.alphaBounds()
        assertTrue(foregroundBounds.width * 2 <= foreground.width)
        assertTrue(foregroundBounds.height * 2 <= foreground.height)
        assertTrue(foregroundBounds.left > foreground.width / 4)
        assertTrue(foregroundBounds.top > foreground.height / 4)
    }

    private fun Bitmap.alphaBounds(): AlphaBounds {
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if ((getPixel(x, y) ushr 24) != 0) {
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x)
                    bottom = maxOf(bottom, y)
                }
            }
        }
        check(right >= left && bottom >= top)
        return AlphaBounds(left, top, right - left + 1, bottom - top + 1)
    }

    private data class AlphaBounds(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
    )

    private companion object {
        const val ApplicationInfoFlags = 0
    }
}

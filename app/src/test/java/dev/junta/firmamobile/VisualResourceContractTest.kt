package dev.junta.firmamobile

import android.content.pm.ApplicationInfo
import android.graphics.RectF
import androidx.core.graphics.PathParser
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

        val foregroundBounds = vectorPathBounds(R.drawable.ic_launcher_foreground)
        assertTrue(foregroundBounds.bounds.width() * 2 <= foregroundBounds.viewportWidth)
        assertTrue(foregroundBounds.bounds.height() * 2 <= foregroundBounds.viewportHeight)
        assertTrue(foregroundBounds.bounds.left > foregroundBounds.viewportWidth / 4)
        assertTrue(foregroundBounds.bounds.top > foregroundBounds.viewportHeight / 4)
    }

    private fun vectorPathBounds(resourceId: Int): VectorBounds {
        val parser = RuntimeEnvironment.getApplication().resources.getXml(resourceId)
        var viewportWidth = -1f
        var viewportHeight = -1f
        val combinedBounds = RectF()
        var hasPath = false
        while (parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG) {
                when (parser.name) {
                    "vector" -> {
                        viewportWidth = parser.getAttributeFloatValue(ANDROID_NS, "viewportWidth", -1f)
                        viewportHeight = parser.getAttributeFloatValue(ANDROID_NS, "viewportHeight", -1f)
                    }
                    "path" -> {
                        val pathData = checkNotNull(parser.getAttributeValue(ANDROID_NS, "pathData"))
                        val path = checkNotNull(PathParser.createPathFromPathData(pathData))
                        val pathBounds = RectF()
                        path.computeBounds(pathBounds, true)
                        if (hasPath) combinedBounds.union(pathBounds) else combinedBounds.set(pathBounds)
                        hasPath = true
                    }
                }
            }
            parser.next()
        }
        parser.close()
        check(viewportWidth > 0f && viewportHeight > 0f && hasPath)
        return VectorBounds(viewportWidth, viewportHeight, RectF(combinedBounds))
    }

    private data class VectorBounds(
        val viewportWidth: Float,
        val viewportHeight: Float,
        val bounds: RectF,
    )

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        const val ApplicationInfoFlags = 0
    }
}

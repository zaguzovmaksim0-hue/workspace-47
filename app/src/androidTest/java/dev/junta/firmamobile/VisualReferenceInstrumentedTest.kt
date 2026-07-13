package dev.junta.firmamobile

import android.graphics.Bitmap
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisualReferenceInstrumentedTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    @Test
    fun capturesSafeFirstRunReferenceScreen() {
        TestCertificateDependencies.install().use {
            ActivityScenario.launch(MainActivity::class.java).use {
                rule.onNodeWithText("Junta Firma Mobile").assertIsDisplayed()
                rule.onNodeWithText("Seleccionar certificado").assertIsDisplayed()
                rule.waitForIdle()

                val instrumentation = InstrumentationRegistry.getInstrumentation()
                val screenshot = instrumentation.uiAutomation.takeScreenshot()
                check(screenshot.width > 0 && screenshot.height > 0)
                val outputDir = File(
                    instrumentation.targetContext.getExternalFilesDir(null),
                    "visual-qa",
                ).apply { mkdirs() }
                val output = File(outputDir, "junta-firma-home-reference.png")
                FileOutputStream(output).use { stream ->
                    check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream))
                }
                screenshot.recycle()
            }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 33)
    fun launcherIconProvidesAdaptiveColorAndThemedLayers() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val icon = context.packageManager.getApplicationIcon(context.packageName)

        check(icon is AdaptiveIconDrawable)
        check(icon.background != null)
        check(icon.foreground != null)
        check(icon.monochrome != null)
    }
}

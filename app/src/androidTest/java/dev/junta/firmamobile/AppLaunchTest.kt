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
                rule.onNodeWithText("Junta Firma Mobile").assertIsDisplayed()
                rule.onNodeWithText("Cliente no oficial para uso personal").assertIsDisplayed()
                rule.onNodeWithText("Certificado digital").assertIsDisplayed()
                rule.onNodeWithText("Selecciona tu archivo .p12 o .pfx.").assertIsDisplayed()
                rule.onNodeWithText("El archivo y la contraseña no se enviarán a terceros.")
                    .assertIsDisplayed()
                rule.onNodeWithText("Seleccionar certificado").assertIsDisplayed()
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

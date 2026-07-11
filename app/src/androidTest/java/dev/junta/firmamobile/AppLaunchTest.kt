package dev.junta.firmamobile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLaunchTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun showsRequiredFirstRunCopy() {
        rule.onNodeWithText("Junta Firma Mobile").assertIsDisplayed()
        rule.onNodeWithText("Cliente no oficial para uso personal").assertIsDisplayed()
        rule.onNodeWithText("Certificado digital").assertIsDisplayed()
        rule.onNodeWithText("Selecciona tu archivo .p12 o .pfx.").assertIsDisplayed()
        rule.onNodeWithText("El archivo y la contraseña no se enviarán a terceros.").assertIsDisplayed()
        rule.onNodeWithText("Seleccionar certificado").assertIsDisplayed()
    }
}

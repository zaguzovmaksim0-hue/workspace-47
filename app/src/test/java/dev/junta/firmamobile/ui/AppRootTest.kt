package dev.junta.firmamobile.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class AppRootTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun showsPhaseTwoNoticeWhenCertificateButtonIsClicked() {
        val phaseTwoNotice = "La selección de certificados estará disponible en la fase 2."
        rule.setContent {
            JuntaFirmaTheme {
                AppRoot()
            }
        }

        rule.onNodeWithText(phaseTwoNotice).assertDoesNotExist()
        rule.onNodeWithText("Seleccionar certificado").performClick()
        rule.onNodeWithText(phaseTwoNotice).assertIsDisplayed()
    }
}

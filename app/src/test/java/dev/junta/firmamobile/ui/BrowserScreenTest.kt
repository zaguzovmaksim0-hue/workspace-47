package dev.junta.firmamobile.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
class BrowserScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun browserChromeExposesNavigationCertificateAndConfirmedSessionActions() {
        val events = mutableListOf<String>()
        rule.setContent {
            JuntaFirmaTheme {
                BrowserLayout(
                    certificateOwner = "Persona de Prueba",
                    onBack = { events += "back" },
                    onHome = { events += "home" },
                    onReload = { events += "reload" },
                    onChangeCertificate = { events += "change" },
                    onLockCertificate = { events += "lock" },
                    onClearSession = { events += "clear" },
                ) {
                    Text("contenido-web")
                }
            }
        }

        rule.onNodeWithText("Portal Junta de Andalucía").assertIsDisplayed()
        rule.onNodeWithText("Certificado: Persona de Prueba").assertIsDisplayed()
        rule.onNodeWithText("contenido-web").assertIsDisplayed()

        rule.onNodeWithContentDescription("Atrás").performClick()
        rule.onNodeWithContentDescription("Inicio").performClick()
        rule.onNodeWithContentDescription("Recargar").performClick()
        rule.runOnIdle { check(events == listOf("back", "home", "reload")) }

        rule.onNodeWithContentDescription("Más opciones").performClick()
        rule.onNodeWithText("Cambiar certificado").performClick()
        rule.onNodeWithContentDescription("Más opciones").performClick()
        rule.onNodeWithText("Bloquear certificado").performClick()
        rule.runOnIdle { check(events.takeLast(2) == listOf("change", "lock")) }

        rule.onNodeWithContentDescription("Más opciones").performClick()
        rule.onNodeWithText("Cerrar sesión").performClick()
        rule.onNodeWithText("Cerrar sesión y borrar datos").assertIsDisplayed()
        rule.runOnIdle { check("clear" !in events) }
        rule.onNodeWithText("Confirmar cierre").performClick()
        rule.runOnIdle { check(events.last() == "clear") }
    }
}

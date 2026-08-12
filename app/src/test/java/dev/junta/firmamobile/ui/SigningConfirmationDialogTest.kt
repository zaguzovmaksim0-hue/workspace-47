package dev.junta.firmamobile.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.junta.firmamobile.signing.SigningUiState
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
import java.util.UUID
import org.junit.Assert.assertEquals
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
class SigningConfirmationDialogTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun confirmationShowsExactSafeSummaryAndNeverActsBeforeExplicitClick() {
        val events = mutableListOf<String>()
        rule.setContent {
            JuntaFirmaTheme {
                SigningConfirmationDialog(
                    state = state(requiresLegacySha1Warning = true),
                    onConfirm = { events += "confirm" },
                    onCancel = { events += "cancel" },
                )
            }
        }

        rule.onNodeWithText("Solicitud de firma").assertIsDisplayed()
        rule.onNodeWithText("Sitio: www.juntadeandalucia.es").assertIsDisplayed()
        rule.onNodeWithText("Perfil: Junta de Andalucía").assertIsDisplayed()
        rule.onNodeWithText("Nivel de soporte: VERIFIED_E2E").assertIsDisplayed()
        rule.onNodeWithText("Operación: Autenticación con certificado").assertIsDisplayed()
        rule.onNodeWithText("Certificado: Persona de Prueba").assertIsDisplayed()
        rule.onNodeWithText("Formato: CAdES").assertIsDisplayed()
        rule.onNodeWithText("Algoritmo: SHA1withRSA").assertIsDisplayed()
        rule.onNodeWithText("SHA-1", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(RAW_PAYLOAD).assertDoesNotExist()
        rule.runOnIdle { assertEquals(emptyList<String>(), events) }

        rule.onNodeWithText("Firmar").performClick()
        rule.runOnIdle { assertEquals(listOf("confirm"), events) }
    }

    @Test
    fun cancelIsTheOnlyDismissActionAndDoesNotConfirm() {
        val events = mutableListOf<String>()
        rule.setContent {
            JuntaFirmaTheme {
                SigningConfirmationDialog(
                    state = state(requiresLegacySha1Warning = false),
                    onConfirm = { events += "confirm" },
                    onCancel = { events += "cancel" },
                )
            }
        }

        rule.onNodeWithText("Cancelar").performClick()

        rule.runOnIdle { assertEquals(listOf("cancel"), events) }
        rule.onNodeWithText("SHA-1", substring = true).assertDoesNotExist()
    }

    private fun state(requiresLegacySha1Warning: Boolean) =
        SigningUiState.AwaitingConfirmation(
            requestId = UUID.fromString("123e4567-e89b-42d3-a456-426614174000"),
            siteHost = "www.juntadeandalucia.es",
            profileName = "Junta de Andalucía",
            supportLevel = "VERIFIED_E2E",
            safeDescription = "Autenticación con certificado",
            format = "CAdES",
            algorithm = if (requiresLegacySha1Warning) "SHA1withRSA" else "SHA256withRSA",
            certificateOwner = "Persona de Prueba",
            requiresLegacySha1Warning = requiresLegacySha1Warning,
        )

    private companion object {
        const val RAW_PAYLOAD = "synthetic-secret-payload-base64"
    }
}

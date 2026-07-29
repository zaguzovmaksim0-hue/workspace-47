package dev.junta.firmamobile.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.junta.firmamobile.signing.SigningErrorCode
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
class SigningStatusDialogTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun signingStateShowsNonDismissibleProgressWithoutSensitiveData() {
        rule.setContent {
            JuntaFirmaTheme {
                SigningStatusDialog(
                    state = SigningUiState.Signing(REQUEST_ID),
                    onDismiss = {},
                )
            }
        }

        rule.onNodeWithText("Firmando…").assertIsDisplayed()
        rule.onNodeWithText("No cierres la aplicación.").assertIsDisplayed()
        rule.onNodeWithText("Cerrar").assertDoesNotExist()
        rule.onNodeWithText(RAW_PAYLOAD).assertDoesNotExist()
    }

    @Test
    fun secureConnectionProgressUsesExactSafeSpanishCopy() {
        rule.setContent {
            JuntaFirmaTheme {
                SigningStatusDialog(
                    state = SigningUiState.ConnectingSecurely(REQUEST_ID),
                    onDismiss = {},
                )
            }
        }

        val copy = "Conectando de forma segura con el servicio de firma…"
        rule.onNodeWithText(copy).assertIsDisplayed()
        rule.onNodeWithText("Cerrar").assertDoesNotExist()
        for (forbidden in FORBIDDEN_UI_TERMS) {
            rule.onNodeWithText(forbidden, substring = true, ignoreCase = true).assertDoesNotExist()
        }
        rule.onNodeWithText(REQUEST_ID.toString(), substring = true).assertDoesNotExist()
    }

    @Test
    fun closedNetworkErrorsUseExactActionableSpanishCopy() {
        var state by mutableStateOf<SigningUiState>(
            SigningUiState.Failed(REQUEST_ID, SigningErrorCode.SIGNING_SERVICE_UNAVAILABLE),
        )
        rule.setContent {
            JuntaFirmaTheme {
                SigningStatusDialog(state = state, onDismiss = {})
            }
        }

        rule.onNodeWithText(
            "No se pudo conectar con el servicio de firma de la Junta. Inténtalo de nuevo más tarde.",
        ).assertIsDisplayed()
        rule.onNodeWithText("Código: SIGNING_SERVICE_UNAVAILABLE").assertDoesNotExist()

        rule.runOnIdle {
            state = SigningUiState.Failed(REQUEST_ID, SigningErrorCode.NETWORK_RESULT_UNCERTAIN)
        }
        rule.onNodeWithText(
            "El resultado de red no es seguro. Vuelve al portal e inicia la operación de nuevo.",
        ).assertIsDisplayed()
        rule.onNodeWithText("Código: NETWORK_RESULT_UNCERTAIN").assertDoesNotExist()
        for (forbidden in FORBIDDEN_UI_TERMS) {
            rule.onNodeWithText(forbidden, substring = true, ignoreCase = true).assertDoesNotExist()
        }
    }

    @Test
    fun terminalStatesExposeOnlySuccessOrClosedErrorAndDismiss() {
        val events = mutableListOf<String>()
        var state by mutableStateOf<SigningUiState>(
            SigningUiState.Failed(
                requestId = REQUEST_ID,
                code = SigningErrorCode.PROTOCOL_FAILED,
            ),
        )
        rule.setContent {
            JuntaFirmaTheme {
                SigningStatusDialog(
                    state = state,
                    onDismiss = { events += "dismiss" },
                )
            }
        }

        rule.onNodeWithText("No se pudo completar la firma").assertIsDisplayed()
        rule.onNodeWithText("Código: PROTOCOL_FAILED").assertIsDisplayed()
        rule.onNodeWithText(RAW_PAYLOAD).assertDoesNotExist()
        rule.onNodeWithText("Cerrar").performClick()
        rule.runOnIdle { assertEquals(listOf("dismiss"), events) }

        rule.runOnIdle {
            state = SigningUiState.Completed(REQUEST_ID)
        }
        rule.onNodeWithText("Firma enviada al portal").assertIsDisplayed()
        rule.onNodeWithText(
            "La aplicación entregó el resultado a la página. El acceso solo termina cuando el portal lo confirma.",
        ).assertIsDisplayed()
        rule.onNodeWithText("Cerrar").performClick()
        rule.runOnIdle {
            assertEquals(listOf("dismiss", "dismiss"), events)
        }
    }

    private companion object {
        val REQUEST_ID: UUID = UUID.fromString("123e4567-e89b-42d3-a456-426614174000")
        const val RAW_PAYLOAD = "synthetic-secret-payload-base64"
        val FORBIDDEN_UI_TERMS = listOf(
            "proxy", "CONNECT", "Tor", "relay.example", "Authorization",
            "Bearer", "certificado: María", RAW_PAYLOAD,
        )
    }
}

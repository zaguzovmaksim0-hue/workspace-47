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
        rule.onNodeWithText("Firma completada").assertIsDisplayed()
        rule.onNodeWithText("Cerrar").performClick()
        rule.runOnIdle {
            assertEquals(listOf("dismiss", "dismiss"), events)
        }
    }

    private companion object {
        val REQUEST_ID: UUID = UUID.fromString("123e4567-e89b-42d3-a456-426614174000")
        const val RAW_PAYLOAD = "synthetic-secret-payload-base64"
    }
}

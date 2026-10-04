package dev.junta.firmamobile.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.*
import dev.junta.firmamobile.afirma.servlet.*
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
class NativeAfirmaConsentDialogTest {
    @get:Rule val rule = createComposeRule()
    private fun prompt() = AfirmaConsentPrompt(
        UUID.randomUUID(),
        AfirmaConsentDetails("https://page.synthetic.example", "https://store.synthetic.example/storage",
            "sign", "CAdES detached", "SHA256withRSA", 12, "a".repeat(64)),
        AfirmaConsentPhase.REVIEW, "Certificado de prueba", null,
    )

    @Test fun detailsAreCollapsedAndNeverGrantConsentAndResetForNewRequest() {
        val state = mutableStateOf(prompt())
        var actions = 0
        rule.setContent { JuntaFirmaTheme {
            NativeAfirmaConsentDialog(state.value, { actions++ }, { actions++ }, { actions++ }, { actions++ })
        } }
        rule.onNodeWithText(state.value.details.destination).assertExists()
        rule.onNodeWithText("Certificado de prueba").assertExists()
        rule.onNodeWithTag("native-afirma-details").assertDoesNotExist()
        rule.onNodeWithTag("native-afirma-details-toggle").performScrollTo().performClick()
        rule.onNodeWithTag("native-afirma-details").assertExists()
        rule.onNodeWithText("Algoritmo: SHA256withRSA").assertExists()
        rule.onNodeWithTag("native-afirma-details-toggle").performScrollTo().performClick()
        rule.onNodeWithTag("native-afirma-details").assertDoesNotExist()
        rule.onNodeWithTag("native-afirma-details-toggle").performScrollTo().performClick()
        rule.runOnIdle { state.value = prompt(); assertEquals(0, actions) }
        rule.onNodeWithTag("native-afirma-details").assertDoesNotExist()
        rule.onNodeWithTag("native-afirma-confirm").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(1, actions) }
    }

    @Test fun lockedCertificateOnlyUnlocksAndSendingNeverOffersSigningAgain() {
        val state = mutableStateOf(prompt().copy(certificateOwner = null, problem = AfirmaConsentProblem.LOCKED))
        var signed = 0; var unlocked = 0; var cancelled = 0
        rule.setContent { JuntaFirmaTheme {
            NativeAfirmaConsentDialog(state.value, { signed++ }, { unlocked++ }, { cancelled++ }, {})
        } }
        rule.onNodeWithTag("native-afirma-confirm").assertDoesNotExist()
        rule.onNodeWithTag("native-afirma-unlock").performClick()
        rule.runOnIdle { assertEquals(1, unlocked); assertEquals(0, signed)
            state.value = prompt().copy(phase = AfirmaConsentPhase.SENDING)
        }
        rule.onNodeWithTag("native-afirma-confirm").assertDoesNotExist()
        rule.onNodeWithTag("native-afirma-unlock").assertDoesNotExist()
        rule.onNodeWithTag("native-afirma-progress").assertExists()
        rule.onNodeWithTag("native-afirma-cancel").performClick()
        rule.runOnIdle { assertEquals(1, cancelled); assertEquals(0, signed) }
    }

    @Test fun uncertainResultAndHashWarningStayOutsideCollapsedDetails() {
        val original = prompt()
        val state = mutableStateOf(original.copy(details = original.details.copy(providedDigestAlgorithm = "SHA-256")))
        var closed = 0
        rule.setContent { JuntaFirmaTheme {
            NativeAfirmaConsentDialog(state.value, {}, {}, {}, { closed++ })
        } }
        rule.onNodeWithTag("native-cades-provided-hash-notice").assertExists()
        rule.onNodeWithTag("native-afirma-details").assertDoesNotExist()
        rule.runOnIdle { state.value = original.copy(phase = AfirmaConsentPhase.FINISHED, problem = AfirmaConsentProblem.UNCERTAIN) }
        rule.onNodeWithText("No se pudo confirmar el resultado del envío. Puede haber llegado al servidor. No se repetirá automáticamente; comprueba el estado en la página.").assertExists()
        rule.onNodeWithTag("native-afirma-confirm").assertDoesNotExist()
        rule.onNodeWithTag("native-afirma-close").performClick()
        rule.runOnIdle { assertEquals(1, closed) }
    }
}

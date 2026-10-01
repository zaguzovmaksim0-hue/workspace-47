package dev.junta.firmamobile

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.junta.firmamobile.browser.InteractiveClientAuthProblem
import dev.junta.firmamobile.browser.InteractiveClientAuthPrompt
import dev.junta.firmamobile.ui.InteractiveClientAuthDialog
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UI-only synthetic prompts. No TLS server, real certificate or account is used. */
@RunWith(AndroidJUnit4::class)
class InteractiveClientAuthDialogInstrumentedTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun lockedPromptOffersUnlockWithoutInvokingCertificateConfirmation() {
        var unlocked = 0; var confirmed = 0
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    InteractiveClientAuthDialog(
                        prompt = prompt(null, InteractiveClientAuthProblem.NO_CERTIFICATE),
                        onConfirm = { confirmed++ }, onUnlock = { unlocked++ }, onCancel = {},
                    )
                }
            }
            rule.onNodeWithTag("interactive-client-auth-unlock").assertIsDisplayed().performClick()
            rule.onNodeWithTag("interactive-client-auth-confirm").assertDoesNotExist()
            rule.runOnIdle { assertEquals(1, unlocked); assertEquals(0, confirmed) }
        }
    }

    @Test fun readyPromptSeparatesTheRequestingServerFromTheOpenPage() {
        var confirmed = 0; var unlocked = 0
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    InteractiveClientAuthDialog(
                        prompt = prompt("Persona de prueba", null),
                        onConfirm = { confirmed++ }, onUnlock = { unlocked++ }, onCancel = {},
                    )
                }
            }
            rule.onNodeWithText("Servidor que solicita el certificado: auth.synthetic.example").assertIsDisplayed()
            rule.onNodeWithText("Página abierta: https://portal.synthetic.example").assertIsDisplayed()
            rule.onNodeWithTag("interactive-client-auth-confirm").assertIsDisplayed().performClick()
            rule.runOnIdle { assertEquals(1, confirmed); assertEquals(0, unlocked) }
        }
    }

    @Test fun incompatibleCertificateCannotBeSentAndCancelIsExplicit() {
        var confirmed = 0; var canceled = 0
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    InteractiveClientAuthDialog(
                        prompt = prompt("Persona de prueba", InteractiveClientAuthProblem.INCOMPATIBLE_CERTIFICATE),
                        onConfirm = { confirmed++ }, onUnlock = {}, onCancel = { canceled++ },
                    )
                }
            }
            rule.onNodeWithTag("interactive-client-auth-confirm").assertDoesNotExist()
            rule.onNodeWithTag("interactive-client-auth-cancel").performClick()
            rule.runOnIdle { assertEquals(0, confirmed); assertEquals(1, canceled) }
        }
    }

    private fun prompt(owner: String?, problem: InteractiveClientAuthProblem?) = InteractiveClientAuthPrompt(
        UUID.randomUUID(), "auth.synthetic.example", "https://portal.synthetic.example", owner, problem,
    )
}

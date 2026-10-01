package dev.junta.firmamobile

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.junta.firmamobile.afirma.servlet.AfirmaConsentDetails
import dev.junta.firmamobile.afirma.servlet.AfirmaConsentPhase
import dev.junta.firmamobile.afirma.servlet.AfirmaConsentProblem
import dev.junta.firmamobile.afirma.servlet.AfirmaConsentPrompt
import dev.junta.firmamobile.ui.NativeAfirmaConsentDialog
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Pure synthetic UI decisions; no certificate material or network is used. */
@RunWith(AndroidJUnit4::class)
class NativeAfirmaConsentInstrumentedTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun lockedReviewOnlyOpensCertificatePanelAndDoesNotConfirm() {
        var unlocked = 0; var confirmed = 0
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                NativeAfirmaConsentDialog(prompt(AfirmaConsentPhase.REVIEW, AfirmaConsentProblem.LOCKED),
                    onConfirm = { confirmed++ }, onUnlock = { unlocked++ }, onCancel = {}, onDismiss = {})
            } }
            rule.onNodeWithTag("native-afirma-unlock").assertIsDisplayed().performClick()
            rule.onNodeWithTag("native-afirma-confirm").assertDoesNotExist()
            rule.runOnIdle { assertEquals(0, confirmed); assertEquals(1, unlocked) }
        }
    }

    @Test fun readyDocumentRequiresTheExplicitSignAndSendAction() {
        var confirmed = 0; var canceled = 0
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                NativeAfirmaConsentDialog(prompt(AfirmaConsentPhase.REVIEW, null),
                    onConfirm = { confirmed++ }, onUnlock = {}, onCancel = { canceled++ }, onDismiss = {})
            } }
            rule.runOnIdle { assertEquals(0, confirmed) }
            rule.onNodeWithTag("native-afirma-confirm").assertIsDisplayed().performClick()
            rule.runOnIdle { assertEquals(1, confirmed); assertEquals(0, canceled) }
        }
    }

    @Test fun sendingHasNoRetryOrSecondConfirmationAction() {
        var canceled = 0; var confirmed = 0
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                NativeAfirmaConsentDialog(prompt(AfirmaConsentPhase.SENDING, null),
                    onConfirm = { confirmed++ }, onUnlock = {}, onCancel = { canceled++ }, onDismiss = {})
            } }
            rule.onNodeWithTag("native-afirma-confirm").assertDoesNotExist()
            rule.onNodeWithTag("native-afirma-unlock").assertDoesNotExist()
            rule.onNodeWithTag("native-afirma-cancel").assertIsDisplayed().performClick()
            rule.runOnIdle { assertEquals(0, confirmed); assertEquals(1, canceled) }
        }
    }

    @Test fun uncertainReceiptIsDismissedOnlyByTheCloseActionNotRetried() {
        var closed = 0; var confirmed = 0
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                NativeAfirmaConsentDialog(prompt(AfirmaConsentPhase.FINISHED, AfirmaConsentProblem.UNCERTAIN),
                    onConfirm = { confirmed++ }, onUnlock = {}, onCancel = {}, onDismiss = { closed++ })
            } }
            rule.onNodeWithTag("native-afirma-confirm").assertDoesNotExist()
            rule.onNodeWithTag("native-afirma-cancel").assertDoesNotExist()
            rule.onNodeWithTag("native-afirma-close").assertIsDisplayed().performClick()
            rule.runOnIdle { assertEquals(0, confirmed); assertEquals(1, closed) }
        }
    }

    private fun prompt(phase: AfirmaConsentPhase, problem: AfirmaConsentProblem?) = AfirmaConsentPrompt(
        UUID.randomUUID(), AfirmaConsentDetails("https://page.synthetic.example", "https://store.synthetic.example/storage",
            "sign", "CAdES detached", "SHA256withRSA", 12, "a".repeat(64)),
        phase, if (problem == AfirmaConsentProblem.LOCKED) null else "Certificado de prueba", problem,
    )
}

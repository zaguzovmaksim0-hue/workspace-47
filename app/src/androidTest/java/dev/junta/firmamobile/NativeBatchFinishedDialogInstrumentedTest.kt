package dev.junta.firmamobile

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.junta.firmamobile.afirma.servlet.AfirmaConsentDetails
import dev.junta.firmamobile.afirma.servlet.AfirmaConsentPhase
import dev.junta.firmamobile.afirma.servlet.AfirmaConsentProblem
import dev.junta.firmamobile.afirma.servlet.AfirmaConsentPrompt
import dev.junta.firmamobile.afirma.servlet.NativeBatchReceipt
import dev.junta.firmamobile.ui.NativeAfirmaConsentDialog
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeBatchFinishedDialogInstrumentedTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun thirtyTwoResultsRemainScrollableWithoutTurningUncertainDeliveryIntoSuccess() {
        val receipt = NativeBatchReceipt(NativeBatchReceipt.Origin.SERVICE, (1..32).map {
            NativeBatchReceipt.Entry("Document $it", if (it == 32) "ERROR_POST" else "DONE_AND_SAVED", if (it == 32) "Synthetic service failure" else null)
        })
        var closed = 0; var signed = 0
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            scenario.onActivity { a ->
                activity = a
                a.setContent {
                    NativeAfirmaConsentDialog(prompt(receipt, AfirmaConsentPhase.FINISHED, AfirmaConsentProblem.UNCERTAIN),
                        onConfirm = { signed++ }, onUnlock = { error("No unlock for completed results") },
                        onCancel = { error("No cancellation upload from result view") }, onDismiss = { closed++ })
                }
            }
            rule.onNodeWithTag("native-batch-details-toggle").performScrollTo().performClick()
            rule.onNodeWithTag("native-batch-result-31").performScrollTo().assertIsDisplayed()
            rule.onNodeWithText(activity.getString(R.string.native_batch_id, 32, "Document 32")).assertIsDisplayed()
            rule.onNodeWithText(activity.getString(R.string.native_batch_status, "ERROR_POST")).assertIsDisplayed()
            rule.onNodeWithText(activity.getString(R.string.native_afirma_uncertain)).assertExists()
            rule.onNodeWithTag("native-afirma-confirm").assertDoesNotExist()
            rule.onNodeWithTag("native-afirma-unlock").assertDoesNotExist()
            rule.onNodeWithTag("native-afirma-cancel").assertDoesNotExist()
            rule.onNodeWithTag("native-afirma-close").performClick()
            rule.runOnIdle { assertEquals(1, closed); assertEquals(0, signed) }
        }
    }

    @Test fun anInProgressOperationDoesNotPublishATerminalResultView() {
        val receipt = NativeBatchReceipt(NativeBatchReceipt.Origin.LOCAL,
            listOf(NativeBatchReceipt.Entry("Document 1", "DONE_AND_SAVED", null)))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    NativeAfirmaConsentDialog(prompt(receipt, AfirmaConsentPhase.SENDING, null),
                        onConfirm = { error("No second signature") }, onUnlock = {}, onCancel = {}, onDismiss = {})
                }
            }
            rule.onNodeWithTag("native-batch-details-toggle").assertDoesNotExist()
            rule.onNodeWithTag("native-afirma-confirm").assertDoesNotExist()
            rule.onNodeWithTag("native-afirma-cancel").assertIsDisplayed()
        }
    }

    private fun prompt(receipt: NativeBatchReceipt, phase: AfirmaConsentPhase, problem: AfirmaConsentProblem?) = AfirmaConsentPrompt(
        token = UUID.randomUUID(),
        details = AfirmaConsentDetails("https://source.example", "https://storage.example/put", "batch", "Batch", "SHA256withRSA", 42,
            "00".repeat(32), batchItems = receipt.entries.size, delegatedSigning = true),
        phase = phase, certificateOwner = "Synthetic test identity", problem = problem, batchReceipt = receipt,
    )
}

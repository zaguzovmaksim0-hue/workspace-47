package dev.junta.firmamobile

import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.pressKey
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import dev.junta.firmamobile.afirma.servlet.AfirmaConsentDetails
import dev.junta.firmamobile.afirma.servlet.AfirmaConsentPhase
import dev.junta.firmamobile.afirma.servlet.AfirmaConsentProblem
import dev.junta.firmamobile.afirma.servlet.AfirmaConsentPrompt
import dev.junta.firmamobile.ui.NativeAfirmaConsentDialog
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic prompts and callback counters; no certificate material or network calls. */
@RunWith(AndroidJUnit4::class)
class AfirmaServerCancellationInstrumentedTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun lockedReviewCancellationDoesNotUnlockOrConfirm() = runTest {
        var confirmed = 0; var unlocked = 0; var canceled = 0; var dismissed = 0
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var notice: String
            scenario.onActivity { activity ->
                notice = activity.getString(R.string.native_afirma_cancel_notice)
                activity.setContent {
                    NativeAfirmaConsentDialog(
                        prompt(AfirmaConsentPhase.REVIEW, AfirmaConsentProblem.LOCKED),
                        onConfirm = { confirmed++ }, onUnlock = { unlocked++ },
                        onCancel = { canceled++ }, onDismiss = { dismissed++ },
                    )
                }
            }
            rule.onNodeWithTag("native-afirma-confirm").assertDoesNotExist()
            rule.onNodeWithTag("native-afirma-unlock").assertIsDisplayed()
            rule.onNodeWithText(notice).performScrollTo().assertIsDisplayed()
            rule.onNodeWithTag("native-afirma-cancel")
                .assertIsDisplayed().assertTextEquals("Cancelar y avisar al servidor").performClick()
            rule.runOnIdle {
                assertEquals(1, canceled)
                assertEquals(0, confirmed)
                assertEquals(0, unlocked)
                assertEquals(0, dismissed)
            }
        }
    }

    @Test fun cancellingOffersOnlyLocalStop() = runTest {
        var confirmed = 0; var unlocked = 0; var stopped = 0; var dismissed = 0
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var progress: String
            lateinit var warning: String
            scenario.onActivity { activity ->
                progress = activity.getString(R.string.native_afirma_cancel_progress)
                warning = activity.getString(R.string.native_afirma_cancel_stop_warning)
                activity.setContent {
                    NativeAfirmaConsentDialog(
                        prompt(AfirmaConsentPhase.CANCELLING),
                        onConfirm = { confirmed++ }, onUnlock = { unlocked++ },
                        onCancel = { stopped++ }, onDismiss = { dismissed++ },
                    )
                }
            }
            assertNoSigningActions()
            rule.onNodeWithTag("native-afirma-cancel").assertDoesNotExist()
            rule.onNodeWithTag("native-afirma-close").assertDoesNotExist()
            rule.onNodeWithText(progress).performScrollTo().assertIsDisplayed()
            rule.onNodeWithText(warning).performScrollTo().assertIsDisplayed()
            rule.onNodeWithTag("native-afirma-stop-cancel")
                .assertIsDisplayed().assertTextEquals("Detener la espera").performClick()
            rule.runOnIdle {
                assertEquals(1, stopped)
                assertEquals(0, confirmed)
                assertEquals(0, unlocked)
                assertEquals(0, dismissed)
            }
        }
    }

    @Test fun everyCancellationReceiptOffersOnlyClose() = runTest {
        val receipts = listOf(
            AfirmaConsentProblem.CANCEL_ACKNOWLEDGED to R.string.native_afirma_cancel_acknowledged,
            AfirmaConsentProblem.CANCEL_NOT_SENT to R.string.native_afirma_cancel_not_sent,
            AfirmaConsentProblem.CANCEL_REJECTED to R.string.native_afirma_cancel_rejected,
            AfirmaConsentProblem.CANCEL_UNCERTAIN to R.string.native_afirma_cancel_uncertain,
        )
        val current = mutableStateOf(prompt(AfirmaConsentPhase.FINISHED, receipts.first().first))
        var confirmed = 0; var unlocked = 0; var canceled = 0; var closed = 0
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var messages: List<String>
            scenario.onActivity { activity ->
                messages = receipts.map { activity.getString(it.second) }
                activity.setContent {
                    NativeAfirmaConsentDialog(
                        current.value,
                        onConfirm = { confirmed++ }, onUnlock = { unlocked++ },
                        onCancel = { canceled++ }, onDismiss = { closed++ },
                    )
                }
            }
            receipts.forEachIndexed { index, (problem, _) ->
                rule.runOnIdle { current.value = prompt(AfirmaConsentPhase.FINISHED, problem) }
                assertNoSigningActions()
                rule.onNodeWithTag("native-afirma-cancel").assertDoesNotExist()
                rule.onNodeWithTag("native-afirma-stop-cancel").assertDoesNotExist()
                rule.onNodeWithText(messages[index]).performScrollTo().assertIsDisplayed()
                rule.onNodeWithTag("native-afirma-close").assertIsDisplayed().performClick()
                rule.runOnIdle {
                    assertEquals(index + 1, closed)
                    assertEquals(0, confirmed)
                    assertEquals(0, unlocked)
                    assertEquals(0, canceled)
                }
            }
        }
    }

    @Test fun reviewDismissalIsLocalAndExplicitCancellationRemovesSigningActions() = runTest {
        val current = mutableStateOf(prompt(AfirmaConsentPhase.REVIEW))
        var confirmed = 0; var unlocked = 0; var canceled = 0; var dismissed = 0
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    NativeAfirmaConsentDialog(
                        current.value,
                        onConfirm = { confirmed++ }, onUnlock = { unlocked++ },
                        onCancel = {
                            canceled++
                            current.value = current.value.copy(phase = AfirmaConsentPhase.CANCELLING, problem = null)
                        },
                        onDismiss = { dismissed++ },
                    )
                }
            }
            listOf("sign", "selectcert").forEachIndexed { index, operation ->
                rule.runOnIdle { current.value = prompt(AfirmaConsentPhase.REVIEW, operation = operation) }
                rule.onNodeWithTag("native-afirma-confirm").assertIsDisplayed()
                rule.onNodeWithTag("native-afirma-cancel").assertIsDisplayed()
                // Target the focused modal window, not the Activity behind it.
                onView(isRoot()).inRoot(isDialog()).perform(pressKey(KeyEvent.KEYCODE_BACK))
                rule.runOnIdle {
                    assertEquals(index + 1, dismissed)
                    assertEquals(index, canceled)
                    assertEquals(0, confirmed)
                    assertEquals(0, unlocked)
                }
                rule.onNodeWithTag("native-afirma-cancel")
                    .assertTextEquals("Cancelar y avisar al servidor").performClick()
                assertNoSigningActions()
                rule.onNodeWithTag("native-afirma-cancel").assertDoesNotExist()
                rule.onNodeWithTag("native-afirma-stop-cancel").assertIsDisplayed()
                rule.runOnIdle {
                    assertEquals(index + 1, canceled)
                    assertEquals(index + 1, dismissed)
                    assertEquals(0, confirmed)
                    assertEquals(0, unlocked)
                }
            }
        }
    }

    private fun assertNoSigningActions() {
        rule.onNodeWithTag("native-afirma-confirm").assertDoesNotExist()
        rule.onNodeWithTag("native-afirma-unlock").assertDoesNotExist()
        rule.onNodeWithTag("native-afirma-retry").assertDoesNotExist()
    }

    private fun prompt(
        phase: AfirmaConsentPhase,
        problem: AfirmaConsentProblem? = null,
        operation: String = "sign",
    ) = AfirmaConsentPrompt(
        token = UUID.randomUUID(),
        details = AfirmaConsentDetails(
            sourceOrigin = "https://page.synthetic.example",
            destination = "https://store.synthetic.example/storage",
            operation = operation,
            format = if (operation == "sign") "CAdES detached" else null,
            algorithm = if (operation == "sign") "SHA256withRSA" else null,
            payloadBytes = if (operation == "sign") 12 else 0,
            payloadSha256 = if (operation == "sign") "a".repeat(64) else null,
        ),
        phase = phase,
        certificateOwner = if (problem == AfirmaConsentProblem.LOCKED) null else "Certificado de prueba",
        problem = problem,
    )
}

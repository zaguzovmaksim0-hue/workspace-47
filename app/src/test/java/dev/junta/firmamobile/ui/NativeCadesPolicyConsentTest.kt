package dev.junta.firmamobile.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.junta.firmamobile.afirma.servlet.AfirmaConsentDetails
import dev.junta.firmamobile.afirma.servlet.AfirmaConsentPhase
import dev.junta.firmamobile.afirma.servlet.AfirmaConsentProblem
import dev.junta.firmamobile.afirma.servlet.AfirmaConsentPrompt
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

/** Isolated JVM consent component; no MainActivity/browser/device E2E. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@Config(qualifiers = "w400dp-h1200dp")
class NativeCadesPolicyConsentTest {
    @get:Rule val rule = createComposeRule()
    private var confirmations = 0
    private var unlocks = 0

    @Test fun policyReferenceIsDisclosedWhileTheCertificateIsStillLocked() {
        show(prompt(details().copy(signaturePolicySummary = "1.3.6.1.4.1.55555.47.1 · SHA-256 · synthetic"), true))
        rule.onNodeWithTag("native-cades-policy-notice").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("native-afirma-confirm").assertDoesNotExist()
        rule.runOnIdle { assertEquals(0, confirmations); assertEquals(0, unlocks) }
    }

    @Test fun policyMetadataDoesNotReplaceTheExistingExplicitConfirmation() {
        show(prompt(details().copy(signaturePolicySummary = "1.2.3.4 · SHA-256 · synthetic"), false))
        rule.onNodeWithTag("native-cades-policy-notice").performScrollTo().assertIsDisplayed()
        rule.runOnIdle { assertEquals(0, confirmations) }
        rule.onNodeWithTag("native-afirma-confirm").performClick()
        rule.runOnIdle { assertEquals(1, confirmations); assertEquals(0, unlocks) }
    }

    @Test fun localPolicyBatchWarningDoesNotClaimRemoteOrOrdinarySigningWasValidated() {
        val state = mutableStateOf(prompt(details().copy(operation = "batch", format = "Batch · local", signaturePolicyItems = 2), true))
        rule.setContent { JuntaFirmaTheme { NativeAfirmaConsentDialog(state.value, { confirmations++ }, { unlocks++ }, {}, {}) } }
        rule.onNodeWithTag("native-cades-policy-batch").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("native-cades-policy-notice").assertDoesNotExist()
        rule.runOnIdle { state.value = prompt(details().copy(delegatedSigning = true), true) }
        rule.onNodeWithTag("native-cades-policy-batch").assertDoesNotExist()
        rule.onNodeWithTag("native-cades-policy-notice").assertDoesNotExist()
    }

    private fun show(prompt: AfirmaConsentPrompt) {
        rule.setContent { JuntaFirmaTheme { NativeAfirmaConsentDialog(prompt, { confirmations++ }, { unlocks++ }, {}, {}) } }
    }
    private fun details() = AfirmaConsentDetails("https://portal.synthetic.example", "https://storage.synthetic.example/put", "sign", "CAdES", "SHA256withRSA", 32, "a".repeat(64))
    private fun prompt(details: AfirmaConsentDetails, locked: Boolean) = AfirmaConsentPrompt(UUID.randomUUID(), details, AfirmaConsentPhase.REVIEW,
        if (locked) null else "Generated test identity", if (locked) AfirmaConsentProblem.LOCKED else null)
}

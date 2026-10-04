package dev.junta.firmamobile.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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

/** Isolated JVM consent component. No Activity, WebView or signing E2E. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@Config(qualifiers = "w400dp-h1200dp")
class NativePrecalculatedHashConsentTest {
    @get:Rule val rule = createComposeRule()
    private var confirmations = 0
    private var unlocks = 0

    @Test fun lockedDigestRequestDisclosesTheAbsentOriginalAndDoesNotOfferSilentSigning() {
        val prompt = prompt(details("SHA-256"), locked = true)
        rule.setContent { JuntaFirmaTheme { NativeAfirmaConsentDialog(prompt, { confirmations++ }, { unlocks++ }, {}, {}) } }
        rule.onNodeWithTag("native-cades-provided-hash-notice").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("native-afirma-details-toggle").performScrollTo().performClick()
        rule.onNodeWithText("Huella recibida: 32 bytes; tamaño del documento original desconocido.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("native-afirma-confirm").assertDoesNotExist()
        rule.runOnIdle { assertEquals(0, confirmations); assertEquals(0, unlocks) }
    }

    @Test fun aReviewableHashRequestStillNeedsTheExistingExplicitConfirmAction() {
        val prompt = prompt(details("SHA-256"), locked = false)
        rule.setContent { JuntaFirmaTheme { NativeAfirmaConsentDialog(prompt, { confirmations++ }, { unlocks++ }, {}, {}) } }
        rule.onNodeWithTag("native-afirma-details-toggle").performScrollTo().performClick()
        rule.onNodeWithText("SHA-256 de los bytes recibidos, no del documento original: " + "a".repeat(64)).performScrollTo().assertIsDisplayed()
        rule.runOnIdle { assertEquals(0, confirmations) }
        rule.onNodeWithTag("native-afirma-confirm").performClick()
        rule.runOnIdle { assertEquals(1, confirmations); assertEquals(0, unlocks) }
    }

    @Test fun batchWarningIsSeparateFromOrdinaryOrServiceDelegatedDocumentSigning() {
        val state = mutableStateOf(prompt(details(null).copy(operation = "batch", format = "Batch · local", providedDigestItems = 2), true))
        rule.setContent { JuntaFirmaTheme { NativeAfirmaConsentDialog(state.value, { confirmations++ }, { unlocks++ }, {}, {}) } }
        rule.onNodeWithTag("native-cades-provided-hash-batch").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("native-cades-provided-hash-notice").assertDoesNotExist()
        rule.runOnIdle { state.value = prompt(details(null).copy(format = "CAdES · trifásico", delegatedSigning = true), true) }
        rule.onNodeWithTag("native-cades-provided-hash-batch").assertDoesNotExist()
        rule.onNodeWithTag("native-cades-provided-hash-notice").assertDoesNotExist()
        rule.runOnIdle { assertEquals(0, confirmations); assertEquals(0, unlocks) }
    }

    private fun details(hash: String?) = AfirmaConsentDetails(
        "https://portal.synthetic.example", "https://storage.synthetic.example/put", "sign",
        "CAdES · detached", "SHA256withRSA", 32, "a".repeat(64), providedDigestAlgorithm = hash,
    )
    private fun prompt(details: AfirmaConsentDetails, locked: Boolean) = AfirmaConsentPrompt(
        UUID.randomUUID(), details, AfirmaConsentPhase.REVIEW,
        if (locked) null else "Generated test identity", if (locked) AfirmaConsentProblem.LOCKED else null,
    )
}

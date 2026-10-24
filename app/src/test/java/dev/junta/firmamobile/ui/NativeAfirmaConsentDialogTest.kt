package dev.junta.firmamobile.ui

import androidx.compose.runtime.mutableStateOf
import dev.junta.firmamobile.testing.createComposeRule
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
import org.robolectric.annotation.Config

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

    @Test fun batchReviewPreservesOrderCollapsesAndNeverAuthorizes() {
        val review = NativeBatchConsentReview.from(NativeBatchDescriptor(true,
            dev.junta.firmamobile.signing.SigningAlgorithm.SHA256_WITH_RSA, false, listOf(
                NativeBatchItem("second-id", "CAdES", "sign", "YWJj", emptyMap()),
                NativeBatchItem("first-id", "CAdES", "sign", "https://private.example/doc?secret=1", emptyMap()),
            )), localBatch = true)
        val state = mutableStateOf(prompt().let { it.copy(details = it.details.copy(
            operation = "batch", batchItems = 2, batchReview = review)) })
        var actions = 0
        rule.setContent { JuntaFirmaTheme {
            NativeAfirmaConsentDialog(state.value, { actions++ }, { actions++ }, { actions++ }, { actions++ })
        } }
        rule.onNodeWithTag("native-batch-count").performScrollTo().assertTextEquals("Documentos en el lote: 2")
        rule.onNodeWithTag("native-batch-documents").assertDoesNotExist()
        rule.onNodeWithTag("native-batch-documents-toggle").performScrollTo().performClick()
        rule.onNodeWithTag("native-batch-document-0").performScrollTo()
            .assertIsDisplayed().assertTextEquals("Documento 1 · ID: second-id")
        rule.onNodeWithTag("native-batch-document-1").performScrollTo()
            .assertIsDisplayed().assertTextEquals("Documento 2 · ID: first-id")
        rule.onNodeWithTag("native-batch-document-bytes-0").assertTextEquals("Datos locales del documento: 3 bytes")
        rule.onNodeWithTag("native-batch-document-sha256-0").assertTextEquals(
            "SHA-256 calculado localmente: ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
        rule.onNodeWithTag("native-batch-document-unavailable-1").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("native-batch-document-sha256-1").assertDoesNotExist()
        rule.onNodeWithText("secret=1", substring = true).assertDoesNotExist()
        rule.onNodeWithTag("native-batch-documents-toggle").performScrollTo().performClick()
        rule.onNodeWithTag("native-batch-documents").assertDoesNotExist()
        rule.onNodeWithTag("native-batch-count").assertExists()
        rule.onNodeWithTag("native-batch-documents-toggle").performScrollTo().performClick()
        rule.runOnIdle {
            assertEquals(0, actions)
            state.value = state.value.copy(token = UUID.randomUUID())
        }
        rule.onNodeWithTag("native-batch-documents").assertDoesNotExist()
        rule.onNodeWithTag("native-afirma-cancel").performClick()
        rule.runOnIdle { assertEquals(1, actions) }
    }

    @Test
    @Config(qualifiers = "en")
    fun maximumDelegatedListIsScrollableAndBlankIdsHaveANumberedFallback() {
        val review = NativeBatchConsentReview.from(NativeBatchDescriptor(true,
            dev.junta.firmamobile.signing.SigningAlgorithm.SHA256_WITH_RSA, false,
            (32 downTo 1).map { NativeBatchItem(if (it == 32) "  " else "id-$it-" + "x".repeat(200),
                "CAdES", "sign", "YWJj", emptyMap()) }), localBatch = false)
        val state = mutableStateOf(prompt().let { it.copy(details = it.details.copy(
            operation = "batch", batchItems = 32, delegatedSigning = true, batchReview = review),
            certificateOwner = null, problem = AfirmaConsentProblem.LOCKED) })
        var actions = 0
        rule.setContent { JuntaFirmaTheme {
            NativeAfirmaConsentDialog(state.value, { actions++ }, { actions++ }, { actions++ }, { actions++ })
        } }
        rule.onNodeWithTag("native-batch-count").performScrollTo().assertTextEquals("Documents in the batch: 32")
        rule.onNodeWithTag("native-batch-documents-toggle").performScrollTo().performClick()
        rule.onNodeWithTag("native-batch-document-0").performScrollTo().assertTextEquals("Document 1")
        for (index in 1..31) {
            val id = "id-${32 - index}-" + "x".repeat(200)
            rule.onNodeWithTag("native-batch-document-$index").performScrollTo().assertIsDisplayed()
                .assertTextEquals("Document ${index + 1} · ID: " + id.take(119) + "…")
            rule.onNodeWithTag("native-batch-document-sha256-$index").assertDoesNotExist()
        }
        rule.onNodeWithTag("native-batch-document-unavailable-31").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("native-afirma-confirm").assertDoesNotExist()
        rule.onNodeWithTag("native-batch-documents-toggle").performScrollTo().performClick()
        rule.onNodeWithTag("native-batch-documents").assertDoesNotExist()
        rule.onNodeWithTag("native-afirma-unlock").performClick()
        rule.runOnIdle { assertEquals(1, actions) }
    }

    @Test
    @Config(qualifiers = "en")
    fun resultSummariesUseTheCurrentLocaleAndRetainPartialResults() {
        val state = mutableStateOf(prompt().copy(phase = AfirmaConsentPhase.FINISHED,
            resultSummary = AfirmaResultSummary.Batch(3, 1)))
        rule.setContent { JuntaFirmaTheme {
            NativeAfirmaConsentDialog(state.value, {}, {}, {}, {})
        } }
        rule.onNodeWithTag("native-multiphase-results")
            .assertTextEquals("Documents: 3; positive results: 1; other results: 2.")
        rule.runOnIdle { state.value = state.value.copy(resultSummary = AfirmaResultSummary.ServiceResult) }
        rule.onNodeWithTag("native-multiphase-results")
            .assertTextEquals("The service returned a result. This alone does not prove acceptance of the procedure.")
        rule.runOnIdle { state.value = state.value.copy(resultSummary = null) }
        rule.onNodeWithTag("native-multiphase-results").assertDoesNotExist()
    }

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
        rule.mainClock.advanceTimeBy(CONSENT_ARM_DELAY_MS)
        rule.waitForIdle()
        rule.onNodeWithTag("native-afirma-confirm").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(1, actions) }
    }

    @Test fun confirmArmsAfterDelayAndCancelRemainsEnabled() {
        rule.mainClock.autoAdvance = false
        rule.setContent { JuntaFirmaTheme {
            NativeAfirmaConsentDialog(prompt(), {}, {}, {}, {})
        } }
        rule.waitForIdle()

        val confirm = rule.onNodeWithTag("native-afirma-confirm")
        confirm.assertIsNotEnabled()
        rule.onNodeWithTag("native-afirma-cancel").assertIsEnabled()
        rule.mainClock.advanceTimeBy(CONSENT_ARM_DELAY_MS - 100)
        rule.waitForIdle()
        confirm.assertIsNotEnabled()
        rule.mainClock.advanceTimeBy(200)
        rule.waitForIdle()
        confirm.assertIsEnabled()
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

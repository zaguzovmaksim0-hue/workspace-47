package dev.junta.firmamobile.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.junta.firmamobile.profile.CompatibilityStatus
import dev.junta.firmamobile.signing.BatchSigningDocumentReview
import dev.junta.firmamobile.signing.BatchSigningFormat
import dev.junta.firmamobile.signing.NormalizedBatchSigningDocument
import dev.junta.firmamobile.signing.SigningUiState
import dev.junta.firmamobile.testing.createComposeRule
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
class SigningConfirmationDialogTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun confirmationShowsExactSafeSummaryAndNeverActsBeforeExplicitClick() {
        val events = mutableListOf<String>()
        rule.setContent {
            JuntaFirmaTheme {
                SigningConfirmationDialog(
                    state = state(requiresLegacySha1Warning = true),
                    onConfirm = { events += "confirm" },
                    onCancel = { events += "cancel" },
                )
            }
        }

        rule.onNodeWithText("Solicitud de firma").assertIsDisplayed()
        rule.onNodeWithText("Sitio: www.juntadeandalucia.es").assertIsDisplayed()
        rule.onNodeWithText("Perfil: Junta de Andalucía").assertIsDisplayed()
        rule.onNodeWithText("Nivel de soporte: Verificada en escenario real").assertIsDisplayed()
        rule.onNodeWithText("Operación: Autenticación con certificado").assertIsDisplayed()
        rule.onNodeWithText("Certificado: Persona de Prueba").assertIsDisplayed()
        rule.onNodeWithText("Formato: CAdES").assertIsDisplayed()
        rule.onNodeWithText("Algoritmo: SHA1withRSA").assertIsDisplayed()
        rule.onNodeWithText("SHA-1", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(RAW_PAYLOAD).assertDoesNotExist()
        rule.onNodeWithTag("signing-batch-documents-toggle").assertDoesNotExist()
        rule.onNodeWithTag("signing-batch-documents-count").assertDoesNotExist()
        rule.runOnIdle { assertEquals(emptyList<String>(), events) }

        rule.mainClock.advanceTimeBy(CONSENT_ARM_DELAY_MS)
        rule.waitForIdle()
        rule.onNodeWithText("Firmar").performClick()
        rule.runOnIdle { assertEquals(listOf("confirm"), events) }
    }

    @Test
    fun confirmArmsAfterDelayAndCancelRemainsEnabled() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            JuntaFirmaTheme {
                SigningConfirmationDialog(
                    state = state(requiresLegacySha1Warning = false),
                    onConfirm = {},
                    onCancel = {},
                )
            }
        }
        rule.waitForIdle()

        val confirm = rule.onNodeWithText("Firmar")
        confirm.assertIsNotEnabled()
        rule.onNodeWithText("Cancelar").assertIsEnabled()
        rule.mainClock.advanceTimeBy(CONSENT_ARM_DELAY_MS - 100)
        rule.waitForIdle()
        confirm.assertIsNotEnabled()
        rule.mainClock.advanceTimeBy(200)
        rule.waitForIdle()
        confirm.assertIsEnabled()
    }

    @Test
    fun cancelIsTheOnlyDismissActionAndDoesNotConfirm() {
        val events = mutableListOf<String>()
        rule.setContent {
            JuntaFirmaTheme {
                SigningConfirmationDialog(
                    state = state(requiresLegacySha1Warning = false),
                    onConfirm = { events += "confirm" },
                    onCancel = { events += "cancel" },
                )
            }
        }

        rule.onNodeWithText("Cancelar").performClick()

        rule.runOnIdle { assertEquals(listOf("cancel"), events) }
        rule.onNodeWithText("SHA-1", substring = true).assertDoesNotExist()
    }

    @Test
    fun batchReviewStartsCollapsedAndTogglesWithoutConsenting() {
        rule.mainClock.autoAdvance = false
        val events = mutableListOf<String>()
        rule.setContent {
            JuntaFirmaTheme {
                SigningConfirmationDialog(
                    state = batchState(listOf("doc-z", "\u202Ereport-\u2066A\u2069", "\u2066")).copy(
                        algorithm = "SHA1withRSA",
                        requiresLegacySha1Warning = true,
                    ),
                    onConfirm = { events += "confirm" },
                    onCancel = { events += "cancel" },
                )
            }
        }

        rule.onNodeWithTag("signing-batch-documents-count").performScrollTo()
            .assertTextEquals("3 documentos en el lote")
        rule.onNodeWithTag("signing-batch-documents").assertDoesNotExist()
        rule.onNodeWithTag("signing-batch-documents-toggle").performScrollTo().performClick()
        rule.onNodeWithText("Documento 1 · ID: doc-z").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Documento 2 · ID: report-A").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Documento 3").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("SHA-1", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(RAW_REFERENCE, substring = true).assertDoesNotExist()
        rule.onNodeWithText(RAW_PAYLOAD, substring = true).assertDoesNotExist()
        rule.onNodeWithText("\u202Ereport-\u2066A\u2069", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Firmar").assertIsNotEnabled()
        rule.onNodeWithText("Cancelar").assertIsEnabled()
        rule.runOnIdle { assertEquals(emptyList<String>(), events) }

        rule.onNodeWithTag("signing-batch-documents-toggle").performScrollTo().performClick()
        rule.onNodeWithTag("signing-batch-documents").assertDoesNotExist()
        rule.onNodeWithText("Firmar").assertIsNotEnabled()
        rule.mainClock.advanceTimeBy(CONSENT_ARM_DELAY_MS)
        rule.waitForIdle()
        rule.onNodeWithText("Firmar").assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(listOf("confirm"), events) }
    }

    @Test
    fun aNewBatchRequestStartsCollapsedAndUsesTheSingularCount() {
        var confirmation by mutableStateOf(batchState(listOf("doc-a", "doc-b")))
        rule.setContent {
            JuntaFirmaTheme {
                SigningConfirmationDialog(confirmation, onConfirm = {}, onCancel = {})
            }
        }
        rule.onNodeWithTag("signing-batch-documents-toggle").performScrollTo().performClick()
        rule.onNodeWithTag("signing-batch-document-0").performScrollTo().assertIsDisplayed()

        rule.runOnIdle {
            confirmation = batchState(listOf("new-doc")).copy(
                requestId = UUID.fromString("123e4567-e89b-42d3-a456-426614174001"),
            )
        }

        rule.onNodeWithTag("signing-batch-documents-count").performScrollTo()
            .assertTextEquals("1 documento en el lote")
        rule.onNodeWithTag("signing-batch-documents").assertDoesNotExist()
        rule.onNodeWithText("Ver documentos").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("signing-batch-documents-toggle").performClick()
        rule.onNodeWithText("Documento 1 · ID: new-doc").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Documento 1 · ID: doc-a").assertDoesNotExist()
    }

    @Test
    fun aLongBatchKeepsItsLastDocumentScrollableAndCancelAvailable() {
        val events = mutableListOf<String>()
        rule.setContent {
            JuntaFirmaTheme {
                SigningConfirmationDialog(
                    state = batchState((1..128).map { "doc-$it" }),
                    onConfirm = { events += "confirm" },
                    onCancel = { events += "cancel" },
                )
            }
        }

        rule.onNodeWithTag("signing-batch-documents-count").performScrollTo()
            .assertTextEquals("128 documentos en el lote")
        rule.onNodeWithTag("signing-batch-documents-toggle").performScrollTo().performClick()
        rule.onNodeWithText("Documento 1 · ID: doc-1").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Documento 128 · ID: doc-128").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(RAW_REFERENCE, substring = true).assertDoesNotExist()
        rule.onNodeWithText(RAW_PAYLOAD, substring = true).assertDoesNotExist()
        rule.runOnIdle { assertEquals(emptyList<String>(), events) }
        rule.onNodeWithText("Cancelar").assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(listOf("cancel"), events) }
    }

    private fun batchState(ids: List<String>) = state(requiresLegacySha1Warning = false).copy(
        safeDescription = "Firma por lotes",
        batchDocumentReview = BatchSigningDocumentReview(
            ids.mapIndexed { index, id ->
                NormalizedBatchSigningDocument(
                    id = id,
                    dataReference = if (index == 0) RAW_REFERENCE else RAW_PAYLOAD,
                    format = BatchSigningFormat.CADES,
                )
            },
        ),
    )

    private fun state(requiresLegacySha1Warning: Boolean) =
        SigningUiState.AwaitingConfirmation(
            requestId = UUID.fromString("123e4567-e89b-42d3-a456-426614174000"),
            siteHost = "www.juntadeandalucia.es",
            profileName = "Junta de Andalucía",
            compatibilityStatus = CompatibilityStatus.VERIFIED_E2E,
            safeDescription = "Autenticación con certificado",
            format = "CAdES",
            algorithm = if (requiresLegacySha1Warning) "SHA1withRSA" else "SHA256withRSA",
            certificateOwner = "Persona de Prueba",
            requiresLegacySha1Warning = requiresLegacySha1Warning,
        )

    private companion object {
        const val RAW_PAYLOAD = "synthetic-secret-payload-base64"
        const val RAW_REFERENCE = "https://example.invalid/private.pdf?token=synthetic-secret"
    }
}

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
import dev.junta.firmamobile.browser.NavigationId
import dev.junta.firmamobile.network.TrustedOrigin
import dev.junta.firmamobile.profile.CompatibilityStatus
import dev.junta.firmamobile.signing.BatchSigningDocumentReview
import dev.junta.firmamobile.signing.BatchSigningFormat
import dev.junta.firmamobile.signing.DocumentConsentEvidence
import dev.junta.firmamobile.signing.MiniAppletPayloadCodec
import dev.junta.firmamobile.signing.NormalizedBatchSigningDocument
import dev.junta.firmamobile.signing.NormalizedSignRequest
import dev.junta.firmamobile.signing.SigningAlgorithm
import dev.junta.firmamobile.signing.SigningContext
import dev.junta.firmamobile.signing.SigningFormat
import dev.junta.firmamobile.signing.SigningProtocolId
import dev.junta.firmamobile.signing.SigningUiState
import dev.junta.firmamobile.testing.createComposeRule
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
import java.time.Instant
import java.util.UUID
import org.junit.After
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
class SigningDocumentConsentTest {
    @get:Rule
    val rule = createComposeRule()
    private val ownedEvidence = mutableListOf<DocumentConsentEvidence>()

    @After
    fun closeEvidence() { ownedEvidence.forEach { it.close() } }

    @Test
    fun actualDocumentHasHonestUnavailableNameExactBytesAndHashWithoutImplicitConsent() {
        rule.mainClock.autoAdvance = false
        val events = mutableListOf<String>()
        val documentEvidence = evidence()
        rule.setContent {
            JuntaFirmaTheme {
                SigningConfirmationDialog(state(documentEvidence),
                    onConfirm = { events += "confirm" }, onCancel = { events += "cancel" })
            }
        }
        rule.onNodeWithTag("signing-document-name")
            .assertTextEquals("Nombre del documento: no disponible")
        rule.onNodeWithTag("signing-document-bytes")
            .assertTextEquals("Tamaño del documento recibido: ${PDF.size} bytes")
        rule.onNodeWithTag("signing-document-sha256")
            .assertTextEquals("SHA-256 del documento recibido: $EXPECTED_PDF_SHA256")
        rule.onNodeWithTag("signing-document-unavailable").assertDoesNotExist()
        rule.onNodeWithText(PDF.decodeToString(), substring = true).assertDoesNotExist()
        rule.onNodeWithText("Firmar").assertIsNotEnabled()
        rule.onNodeWithText("Cancelar").assertIsEnabled()
        rule.runOnIdle { assertEquals(emptyList<String>(), events) }
        rule.onNodeWithText("Cancelar").performClick()
        rule.runOnIdle { assertEquals(listOf("cancel"), events) }
    }

    @Test
    fun unavailableIsExplicitAndSingleRequestSha1WarningAndArmingRemainIntact() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            JuntaFirmaTheme {
                SigningConfirmationDialog(state().copy(
                    algorithm = "SHA1withRSA", requiresLegacySha1Warning = true,
                ), onConfirm = {}, onCancel = {})
            }
        }
        rule.onNodeWithTag("signing-document-unavailable").assertExists()
        rule.onNodeWithTag("signing-document-name").assertDoesNotExist()
        rule.onNodeWithTag("signing-document-bytes").assertDoesNotExist()
        rule.onNodeWithTag("signing-document-sha256").assertDoesNotExist()
        rule.onNodeWithText("SHA-1", substring = true).assertExists()
        rule.onNodeWithText("Firmar").assertIsNotEnabled()
        rule.mainClock.advanceTimeBy(CONSENT_ARM_DELAY_MS)
        rule.waitForIdle()
        rule.onNodeWithText("Firmar").assertIsEnabled()
    }

    @Test
    fun newRequestRemovesPreviousDocumentMetadataAndRearmsConfirmation() {
        rule.mainClock.autoAdvance = false
        val originalEvidence = evidence()
        var confirmation by mutableStateOf(state(originalEvidence))
        rule.setContent {
            JuntaFirmaTheme { SigningConfirmationDialog(confirmation, onConfirm = {}, onCancel = {}) }
        }
        rule.onNodeWithTag("signing-document-sha256").assertExists()
        rule.mainClock.advanceTimeBy(CONSENT_ARM_DELAY_MS)
        rule.waitForIdle()
        rule.onNodeWithText("Firmar").assertIsEnabled()
        rule.runOnIdle {
            originalEvidence.close()
            confirmation = state().copy(requestId = UUID.fromString("123e4567-e89b-42d3-a456-426614174001"))
        }
        rule.onNodeWithTag("signing-document-unavailable").assertExists()
        rule.onNodeWithTag("signing-document-sha256").assertDoesNotExist()
        rule.onNodeWithText(EXPECTED_PDF_SHA256, substring = true).assertDoesNotExist()
        rule.onNodeWithText("Firmar").assertIsNotEnabled()
    }

    @Test
    fun batchReviewRemainsSeparateAndNeverShowsASingleDocumentHash() {
        rule.setContent {
            JuntaFirmaTheme {
                SigningConfirmationDialog(state().copy(
                    batchDocumentReview = BatchSigningDocumentReview(listOf(
                        NormalizedBatchSigningDocument("\u202Eidentifier\u2066", "private-reference", BatchSigningFormat.CADES),
                    )),
                ), onConfirm = {}, onCancel = {})
            }
        }
        rule.onNodeWithTag("signing-document-unavailable").assertDoesNotExist()
        rule.onNodeWithTag("signing-document-sha256").assertDoesNotExist()
        rule.onNodeWithTag("signing-batch-documents-count").performScrollTo()
            .assertTextEquals("1 documento en el lote")
        rule.onNodeWithTag("signing-batch-documents-toggle").performScrollTo().performClick()
        rule.onNodeWithText("Documento 1 · ID: identifier").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("private-reference", substring = true).assertDoesNotExist()
    }

    private fun state(evidence: DocumentConsentEvidence = DocumentConsentEvidence.unavailable()) =
        SigningUiState.AwaitingConfirmation(
            requestId = REQUEST_ID, siteHost = "document.invalid", profileName = "Synthetic profile",
            compatibilityStatus = CompatibilityStatus.EXPERIMENTAL, safeDescription = "Synthetic operation",
            format = "PDF (PAdES)", algorithm = "SHA256withRSA", certificateOwner = "Synthetic owner",
            requiresLegacySha1Warning = false, documentConsent = evidence,
        )

    private fun evidence(): DocumentConsentEvidence {
        val envelope = MiniAppletPayloadCodec.encode(PDF, "fixed=true")
        val request = NormalizedSignRequest(
            requestId = REQUEST_ID, protocolId = SigningProtocolId("synthetic-ui-v1"),
            context = SigningContext("synthetic-ui", 1, TrustedOrigin("https", "document.invalid", 443),
                NavigationId("synthetic-ui"), observedAt = Instant.parse("2026-10-18T18:00:00Z")),
            algorithm = SigningAlgorithm.SHA256_WITH_RSA, format = SigningFormat.PADES,
            safeDescription = "Synthetic operation", payload = envelope,
        )
        return request.use { DocumentConsentEvidence.fromEnvelope(it, envelope, PDF) }
            .also { ownedEvidence += it }
    }

    private companion object {
        val REQUEST_ID: UUID = UUID.fromString("123e4567-e89b-42d3-a456-426614174000")
        val PDF = "%PDF-1.7\nsynthetic consent document\n%%EOF\n".encodeToByteArray()
        const val EXPECTED_PDF_SHA256 = "e5fe40f17d151fafa3c4d3142e359b308694d512ff3227d48cfd6fb092e7cc8e"
    }
}

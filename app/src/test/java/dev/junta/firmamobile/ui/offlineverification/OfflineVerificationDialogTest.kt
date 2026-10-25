package dev.junta.firmamobile.ui.offlineverification

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.junta.firmamobile.R
import dev.junta.firmamobile.testing.createComposeRule
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
import dev.junta.firmamobile.verification.OfflineCmsFixtures
import dev.junta.firmamobile.verification.OfflineCmsOptions
import dev.junta.firmamobile.verification.OfflinePadesVerifierTest
import java.io.IOException
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class OfflineVerificationDialogTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var controller: OfflineVerificationController

    @Test
    fun expiredSelfSignedCmsOnlyReportsIntegrityAfterExplicitVerify() {
        val signer = OfflineCmsFixtures.signer(notAfter = Date(1L))
        val source = SyntheticOfflineDocument(OfflineCmsFixtures.signed(byteArrayOf(3), listOf(signer)))
        showDialog(source)
        rule.onNodeWithTag("offline-verification-verify").assertIsNotEnabled()
        rule.onNodeWithTag("offline-verification-pick-cms").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(0, source.opens) }
        rule.onNodeWithTag("offline-verification-result").assertDoesNotExist()
        rule.onNodeWithTag("offline-verification-verify").assertIsEnabled().performClick()
        awaitOutcome(OfflineVerificationOutcome.VERIFIED)
        rule.onNodeWithText(text(R.string.signature_verification_scope)).assertExists()
        rule.onNodeWithText(text(R.string.offline_verification_verified)).performScrollTo().assertExists()
        rule.onNodeWithText("Firmas con integridad comprobada: 1").assertExists()
        rule.onNodeWithText("Firmante de confianza").assertDoesNotExist()
        rule.onNodeWithText("Firma válida").assertDoesNotExist()
        rule.onNodeWithText("Offline CMS Verifier Test", substring = true).assertDoesNotExist()
        rule.onNodeWithTag("offline-verification-close").performClick()
        rule.onNodeWithTag("offline-verification-dialog").assertDoesNotExist()
        rule.runOnIdle { assertEquals(OfflineVerificationState(), controller.state.value) }
    }

    @Test
    fun detachedMissingOriginalIsClearAndSelectedEmptyOriginalIsNotMissing() {
        val cms = OfflineCmsFixtures.signed(
            byteArrayOf(), listOf(OfflineCmsFixtures.signer()), OfflineCmsOptions(encapsulate = false),
        )
        showDialog(SyntheticOfflineDocument(cms), SyntheticOfflineDocument(byteArrayOf()))
        rule.onNodeWithTag("offline-verification-pick-cms").performScrollTo().performClick()
        rule.onNodeWithTag("offline-verification-verify").performClick()
        awaitOutcome(OfflineVerificationOutcome.DETACHED_CONTENT_REQUIRED)
        rule.onNodeWithText(text(R.string.offline_verification_detached_required)).performScrollTo().assertExists()
        rule.onNodeWithTag("offline-verification-pick-original").performScrollTo().performClick()
        rule.onNodeWithTag("offline-verification-result").assertDoesNotExist()
        rule.onNodeWithTag("offline-verification-verify").performClick()
        awaitOutcome(OfflineVerificationOutcome.VERIFIED)
    }

    @Test
    fun attachedCmsWithExtraneousEmptyOriginalFailsClosedUntilOriginalIsRemoved() {
        val cms = OfflineCmsFixtures.signed(byteArrayOf(4), listOf(OfflineCmsFixtures.signer()))
        showDialog(SyntheticOfflineDocument(cms), SyntheticOfflineDocument(byteArrayOf()))
        rule.onNodeWithTag("offline-verification-pick-cms").performScrollTo().performClick()
        rule.onNodeWithTag("offline-verification-pick-original").performScrollTo().performClick()
        rule.onNodeWithTag("offline-verification-verify").performClick()
        awaitOutcome(OfflineVerificationOutcome.INVALID)
        rule.onNodeWithText(text(R.string.signature_verification_invalid)).performScrollTo().assertExists()
        rule.onNodeWithTag("offline-verification-clear-original").performScrollTo().performClick()
        rule.onNodeWithTag("offline-verification-result").assertDoesNotExist()
        rule.onNodeWithTag("offline-verification-verify").performClick()
        awaitOutcome(OfflineVerificationOutcome.VERIFIED)
    }

    @Test
    fun providerFailureDisplaysOnlyFixedMessageNotUriFilenameOrException() {
        val source = object : OfflineDocumentSource {
            override fun declaredSize(): Long = throw IOException("content://synthetic/private/name.p7m CANARY-DOCUMENT")
            override fun open(): OfflineOpenedDocument = error("must not open")
        }
        showDialog(source)
        rule.onNodeWithTag("offline-verification-pick-cms").performScrollTo().performClick()
        rule.onNodeWithTag("offline-verification-verify").performClick()
        awaitOutcome(OfflineVerificationOutcome.READ_FAILED)
        rule.onNodeWithText(text(R.string.offline_verification_read_failed)).performScrollTo().assertExists()
        rule.onNodeWithText("CANARY-DOCUMENT", substring = true).assertDoesNotExist()
        rule.onNodeWithText("name.p7m", substring = true).assertDoesNotExist()
        rule.onNodeWithText("content://", substring = true).assertDoesNotExist()
        rule.runOnIdle { assertTrue(!controller.state.value.toString().contains("CANARY")) }
    }

    @Test
    fun unsupportedAndOverLimitResultsNeverShowASuccessCount() {
        val cms = OfflineCmsFixtures.signed(byteArrayOf(8), listOf(OfflineCmsFixtures.signer("SHA1withRSA")))
        val declared = java.util.concurrent.atomic.AtomicLong(2_097_153L)
        val backing = SyntheticOfflineDocument(cms)
        val source = object : OfflineDocumentSource {
            override fun declaredSize(): Long = declared.get()
            override fun open(): OfflineOpenedDocument = backing.open()
        }
        showDialog(source)
        rule.onNodeWithTag("offline-verification-pick-cms").performScrollTo().performClick()
        rule.onNodeWithTag("offline-verification-verify").performClick()
        awaitOutcome(OfflineVerificationOutcome.LIMIT_EXCEEDED)
        rule.onNodeWithText(text(R.string.signature_verification_limit_exceeded)).performScrollTo().assertExists()
        rule.onNodeWithText("Firmas con integridad comprobada:", substring = true).assertDoesNotExist()
        declared.set(cms.size.toLong())
        rule.onNodeWithTag("offline-verification-pick-cms").performScrollTo().performClick()
        rule.onNodeWithTag("offline-verification-result").assertDoesNotExist()
        rule.onNodeWithTag("offline-verification-verify").performClick()
        awaitOutcome(OfflineVerificationOutcome.UNSUPPORTED)
        rule.onNodeWithText(text(R.string.signature_verification_unsupported)).performScrollTo().assertExists()
        rule.onNodeWithText("Firmas con integridad comprobada:", substring = true).assertDoesNotExist()
    }

    @Test
    fun pdfShowsBothCertificateRowsAndUnsignedChangesWarning() {
        val first = OfflinePadesVerifierTest.signedPdf()
        val second = OfflinePadesVerifierTest.signedPdf(first)
        showDialog(SyntheticOfflineDocument(second + "\n% unsigned\n".toByteArray()))
        rule.onNodeWithTag("offline-verification-pick-cms").performScrollTo().performClick()
        rule.onNodeWithTag("offline-verification-verify").performClick()
        awaitOutcome(OfflineVerificationOutcome.INVALID)
        val context = RuntimeEnvironment.getApplication()
        for (index in 1..2) {
            val label = context.getString(R.string.signature_verification_signer, index, "CN=Synthetic PDF signer")
            rule.onNodeWithText(label).performScrollTo().assertExists()
        }
        rule.onNodeWithText(text(R.string.signature_verification_unsigned_changes)).performScrollTo().assertExists()
        first.fill(0); second.fill(0)
    }

    private fun showDialog(cms: OfflineDocumentSource, original: OfflineDocumentSource? = null) {
        rule.setContent {
            val scope = rememberCoroutineScope()
            val owner = remember(scope) { OfflineVerificationController(scope) }
            controller = owner
            var visible by remember { mutableStateOf(true) }
            JuntaFirmaTheme {
                if (visible) OfflineVerificationDialog(
                    controller = owner,
                    onPickCms = { choose(owner, OfflineInputKind.CMS, cms) },
                    onPickOriginal = { choose(owner, OfflineInputKind.ORIGINAL, original) },
                    onDismiss = { visible = false },
                )
            }
        }
    }

    private fun choose(owner: OfflineVerificationController, kind: OfflineInputKind, source: OfflineDocumentSource?) {
        owner.beginSelection(kind)?.let { owner.completeSelection(it, source) }
    }

    private fun awaitOutcome(outcome: OfflineVerificationOutcome) {
        rule.waitUntil(timeoutMillis = 10_000) { controller.state.value.outcome == outcome }
        rule.waitForIdle()
    }

    private fun text(id: Int): String = RuntimeEnvironment.getApplication().getString(id)
}

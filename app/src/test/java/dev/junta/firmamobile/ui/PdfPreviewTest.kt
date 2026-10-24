package dev.junta.firmamobile.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.junta.firmamobile.signing.PdfPreviewSource
import dev.junta.firmamobile.testing.createComposeRule
import java.util.concurrent.atomic.AtomicInteger
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
class PdfPreviewTest {
    @get:Rule val rule = createComposeRule()

    @Test fun unavailablePdfHasNoPreviewAction() {
        rule.setContent { pdfPreview(null) }
        rule.onNodeWithTag("pdf-preview-unavailable").assertIsDisplayed()
        rule.onNodeWithTag("pdf-preview-show").assertDoesNotExist()
    }

    @Test fun sourceIsNotReadUntilExplicitActionAndFailureIsGeneric() {
        val calls = AtomicInteger()
        PdfPreviewSource(100) { calls.incrementAndGet(); error("private raw error") }.use { source ->
            rule.setContent { pdfPreview(source) }
            rule.onNodeWithTag("pdf-preview-show").assertIsDisplayed()
            rule.runOnIdle { assertEquals(0, calls.get()) }
            rule.onNodeWithTag("pdf-preview-show").performClick()
            rule.waitUntil(5_000) {
                rule.onAllNodesWithTag("pdf-preview-failed").fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithText("No se puede mostrar la vista previa.").assertIsDisplayed()
            rule.onNodeWithText("private raw error").assertDoesNotExist()
            rule.runOnIdle { assertEquals(1, calls.get()) }
        }
    }
}

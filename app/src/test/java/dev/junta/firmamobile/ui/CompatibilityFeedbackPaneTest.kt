package dev.junta.firmamobile.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import dev.junta.firmamobile.R
import dev.junta.firmamobile.feedback.CompatibilityFeedbackReport
import dev.junta.firmamobile.testing.createComposeRule
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
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
class CompatibilityFeedbackPaneTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun previewIsReadOnlyAndRecompositionNeverCopiesOrOpens() {
        var report by mutableStateOf(report())
        val events = mutableListOf<String>()
        rule.setContent {
            JuntaFirmaTheme {
                compatibilityFeedbackPane(
                    report = report,
                    onCopy = { events += "copy"; true },
                    onOpenIssue = { events += "open"; true },
                )
            }
        }

        rule.onNodeWithTag("compatibility-feedback-report")
            .assertTextEquals(report.body)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.EditableText))
        rule.runOnIdle {
            assertEquals(emptyList<String>(), events)
            report = CompatibilityFeedbackReport.build("0.2.12", 14, 36)
        }
        rule.onNodeWithTag("compatibility-feedback-report").assertTextEquals(report.body)
        rule.onNodeWithTag("compatibility-feedback-confirmation").assertDoesNotExist()
        rule.runOnIdle { assertEquals(emptyList<String>(), events) }
    }

    @Test
    fun copyRequiresTapAndCopiesExactlyThePreviewInAScrollablePane() {
        val report = report()
        val copied = mutableListOf<String>()
        val opened = mutableListOf<String>()
        rule.setContent {
            JuntaFirmaTheme {
                Box(Modifier.fillMaxWidth().height(260.dp)) {
                    compatibilityFeedbackPane(
                        report = report,
                        onCopy = { copied += it.body; true },
                        onOpenIssue = { opened += it.issueFormUrl(); true },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        rule.runOnIdle { assertEquals(emptyList<String>(), copied) }
        rule.onNodeWithTag("compatibility-feedback-copy").performScrollTo().assertIsDisplayed().performClick()
        rule.onNodeWithTag("compatibility-feedback-status").performScrollTo().assertTextEquals("Informe copiado.")
        rule.onNodeWithTag("compatibility-feedback-open").performScrollTo().assertIsDisplayed()
        rule.runOnIdle {
            assertEquals(listOf(report.body), copied)
            assertEquals(emptyList<String>(), opened)
        }
    }

    @Test
    fun githubRequiresSeparateConfirmationAndCancelDoesNothing() {
        val report = report()
        val events = mutableListOf<String>()
        rule.setContent {
            JuntaFirmaTheme {
                compatibilityFeedbackPane(
                    report = report,
                    onCopy = { events += "copy"; true },
                    onOpenIssue = { events += it.issueFormUrl(); true },
                )
            }
        }

        rule.onNodeWithTag("compatibility-feedback-open").performScrollTo().performClick()
        rule.onNodeWithTag("compatibility-feedback-confirmation").assertIsDisplayed()
        rule.runOnIdle { assertEquals(emptyList<String>(), events) }
        rule.onNodeWithTag("compatibility-feedback-cancel").performClick()
        rule.onNodeWithTag("compatibility-feedback-confirmation").assertDoesNotExist()
        rule.runOnIdle { assertEquals(emptyList<String>(), events) }

        rule.onNodeWithTag("compatibility-feedback-open").performScrollTo().performClick()
        rule.onNodeWithTag("compatibility-feedback-confirm-open").performClick()
        rule.onNodeWithTag("compatibility-feedback-confirmation").assertDoesNotExist()
        rule.runOnIdle { assertEquals(listOf(report.issueFormUrl()), events) }
    }

    @Test
    fun changingPreviewDismissesPendingConfirmationWithoutOpeningOldOrNewReport() {
        var report by mutableStateOf(report())
        val events = mutableListOf<String>()
        rule.setContent {
            JuntaFirmaTheme {
                compatibilityFeedbackPane(
                    report = report,
                    onCopy = { events += "copy"; true },
                    onOpenIssue = { events += it.issueFormUrl(); true },
                )
            }
        }
        rule.onNodeWithTag("compatibility-feedback-open").performScrollTo().performClick()
        rule.onNodeWithTag("compatibility-feedback-confirmation").assertIsDisplayed()
        rule.runOnIdle { report = CompatibilityFeedbackReport.build("0.2.12", 14, 36) }

        rule.onNodeWithTag("compatibility-feedback-confirmation").assertDoesNotExist()
        rule.onNodeWithTag("compatibility-feedback-report").assertTextEquals(report.body)
        rule.runOnIdle { assertEquals(emptyList<String>(), events) }
    }

    @Test
    fun actionFailuresUseStaticMessagesAndNeverRetryAutomatically() {
        val events = mutableListOf<String>()
        rule.setContent {
            JuntaFirmaTheme {
                compatibilityFeedbackPane(
                    report = report(),
                    onCopy = { events += "copy"; false },
                    onOpenIssue = { events += "open"; false },
                )
            }
        }
        rule.onNodeWithTag("compatibility-feedback-copy").performScrollTo().performClick()
        rule.onNodeWithTag("compatibility-feedback-status").performScrollTo()
            .assertTextEquals("No se pudo copiar el informe.")
        rule.onNodeWithTag("compatibility-feedback-open").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(listOf("copy"), events) }
        rule.onNodeWithTag("compatibility-feedback-confirm-open").performClick()
        rule.onNodeWithTag("compatibility-feedback-status").performScrollTo()
            .assertTextEquals("No se pudo abrir GitHub. Puedes copiar el informe y abrir GitHub por tu cuenta.")
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(listOf("copy", "open"), events) }
    }

    @Test
    fun existingHelpMenuOpensPreviewAndBackReturnsToMenuWithoutDismissing() {
        var dismissals = 0
        val context = ApplicationProvider.getApplicationContext<Context>()
        rule.setContent { JuntaFirmaTheme { LegalInformationDialog(onDismiss = { dismissals++ }) } }

        rule.onNodeWithTag("legal-compatibility-feedback").performScrollTo().performClick()
        rule.onNodeWithTag("compatibility-feedback-report").assertExists()
        rule.onNodeWithTag("compatibility-feedback-confirmation").assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.legal_back)).performClick()
        rule.onNodeWithTag("compatibility-feedback-report").assertDoesNotExist()
        rule.onNodeWithTag("legal-compatibility-feedback").performScrollTo().assertIsDisplayed()
        rule.runOnIdle { assertEquals(0, dismissals) }
    }

    private fun report() = CompatibilityFeedbackReport.build("0.2.11", 13, 36)
}

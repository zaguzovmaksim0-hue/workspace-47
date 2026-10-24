package dev.junta.firmamobile.ui.history

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import dev.junta.firmamobile.R
import dev.junta.firmamobile.history.InMemoryOperationJournal
import dev.junta.firmamobile.history.LocalOutcome
import dev.junta.firmamobile.history.OperationKind
import dev.junta.firmamobile.testing.createComposeRule
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
class VerificationJournalDialogTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun onlyCoarseRowsAreShownWithHonestScopeAndClearRequiresAnAccessibleExplicitClick() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val journal = InMemoryOperationJournal()
        for (outcome in listOf(LocalOutcome.COMPLETED, LocalOutcome.CANCELLED, LocalOutcome.FAILED)) {
            journal.append(OperationKind.VERIFY, outcome, Instant.EPOCH)
        }
        val snapshot = journal.snapshot()
        rule.setContent { JuntaFirmaTheme { verificationJournalDialog(journal, onDismiss = {}) } }
        rule.onNodeWithText(context.getString(R.string.verification_journal_scope)).performScrollTo().assertExists()
        rule.onNodeWithText(context.getString(R.string.verification_journal_integrity)).performScrollTo().assertExists()
        rule.onNodeWithText(context.getString(R.string.verification_journal_copies)).performScrollTo().assertExists()
        rule.onNodeWithText(context.getString(R.string.verification_journal_provider_warning)).performScrollTo().assertExists()
        for (label in listOf(
            R.string.verification_journal_completed,
            R.string.verification_journal_cancelled,
            R.string.verification_journal_failed,
        )) {
            rule.onNodeWithText(context.getString(label), substring = true).performScrollTo().assertExists()
        }
        rule.onNodeWithTag("verification-journal-export").performScrollTo()
            .assertHasClickAction().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("verification-journal-close").assertHasClickAction().assertHeightIsAtLeast(48.dp)
        assertEquals(3, journal.snapshot().entries.size)
        rule.onNodeWithTag("verification-journal-clear").performScrollTo()
            .assertHasClickAction().assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithText(context.getString(R.string.verification_journal_empty)).performScrollTo().assertExists()
        rule.onNodeWithTag("verification-journal-row-1").assertDoesNotExist()
        assertTrue(journal.snapshot().entries.isEmpty())
        assertEquals(3, snapshot.entries.size)
    }

    @Test
    fun openingAndRecomposingNeverAppendAndClosingDoesNotClearTheSessionJournal() {
        val journal = InMemoryOperationJournal().apply {
            append(OperationKind.VERIFY, LocalOutcome.COMPLETED, Instant.EPOCH)
        }
        val visible = mutableStateOf(true)
        rule.setContent {
            JuntaFirmaTheme {
                verificationJournalEntry(onClick = { visible.value = true })
                if (visible.value) verificationJournalDialog(journal, onDismiss = { visible.value = false })
            }
        }
        rule.onNodeWithTag("verification-journal-row-1").performScrollTo().assertExists()
        rule.runOnIdle { journal.append(OperationKind.VERIFY, LocalOutcome.FAILED, Instant.EPOCH.plusSeconds(1)) }
        rule.onNodeWithTag("verification-journal-row-2").assertDoesNotExist() // The displayed snapshot is detached.
        rule.onNodeWithTag("verification-journal-close").performClick()
        assertEquals(2, journal.snapshot().entries.size)
        rule.onNodeWithTag("open-verification-journal").assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithTag("verification-journal-row-2").performScrollTo().assertExists()
        assertEquals(2, journal.snapshot().entries.size)
    }
}

package dev.junta.firmamobile.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.junta.firmamobile.R
import dev.junta.firmamobile.testing.createComposeRule
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
import org.junit.Assert.assertEquals
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
class BrowserPopupConfirmationDialogTest {
    @get:Rule val rule = createComposeRule()
    private val events = mutableListOf<String>()

    @Test fun aPendingWindowDoesNothingUntilTheUserConfirms() {
        show()
        rule.onNodeWithTag("browser-popup-confirmation").assertIsDisplayed()
        rule.onNodeWithText(text(R.string.browser_popup_confirmation_message)).assertIsDisplayed()
        assertEquals(emptyList<String>(), events)
        rule.onNodeWithText(text(R.string.browser_popup_confirmation_open)).performClick()
        assertEquals(listOf("confirm"), events)
    }

    @Test fun cancellationNeverApprovesThePendingWindow() {
        show()
        rule.onNodeWithText(text(R.string.browser_popup_confirmation_cancel)).performClick()
        assertEquals(listOf("cancel"), events)
    }

    private fun show() {
        rule.setContent {
            JuntaFirmaTheme {
                BrowserPopupConfirmationDialog(onConfirm = { events += "confirm" }, onCancel = { events += "cancel" })
            }
        }
    }
    private fun text(id: Int): String = RuntimeEnvironment.getApplication().getString(id)
}

package dev.junta.firmamobile.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.junta.firmamobile.R
import dev.junta.firmamobile.afirma.servlet.AfirmaFallbackPrompt
import dev.junta.firmamobile.testing.createComposeRule
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
import java.util.UUID
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
class NativeAfirmaFallbackDialogTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<String>()
    private var prompt by mutableStateOf(AfirmaFallbackPrompt(UUID.randomUUID(), invalid = true))

    @Test
    fun incompleteRequestShowsItsReasonAndOnlyClosesThePrompt() {
        prompt = AfirmaFallbackPrompt(UUID.randomUUID(), invalid = true, reason = "missing_storage_url")
        show()
        rule.onNodeWithText(text(R.string.native_afirma_invalid)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.native_afirma_reason, "missing_storage_url")).assertIsDisplayed()
        rule.onAllNodesWithText(text(R.string.native_afirma_official)).assertCountEquals(0)
        rule.onNodeWithText(text(R.string.native_afirma_close)).performClick()
        assertEquals(listOf("close"), events)
    }

    @Test
    fun unsupportedRequestOffersOnlyTheSeparateOfficialApp() {
        prompt = AfirmaFallbackPrompt(UUID.randomUUID(), invalid = false)
        show()
        rule.onNodeWithText(text(R.string.native_afirma_unsupported)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.native_afirma_official)).performClick()
        assertEquals(listOf("official"), events)
    }

    @Test
    fun fallbackNeverOffersToSwitchTheCurrentBrowserMode() {
        for (invalid in listOf(false, true)) {
            events.clear()
            prompt = AfirmaFallbackPrompt(UUID.randomUUID(), invalid = invalid)
            if (!invalid) show()
            rule.onAllNodesWithText("Modo específico del portal").assertCountEquals(0)
            rule.onAllNodesWithText(text(R.string.native_afirma_reason, "").trim(), substring = true).assertCountEquals(0)
            rule.onAllNodesWithText(text(R.string.native_afirma_official)).assertCountEquals(if (invalid) 0 else 1)
            rule.onNodeWithText(text(R.string.native_afirma_close)).performClick()
            assertEquals(listOf("close"), events)
        }
    }

    @Test
    fun desktopWebsocketRefusalExplainsTheUnsupportedProtocolWithoutRawCode() {
        prompt = AfirmaFallbackPrompt(UUID.randomUUID(), invalid = false, reason = "desktop_websocket_not_implemented")
        show()
        rule.onNodeWithText(text(R.string.external_afirma_websocket_unsupported)).assertIsDisplayed()
        rule.onAllNodesWithText(text(R.string.native_afirma_unsupported)).assertCountEquals(0)
        rule.onAllNodesWithText(text(R.string.native_afirma_reason, prompt.reason!!)).assertCountEquals(0)
        assertEquals(emptyList<String>(), events)
        rule.onNodeWithText(text(R.string.native_afirma_close)).performClick()
        assertEquals(listOf("close"), events)
    }

    @Test
    fun similarlyNamedUnknownReasonKeepsTheGenericDiagnostic() {
        prompt = AfirmaFallbackPrompt(UUID.randomUUID(), invalid = false, reason = "desktop_websocket_not_implemented_extra")
        show()
        rule.onNodeWithText(text(R.string.native_afirma_unsupported)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.native_afirma_reason, prompt.reason!!)).assertIsDisplayed()
        rule.onAllNodesWithText(text(R.string.external_afirma_websocket_unsupported)).assertCountEquals(0)
    }

    @Test
    fun invalidRequestKeepsItsErrorEvenIfAnUnsupportedReasonIsSupplied() {
        prompt = AfirmaFallbackPrompt(UUID.randomUUID(), invalid = true, reason = "desktop_websocket_not_implemented")
        show()
        rule.onNodeWithText(text(R.string.native_afirma_invalid)).assertIsDisplayed()
        rule.onAllNodesWithText(text(R.string.external_afirma_websocket_unsupported)).assertCountEquals(0)
        rule.onAllNodesWithText(text(R.string.native_afirma_official)).assertCountEquals(0)
    }

    private fun show() {
        rule.setContent {
            JuntaFirmaTheme {
                NativeAfirmaFallbackDialog(
                    prompt = prompt,
                    onClose = { events += "close" },
                    onOpenOfficial = { events += "official" },
                )
            }
        }
    }

    private fun text(id: Int, vararg args: Any): String = RuntimeEnvironment.getApplication().getString(id, *args)
}

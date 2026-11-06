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
    fun incompleteRequestOnAReviewedPortalOffersItsModeAndNoOfficialPayload() {
        prompt = AfirmaFallbackPrompt(UUID.randomUUID(), invalid = true, reason = "missing_storage_url", portalMode = true)
        show()
        rule.onNodeWithText(text(R.string.native_afirma_invalid)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.native_afirma_portal_mode_hint)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.native_afirma_reason, "missing_storage_url")).assertIsDisplayed()
        rule.onAllNodesWithText(text(R.string.native_afirma_official)).assertCountEquals(0)
        rule.onNodeWithText(text(R.string.catalog_open_profile_compatibility)).performClick()
        rule.onNodeWithText(text(R.string.native_afirma_close)).performClick()
        assertEquals(listOf("portal", "close"), events)
    }

    @Test
    fun unsupportedRequestOnAReviewedPortalKeepsTheOfficialAppAsSecondaryChoice() {
        prompt = AfirmaFallbackPrompt(UUID.randomUUID(), invalid = false, portalMode = true)
        show()
        rule.onNodeWithText(text(R.string.native_afirma_unsupported)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.native_afirma_official)).performClick()
        rule.onNodeWithText(text(R.string.catalog_open_profile_compatibility)).performClick()
        assertEquals(listOf("official", "portal"), events)
    }

    @Test
    fun withoutAReviewedModeTheDialogKeepsItsPreviousActions() {
        for (invalid in listOf(false, true)) {
            events.clear()
            prompt = AfirmaFallbackPrompt(UUID.randomUUID(), invalid = invalid)
            if (!invalid) show()
            rule.onAllNodesWithText(text(R.string.catalog_open_profile_compatibility)).assertCountEquals(0)
            rule.onAllNodesWithText(text(R.string.native_afirma_portal_mode_hint)).assertCountEquals(0)
            rule.onAllNodesWithText(text(R.string.native_afirma_reason, "").trim(), substring = true).assertCountEquals(0)
            rule.onAllNodesWithText(text(R.string.native_afirma_official)).assertCountEquals(if (invalid) 0 else 1)
            rule.onNodeWithText(text(R.string.native_afirma_close)).performClick()
            assertEquals(listOf("close"), events)
        }
    }

    private fun show() {
        rule.setContent {
            JuntaFirmaTheme {
                NativeAfirmaFallbackDialog(
                    prompt = prompt,
                    onClose = { events += "close" },
                    onOpenOfficial = { events += "official" },
                    onOpenPortalMode = { events += "portal" },
                )
            }
        }
    }

    private fun text(id: Int, vararg args: Any): String = RuntimeEnvironment.getApplication().getString(id, *args)
}

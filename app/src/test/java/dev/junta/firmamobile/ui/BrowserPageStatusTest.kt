package dev.junta.firmamobile.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.junta.firmamobile.browser.BrowserErrorCode
import dev.junta.firmamobile.browser.ClientCertPreferenceBarrierState
import dev.junta.firmamobile.browser.SiteClearResult
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
class BrowserPageStatusTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun certificateRecoveryTakesPriorityOverBrowserRetry() {
        val state = mutableStateOf(
            BrowserPageStatusState(
                browserError = BrowserErrorCode.NETWORK_ERROR,
                clientCertPreferenceState = ClientCertPreferenceBarrierState.FAILED,
            ),
        )
        val events = mutableListOf<String>()
        showStatus(state, events)

        rule.onNodeWithText("No se pudo preparar una sesión nueva de certificado. Vuelve a iniciar el acceso.")
            .assertIsDisplayed()
        rule.onNodeWithContentDescription("Reintentar").performClick()
        rule.runOnIdle {
            assertEquals(listOf("recover"), events)
            state.value = BrowserPageStatusState(browserError = BrowserErrorCode.CLIENT_CERT_PREFERENCES)
        }

        rule.onNodeWithContentDescription("Reintentar").performClick()
        rule.runOnIdle {
            assertEquals(listOf("recover", "recover"), events)
            state.value = BrowserPageStatusState(browserError = BrowserErrorCode.NETWORK_ERROR)
        }

        rule.onNodeWithText("No se pudo cargar el portal de forma segura.").assertIsDisplayed()
        rule.onNodeWithContentDescription("Reintentar").performClick()
        rule.runOnIdle { assertEquals(listOf("recover", "recover", "reload"), events) }
    }

    @Test
    fun cleanupOutcomeRemovesPreviousErrorRetryAndUsesPoliteAnnouncement() {
        val state = mutableStateOf(BrowserPageStatusState(browserError = BrowserErrorCode.NETWORK_ERROR))
        val events = mutableListOf<String>()
        showStatus(state, events)

        rule.onNodeWithContentDescription("Reintentar").performClick()
        rule.runOnIdle {
            assertEquals(listOf("reload"), events)
            state.value = BrowserPageStatusState(globalClearResult = true)
        }

        rule.onNodeWithText("Se borraron todos los datos web de la aplicación.").assertIsDisplayed()
        assertLiveRegion(LiveRegionMode.Polite)
        rule.onNodeWithContentDescription("Reintentar").assertDoesNotExist()
        rule.onNodeWithTag(BROWSER_LOADING_TAG).assertDoesNotExist()

        rule.runOnIdle { state.value = BrowserPageStatusState() }
        rule.onNodeWithTag(BROWSER_NOTICE_TAG).assertDoesNotExist()
        rule.onNodeWithContentDescription("Reintentar").assertDoesNotExist()
        rule.runOnIdle { assertEquals(listOf("reload"), events) }
    }

    @Test
    fun clearingNoticeKeepsItsPriorityAndLoadingFollowsCurrentPreparation() {
        val state = mutableStateOf(
            BrowserPageStatusState(
                compatibilityError = true,
                clientCertPreferenceState = ClientCertPreferenceBarrierState.CLEARING,
            ),
        )
        val events = mutableListOf<String>()
        showStatus(state, events)

        rule.onNodeWithText("Preparando una sesión nueva de certificado…").assertIsDisplayed()
        assertLiveRegion(LiveRegionMode.Polite)
        rule.onNodeWithTag(BROWSER_LOADING_TAG).assertIsDisplayed()
        rule.onNodeWithContentDescription("Reintentar").assertDoesNotExist()

        rule.runOnIdle { state.value = BrowserPageStatusState(clientAuthPreparing = true) }
        rule.onNodeWithTag(BROWSER_NOTICE_TAG).assertDoesNotExist()
        rule.onNodeWithTag(BROWSER_LOADING_TAG).assertIsDisplayed()

        rule.runOnIdle { state.value = BrowserPageStatusState(pageLoading = true) }
        rule.onNodeWithTag(BROWSER_LOADING_TAG).assertIsDisplayed()

        rule.runOnIdle { state.value = BrowserPageStatusState() }
        rule.onNodeWithTag(BROWSER_LOADING_TAG).assertDoesNotExist()
        rule.runOnIdle { assertEquals(emptyList<String>(), events) }
    }

    @Test
    fun limitedSiteCleanupShowsItsExistingNoticeWithoutRetry() {
        val state = mutableStateOf(
            BrowserPageStatusState(siteClearResult = SiteClearResult.WEB_STORAGE_CLEARED_COOKIE_CLEAR_UNAVAILABLE),
        )
        val events = mutableListOf<String>()
        showStatus(state, events)

        rule.onNodeWithText(
            "Se borró el almacenamiento del sitio. No se puede garantizar la eliminación de todas sus cookies; " +
                "pueden quedar cookies de otras rutas.",
        ).assertIsDisplayed()
        assertLiveRegion(LiveRegionMode.Assertive)
        rule.onNodeWithContentDescription("Reintentar").assertDoesNotExist()
        rule.runOnIdle { assertEquals(emptyList<String>(), events) }
    }

    private fun showStatus(state: State<BrowserPageStatusState>, events: MutableList<String>) {
        rule.setContent {
            JuntaFirmaTheme {
                Column {
                    BrowserPageStatus(
                        state = state.value,
                        onRecoverClientCertPreferences = { events += "recover" },
                        onRetryBrowserError = { events += "reload" },
                    )
                }
            }
        }
    }

    private fun assertLiveRegion(mode: LiveRegionMode) {
        rule.onNodeWithTag(BROWSER_NOTICE_TAG)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, mode))
    }
}

package dev.junta.firmamobile.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.R
import dev.junta.firmamobile.testing.createComposeRule
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

/** Synthetic callbacks only. Execution belongs to the exact-head canonical Android CI gate. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w480dp-h800dp")
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class IndustrialBrowserTopBarDecompositionTest {
    @get:Rule
    val rule = createComposeRule()

    private val menuEntries = listOf(
        R.string.browser_site_information to "information",
        R.string.browser_open_external to "external",
        R.string.browser_change_certificate to "change",
        R.string.browser_lock_certificate to "lock",
        R.string.browser_clear_current_site to "clear-site",
        R.string.browser_clear_session to "clear-session",
        R.string.browser_delete_all_data to "delete-all",
    )

    @Test
    fun hostTagHeightAndNavigationCallbacksArePreserved() {
        val events = mutableListOf<String>()
        showToolbar(onEvent = { events += it })

        rule.onNodeWithTag(BROWSER_ADDRESS_LABEL_TAG).assertTextEquals("toolbar.example.invalid")
        rule.onNodeWithTag(TOOLBAR_TAG).assertHeightIsEqualTo(BrowserIndustrialToolbarHeight)
        assertMenuClosed()
        rule.runOnIdle { assertTrue(events.isEmpty()) }
        rule.onNodeWithContentDescription(label(R.string.browser_action_back)).performClick()
        rule.onNodeWithContentDescription(label(R.string.browser_action_home)).performClick()
        rule.onNodeWithContentDescription(label(R.string.browser_action_reload)).performClick()
        rule.runOnIdle { assertEquals(listOf("back", "home", "reload"), events) }
        assertMenuClosed()
    }

    @Test
    fun callerInsetsAddToTheOriginalToolbarAndSeparatorHeight() {
        showToolbar(onEvent = {}, windowInsets = WindowInsets(top = 7.dp, bottom = 5.dp))

        rule.onNodeWithTag(TOOLBAR_TAG).assertHeightIsEqualTo(BrowserIndustrialToolbarHeight + 12.dp)
    }

    @Test
    fun longHostRemainsBetweenBackAndHomeWithoutDisplacingButtons() {
        showToolbar(onEvent = {}, host = { "long-host-".repeat(40) + ".example.invalid" })

        val back = rule.onNodeWithContentDescription(label(R.string.browser_action_back))
        val home = rule.onNodeWithContentDescription(label(R.string.browser_action_home))
        val reload = rule.onNodeWithContentDescription(label(R.string.browser_action_reload))
        val more = rule.onNodeWithContentDescription(label(R.string.browser_action_more))
        listOf(back, home, reload, more).forEach { it.assertIsDisplayed() }
        val host = rule.onNodeWithTag(BROWSER_ADDRESS_LABEL_TAG).getUnclippedBoundsInRoot()
        assertTrue(back.getUnclippedBoundsInRoot().right <= host.left)
        assertTrue(host.right <= home.getUnclippedBoundsInRoot().left)
        assertTrue(home.getUnclippedBoundsInRoot().right <= reload.getUnclippedBoundsInRoot().left)
        assertTrue(reload.getUnclippedBoundsInRoot().right <= more.getUnclippedBoundsInRoot().left)
    }

    @Test
    fun openingMenuShowsOriginalItemOrderWithoutInvokingActions() {
        val events = mutableListOf<String>()
        showToolbar(onEvent = { events += it })
        openMenu()

        val tops = menuEntries.map { (id, _) ->
            rule.onNodeWithText(label(id)).assertIsDisplayed().getUnclippedBoundsInRoot().top
        }
        assertTrue(tops.zipWithNext().all { (previous, next) -> previous < next })
        rule.runOnIdle { assertTrue(events.isEmpty()) }
    }

    @Test
    fun eachRequiredAndOptionalMenuActionRunsOnceAndMenuCanReopen() {
        val events = mutableListOf<String>()
        showToolbar(onEvent = { events += it })

        menuEntries.forEachIndexed { index, (id, _) ->
            openMenu()
            rule.onNodeWithText(label(id)).performClick()
            assertMenuClosed()
            rule.runOnIdle { assertEquals(menuEntries.take(index + 1).map { it.second }, events) }
        }
    }

    @Test
    fun everyMenuActionClosesBeforeInvokingAThrowingCallback() {
        val events = mutableListOf<String>()
        showToolbar(onEvent = {
            events += it
            throw ExpectedToolbarAction()
        })

        menuEntries.forEachIndexed { index, (id, _) ->
            openMenu()
            // Invoke the public semantics action synchronously and catch only the synthetic failure.
            // Closing after the callback would leave the menu open when this callback throws.
            val click = rule.onNodeWithText(label(id)).fetchSemanticsNode()
                .config[SemanticsActions.OnClick].action ?: error("Menu item has no click action")
            rule.runOnUiThread {
                try {
                    click()
                    fail("Expected the synthetic action failure")
                } catch (_: ExpectedToolbarAction) {
                    // The production action is replaced by a test-only callback, never performed.
                }
            }
            rule.waitForIdle()
            assertMenuClosed()
            rule.runOnIdle { assertEquals(menuEntries.take(index + 1).map { it.second }, events) }
        }
    }

    @Test
    fun bothNullOptionalCallbacksKeepTheirItemsAbsent() {
        assertOptionalItems(information = false, external = false)
    }

    @Test
    fun informationCanBePresentWithoutExternalBrowserItem() {
        assertOptionalItems(information = true, external = false)
    }

    @Test
    fun externalBrowserCanBePresentWithoutInformationItem() {
        assertOptionalItems(information = false, external = true)
    }

    @Test
    fun dismissingMenuDoesNotInvokeAnActionAndAllowsReopening() {
        val events = mutableListOf<String>()
        showToolbar(onEvent = { events += it })
        openMenu()
        rule.onNode(isPopup()).performKeyInput { pressKey(Key.Back) }

        assertMenuClosed()
        rule.runOnIdle { assertTrue(events.isEmpty()) }
        openMenu()
        rule.onNodeWithText(label(R.string.browser_change_certificate)).assertIsDisplayed()
    }

    @Test
    fun hostRecompositionKeepsTheExistingOwnersExpandedMenuState() {
        var host by mutableStateOf("first.example.invalid")
        val events = mutableListOf<String>()
        showToolbar(onEvent = { events += it }, host = { host })
        openMenu()
        rule.runOnIdle { host = "second.example.invalid" }

        rule.onNodeWithTag(BROWSER_ADDRESS_LABEL_TAG).assertTextEquals("second.example.invalid")
        rule.onNodeWithText(label(R.string.browser_site_information)).assertIsDisplayed().performClick()
        assertMenuClosed()
        rule.runOnIdle { assertEquals(listOf("information"), events) }
    }

    private fun showToolbar(
        onEvent: (String) -> Unit,
        host: () -> String = { "toolbar.example.invalid" },
        includeExternal: Boolean = true,
        includeInformation: Boolean = true,
        windowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        rule.setContent {
            JuntaFirmaTheme {
                IndustrialBrowserTopBar(
                    host = host(),
                    onBack = { onEvent("back") },
                    onHome = { onEvent("home") },
                    onReload = { onEvent("reload") },
                    onChangeCertificate = { onEvent("change") },
                    onLockCertificate = { onEvent("lock") },
                    onClearCurrentSiteRequested = { onEvent("clear-site") },
                    onClearSessionRequested = { onEvent("clear-session") },
                    onDeleteAllBrowserDataRequested = { onEvent("delete-all") },
                    onOpenInBrowser = if (includeExternal) {
                        { onEvent("external") }
                    } else {
                        null
                    },
                    onShowSiteInformation = if (includeInformation) {
                        { onEvent("information") }
                    } else {
                        null
                    },
                    modifier = Modifier.testTag(TOOLBAR_TAG),
                    windowInsets = windowInsets,
                )
            }
        }
    }

    private fun openMenu() {
        rule.onNodeWithContentDescription(label(R.string.browser_action_more)).performClick()
    }

    private fun assertMenuClosed() {
        menuEntries.forEach { (id, _) -> rule.onNodeWithText(label(id)).assertDoesNotExist() }
    }

    private fun assertOptionalItems(information: Boolean, external: Boolean) {
        val events = mutableListOf<String>()
        showToolbar(onEvent = { events += it }, includeInformation = information, includeExternal = external)
        openMenu()
        if (information) {
            rule.onNodeWithText(label(R.string.browser_site_information)).assertIsDisplayed()
        } else {
            rule.onNodeWithText(label(R.string.browser_site_information)).assertDoesNotExist()
        }
        if (external) {
            rule.onNodeWithText(label(R.string.browser_open_external)).assertIsDisplayed()
        } else {
            rule.onNodeWithText(label(R.string.browser_open_external)).assertDoesNotExist()
        }
        menuEntries.drop(2).forEach { (id, _) -> rule.onNodeWithText(label(id)).assertIsDisplayed() }
        rule.runOnIdle { assertTrue(events.isEmpty()) }
    }

    private fun label(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    private class ExpectedToolbarAction : RuntimeException()

    private companion object {
        const val TOOLBAR_TAG = "toolbar_decomposition_test_root"
    }
}

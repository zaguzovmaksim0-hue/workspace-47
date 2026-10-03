package dev.junta.firmamobile.catalog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
import java.net.URI
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

/** Isolated JVM Compose components: no MainActivity, WebView, external service
 * or device/emulator E2E. The callback records a URI instead of opening it. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@Config(qualifiers = "w400dp-h1200dp")
class CatalogAddressScreenTest {
    @get:Rule val rule = createComposeRule()
    private val opened = mutableListOf<URI>()
    private val ui = mutableStateOf(PortalCatalogUiState())
    private val visible = mutableStateOf(true)
    private var portalOpens = 0

    @Test fun addressIsInlineAboveCatalogSearchWithoutAnExtraOpeningDialog() {
        showCatalog()
        rule.onNodeWithTag("public-web-address").assertIsDisplayed()
        rule.onNodeWithTag("public-web-open-confirm").assertIsNotEnabled()
        rule.onNodeWithTag("catalog-search-field").assertIsDisplayed()
        rule.onNodeWithTag("public-web-open-dialog").assertDoesNotExist()
        rule.onNodeWithTag("catalog-open-public-web").assertDoesNotExist()
        val address = rule.onNodeWithTag("catalog-address-card").fetchSemanticsNode().boundsInRoot
        val search = rule.onNodeWithTag("catalog-search-field").fetchSemanticsNode().boundsInRoot
        assertTrue(address.bottom <= search.top)
        assertTrue(opened.isEmpty())
    }

    @Test fun keyboardOpensABareDomainOnceAndRetainsExactRequestComponents() {
        showCatalog()
        val raw = "portal.example/a%2Fb?token=not-logged+x&v=%252F#section"
        rule.onNodeWithTag("public-web-address").performTextReplacement(raw)
        rule.onNodeWithTag("catalog-address-host").assertTextContains("Destino: portal.example")
        rule.onNodeWithTag("public-web-address").performImeAction()
        rule.onNodeWithTag("public-web-open-confirm").assertIsNotEnabled()
        rule.onNodeWithTag("public-web-address").assertIsNotEnabled()
        rule.runOnIdle { assertEquals(listOf(URI("https://$raw")), opened); assertEquals(0, portalOpens) }
    }

    @Test fun invalidInputShowsInlineErrorAndCorrectingItKeepsTheSameScreen() {
        showCatalog()
        rule.onNodeWithTag("public-web-address").performTextReplacement("http://portal.example")
        rule.onNodeWithTag("public-web-open-confirm").performClick()
        rule.onNodeWithTag("catalog-address-error").assertIsDisplayed()
        rule.runOnIdle { assertTrue(opened.isEmpty()) }
        rule.onNodeWithTag("public-web-address").performTextReplacement("portal.example/correct")
        rule.onNodeWithTag("catalog-address-error").assertDoesNotExist()
        rule.onNodeWithTag("public-web-open-confirm").performClick()
        rule.runOnIdle { assertEquals(URI("https://portal.example/correct"), opened.single()) }
    }

    @Test fun clipboardIsNotReadIntoTheFieldUntilTheUserPressesPasteAndThenOpen() {
        val clipboard = clipboard()
        clipboard.setPrimaryClip(ClipData.newPlainText("synthetic", "portal.example/pasted?state=one%2Ftwo"))
        showCatalog()
        rule.onNodeWithTag("public-web-address").assertTextContains("")
        rule.onNodeWithTag("public-web-open-confirm").assertIsNotEnabled()
        rule.onNodeWithTag("catalog-address-paste").performClick()
        rule.onNodeWithTag("public-web-address").assertTextContains("portal.example/pasted?state=one%2Ftwo")
        rule.runOnIdle { assertTrue(opened.isEmpty()) }
        rule.onNodeWithTag("public-web-open-confirm").performClick()
        rule.runOnIdle { assertEquals("https://portal.example/pasted?state=one%2Ftwo", opened.single().toASCIIString()) }
    }

    @Test fun clipboardContentUrisAndOversizeTextAreNotCoercedOrPartiallyOpened() {
        showCatalog()
        clipboard().setPrimaryClip(ClipData.newRawUri("synthetic", Uri.parse("content://unreachable.synthetic.example/secret")))
        rule.onNodeWithTag("catalog-address-paste").performClick()
        rule.onNodeWithTag("catalog-address-error").assertIsDisplayed()
        rule.onNodeWithTag("public-web-open-confirm").assertIsNotEnabled()
        clipboard().setPrimaryClip(ClipData.newPlainText("synthetic", "https://portal.example/" + "a".repeat(9000)))
        rule.onNodeWithTag("catalog-address-paste").performClick()
        rule.onNodeWithTag("public-web-open-confirm").assertIsNotEnabled()
        rule.onNodeWithTag("catalog-address-clear").performClick()
        rule.onNodeWithTag("catalog-address-error").assertDoesNotExist()
        rule.runOnIdle { assertTrue(opened.isEmpty()) }
    }

    @Test fun scrollAndClearSearchKeepTheAddressDraftAndReturnButtonReachesIt() {
        val repository = PortalCatalogRepository(BuiltInSiteProfiles.qaRegistry, BuiltInSiteProfiles.catalog, loadBundledPublicPortalCatalog())
        val items = repository.portals().take(8)
        ui.value = PortalCatalogUiState(sections = listOf(PortalCatalogSection(PortalCatalogSectionKind.NATIONAL, items)))
        showCatalog()
        rule.onNodeWithTag("public-web-address").performTextReplacement("retained.example/form")
        rule.onNodeWithTag("catalog-search-field").performTextReplacement("search query")
        rule.onNodeWithTag("catalog-search-clear").performClick()
        rule.onNodeWithTag("catalog-list").performScrollToNode(hasTestTag("catalog-open-${items.last().portalId.value}"))
        rule.onNodeWithTag("catalog-return-to-address").assertIsDisplayed().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("public-web-address").assertIsDisplayed().assertTextContains("retained.example/form")
        rule.runOnIdle { assertEquals("", ui.value.searchText); assertTrue(opened.isEmpty()) }
    }

    @Test fun leavingCatalogDropsTheTransientAddressRatherThanSavingAUrl() {
        showCatalog()
        rule.onNodeWithTag("public-web-address").performTextReplacement("portal.example/private?token=one")
        rule.runOnIdle { visible.value = false }
        rule.runOnIdle { visible.value = true }
        rule.onNodeWithTag("public-web-open-confirm").assertIsNotEnabled()
        rule.onNodeWithTag("catalog-address-host").assertDoesNotExist()
        rule.runOnIdle { assertTrue(opened.isEmpty()) }
    }

    @Test fun largeTextAndNarrowCardUseAFullWidthOpenButtonWithoutHorizontalClipping() {
        rule.setContent {
            JuntaFirmaTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                    var state by remember { mutableStateOf(CatalogAddressInput.empty().edit("portal.example")) }
                    Box(Modifier.width(280.dp).verticalScroll(rememberScrollState()).testTag("narrow-card")) {
                        CatalogAddressCard(state, { state = state.edit(it) }, {}, { state = state.clear() }, {
                            val result = state.submit(); state = result.state; result.destination?.let(opened::add)
                        })
                    }
                }
            }
        }
        rule.onNodeWithTag("public-web-open-confirm").performScrollTo().assertIsDisplayed()
        val card = rule.onNodeWithTag("narrow-card").fetchSemanticsNode().boundsInRoot
        for (tag in listOf("public-web-address", "catalog-address-paste", "catalog-address-clear", "public-web-open-confirm")) {
            val rect = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue(tag, rect.left >= card.left && rect.right <= card.right + 1f)
        }
        rule.onNodeWithTag("public-web-open-confirm").performClick()
        rule.runOnIdle { assertEquals(URI("https://portal.example"), opened.single()) }
    }

    private fun clipboard() = ApplicationProvider.getApplicationContext<Context>().getSystemService(ClipboardManager::class.java)
    private fun showCatalog() {
        rule.setContent {
            JuntaFirmaTheme {
                if (visible.value) PortalCatalogScreen(
                    state = ui.value, onSearchTextChange = { ui.value = ui.value.copy(searchText = it) },
                    onSelectRegion = {}, onUseLocation = {}, onOpenLocationSettings = {}, onOpenAppSettings = {},
                    onDismissLocationMessage = {}, onToggleFavorite = {}, onOpenPortal = { portalOpens++ },
                    onBackToCertificate = {}, onUserMessageShown = {}, onOpenPublicWeb = { opened += it },
                )
            }
        }
    }
}

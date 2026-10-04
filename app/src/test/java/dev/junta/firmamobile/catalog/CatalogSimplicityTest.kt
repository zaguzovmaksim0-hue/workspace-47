package dev.junta.firmamobile.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import dev.junta.firmamobile.testing.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

/** Isolated component tests with recording callbacks, not device/browser E2E. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@Config(qualifiers = "w400dp-h1200dp")
class CatalogSimplicityTest {
    @get:Rule val rule = createComposeRule()
    private val repo by lazy { PortalCatalogRepository(BuiltInSiteProfiles.qaRegistry, BuiltInSiteProfiles.catalog, loadBundledPublicPortalCatalog()) }
    private val portal by lazy { repo.portals().first { it.isEnabled } }
    private val opened = mutableListOf<PortalId>(); private val compatibility = mutableListOf<PortalId>()
    private val favorites = mutableListOf<PortalId>(); private var locations = 0
    private val state = mutableStateOf(PortalCatalogUiState())

    @Test fun eachCardShowsExactlyOneOpenSiteActionAndDoesNotSelectCompatibility() {
        showOne()
        scrollToCard()
        rule.onNodeWithTag(cardTag(), useUnmergedTree = true).onChildren().filter(hasClickAction()).assertCountEquals(1)
        rule.onNodeWithTag("catalog-open-profile-${portal.portalId.value}").assertDoesNotExist()
        rule.onNodeWithTag("catalog-option-favorite").assertDoesNotExist()
        rule.onNodeWithTag("catalog-open-${portal.portalId.value}").performClick()
        rule.runOnIdle { assertEquals(listOf(portal.portalId), opened); assertTrue(compatibility.isEmpty()); assertTrue(favorites.isEmpty()) }
    }

    @Test fun longPressOffersExplicitCompatibilityWithoutOpeningOrSigningAutomatically() {
        showOne(); scrollToCard()
        rule.onNodeWithText(portal.displayName).performTouchInput { longClick() }
        rule.onNodeWithTag("catalog-site-options").assertIsDisplayed()
        rule.runOnIdle { assertTrue(opened.isEmpty()); assertTrue(compatibility.isEmpty()) }
        rule.onNodeWithTag("catalog-open-profile-${portal.portalId.value}").performClick()
        rule.runOnIdle { assertTrue(opened.isEmpty()); assertEquals(listOf(portal.portalId), compatibility) }
        rule.onNodeWithTag("catalog-site-options").assertDoesNotExist()
    }

    @Test fun screenReaderOptionsRetainFavoriteManagementWithoutAPermanentButton() {
        showOne(); scrollToCard()
        val actions = rule.onNodeWithTag(cardTag()).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        rule.runOnIdle { assertEquals(1, actions.size); assertTrue(actions.single().action()) }
        rule.onNodeWithTag("catalog-option-favorite").performClick()
        rule.runOnIdle { assertEquals(listOf(portal.portalId), favorites); assertTrue(opened.isEmpty()) }
        rule.onNodeWithTag("catalog-site-options").assertDoesNotExist()
    }

    @Test fun geolocationIsHiddenUntilTheRegionPickerAndStillNeedsAnExplicitTap() {
        showOne()
        rule.onNodeWithText("Usar mi ubicación").assertDoesNotExist()
        rule.onNodeWithText("Cambiar región").performClick()
        rule.runOnIdle { assertEquals(0, locations) }
        rule.onNodeWithText("Usar mi ubicación").performClick()
        rule.runOnIdle { assertEquals(1, locations) }
    }

    @Test fun asynchronousCatalogLoadDoesNotShowTheNoResultsWarning() {
        state.value = PortalCatalogUiState(catalogLoading = true); show()
        rule.onNodeWithTag("catalog-list").performScrollToNode(hasTestTag("catalog-loading"))
        rule.onNodeWithTag("catalog-loading").assertIsDisplayed()
        rule.runOnIdle { state.value = oneState() }
        rule.onNodeWithTag("catalog-loading").assertDoesNotExist()
        scrollToCard(); rule.onNodeWithTag("catalog-open-${portal.portalId.value}").assertIsDisplayed()
    }

    @Test fun fullCatalogKeepsOnlyABoundedVisibleSubsetComposed() {
        state.value = PortalCatalogUiState(searchText = "synthetic visible list", sections = listOf(
            PortalCatalogSection(PortalCatalogSectionKind.NATIONAL, repo.portals())))
        show()
        val cards = SemanticsMatcher("catalog card") { it.config.contains(SemanticsProperties.TestTag) && it.config[SemanticsProperties.TestTag].startsWith("catalog-card-") }
        rule.onNodeWithTag("catalog-list").performScrollToNode(hasTestTag("catalog-open-${repo.portals().last().portalId.value}"))
        val composed = rule.onAllNodes(cards, useUnmergedTree = true).fetchSemanticsNodes().size
        assertTrue("183 catalog rows must remain lazy; composed=$composed", composed in 1..24)
    }

    @Test fun onePrimaryButtonFitsANarrowCardWithLargeText() {
        rule.setContent {
            JuntaFirmaTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.8f)) {
                    Box(Modifier.width(280.dp).verticalScroll(rememberScrollState())) {
                        CatalogPortalCard(portal, false, { opened += it.portalId }, {})
                    }
                }
            }
        }
        rule.onNodeWithTag("catalog-open-${portal.portalId.value}").performScrollTo().assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(listOf(portal.portalId), opened) }
    }

    private fun cardTag() = "catalog-card-${portal.portalId.value}"
    private fun scrollToCard() = rule.onNodeWithTag("catalog-list").performScrollToNode(hasTestTag(cardTag()))
    private fun oneState() = PortalCatalogUiState(searchText = "synthetic one item", sections = listOf(PortalCatalogSection(PortalCatalogSectionKind.NATIONAL, listOf(portal))))
    private fun showOne() { state.value = oneState(); show() }
    private fun show() {
        rule.setContent {
            JuntaFirmaTheme {
                PortalCatalogScreen(state.value, {}, {}, { locations++ }, {}, {}, {}, { favorites += it },
                    { opened += it.portalId }, {}, {}, onOpenCompatibilityPortal = { compatibility += it.portalId })
            }
        }
    }
}

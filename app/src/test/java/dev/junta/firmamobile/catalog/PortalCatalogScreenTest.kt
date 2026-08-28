package dev.junta.firmamobile.catalog

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import dev.junta.firmamobile.profile.BuildTrustPolicy
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.SiteProfileRegistry
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class PortalCatalogScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val profileCatalog = BuiltInSiteProfiles.catalog
    private val publicCatalog by lazy(::loadBundledPublicPortalCatalog)
    private val repository by lazy {
        PortalCatalogRepository(
            SiteProfileRegistry(profileCatalog, BuildTrustPolicy.QA),
            profileCatalog,
            publicCatalog,
        )
    }

    @Test
    fun `consumer card hides every developer status and still opens`() {
        var opened: PortalCatalogItem? = null
        val state = stateFor(searchText = "UNED")
        val uned = state.sections.flatMap { it.items }.single()

        setCatalogContent(state = state, onOpenPortal = { opened = it })

        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(uned.displayName))
        rule.onNodeWithText(uned.displayName).assertIsDisplayed()
        rule.onNodeWithText("Abrir").performClick()
        rule.onAllNodesWithText("E2E", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("CATÁLOGO LOCAL", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("IMPLEMENTADO", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("AutoFirma", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("TLS", substring = true).assertCountEquals(0)
        rule.onNodeWithText(uned.entryUrl.toASCIIString()).assertDoesNotExist()
        rule.onNodeWithText(uned.purpose).assertDoesNotExist()
        rule.runOnIdle { assertEquals(uned.portalId, opened?.portalId) }
    }

    @Test
    fun `non internal entry keeps an enabled open action`() {
        val releaseRepository = PortalCatalogRepository(
            SiteProfileRegistry(profileCatalog, BuildTrustPolicy.RELEASE),
            profileCatalog,
            publicCatalog,
        )
        val externalOnly = releaseRepository.portals().first { !it.isEnabled }
        var opened: PortalCatalogItem? = null
        val state = PortalCatalogUiState(
            selectedRegion = externalOnly.regionCode,
            sections = listOf(
                PortalCatalogSection(
                    kind = PortalCatalogSectionKind.SELECTED_REGION,
                    items = listOf(externalOnly),
                    regionCode = externalOnly.regionCode,
                ),
            ),
        )

        setCatalogContent(state = state, onOpenPortal = { opened = it })

        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(externalOnly.displayName))
        rule.onNodeWithText("Abrir").performClick()
        rule.runOnIdle {
            assertNotNull(opened)
            assertFalse(externalOnly.isEnabled)
        }
    }

    @Test
    fun `region picker searches and returns the selected region`() {
        var selected: PortalRegionCode? = null
        setCatalogContent(
            state = stateFor(selectedRegion = PortalRegionCode.SPAIN),
            onSelectRegion = { selected = it },
        )

        rule.onNodeWithText("Cambiar región").performClick()
        rule.onNodeWithText("ELIGE TU REGIÓN").assertIsDisplayed()
        rule.onNodeWithText("Buscar región").performTextInput("aragon")
        rule.onNodeWithText("Aragón").performClick()

        rule.runOnIdle { assertEquals(PortalRegionCode.ARAGON, selected) }
    }

    @Test
    @Config(qualifiers = "w600dp-h2000dp")
    fun `regional sections reveal only the region the user opens`() {
        val aragon = repository.portals().first { it.regionCode == PortalRegionCode.ARAGON }
        val andalusia = repository.portals().first { it.regionCode == PortalRegionCode.ANDALUSIA }
        val state = PortalCatalogUiState(
            selectedRegion = PortalRegionCode.SPAIN,
            sections = listOf(
                PortalCatalogSection(
                    kind = PortalCatalogSectionKind.REGION,
                    items = listOf(aragon),
                    regionCode = PortalRegionCode.ARAGON,
                ),
                PortalCatalogSection(
                    kind = PortalCatalogSectionKind.REGION,
                    items = listOf(andalusia),
                    regionCode = PortalRegionCode.ANDALUSIA,
                ),
            ),
        )

        setCatalogContent(state = state)

        rule.onNodeWithText(aragon.displayName).assertDoesNotExist()
        rule.onNodeWithText(andalusia.displayName).assertDoesNotExist()

        rule.onNodeWithText("Aragón").performClick()
        rule.onNodeWithText(aragon.displayName).assertIsDisplayed()
        rule.onNodeWithText(andalusia.displayName).assertDoesNotExist()

        rule.onNodeWithText("Andalucía").performClick()
        rule.onNodeWithText(andalusia.displayName).assertIsDisplayed()
        rule.onNodeWithText(aragon.displayName).assertDoesNotExist()
    }

    @Test
    fun `location is requested only from the explicit button`() {
        var requests = 0
        setCatalogContent(
            state = stateFor(),
            onUseLocation = { requests++ },
        )

        rule.runOnIdle { assertEquals(0, requests) }
        rule.onNodeWithText("Usar mi ubicación").performClick()
        rule.runOnIdle { assertEquals(1, requests) }
    }

    @Test
    fun `disabled location offers settings and manual selection`() {
        var settings = 0
        setCatalogContent(
            state = stateFor().copy(locationState = CatalogLocationState.LOCATION_DISABLED),
            onOpenLocationSettings = { settings++ },
        )

        rule.onNodeWithText("La ubicación está desactivada").assertIsDisplayed()
        rule.onNodeWithText("Elegir manualmente").assertIsDisplayed()
        rule.onNodeWithText("Abrir ajustes").performClick()
        rule.runOnIdle { assertEquals(1, settings) }
    }

    @Test
    fun `favorites and recent services appear before regional catalog`() {
        val favorite = repository.portals().first { it.regionCode == PortalRegionCode.SPAIN }
        val state = stateFor(
            favoritePortalIds = setOf(favorite.portalId),
            recentPortalIds = listOf(favorite.portalId),
        )

        setCatalogContent(state = state)

        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Favoritos"))
        rule.onNodeWithText("Favoritos").assertIsDisplayed()
        rule.onNodeWithText("Recientes").performScrollTo().assertIsDisplayed()
        assertEquals(PortalCatalogSectionKind.FAVORITES, state.sections[0].kind)
        assertEquals(PortalCatalogSectionKind.RECENT, state.sections[1].kind)
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun `catalog remains usable at narrow width and large font scale`() {
        val state = stateFor(searchText = "carne joven europeo")
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                JuntaFirmaTheme {
                    PortalCatalogScreen(
                        state = state,
                        onSearchTextChange = {},
                        onSelectRegion = {},
                        onUseLocation = {},
                        onOpenLocationSettings = {},
                        onOpenAppSettings = {},
                        onDismissLocationMessage = {},
                        onToggleFavorite = {},
                        onOpenPortal = {},
                        onUserMessageShown = {},
                    )
                }
            }
        }

        rule.onNodeWithText("Carné Joven Europeo de Andalucía")
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithText("Favorito").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Abrir").assertIsDisplayed()
    }

    private fun stateFor(
        searchText: String = "",
        selectedRegion: PortalRegionCode = PortalRegionCode.SPAIN,
        favoritePortalIds: Set<PortalId> = emptySet(),
        recentPortalIds: List<PortalId> = emptyList(),
    ): PortalCatalogUiState {
        val items = repository.portals(
            PortalCatalogQuery(
                searchText = searchText,
                selectedRegion = selectedRegion,
                favoritePortalIds = favoritePortalIds,
                recentPortalIds = recentPortalIds,
            ),
        )
        return PortalCatalogUiState(
            searchText = searchText,
            selectedRegion = selectedRegion,
            favoritePortalIds = favoritePortalIds,
            recentPortalIds = recentPortalIds,
            sections = buildPersonalizedPortalSections(
                items = items,
                selectedRegion = selectedRegion,
                favoritePortalIds = favoritePortalIds,
                recentPortalIds = recentPortalIds,
                searching = searchText.isNotBlank(),
            ),
        )
    }

    private fun setCatalogContent(
        state: PortalCatalogUiState,
        onSelectRegion: (PortalRegionCode) -> Unit = {},
        onUseLocation: () -> Unit = {},
        onOpenLocationSettings: () -> Unit = {},
        onOpenPortal: (PortalCatalogItem) -> Unit = {},
    ) {
        rule.setContent {
            JuntaFirmaTheme {
                PortalCatalogScreen(
                    state = state,
                    onSearchTextChange = {},
                    onSelectRegion = onSelectRegion,
                    onUseLocation = onUseLocation,
                    onOpenLocationSettings = onOpenLocationSettings,
                    onOpenAppSettings = {},
                    onDismissLocationMessage = {},
                    onToggleFavorite = {},
                    onOpenPortal = onOpenPortal,
                    onUserMessageShown = {},
                )
            }
        }
    }
}

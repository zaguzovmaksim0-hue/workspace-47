package dev.junta.firmamobile.catalog

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import dev.junta.firmamobile.testing.createComposeRule
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import dev.junta.firmamobile.profile.BuildTrustPolicy
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.SiteProfileRegistry
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    @Config(qualifiers = "es")
    fun `verified card displays Spanish verification date`() = assertVerificationDate("Verificado el ")

    @Test
    @Config(qualifiers = "en")
    fun `verified card displays English verification date`() = assertVerificationDate("Verified on ")

    @Test
    @Config(qualifiers = "ca")
    fun `verified card displays Catalan verification date`() = assertVerificationDate("Verificat el ")

    @Test
    @Config(qualifiers = "gl")
    fun `verified card displays Galician verification date`() = assertVerificationDate("Verificado o ")

    @Test
    @Config(qualifiers = "eu")
    fun `verified card displays Basque verification date`() = assertVerificationDate("Egiaztatze-data: ")

    @Test
    fun `card with missing verification date does not invent one`() {
        val portal = repository.portals().first { it.lastVerifiedOn != null }.copy(lastVerifiedOn = null)
        setVerificationCard(portal)
        rule.onNodeWithTag("catalog-verified-on-${portal.portalId.value}").assertDoesNotExist()
    }

    @Test
    fun `unverified card never displays a verification date`() {
        val portal = repository.portals().first { it.lastVerifiedOn != null }
            .copy(supportStatus = PortalSupportStatus.VERIFIED_CONTRACT)
        setVerificationCard(portal)
        rule.onNodeWithTag("catalog-verified-on-${portal.portalId.value}").assertDoesNotExist()
    }

    private fun assertVerificationDate(prefix: String) {
        val portal = repository.portals().single { it.profileId?.value == "carne-joven-andalucia" }
        var expected = ""
        rule.setContent {
            val locale = LocalConfiguration.current.locales[0]
            expected = prefix + checkNotNull(portal.lastVerifiedOn)
                .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
            JuntaFirmaTheme { CatalogPortalCard(portal, false, {}, {}, {}) }
        }
        rule.onNodeWithTag("catalog-verified-on-${portal.portalId.value}").assertIsDisplayed()
        rule.onAllNodesWithTag("catalog-status-${portal.portalId.value}").assertCountEquals(1)
        rule.onAllNodesWithText(expected).assertCountEquals(1)
        rule.onNodeWithText(expected).assertIsDisplayed()
    }

    private fun setVerificationCard(portal: PortalCatalogItem) {
        rule.setContent { JuntaFirmaTheme { CatalogPortalCard(portal, false, {}, {}, {}) } }
    }

    @Test
    fun `catalog offers a visible way back to the certificate screen`() {
        var backRequested = false
        setCatalogContent(
            state = stateFor(selectedRegion = PortalRegionCode.ANDALUSIA),
            onBackToCertificate = { backRequested = true },
        )

        rule.onNodeWithContentDescription("← Volver al certificado")
            .assertIsDisplayed()
            .performClick()

        rule.runOnIdle { assertEquals(true, backRequested) }
    }

    @Test
    fun `consumer card hides every developer status and still opens`() {
        var opened: PortalCatalogItem? = null
        val state = stateFor(searchText = "UNED")
        val uned = state.sections.flatMap { it.items }.single()

        setCatalogContent(state = state, onOpenPortal = { opened = it })

        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("catalog-open-${uned.portalId.value}"))
        rule.onNodeWithText(uned.displayName).assertIsDisplayed()
        rule.onNodeWithTag("catalog-open-${uned.portalId.value}").assertIsEnabled().performClick()
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
            searchText = "show external test card",
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

        rule.onNode(hasScrollToIndexAction()).performScrollToNode(
            hasTestTag("catalog-open-${externalOnly.portalId.value}"),
        )
        rule.onNodeWithTag("catalog-open-${externalOnly.portalId.value}").assertIsEnabled().performClick()
        rule.runOnIdle {
            assertNotNull(opened)
            assertFalse(externalOnly.isEnabled)
            assertTrue(externalOnly.opensWithoutProfile)
            assertTrue(externalOnly.canOpen)
        }
    }

    @Test
    fun `region picker searches and returns the selected region`() {
        var selected: PortalRegionCode? = null
        setCatalogContent(
            state = stateFor(selectedRegion = PortalRegionCode.SPAIN),
            onSelectRegion = { selected = it },
        )

        rule.onNodeWithText(
            org.robolectric.RuntimeEnvironment.getApplication()
                .getString(dev.junta.firmamobile.R.string.catalog_region_change),
        ).performClick()
        rule.onNodeWithText("ELIGE TU REGIÓN").assertIsDisplayed()
        rule.onNodeWithText("Buscar región").assertDoesNotExist()
        rule.onNodeWithText("Aragón").performClick()

        rule.runOnIdle { assertEquals(PortalRegionCode.ARAGON, selected) }
    }

    @Test
    fun `selected region catalog keeps every bundled service reachable`() {
        val items = repository.portals()
        val sections = buildPersonalizedPortalSections(
            items = items,
            selectedRegion = PortalRegionCode.ANDALUSIA,
            favoritePortalIds = emptySet(),
            recentPortalIds = emptyList(),
            searching = false,
        )
        val primary = sections.filter {
            it.kind in setOf(
                PortalCatalogSectionKind.SELECTED_REGION,
                PortalCatalogSectionKind.NATIONAL,
                PortalCatalogSectionKind.OTHER_REGIONS,
            )
        }
        val byKind = primary.associateBy { it.kind }

        assertEquals(183, items.size)
        assertEquals(15, byKind.getValue(PortalCatalogSectionKind.SELECTED_REGION).items.size)
        assertEquals(83, byKind.getValue(PortalCatalogSectionKind.NATIONAL).items.size)
        assertEquals(85, byKind.getValue(PortalCatalogSectionKind.OTHER_REGIONS).items.size)

        val visiblePortalIds = primary.flatMap { it.items }.map { it.portalId }
        assertEquals(items.size, visiblePortalIds.size)
        assertEquals(items.map { it.portalId }.toSet(), visiblePortalIds.toSet())
    }

    @Test
    @Config(qualifiers = "es-rES-w600dp-h2000dp")
    fun `other territories section is visible and collapsed for a selected region`() {
        val state = stateFor(selectedRegion = PortalRegionCode.ANDALUSIA)
        val otherSection = state.sections.single { it.kind == PortalCatalogSectionKind.OTHER_REGIONS }
        val otherPortal = otherSection.items.first()

        setCatalogContent(state = state)

        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Otros territorios de España"))
        rule.onNodeWithText("Otros territorios de España").assertIsDisplayed()
        rule.onNodeWithText("85 servicios").assertIsDisplayed()
        rule.onNodeWithText(otherPortal.displayName).assertDoesNotExist()

        rule.onNodeWithText("Otros territorios de España").performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(otherPortal.displayName))
        rule.onNodeWithText(otherPortal.displayName).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "es-rES-w600dp-h2000dp")
    fun `regional catalog shows selected region and collapses national`() {
        val andalusia = repository.portals().first { it.regionCode == PortalRegionCode.ANDALUSIA }
        val national = repository.portals().first { it.regionCode == PortalRegionCode.SPAIN }
        val state = PortalCatalogUiState(
            selectedRegion = PortalRegionCode.ANDALUSIA,
            sections = listOf(
                PortalCatalogSection(
                    kind = PortalCatalogSectionKind.SELECTED_REGION,
                    items = listOf(andalusia),
                    regionCode = PortalRegionCode.ANDALUSIA,
                ),
                PortalCatalogSection(
                    kind = PortalCatalogSectionKind.NATIONAL,
                    items = listOf(national),
                ),
            ),
        )

        setCatalogContent(state = state)

        rule.onNodeWithText(andalusia.displayName).assertIsDisplayed()
        rule.onNodeWithText(national.displayName).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "es-rES-w600dp-h2000dp")
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
        rule.onNodeWithText("Usar mi ubicación").assertDoesNotExist()
        rule.onNodeWithText(
            org.robolectric.RuntimeEnvironment.getApplication()
                .getString(dev.junta.firmamobile.R.string.catalog_region_change),
        ).performClick()
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
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Recientes"))
        rule.onNodeWithText("Recientes").assertIsDisplayed()
        assertEquals(PortalCatalogSectionKind.FAVORITES, state.sections[0].kind)
        assertEquals(PortalCatalogSectionKind.RECENT, state.sections[1].kind)
    }

    @Test
    @Config(qualifiers = "es-rES-w320dp-h640dp")
    fun `catalog remains usable at narrow width and large font scale`() {
        val state = stateFor(searchText = "carne joven europeo", selectedRegion = PortalRegionCode.ANDALUSIA)
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
                        onBackToCertificate = {},
                        onUserMessageShown = {},
                    )
                }
            }
        }

        rule.onNode(hasScrollToIndexAction())
            .performScrollToNode(hasText("Carné Joven Europeo de Andalucía"))
        rule.onNodeWithText("Carné Joven Europeo de Andalucía").assertIsDisplayed()
        rule.onNodeWithText("Favorito").assertDoesNotExist()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Abrir sitio"))
        rule.onNodeWithText("Abrir sitio").assertIsDisplayed()
        val portal = state.sections.flatMap { it.items }.single()
        val options = rule.onNodeWithTag("catalog-card-${portal.portalId.value}")
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
        rule.runOnIdle { assertTrue(options.single().action()) }
        rule.onNodeWithTag("catalog-option-favorite").assertIsDisplayed()
    }

    @Test
    fun `search by name filters catalog without an address open button`() {
        var opened: java.net.URI? = null
        val state = stateFor(searchText = "UNED")
        setCatalogContent(state = state, onOpenPublicWeb = { opened = it })

        rule.onNodeWithTag("public-web-open-confirm").assertDoesNotExist()
        rule.onNodeWithTag("profile-scope-verified-only").assertDoesNotExist()
        rule.runOnIdle { assertEquals(null, opened) }
    }

    @Test
    fun `address input shows open site and opens the same public web destination`() {
        var opened: java.net.URI? = null
        setCatalogContent(state = stateFor(), onOpenPublicWeb = { opened = it })

        rule.onNodeWithTag("public-web-address").performTextInput("portal.example")
        rule.onNodeWithTag("public-web-open-confirm").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(java.net.URI("https://portal.example"), opened) }
    }

    @Test
    fun `long address remains visible and opens the same destination`() {
        var opened: java.net.URI? = null
        var searched = ""
        val longAddress = "https://portal.example/" + "a".repeat(280)
        setCatalogContent(
            state = stateFor(),
            onOpenPublicWeb = { opened = it },
            onSearchTextChange = { searched = it },
        )

        rule.onNodeWithTag("public-web-address").performTextInput(longAddress)
        rule.onNodeWithTag("public-web-address").assertTextContains(longAddress)
        rule.onNodeWithTag("public-web-open-confirm").assertIsDisplayed().performClick()
        rule.runOnIdle {
            assertEquals(java.net.URI(longAddress), opened)
            assertEquals("", searched)
        }
    }

    @Test
    fun `address field shows the current search text`() {
        setCatalogContent(state = stateFor(searchText = "valencia"))

        rule.onNodeWithTag("public-web-address").assertTextContains("valencia")
    }

    @Test
    fun `address draft survives scrolling the catalog list`() {
        val state = PortalCatalogUiState(
            searchText = "",
            selectedRegion = PortalRegionCode.ANDALUSIA,
            sections = listOf(
                PortalCatalogSection(
                    PortalCatalogSectionKind.SELECTED_REGION,
                    repository.portals(),
                    PortalRegionCode.ANDALUSIA,
                ),
            ),
        )
        setCatalogContent(state = state, onOpenPublicWeb = {})

        rule.onNodeWithTag("public-web-address").performTextInput("portal.example")
        val lastPortal = state.sections.single().items.last()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(
            hasTestTag("catalog-open-${lastPortal.portalId.value}"),
        )
        rule.onNodeWithTag("public-web-address").assertIsNotDisplayed()

        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        rule.onNode(hasScrollToIndexAction())
            .performScrollToNode(hasTestTag("public-web-address"))
        rule.onNodeWithTag("public-web-address").assertTextContains("portal.example")
        rule.onNodeWithTag("public-web-open-confirm").assertIsDisplayed()
    }

    @Test
    fun `address field restores a short url search text`() {
        setCatalogContent(state = stateFor(searchText = "https://portal.example/path"))

        rule.onNodeWithTag("public-web-address")
            .assertTextContains("https://portal.example/path")
    }

    @Test
    fun `address field restores a bare host search text`() {
        setCatalogContent(state = stateFor(searchText = "portal.example"))

        rule.onNodeWithTag("public-web-address").assertTextContains("portal.example")
    }

    @Test
    fun `external search text change replaces the visible address draft`() {
        var state by mutableStateOf(stateFor(searchText = "valencia"))
        rule.setContent {
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
                    onBackToCertificate = {},
                    onUserMessageShown = {},
                )
            }
        }

        rule.onNodeWithTag("public-web-address").assertTextContains("valencia")
        rule.runOnIdle { state = stateFor(searchText = "madrid") }

        rule.onNodeWithTag("public-web-address").assertTextContains("madrid")
        val addressNode = rule.onNodeWithTag("public-web-address").fetchSemanticsNode()
        assertEquals("madrid", addressNode.config[SemanticsProperties.EditableText].text)
    }

    @Test
    fun `external search reset clears the visible address draft`() {
        var state by mutableStateOf(stateFor(searchText = "valencia"))
        rule.setContent {
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
                    onBackToCertificate = {},
                    onUserMessageShown = {},
                )
            }
        }

        rule.onNodeWithTag("public-web-address").assertTextContains("valencia")
        rule.runOnIdle { state = stateFor(searchText = "") }

        rule.onNodeWithTag("catalog-address-paste").assertIsDisplayed()
        val clearedAddressNode = rule.onNodeWithTag("public-web-address").fetchSemanticsNode()
        assertEquals("", clearedAddressNode.config[SemanticsProperties.EditableText].text)
    }

    @Test
    fun `favorite star on card toggles favorite with content description`() {
        val portal = repository.portals().first()
        val favorites = mutableListOf<PortalId>()
        val state = PortalCatalogUiState(
            searchText = "favorite star",
            sections = listOf(
                PortalCatalogSection(PortalCatalogSectionKind.NATIONAL, listOf(portal)),
            ),
        )
        setCatalogContent(state = state, onToggleFavorite = { favorites += it })

        val label = org.robolectric.RuntimeEnvironment.getApplication()
            .getString(dev.junta.firmamobile.R.string.catalog_favorite_add)
        rule.onAllNodesWithTag("catalog-favorite-${portal.portalId.value}").assertCountEquals(1)
        rule.onAllNodesWithTag("catalog-status-${portal.portalId.value}").assertCountEquals(1)
        val verifiedCount = if (portal.supportStatus == PortalSupportStatus.VERIFIED_E2E &&
            portal.lastVerifiedOn != null) 1 else 0
        rule.onAllNodesWithTag("catalog-verified-on-${portal.portalId.value}").assertCountEquals(verifiedCount)
        rule.onNodeWithContentDescription(label).assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(listOf(portal.portalId), favorites) }
    }

    @Test
    @Config(qualifiers = "es-rES-w600dp-h2000dp")
    fun `searching expands every matching section`() {
        val state = stateFor(searchText = "a", selectedRegion = PortalRegionCode.ANDALUSIA)
        setCatalogContent(state = state)

        val items = state.sections.flatMap { it.items }
        assertTrue(items.isNotEmpty())
        for (item in items.take(3)) {
            rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(item.displayName))
            rule.onNodeWithText(item.displayName).assertIsDisplayed()
        }
    }


    @Test
    fun `delayed search echo keeps two sequential local edits`() {
        var state by mutableStateOf(stateFor())
        var searched = ""
        setMutableCatalogContent(state = { state }, onSearchTextChange = { searched = it })

        val field = rule.onNodeWithTag("public-web-address")
        field.performTextInput("m")
        field.performTextInput("a")
        rule.runOnIdle { assertEquals("ma", catalogAddressEditableText()) }

        rule.runOnIdle { state = stateFor(searchText = "m") }
        rule.runOnIdle { assertEquals("ma", catalogAddressEditableText()) }

        rule.runOnIdle { state = stateFor(searchText = "ma") }
        rule.runOnIdle { assertEquals("ma", catalogAddressEditableText()) }

        rule.runOnIdle { state = stateFor(searchText = "madrid") }
        rule.runOnIdle { assertEquals("madrid", catalogAddressEditableText()) }

        rule.runOnIdle { state = stateFor(searchText = "") }
        rule.runOnIdle {
            assertEquals("", catalogAddressEditableText())
            assertEquals("ma", searched)
        }
    }

    @Test
    fun `delayed search echo preserves a long address draft`() {
        var state by mutableStateOf(stateFor())
        val searched = mutableListOf<String>()
        val longAddress = "https://portal.example/path?token=" + "a".repeat(200)
        setMutableCatalogContent(state = { state }, onSearchTextChange = { searched += it })

        val field = rule.onNodeWithTag("public-web-address")
        field.performTextInput("m")
        field.performTextReplacement(longAddress)
        rule.runOnIdle {
            assertEquals(longAddress, catalogAddressEditableText())
            assertEquals(listOf("m", ""), searched)
        }

        rule.runOnIdle { state = stateFor(searchText = "m") }
        rule.runOnIdle { assertEquals(longAddress, catalogAddressEditableText()) }

        rule.runOnIdle { state = stateFor(searchText = "madrid") }
        rule.runOnIdle { assertEquals("madrid", catalogAddressEditableText()) }
    }

    @Test
    fun `truncated initial url search text is visible but not openable`() {
        var opened: java.net.URI? = null
        val fullUrl = "https://portal.example/path?token=" + "a".repeat(200)
        setCatalogContent(
            state = stateFor(searchText = fullUrl.take(160)),
            onOpenPublicWeb = { opened = it },
        )

        rule.onNodeWithTag("public-web-address")
            .assertTextContains(fullUrl.take(160))
        rule.onNodeWithTag("public-web-open-confirm").assertDoesNotExist()
        rule.runOnIdle { assertEquals(null, opened) }
    }

    @Test
    fun `address draft is not persisted as catalog search text`() {
        var state by mutableStateOf(stateFor())
        var visible by mutableStateOf(true)
        var searched = ""
        rule.setContent {
            JuntaFirmaTheme {
                if (visible) {
                    PortalCatalogScreen(
                        state = state,
                        onSearchTextChange = { searched = it },
                        onSelectRegion = {},
                        onUseLocation = {},
                        onOpenLocationSettings = {},
                        onOpenAppSettings = {},
                        onDismissLocationMessage = {},
                        onToggleFavorite = {},
                        onOpenPortal = {},
                        onBackToCertificate = {},
                        onUserMessageShown = {},
                    )
                }
            }
        }

        rule.onNodeWithTag("public-web-address")
            .performTextInput("portal.example/private?token=one")
        rule.runOnIdle {
            assertEquals("portal.example/private?token=one", catalogAddressEditableText())
            assertEquals("", searched)
        }

        rule.runOnIdle { visible = false }
        rule.runOnIdle { visible = true }
        rule.runOnIdle { assertEquals("", catalogAddressEditableText()) }
        rule.onNodeWithTag("public-web-open-confirm").assertDoesNotExist()
    }

    @Test
    fun `replacing active search with an address clears the filter and keeps the draft`() {
        var state by mutableStateOf(stateFor(searchText = "valencia"))
        val searched = mutableListOf<String>()
        setMutableCatalogContent(state = { state }, onSearchTextChange = { searched += it })

        val address = "https://portal.example/private?token=one"
        rule.onNodeWithTag("public-web-address").performTextReplacement(address)
        rule.runOnIdle {
            assertEquals(address, catalogAddressEditableText())
            assertTrue("" in searched)
        }

        rule.runOnIdle { state = stateFor(searchText = "") }
        rule.runOnIdle { assertEquals(address, catalogAddressEditableText()) }
    }

    @Test
    fun `pasting an address after active search clears the filter and keeps the draft`() {
        var state by mutableStateOf(stateFor(searchText = "valencia"))
        val searched = mutableListOf<String>()
        var opened: java.net.URI? = null
        setMutableCatalogContent(
            state = { state },
            onSearchTextChange = { searched += it },
            onOpenPublicWeb = { opened = it },
        )

        val address = "https://portal.example/paste?token=one"
        val clipboard = org.robolectric.RuntimeEnvironment.getApplication()
            .getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("catalog", address))
        // The paste action is available only for an empty field. Clearing the
        // active search first is itself the search-to-empty transition under test.
        rule.onNodeWithTag("public-web-address").performTextReplacement("")
        rule.onNodeWithTag("catalog-address-paste").performClick()
        rule.runOnIdle {
            assertEquals(address, catalogAddressEditableText())
            assertTrue("" in searched)
            assertEquals(null, opened)
        }

        rule.runOnIdle { state = stateFor(searchText = "") }
        rule.runOnIdle {
            assertEquals(address, catalogAddressEditableText())
            assertTrue("" in searched)
        }
    }

    @Test
    fun `gradually entering an address after search clears the filter and keeps the draft`() {
        var state by mutableStateOf(stateFor(searchText = "valencia"))
        val searched = mutableListOf<String>()
        var opened: java.net.URI? = null
        setMutableCatalogContent(
            state = { state },
            onSearchTextChange = { searched += it },
            onOpenPublicWeb = { opened = it },
        )

        val address = "https://portal.example/gradual"
        val field = rule.onNodeWithTag("public-web-address")
        field.performTextReplacement("")
        field.performTextInput("https://portal")
        field.performTextInput(".example/gradual")
        rule.runOnIdle {
            assertEquals(address, catalogAddressEditableText())
            assertTrue("" in searched)
        }

        rule.onNodeWithTag("public-web-open-confirm").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(java.net.URI(address), opened) }

        rule.runOnIdle { state = stateFor(searchText = "") }
        rule.runOnIdle {
            assertEquals(address, catalogAddressEditableText())
            assertTrue("" in searched)
        }
    }

    @Test
    fun `externally truncated url search text is visible but not openable`() {
        var state by mutableStateOf(stateFor(searchText = "valencia"))
        var opened: java.net.URI? = null
        setMutableCatalogContent(
            state = { state },
            onSearchTextChange = {},
            onOpenPublicWeb = { opened = it },
        )

        val fullUrl = "https://portal.example/path?token=" + "a".repeat(200)
        rule.runOnIdle { state = stateFor(searchText = fullUrl.take(160)) }

        rule.onNodeWithTag("public-web-address")
            .assertTextContains(fullUrl.take(160))
        rule.onNodeWithTag("public-web-open-confirm").assertDoesNotExist()
        rule.runOnIdle { assertEquals(null, opened) }
    }

    @Test
    fun `fully typed 160 character address remains openable`() {
        var opened: java.net.URI? = null
        setCatalogContent(state = stateFor(), onOpenPublicWeb = { opened = it })

        val prefix = "https://portal.example/"
        val address = prefix + "a".repeat(160 - prefix.length)
        rule.onNodeWithTag("public-web-address").performTextInput(address)
        rule.onNodeWithTag("public-web-open-confirm").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(java.net.URI(address), opened) }
    }

    @Test
    fun `stale echo beyond pending buffer does not replace newer local draft`() {
        var state by mutableStateOf(stateFor())
        setMutableCatalogContent(state = { state }, onSearchTextChange = {})

        val field = rule.onNodeWithTag("public-web-address")
        repeat(40) { field.performTextInput("x") }
        val latest = "x".repeat(40)
        rule.runOnIdle { assertEquals(latest, catalogAddressEditableText()) }

        rule.runOnIdle { state = stateFor(searchText = "x") }
        rule.runOnIdle { assertEquals(latest, catalogAddressEditableText()) }

        rule.runOnIdle { state = stateFor(searchText = latest) }
        rule.runOnIdle { assertEquals(latest, catalogAddressEditableText()) }
    }

    @Test
    fun `external search recovery works after pending buffer overflows`() {
        var state by mutableStateOf(stateFor())
        setMutableCatalogContent(state = { state }, onSearchTextChange = {})

        val field = rule.onNodeWithTag("public-web-address")
        repeat(40) { field.performTextInput("x") }
        val latest = "x".repeat(40)
        rule.runOnIdle { assertEquals(latest, catalogAddressEditableText()) }

        rule.runOnIdle { state = stateFor(searchText = "madrid") }
        rule.runOnIdle { assertEquals("madrid", catalogAddressEditableText()) }

        rule.runOnIdle { state = stateFor(searchText = "") }
        rule.runOnIdle {
            assertEquals("", catalogAddressEditableText())
            rule.onNodeWithTag("public-web-open-confirm").assertDoesNotExist()
        }
    }

    @Test
    fun `clearing an external bare host search text clears the view model filter`() {
        var state by mutableStateOf(stateFor(searchText = "portal.example"))
        val searched = mutableListOf<String>()
        var opened: java.net.URI? = null
        setMutableCatalogContent(
            state = { state },
            onSearchTextChange = { searched += it },
            onOpenPublicWeb = { opened = it },
        )

        rule.onNodeWithTag("public-web-address").assertTextContains("portal.example")
        rule.onNodeWithTag("catalog-address-clear").performClick()
        rule.runOnIdle {
            assertEquals(listOf(""), searched)
            assertEquals(null, opened)
        }

        rule.runOnIdle { state = stateFor(searchText = "") }
        rule.runOnIdle {
            assertEquals("", catalogAddressEditableText())
            rule.onNodeWithTag("public-web-open-confirm").assertDoesNotExist()
        }
    }

    @Test
    fun `clearing an external url search text clears the view model filter`() {
        var state by mutableStateOf(stateFor(searchText = "https://portal.example/path"))
        val searched = mutableListOf<String>()
        var opened: java.net.URI? = null
        setMutableCatalogContent(
            state = { state },
            onSearchTextChange = { searched += it },
            onOpenPublicWeb = { opened = it },
        )

        rule.onNodeWithTag("public-web-address")
            .assertTextContains("https://portal.example/path")
        rule.onNodeWithTag("catalog-address-clear").performClick()
        rule.runOnIdle {
            assertEquals(listOf(""), searched)
            assertEquals(null, opened)
        }

        rule.runOnIdle { state = stateFor(searchText = "") }
        rule.runOnIdle {
            assertEquals("", catalogAddressEditableText())
            rule.onNodeWithTag("public-web-open-confirm").assertDoesNotExist()
        }
    }

    @Test
    fun `clearing a locally entered address does not persist it as search text`() {
        var state by mutableStateOf(stateFor())
        val searched = mutableListOf<String>()
        var opened: java.net.URI? = null
        setMutableCatalogContent(
            state = { state },
            onSearchTextChange = { searched += it },
            onOpenPublicWeb = { opened = it },
        )

        val address = "https://portal.example/private?token=one"
        rule.onNodeWithTag("public-web-address").performTextInput(address)
        rule.onNodeWithTag("catalog-address-clear").performClick()
        rule.runOnIdle {
            assertEquals("", catalogAddressEditableText())
            assertTrue(address !in searched)
            assertEquals(null, opened)
        }

        rule.runOnIdle { state = stateFor(searchText = "") }
        rule.runOnIdle {
            assertEquals("", catalogAddressEditableText())
            rule.onNodeWithTag("catalog-address-paste").assertIsDisplayed()
        }
    }

    @Test
    fun `clearing an active search clears the view model filter without opening`() {
        var state by mutableStateOf(stateFor(searchText = "valencia"))
        val searched = mutableListOf<String>()
        var opened: java.net.URI? = null
        setMutableCatalogContent(
            state = { state },
            onSearchTextChange = { searched += it },
            onOpenPublicWeb = { opened = it },
        )

        rule.onNodeWithTag("public-web-address").assertTextContains("valencia")
        rule.onNodeWithTag("catalog-address-clear").performClick()
        rule.runOnIdle {
            assertEquals(listOf(""), searched)
            assertEquals(null, opened)
        }

        rule.runOnIdle { state = stateFor(searchText = "") }
        rule.runOnIdle {
            assertEquals("", catalogAddressEditableText())
            rule.onNodeWithTag("public-web-open-confirm").assertDoesNotExist()
        }
    }

    private fun setMutableCatalogContent(
        state: () -> PortalCatalogUiState,
        onSearchTextChange: (String) -> Unit,
        onOpenPublicWeb: ((java.net.URI) -> Unit)? = null,
    ) {
        rule.setContent {
            JuntaFirmaTheme {
                PortalCatalogScreen(
                    state = state(),
                    onSearchTextChange = onSearchTextChange,
                    onSelectRegion = {},
                    onUseLocation = {},
                    onOpenLocationSettings = {},
                    onOpenAppSettings = {},
                    onDismissLocationMessage = {},
                    onToggleFavorite = {},
                    onOpenPortal = {},
                    onBackToCertificate = {},
                    onUserMessageShown = {},
                    onOpenPublicWeb = onOpenPublicWeb,
                )
            }
        }
    }

    private fun catalogAddressEditableText(): String =
        rule.onNodeWithTag("public-web-address")
            .fetchSemanticsNode()
            .config[SemanticsProperties.EditableText]
            .text

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
        onSearchTextChange: (String) -> Unit = {},
        onSelectRegion: (PortalRegionCode) -> Unit = {},
        onUseLocation: () -> Unit = {},
        onOpenLocationSettings: () -> Unit = {},
        onOpenPortal: (PortalCatalogItem) -> Unit = {},
        onBackToCertificate: () -> Unit = {},
        onToggleFavorite: (PortalId) -> Unit = {},
        onOpenPublicWeb: ((java.net.URI) -> Unit)? = null,
    ) {
        rule.setContent {
            JuntaFirmaTheme {
                PortalCatalogScreen(
                    state = state,
                    onSearchTextChange = onSearchTextChange,
                    onSelectRegion = onSelectRegion,
                    onUseLocation = onUseLocation,
                    onOpenLocationSettings = onOpenLocationSettings,
                    onOpenAppSettings = {},
                    onDismissLocationMessage = {},
                    onToggleFavorite = onToggleFavorite,
                    onOpenPortal = onOpenPortal,
                    onBackToCertificate = onBackToCertificate,
                    onUserMessageShown = {},
                    onOpenPublicWeb = onOpenPublicWeb,
                )
            }
        }
    }
}

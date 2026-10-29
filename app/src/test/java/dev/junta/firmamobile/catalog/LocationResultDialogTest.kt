package dev.junta.firmamobile.catalog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

/** Exercises the private leaf through its existing stateless screen entry point. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "es-rES")
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class LocationResultDialogTest {
    @get:Rule
    val rule = createComposeRule()

    private var locationState by mutableStateOf(CatalogLocationState.IDLE)
    private val events = mutableListOf<String>()
    private val failures = listOf(
        Failure(CatalogLocationState.PERMISSION_DENIED,
            R.string.catalog_location_permission_title, R.string.catalog_location_permission_copy,
            R.string.catalog_open_permissions, R.string.catalog_choose_manually),
        Failure(CatalogLocationState.LOCATION_DISABLED,
            R.string.catalog_location_disabled_title, R.string.catalog_location_disabled_copy,
            R.string.catalog_open_location_settings, R.string.catalog_choose_manually),
        Failure(CatalogLocationState.UNAVAILABLE,
            R.string.catalog_location_unavailable_title, R.string.catalog_location_unavailable_copy),
        Failure(CatalogLocationState.GEOCODING_FAILED,
            R.string.catalog_geocoding_failed_title, R.string.catalog_geocoding_failed_copy),
        Failure(CatalogLocationState.LOCATION_TIMEOUT,
            R.string.catalog_location_timeout_title, R.string.catalog_location_timeout_copy),
        Failure(CatalogLocationState.OUTSIDE_SPAIN,
            R.string.catalog_location_outside_title, R.string.catalog_location_outside_copy),
    )

    @Test
    fun idleAndLoadingRenderNoResultDialogAndInvokeNothing() {
        setCatalogContent()
        for (state in listOf(CatalogLocationState.IDLE, CatalogLocationState.LOADING)) {
            rule.runOnIdle { locationState = state }
            rule.onAllNodes(isDialog()).assertCountEquals(0)
            rule.runOnIdle { assertEquals(emptyList<String>(), events) }
        }
    }

    @Test
    fun allFailuresRenderTheirResourceTextAndEnabledAccessibleButtons() {
        setCatalogContent()
        for (failure in failures) {
            rule.runOnIdle { locationState = failure.state }
            rule.onAllNodes(isDialog()).assertCountEquals(1)
            rule.onNodeWithText(text(failure.title)).assertIsDisplayed()
            rule.onNodeWithText(text(failure.copy)).assertIsDisplayed()
            for (label in listOf(failure.confirm, failure.dismiss)) {
                rule.onNodeWithText(text(label)).assertIsDisplayed().assertIsEnabled()
                    .assertHasClickAction()
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            }
            rule.runOnIdle { assertEquals(emptyList<String>(), events) }
        }
    }

    @Test
    fun settingsActionsDismissBeforeForwardingAndNeverRequestLocation() {
        setCatalogContent()
        for ((state, label, event) in listOf(
            Triple(CatalogLocationState.LOCATION_DISABLED, R.string.catalog_open_location_settings, "location-settings"),
            Triple(CatalogLocationState.PERMISSION_DENIED, R.string.catalog_open_permissions, "app-settings"),
        )) {
            rule.runOnIdle { events.clear(); locationState = state }
            rule.onNodeWithText(text(label)).performClick()
            rule.runOnIdle { assertEquals(listOf("dismiss", event), events) }
            rule.onAllNodes(isDialog()).assertCountEquals(0)
        }
    }

    @Test
    fun manualActionFromEitherSlotDismissesThenOpensTheExistingRegionPicker() {
        setCatalogContent()
        for (failure in failures) {
            rule.runOnIdle { events.clear(); locationState = failure.state }
            rule.onNodeWithText(text(R.string.catalog_choose_manually)).performClick()
            rule.runOnIdle { assertEquals(listOf("dismiss"), events) }
            rule.onNodeWithText(text(failure.title)).assertDoesNotExist()
            rule.onNodeWithText(text(R.string.catalog_choose_region)).assertIsDisplayed()
            rule.onNode(
                hasText(text(R.string.region_spain)) and hasClickAction() and hasAnyAncestor(isDialog()),
            ).performClick()
            rule.runOnIdle { assertEquals(listOf("dismiss", "select-region"), events) }
            rule.onNodeWithText(text(R.string.catalog_choose_region)).assertDoesNotExist()
        }
    }

    @Test
    fun ordinaryFailureCloseOnlyDismissesWithoutOpeningSettingsOrPicker() {
        setCatalogContent()
        for (failure in failures.filter { it.dismiss == R.string.close }) {
            rule.runOnIdle { events.clear(); locationState = failure.state }
            rule.onNodeWithText(text(R.string.close)).performClick()
            rule.runOnIdle { assertEquals(listOf("dismiss"), events) }
            rule.onAllNodes(isDialog()).assertCountEquals(0)
            rule.onNodeWithText(text(R.string.catalog_choose_region)).assertDoesNotExist()
        }
    }

    private fun setCatalogContent() {
        rule.setContent {
            JuntaFirmaTheme {
                PortalCatalogScreen(
                    state = PortalCatalogUiState(locationState = locationState),
                    onSearchTextChange = {},
                    onSelectRegion = { events += "select-region" },
                    onUseLocation = { events += "request-location" },
                    onOpenLocationSettings = { events += "location-settings" },
                    onOpenAppSettings = { events += "app-settings" },
                    onDismissLocationMessage = {
                        events += "dismiss"
                        locationState = CatalogLocationState.IDLE
                    },
                    onToggleFavorite = {},
                    onOpenPortal = {},
                    onBackToCertificate = {},
                    onUserMessageShown = {},
                )
            }
        }
    }

    private fun text(resource: Int): String = RuntimeEnvironment.getApplication().getString(resource)

    private data class Failure(
        val state: CatalogLocationState,
        val title: Int,
        val copy: Int,
        val confirm: Int = R.string.catalog_choose_manually,
        val dismiss: Int = R.string.close,
    )
}

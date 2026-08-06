package dev.junta.firmamobile.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class BrowserChromeComponentsTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun noticeBannerIsAnAssertiveLiveRegion() {
        rule.setContent {
            JuntaFirmaTheme {
                BrowserNoticeBanner(
                    message = "No se pudo cargar el portal.",
                    onRetry = {},
                )
            }
        }

        rule.onNodeWithTag(BROWSER_NOTICE_TAG)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Assertive,
                ),
            )
    }

    @Test
    fun noticeBannerSupportsPoliteLiveRegionForStatusUpdates() {
        rule.setContent {
            JuntaFirmaTheme {
                BrowserNoticeBanner(
                    message = "Se borraron los datos del sitio actual.",
                    onRetry = null,
                    liveRegionMode = LiveRegionMode.Polite,
                )
            }
        }

        rule.onNodeWithTag(BROWSER_NOTICE_TAG)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ),
            )
    }

    @Test
    fun clickableServiceIdentityHasButtonRole() {
        val events = mutableListOf<String>()
        rule.setContent {
            JuntaFirmaTheme {
                IndustrialBrowserTopBar(
                    profileName = "Registro Electrónico General",
                    host = "reg.redsara.es",
                    trustLabel = "Firma protegida",
                    onBack = {},
                    onHome = {},
                    onReload = {},
                    onChangeCertificate = {},
                    onLockCertificate = {},
                    onClearCurrentSiteRequested = {},
                    onClearSessionRequested = {},
                    onDeleteAllBrowserDataRequested = {},
                    onIdentityClick = { events += "identity" },
                )
            }
        }

        rule.onNodeWithTag(BROWSER_ADDRESS_LABEL_TAG)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.Role,
                    Role.Button,
                ),
            )
            .performClick()
        rule.runOnIdle { assertEquals(listOf("identity"), events) }
    }

    @Test
    fun passiveServiceIdentityHasNoClickActionOrButtonRole() {
        rule.setContent {
            JuntaFirmaTheme {
                IndustrialBrowserTopBar(
                    profileName = "Registro Electrónico General",
                    host = "reg.redsara.es",
                    trustLabel = "Firma protegida",
                    onBack = {},
                    onHome = {},
                    onReload = {},
                    onChangeCertificate = {},
                    onLockCertificate = {},
                    onClearCurrentSiteRequested = {},
                    onClearSessionRequested = {},
                    onDeleteAllBrowserDataRequested = {},
                    onIdentityClick = null,
                )
            }
        }

        val config = rule.onNodeWithTag(BROWSER_ADDRESS_LABEL_TAG).fetchSemanticsNode().config
        assertFalse(config.contains(SemanticsActions.OnClick))
        assertFalse(config.contains(SemanticsProperties.Role))
    }

    @Test
    fun chromeShowsOnlyRealActionsAndStyledStatusElements() {
        val events = mutableListOf<String>()
        rule.setContent {
            JuntaFirmaTheme {
                Column {
                    IndustrialBrowserTopBar(
                        profileName = "Registro Electrónico General",
                        host = "reg.redsara.es",
                        trustLabel = "Firma protegida",
                        onBack = { events += "back" },
                        onHome = { events += "home" },
                        onReload = { events += "reload" },
                        onChangeCertificate = { events += "change" },
                        onLockCertificate = { events += "lock" },
                        onClearCurrentSiteRequested = { events += "clear-site" },
                        onClearSessionRequested = { events += "close-session" },
                        onDeleteAllBrowserDataRequested = { events += "clear-all" },
                        onIdentityClick = { events += "identity" },
                    )
                    BrowserLoadingIndicator(visible = true)
                    BrowserNoticeBanner("No se pudo cargar el portal.", onRetry = { events += "retry" })
                    BrowserCertificateStrip("Persona de Prueba")
                }
            }
        }

        rule.onNodeWithText("Registro Electrónico General").assertIsDisplayed()
        rule.onNodeWithText("reg.redsara.es", substring = true).assertIsDisplayed()
        rule.onNodeWithTag(BROWSER_LOADING_TAG).assertIsDisplayed()
        rule.onNodeWithTag(BROWSER_NOTICE_TAG).assertIsDisplayed()
        rule.onNodeWithText("Certificado activo").assertIsDisplayed()
        rule.onNodeWithContentDescription("Reintentar").performClick()
        rule.onNodeWithContentDescription("Más opciones").performClick()
        rule.onNodeWithText("Cambiar certificado").assertIsDisplayed()
        rule.onNodeWithText("Bloquear certificado").assertIsDisplayed()
        rule.onNodeWithText("Borrar datos de este sitio").assertIsDisplayed()
        rule.onNodeWithText("Cerrar sesión").assertIsDisplayed()
        rule.onNodeWithText("Borrar todos los datos web").assertIsDisplayed()
        rule.onNodeWithText("Historial").assertDoesNotExist()
        rule.onNodeWithText("Ajustes").assertDoesNotExist()
        rule.onNodeWithText("Ayuda").assertDoesNotExist()
        rule.runOnIdle { assertEquals(listOf("retry"), events) }
    }
}

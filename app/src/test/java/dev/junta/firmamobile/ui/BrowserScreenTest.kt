package dev.junta.firmamobile.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
import org.junit.Rule
import org.junit.Assert.assertTrue
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
class BrowserScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun browserChromeExposesNavigationCertificateAndConfirmedSessionActions() {
        val events = mutableListOf<String>()
        rule.setContent {
            JuntaFirmaTheme {
                BrowserLayout(
                    certificateOwner = "Persona de Prueba",
                    onBack = { events += "back" },
                    onHome = { events += "home" },
                    onReload = { events += "reload" },
                    onChangeCertificate = { events += "change" },
                    onLockCertificate = { events += "lock" },
                    onClearSession = { events += "clear" },
                ) {
                    Text("contenido-web")
                }
            }
        }

        rule.onNodeWithText("Junta de Andalucía").assertIsDisplayed()
        rule.onNodeWithText("www.juntadeandalucia.es", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Certificado activo").assertIsDisplayed()
        rule.onNodeWithText("Persona de Prueba").assertIsDisplayed()
        rule.onNodeWithText("contenido-web").assertIsDisplayed()

        rule.onNodeWithContentDescription("Atrás").performClick()
        rule.onNodeWithContentDescription("Inicio de la aplicación").performClick()
        rule.onNodeWithContentDescription("Recargar").performClick()
        rule.runOnIdle { check(events == listOf("back", "home", "reload")) }

        rule.onNodeWithContentDescription("Más opciones").performClick()
        rule.onNodeWithText("Cambiar certificado").performClick()
        rule.onNodeWithContentDescription("Más opciones").performClick()
        rule.onNodeWithText("Bloquear certificado").performClick()
        rule.runOnIdle { check(events.takeLast(2) == listOf("change", "lock")) }

        rule.onNodeWithContentDescription("Más opciones").performClick()
        rule.onNodeWithText("Cerrar sesión").performClick()
        rule.onNodeWithText("Cerrar sesión y borrar datos").assertIsDisplayed()
        rule.runOnIdle { check("clear" !in events) }
        rule.onNodeWithText("Confirmar cierre").performClick()
        rule.runOnIdle { check(events.last() == "clear") }
    }

    @Test
    fun longAddressUsesOneLineHostAndFullUrlOnlyWhileEditing() {
        rule.setContent {
            JuntaFirmaTheme {
                BrowserLayout(
                    currentUrl = LONG_URL,
                    certificateOwner = "Persona de Prueba",
                    browserInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                    onAddressSubmitted = {},
                    onBack = {},
                    onHome = {},
                    onReload = {},
                    onChangeCertificate = {},
                    onLockCertificate = {},
                    onClearSession = {},
                ) { modifier ->
                    Text(
                        text = "contenido-web",
                        modifier = modifier.testTag(BROWSER_CONTENT_TAG),
                    )
                }
            }
        }

        rule.onNodeWithText("www.juntadeandalucia.es", substring = true).assertIsDisplayed()
        rule.onNodeWithText(LONG_URL).assertDoesNotExist()
        rule.onNodeWithTag(BROWSER_TOOLBAR_TAG).assertHeightIsEqualTo(72.dp)

        rule.onNodeWithTag(BROWSER_ADDRESS_LABEL_TAG).performClick()

        rule.onNodeWithText(LONG_URL).assertIsDisplayed()
        rule.onNodeWithTag(BROWSER_TOOLBAR_TAG).assertHeightIsEqualTo(72.dp)
    }

    @Test
    fun browserBottomChromeReservesInjectedNavigationInset() {
        rule.setContent {
            JuntaFirmaTheme {
                BrowserLayout(
                    currentUrl = LONG_URL,
                    certificateOwner = "Persona de Prueba",
                    browserInsets = WindowInsets(bottom = 96.dp),
                    onAddressSubmitted = {},
                    onBack = {},
                    onHome = {},
                    onReload = {},
                    onChangeCertificate = {},
                    onLockCertificate = {},
                    onClearSession = {},
                ) { modifier ->
                    Text(
                        text = "contenido-web",
                        modifier = modifier.testTag(BROWSER_CONTENT_TAG),
                    )
                }
            }
        }

        rule.onNodeWithTag(BROWSER_BOTTOM_BAR_TAG).assertHeightIsAtLeast(96.dp)
        rule.onNodeWithTag(BROWSER_CONTENT_TAG).assertIsDisplayed()
        val contentBounds = rule.onNodeWithTag(BROWSER_CONTENT_TAG)
            .fetchSemanticsNode().boundsInRoot
        val bottomBarBounds = rule.onNodeWithTag(BROWSER_BOTTOM_BAR_TAG)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(contentBounds.bottom <= bottomBarBounds.top)
    }

    @Test
    fun safeTopInsetIsAddedOnceAndEditingDoesNotChangeChromeHeight() {
        rule.setContent {
            JuntaFirmaTheme {
                BrowserLayout(
                    currentUrl = LONG_URL,
                    certificateOwner = "Persona de Prueba",
                    browserInsets = WindowInsets(top = 24.dp),
                    onAddressSubmitted = {},
                    onBack = {},
                    onHome = {},
                    onReload = {},
                    onChangeCertificate = {},
                    onLockCertificate = {},
                    onClearSession = {},
                ) { modifier ->
                    Text("contenido-web", modifier.testTag(BROWSER_CONTENT_TAG))
                }
            }
        }

        rule.onNodeWithTag(BROWSER_TOOLBAR_TAG).assertHeightIsEqualTo(96.dp)
        rule.onNodeWithTag(BROWSER_ADDRESS_LABEL_TAG).performClick()
        rule.onNodeWithTag(BROWSER_TOOLBAR_TAG).assertHeightIsEqualTo(96.dp)
    }

    @Test
    fun toolbarActionLeavesEditModeAndReturnsToHostOnlyDisplay() {
        rule.setContent {
            JuntaFirmaTheme {
                BrowserLayout(
                    currentUrl = LONG_URL,
                    certificateOwner = "Persona de Prueba",
                    browserInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                    onAddressSubmitted = {},
                    onBack = {},
                    onHome = {},
                    onReload = {},
                    onChangeCertificate = {},
                    onLockCertificate = {},
                    onClearSession = {},
                ) { modifier ->
                    Text("contenido-web", modifier.testTag(BROWSER_CONTENT_TAG))
                }
            }
        }

        rule.onNodeWithTag(BROWSER_ADDRESS_LABEL_TAG).performClick()
        rule.onNodeWithTag(BROWSER_ADDRESS_FIELD_TAG).assertIsDisplayed()
        rule.onNodeWithContentDescription("Inicio de la aplicación").performClick()

        rule.onNodeWithTag(BROWSER_ADDRESS_FIELD_TAG).assertDoesNotExist()
        rule.onNodeWithText("www.juntadeandalucia.es", substring = true).assertIsDisplayed()
    }

    @Test
    fun pageLifecycleUpdateDoesNotReplaceTheUsersEditBuffer() {
        val currentUrl = mutableStateOf(LONG_URL)
        rule.setContent {
            JuntaFirmaTheme {
                BrowserLayout(
                    currentUrl = currentUrl.value,
                    certificateOwner = "Persona de Prueba",
                    browserInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                    onAddressSubmitted = {},
                    onBack = {},
                    onHome = {},
                    onReload = {},
                    onChangeCertificate = {},
                    onLockCertificate = {},
                    onClearSession = {},
                ) { modifier ->
                    Text("contenido-web", modifier.testTag(BROWSER_CONTENT_TAG))
                }
            }
        }

        rule.onNodeWithTag(BROWSER_ADDRESS_LABEL_TAG).performClick()
        rule.onNodeWithTag(BROWSER_ADDRESS_FIELD_TAG)
            .performTextReplacement(EDITED_URL)
        rule.runOnIdle { currentUrl.value = REDIRECT_URL }

        rule.onNodeWithText(EDITED_URL).assertIsDisplayed()
        rule.onNodeWithText(REDIRECT_URL).assertDoesNotExist()
    }

    private companion object {
        const val LONG_URL =
            "https://www.juntadeandalucia.es/empleoformacionytrabajoautonomo/" +
                "ovorion/auth/signInAutcertjs?redacted=not-logged#fragment"
        const val EDITED_URL = "https://sede.juntadeandalucia.es/user-entry"
        const val REDIRECT_URL = "https://ssoweb.juntadeandalucia.es/redirected"
    }
}

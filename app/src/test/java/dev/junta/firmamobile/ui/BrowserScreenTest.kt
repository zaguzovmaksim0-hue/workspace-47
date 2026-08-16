package dev.junta.firmamobile.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.browser.BrowserErrorCode
import dev.junta.firmamobile.browser.ClientCertPreferenceBarrierState
import dev.junta.firmamobile.browser.NavigationBlockReason
import dev.junta.firmamobile.browser.SiteClearResult
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import org.junit.Rule
import org.junit.Assert.assertEquals
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
    fun webMessageBridgeIsRequiredOnlyForProfilesWithNativeSigningCapabilities() {
        fun profile(id: String) = BuiltInSiteProfiles.catalog.profiles.single {
            it.profileId == ProfileId(id)
        }

        assertTrue(profileRequiresWebMessageBridge(profile("junta-andalucia")))
        assertTrue(profileRequiresWebMessageBridge(profile("reg-age-redsara")))
        assertTrue(profileRequiresWebMessageBridge(profile("isciii-certificate-selection")))
        assertTrue(profileRequiresWebMessageBridge(profile("diputacion-valencia-sede")))
        assertTrue(profileRequiresWebMessageBridge(profile("unizar-tramitador")))
        assertTrue(profileRequiresWebMessageBridge(profile("diputacion-huesca-portal")))
        assertTrue(profileRequiresWebMessageBridge(profile("diputacion-burgos-portal")))
        assertTrue(!profileRequiresWebMessageBridge(profile("carne-joven-andalucia")))
    }

    @Test
    fun browserNoticeLiveRegionSeverityMatchesDisplayedNoticePrecedence() {
        fun mode(
            compatibilityError: Boolean = false,
            blockedReason: NavigationBlockReason? = null,
            browserError: BrowserErrorCode? = null,
            clientCertPreferenceState: ClientCertPreferenceBarrierState =
                ClientCertPreferenceBarrierState.IDLE,
            siteClearResult: SiteClearResult? = null,
            globalClearResult: Boolean? = null,
        ) = browserNoticeLiveRegionMode(
            compatibilityError = compatibilityError,
            blockedReason = blockedReason,
            browserError = browserError,
            clientCertPreferenceState = clientCertPreferenceState,
            siteClearResult = siteClearResult,
            globalClearResult = globalClearResult,
        )

        assertEquals(
            LiveRegionMode.Polite,
            mode(clientCertPreferenceState = ClientCertPreferenceBarrierState.CLEARING),
        )
        assertEquals(LiveRegionMode.Polite, mode(globalClearResult = true))
        assertEquals(
            LiveRegionMode.Polite,
            mode(siteClearResult = SiteClearResult.CLEARED_EXACTLY),
        )

        assertEquals(
            LiveRegionMode.Assertive,
            mode(clientCertPreferenceState = ClientCertPreferenceBarrierState.FAILED),
        )
        assertEquals(
            LiveRegionMode.Assertive,
            mode(
                compatibilityError = true,
                siteClearResult = SiteClearResult.CLEARED_EXACTLY,
            ),
        )
        assertEquals(
            LiveRegionMode.Assertive,
            mode(browserError = BrowserErrorCode.NETWORK_ERROR, globalClearResult = true),
        )
        assertEquals(
            LiveRegionMode.Assertive,
            mode(
                blockedReason = NavigationBlockReason.CROSS_PROFILE_NAVIGATION,
                globalClearResult = true,
            ),
        )
        assertEquals(LiveRegionMode.Assertive, mode(globalClearResult = false))
        assertEquals(
            LiveRegionMode.Assertive,
            mode(
                siteClearResult =
                    SiteClearResult.WEB_STORAGE_CLEARED_COOKIE_CLEAR_UNAVAILABLE,
            ),
        )
        assertEquals(
            LiveRegionMode.Assertive,
            mode(siteClearResult = SiteClearResult.FAILED),
        )
    }

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
                    onClearCurrentSite = { events += "clear-site" },
                    onClearSession = { events += "close-session" },
                    onDeleteAllBrowserData = { events += "clear-all" },
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
        rule.onNodeWithText("Borrar datos de este sitio").performClick()
        rule.onNodeWithText("Borrar datos de este sitio").assertIsDisplayed()
        rule.runOnIdle { check("clear-site" !in events) }
        rule.onNodeWithText("Borrar este sitio").performClick()
        rule.runOnIdle { check(events.last() == "clear-site") }

        rule.onNodeWithContentDescription("Más opciones").performClick()
        rule.onNodeWithText("Cerrar sesión").performClick()
        rule.onNodeWithText("Cerrar sesión del certificado").assertIsDisplayed()
        rule.runOnIdle { check("close-session" !in events) }
        rule.onNodeWithText("Cerrar sesión", useUnmergedTree = true).performClick()
        rule.runOnIdle { check(events.last() == "close-session") }

        rule.onNodeWithContentDescription("Más opciones").performClick()
        rule.onNodeWithText("Borrar todos los datos web").performClick()
        rule.onNodeWithText("Borrar todos los datos web").assertIsDisplayed()
        rule.onNodeWithText("todos los portales", substring = true).assertIsDisplayed()
        rule.runOnIdle { check("clear-all" !in events) }
        rule.onNodeWithText("Borrar todo").performClick()
        rule.runOnIdle { check(events.last() == "clear-all") }
    }

    @Test
    fun longAddressUsesOneLineReadOnlyHost() {
        rule.setContent {
            JuntaFirmaTheme {
                BrowserLayout(
                    currentUrl = LONG_URL,
                    certificateOwner = "Persona de Prueba",
                    browserInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                    onBack = {},
                    onHome = {},
                    onReload = {},
                    onChangeCertificate = {},
                    onLockCertificate = {},
                    onClearCurrentSite = {},
                    onClearSession = {},
                    onDeleteAllBrowserData = {},
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

        rule.onNodeWithText(LONG_URL).assertDoesNotExist()
        rule.onNodeWithTag("browser_address_field").assertDoesNotExist()
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
                    onBack = {},
                    onHome = {},
                    onReload = {},
                    onChangeCertificate = {},
                    onLockCertificate = {},
                    onClearCurrentSite = {},
                    onClearSession = {},
                    onDeleteAllBrowserData = {},
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
                    onBack = {},
                    onHome = {},
                    onReload = {},
                    onChangeCertificate = {},
                    onLockCertificate = {},
                    onClearCurrentSite = {},
                    onClearSession = {},
                    onDeleteAllBrowserData = {},
                ) { modifier ->
                    Text("contenido-web", modifier.testTag(BROWSER_CONTENT_TAG))
                }
            }
        }

        rule.onNodeWithTag(BROWSER_TOOLBAR_TAG).assertHeightIsEqualTo(96.dp)
        rule.onNodeWithTag(BROWSER_TOOLBAR_TAG).assertHeightIsEqualTo(96.dp)
    }

    @Test
    fun toolbarIdentityCannotOpenManualUrlEditor() {
        rule.setContent {
            JuntaFirmaTheme {
                BrowserLayout(
                    currentUrl = LONG_URL,
                    certificateOwner = "Persona de Prueba",
                    browserInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                    onBack = {},
                    onHome = {},
                    onReload = {},
                    onChangeCertificate = {},
                    onLockCertificate = {},
                    onClearCurrentSite = {},
                    onClearSession = {},
                    onDeleteAllBrowserData = {},
                ) { modifier ->
                    Text("contenido-web", modifier.testTag(BROWSER_CONTENT_TAG))
                }
            }
        }

        rule.onNodeWithTag("browser_address_field").assertDoesNotExist()
        rule.onNodeWithContentDescription("Inicio de la aplicación").performClick()

        rule.onNodeWithTag("browser_address_field").assertDoesNotExist()
        rule.onNodeWithText("www.juntadeandalucia.es", substring = true).assertIsDisplayed()
    }

    @Test
    fun pageLifecycleUpdatesTheReadOnlyHost() {
        val currentUrl = mutableStateOf(LONG_URL)
        rule.setContent {
            JuntaFirmaTheme {
                BrowserLayout(
                    currentUrl = currentUrl.value,
                    certificateOwner = "Persona de Prueba",
                    browserInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                    onBack = {},
                    onHome = {},
                    onReload = {},
                    onChangeCertificate = {},
                    onLockCertificate = {},
                    onClearCurrentSite = {},
                    onClearSession = {},
                    onDeleteAllBrowserData = {},
                ) { modifier ->
                    Text("contenido-web", modifier.testTag(BROWSER_CONTENT_TAG))
                }
            }
        }

        rule.runOnIdle { currentUrl.value = REDIRECT_URL }

        rule.onNodeWithText("ssoweb.juntadeandalucia.es", substring = true).assertIsDisplayed()
        rule.onNodeWithText(REDIRECT_URL).assertDoesNotExist()
    }

    private companion object {
        const val LONG_URL =
            "https://www.juntadeandalucia.es/empleoformacionytrabajoautonomo/" +
                "ovorion/auth/signInAutcertjs?redacted=not-logged#fragment"
        const val REDIRECT_URL = "https://ssoweb.juntadeandalucia.es/redirected"
    }
}

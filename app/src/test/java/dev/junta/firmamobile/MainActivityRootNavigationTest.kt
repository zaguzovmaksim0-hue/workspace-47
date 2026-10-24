package dev.junta.firmamobile

import android.Manifest
import android.content.pm.PackageManager
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentDialog
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import org.robolectric.shadows.ShadowDialog

/** Real Activity/root callbacks; only Android's WebView is Robolectric's shadow.
 * No copied navigation switch, private-field access, portal traffic or real identity.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-rES-w400dp-h1200dp")
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class MainActivityRootNavigationTest {
    @get:Rule val rule = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before fun launch() {
        val app = ApplicationProvider.getApplicationContext<JuntaFirmaApplication>()
        // Robolectric does not grant this manifest-declared normal permission automatically.
        // Keep MainActivity's real overlay protection enabled, including after recreation.
        shadowOf(app).grantPermissions(Manifest.permission.HIDE_OVERLAY_WINDOWS)
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            app.checkSelfPermission(Manifest.permission.HIDE_OVERLAY_WINDOWS),
        )
        scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitHome()
    }

    @After fun close() {
        if (::scenario.isInitialized) scenario.close()
    }

    @Test fun freshStartupShowsCertificateHomeNotCatalogOrBrowser() {
        rule.onNodeWithTag("jfm-home-background").assertIsDisplayed()
        rule.onNodeWithTag("catalog-list").assertDoesNotExist()
        scenario.onActivity { assertTrue(webViews(it.window.decorView).isEmpty()) }
    }

    @Test fun certificateHomeSystemBackFinishesInsteadOfOpeningCatalog() {
        scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
            assertTrue(it.isFinishing)
        }
    }

    @Test fun certificateLessContinueOpensCatalogAndExplicitBackReturnsHome() {
        openCatalog()
        rule.onNodeWithContentDescription("← Volver al certificado").performClick()
        awaitHome()
        rule.onNodeWithTag("catalog-list").assertDoesNotExist()
    }

    @Test fun catalogSystemBackReturnsHomeRatherThanFinishingActivity() {
        openCatalog()
        systemBack()
        awaitHome()
        scenario.onActivity { assertFalse(it.isFinishing) }
    }

    @Test fun typedPublicAddressOpensWithoutCertificateAndHomeExitsToCatalog() {
        openPublicBrowser()
        rule.onNodeWithText("Sin certificado desbloqueado").assertIsDisplayed()
        assertPublicMode(true)
        assertLoadedUrl(PUBLIC_URL)
        rule.onNodeWithContentDescription("Inicio de la aplicación").performClick()
        rule.onNodeWithTag("catalog-list").assertIsDisplayed()
        rule.onNodeWithTag("jfm-home-background").assertDoesNotExist()
        scenario.onActivity { assertTrue(webViews(it.window.decorView).isEmpty()) }
    }

    @Test fun invalidPublicAddressStaysInCatalogUntilCorrected() {
        openCatalog()
        rule.onNodeWithTag("public-web-address").performTextReplacement("http://public.example/blocked")
        rule.onNodeWithTag("public-web-open-confirm").performClick()
        rule.onNodeWithTag("catalog-address-error", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("catalog-list").assertIsDisplayed()
        scenario.onActivity { assertTrue(webViews(it.window.decorView).isEmpty()) }
        submitPublicAddress()
        assertLoadedUrl(PUBLIC_URL)
    }

    @Test fun primaryPortalOpenRemainsPublicEvenForAProfileBoundPortal() {
        findOvorion()
        rule.onNodeWithTag("catalog-open-junta-andalucia-ovorion").performClick()
        awaitBrowser()
        assertLoadedUrl(OVORION_URL)
        assertPublicMode(true)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun explicitCompatibilityOpenUsesProfileButStillAllowsNoCertificate() {
        findOvorion()
        rule.onNodeWithTag("catalog-card-junta-andalucia-ovorion")
            .performCustomAccessibilityActionWithLabel("Opciones del sitio")
        rule.onNodeWithTag("catalog-open-profile-junta-andalucia-ovorion").performClick()
        awaitBrowser()
        assertLoadedUrl(OVORION_URL)
        assertPublicMode(false)
        rule.onNodeWithText("Sin certificado desbloqueado").assertIsDisplayed()
    }

    @Test fun browserToolbarBackWithoutHistoryExitsToCatalog() {
        openPublicBrowser()
        scenario.onActivity { assertFalse(browser(it).canGoBack()) }
        rule.onNodeWithContentDescription("Atrás").performClick()
        rule.onNodeWithTag("catalog-list").assertIsDisplayed()
    }

    @Test fun browserSystemBackWithoutHistoryExitsToCatalogThenHome() {
        openPublicBrowser()
        systemBack()
        rule.onNodeWithTag("catalog-list").assertIsDisplayed()
        systemBack()
        awaitHome()
    }

    @Test fun browserSystemAndToolbarBackWithHistoryStayInTheSameBrowser() {
        openPublicBrowser()
        lateinit var original: WebView
        scenario.onActivity {
            original = browser(it)
            shadowOf(original).setCanGoBack(true)
        }
        systemBack()
        scenario.onActivity {
            assertSame(original, browser(it))
            assertEquals(1, shadowOf(original).goBackInvocations)
            assertFalse(it.isFinishing)
        }
        rule.onNodeWithContentDescription("Atrás").performClick()
        scenario.onActivity {
            assertSame(original, browser(it))
            assertEquals(2, shadowOf(original).goBackInvocations)
            shadowOf(original).setCanGoBack(false)
        }
        rule.onNodeWithTag("catalog-list").assertDoesNotExist()
        systemBack()
        rule.onNodeWithTag("catalog-list").assertIsDisplayed()
    }

    @Test fun certificatePanelContinueClosesOnlyPanelAndKeepsBrowserInstance() {
        openPublicBrowser()
        val original = currentBrowser()
        openCertificatePanel()
        rule.onNodeWithTag("browse-without-certificate").performScrollTo().performClick()
        rule.onNodeWithTag("jfm-home-background").assertDoesNotExist()
        scenario.onActivity { assertSame(original, browser(it)) }
        assertLoadedUrl(PUBLIC_URL)
        rule.onNodeWithTag("catalog-list").assertDoesNotExist()
    }

    @Test fun certificatePanelSystemBackDismissesOnlyPanel() {
        openPublicBrowser()
        val original = currentBrowser()
        openCertificatePanel()
        rule.runOnIdle {
            // Dispatch to the actual dialog window, not the underlying Activity.
            (ShadowDialog.getLatestDialog() as ComponentDialog)
                .onBackPressedDispatcher.onBackPressed()
        }
        rule.onNodeWithTag("jfm-home-background").assertDoesNotExist()
        scenario.onActivity { assertSame(original, browser(it)) }
        rule.onNodeWithTag("catalog-list").assertDoesNotExist()
        systemBack()
        rule.onNodeWithTag("catalog-list").assertIsDisplayed()
    }

    @Test fun forcedActivityRecreationDoesNotRestoreBrowserDestination() {
        openPublicBrowser()
        scenario.recreate()
        awaitHome()
        rule.onNodeWithTag("catalog-list").assertDoesNotExist()
        scenario.onActivity { assertTrue(webViews(it.window.decorView).isEmpty()) }
    }

    private fun awaitHome() {
        rule.waitUntil(20_000) {
            rule.onAllNodesWithTag("browse-without-certificate").fetchSemanticsNodes().size == 1
        }
    }

    private fun openCatalog() {
        rule.onNodeWithTag("browse-without-certificate").performScrollTo().performClick()
        rule.onNodeWithTag("catalog-list").assertIsDisplayed()
    }

    private fun openPublicBrowser() {
        openCatalog()
        submitPublicAddress()
    }

    private fun submitPublicAddress() {
        rule.onNodeWithTag("public-web-address").performTextReplacement(PUBLIC_URL)
        rule.onNodeWithTag("public-web-open-confirm").performClick()
        awaitBrowser()
    }

    private fun awaitBrowser() {
        rule.onNodeWithContentDescription("Inicio de la aplicación").assertIsDisplayed()
        rule.onNodeWithTag("catalog-list").assertDoesNotExist()
    }

    private fun findOvorion() {
        openCatalog()
        rule.onNodeWithTag("catalog-list").performScrollToNode(hasTestTag("catalog-search-field"))
        rule.onNodeWithTag("catalog-search-field").performTextReplacement("Ovorion")
        rule.waitUntil(20_000) {
            // Search runs off the UI thread; wait for the actual card, including
            // lazy-list placement, rather than sleeping or assuming query completion.
            runCatching {
                rule.onNodeWithTag("catalog-list").performScrollToNode(
                    hasTestTag("catalog-card-junta-andalucia-ovorion"),
                )
            }.isSuccess
        }
        rule.onNodeWithTag("catalog-list")
            .performScrollToNode(hasTestTag("catalog-open-junta-andalucia-ovorion"))
    }

    private fun assertPublicMode(expected: Boolean) {
        rule.onNodeWithContentDescription("Más opciones").performClick()
        rule.onNodeWithText("Información del sitio").performClick()
        if (expected) rule.onNodeWithTag("public-browsing-notice").assertExists()
        else rule.onNodeWithTag("public-browsing-notice").assertDoesNotExist()
        rule.onNodeWithTag("browser-site-information-close").performClick()
    }

    private fun openCertificatePanel() {
        rule.onNodeWithContentDescription("Más opciones").performClick()
        rule.onNodeWithText("Cambiar certificado").performClick()
        rule.onNodeWithTag("jfm-home-background").assertIsDisplayed()
    }

    private fun systemBack() {
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    private fun assertLoadedUrl(expected: String) {
        scenario.onActivity { assertEquals(expected, shadowOf(browser(it)).lastLoadedUrl) }
    }

    private fun currentBrowser(): WebView {
        lateinit var result: WebView
        scenario.onActivity { result = browser(it) }
        return result
    }

    private fun browser(activity: MainActivity): WebView = webViews(activity.window.decorView).single()

    private fun webViews(view: View): List<WebView> = when (view) {
        is WebView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { webViews(view.getChildAt(it)) }
        else -> emptyList()
    }

    private companion object {
        const val PUBLIC_URL = "https://public.example/characterization"
        const val OVORION_URL = "https://www.juntadeandalucia.es/empleoformacionytrabajoautonomo/ovorion/auth/signInAutcertjs"
    }
}

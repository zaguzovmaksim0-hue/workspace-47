package dev.junta.firmamobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.junta.firmamobile.testing.createComposeRule
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
@Config(qualifiers = "w480dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class BrowserPopupInsetsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun popupOwnsStatusAndCutoutInsetsWithoutToolbarGap() = verifyLayout(1f)

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun narrowPopupKeepsLargeFontCloseActionClickable() = verifyLayout(1.5f)

    @Test
    @Config(qualifiers = "w800dp-h480dp")
    fun landscapePopupKeepsCertificateStripAndSingleTopInset() = verifyLayout(1f)

    private fun verifyLayout(fontScale: Float) {
        var closes = 0
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                JuntaFirmaTheme {
                    val insets = WindowInsets(left = 7.dp, top = 32.dp, right = 9.dp, bottom = 24.dp)
                    Column(Modifier.fillMaxSize().browserPopupSafeInsets(insets)) {
                        BrowserPopupHeader { closes++ }
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            BrowserLayout(
                                certificateOwner = "Synthetic certificate",
                                currentUrl = "https://popup.synthetic.example/",
                                browserInsets = insets,
                                onBack = {}, onHome = {}, onReload = {},
                                onChangeCertificate = {}, onLockCertificate = {},
                                onClearCurrentSite = {}, onClearSession = {},
                                onDeleteAllBrowserData = {},
                            ) { modifier -> Box(modifier.testTag("popup-test-content")) }
                        }
                    }
                }
            }
        }
        val header = rule.onNodeWithTag("browser-popup-header").assertIsDisplayed().getUnclippedBoundsInRoot()
        val toolbar = rule.onNodeWithTag(BROWSER_TOOLBAR_TAG).assertIsDisplayed().getUnclippedBoundsInRoot()
        val close = rule.onNodeWithTag("browser-popup-close").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertEquals(32.dp, header.top)
        assertEquals(7.dp, header.left)
        assertEquals(header.bottom, toolbar.top)
        rule.onNodeWithTag(BROWSER_TOOLBAR_TAG).assertHeightIsEqualTo(BrowserIndustrialToolbarHeight)
        assertTrue(close.top >= header.top && close.bottom <= header.bottom)
        assertTrue(close.bottom - close.top >= 48.dp)
        rule.onNodeWithTag(BROWSER_BOTTOM_BAR_TAG).assertIsDisplayed()
        rule.onNodeWithTag("popup-test-content").assertIsDisplayed()
        rule.onNodeWithTag("browser-popup-close").performClick()
        rule.runOnIdle { assertEquals(1, closes) }
    }
}

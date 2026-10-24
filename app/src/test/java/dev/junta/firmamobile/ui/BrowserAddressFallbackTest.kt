package dev.junta.firmamobile.ui

import android.content.Context
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.junta.firmamobile.R
import dev.junta.firmamobile.testing.createComposeRule
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
import org.junit.Assert.assertEquals
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
@Config(qualifiers = "en-rGB-w412dp-h915dp-mdpi")
class BrowserAddressFallbackTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun emptyAndMalformedUrlsDisplayLocalizedFallbackInToolbarAndSiteInformation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("en", context.resources.configuration.locales[0].language)
        val fallback = context.getString(R.string.browser_address_unavailable)
        val trustLabel = "Test trust label"
        val currentUrl = mutableStateOf("https://example.test/path")
        rule.setContent {
            JuntaFirmaTheme {
                BrowserLayout(
                    certificateOwner = "Test certificate",
                    currentUrl = currentUrl.value,
                    trustLabel = trustLabel,
                    browserInsets = WindowInsets(0, 0, 0, 0),
                    onBack = {},
                    onHome = {},
                    onReload = {},
                    onChangeCertificate = {},
                    onLockCertificate = {},
                    onClearCurrentSite = {},
                    onClearSession = {},
                    onDeleteAllBrowserData = {},
                ) { modifier -> Text("Test web content", modifier = modifier) }
            }
        }
        rule.onNodeWithTag(BROWSER_ADDRESS_LABEL_TAG).assertTextEquals("example.test")

        for (url in listOf("", "not an address", "https://")) {
            rule.runOnIdle { currentUrl.value = url }
            rule.onNodeWithTag(BROWSER_ADDRESS_LABEL_TAG).assertIsDisplayed().assertTextEquals(fallback)
            rule.onNodeWithContentDescription(context.getString(R.string.browser_action_more)).performClick()
            rule.onNodeWithText(context.getString(R.string.browser_site_information)).performClick()
            rule.onNodeWithTag("browser-site-information").assertIsDisplayed()
            rule.onNodeWithTag(BROWSER_PROFILE_STATUS_TAG).assertTextEquals(
                context.getString(R.string.browser_host_and_trust, fallback, trustLabel),
            ).assertIsDisplayed()
            rule.onNodeWithTag("browser-site-information-close").performClick()
            rule.onNodeWithTag("browser-site-information").assertDoesNotExist()
        }

        rule.runOnIdle { currentUrl.value = "https://example.test/again" }
        rule.onNodeWithTag(BROWSER_ADDRESS_LABEL_TAG).assertIsDisplayed().assertTextEquals("example.test")
    }
}

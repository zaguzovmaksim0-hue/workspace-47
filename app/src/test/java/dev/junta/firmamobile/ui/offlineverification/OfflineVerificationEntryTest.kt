package dev.junta.firmamobile.ui.offlineverification

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.junta.firmamobile.testing.createComposeRule
import dev.junta.firmamobile.ui.AppRoot
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
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
class OfflineVerificationEntryTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun independentHomeEntryOpensWithoutCertificateAndReopeningStartsEmpty() {
        rule.setContent { JuntaFirmaTheme { AppRoot() } }
        rule.onNodeWithTag("open-offline-verification").performScrollTo().performClick()
        rule.onNodeWithTag("offline-verification-dialog").assertExists()
        rule.onNodeWithTag("offline-verification-verify").assertIsNotEnabled()
        rule.onNodeWithTag("offline-verification-close").performClick()
        rule.onNodeWithTag("offline-verification-dialog").assertDoesNotExist()
        rule.onNodeWithTag("open-legal-information").assertExists()
        rule.onNodeWithTag("open-offline-verification").performScrollTo().performClick()
        rule.onNodeWithTag("offline-verification-verify").assertIsNotEnabled()
        rule.onNodeWithTag("offline-verification-result").assertDoesNotExist()
    }
}

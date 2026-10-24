package dev.junta.firmamobile.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import dev.junta.firmamobile.R
import dev.junta.firmamobile.testing.createComposeRule
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
import java.io.IOException
import org.json.JSONArray
import org.junit.Assert.assertEquals
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
class LegalInformationDialogTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun selectedDocumentSurvivesRecompositionAndBackPreservesTheLicenseSection() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val firstDocument = context.assets.open("legal/documents.json").bufferedReader().use {
            JSONArray(it.readText()).getJSONObject(0)
        }
        val title = firstDocument.getString("title")
        val opening = documentOpening(context, firstDocument.getString("asset"))
        var dismissals = 0
        val onDismiss = mutableStateOf<() -> Unit>({ dismissals++ })
        rule.setContent { JuntaFirmaTheme { LegalInformationDialog(onDismiss = onDismiss.value) } }

        rule.onNodeWithText(context.getString(R.string.legal_licenses)).performScrollTo().performClick()
        waitForText(title)
        rule.onNodeWithText(title).performClick()
        waitForText(opening)
        rule.onNodeWithTag("legal-document-text").assertIsDisplayed()

        // Change a parent-supplied callback without removing the dialog from composition.
        rule.runOnIdle { onDismiss.value = { dismissals += 10 } }
        rule.onNodeWithText(title).assertIsDisplayed()
        rule.onNodeWithText(opening, substring = true).assertIsDisplayed()
        rule.onNodeWithTag("legal-document-text").assertIsDisplayed()

        rule.onNodeWithText(context.getString(R.string.legal_back)).performClick()
        waitForText(title)
        rule.onNodeWithTag("legal-document-text").assertDoesNotExist()
        rule.onNodeWithTag("legal-compatibility-feedback").assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.legal_licenses)).assertIsDisplayed()
        rule.runOnIdle { assertEquals(0, dismissals) }

        rule.onNodeWithText(context.getString(R.string.legal_back)).performClick()
        rule.onNodeWithTag("legal-compatibility-feedback").performScrollTo().assertIsDisplayed()
        rule.runOnIdle { assertEquals(0, dismissals) }
        rule.onNodeWithText(context.getString(R.string.legal_back)).performClick()
        rule.runOnIdle { assertEquals(10, dismissals) }
    }

    @Test
    fun missingLegalAssetsShowDocumentAndEmptyCatalogErrorsAndAllowRecovery() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val assetContext = mutableStateOf<Context>(object : ContextWrapper(context) {
            override fun getAssets(): AssetManager = throw IOException("Synthetic legal assets unavailable")
        })
        val error = context.getString(R.string.legal_load_error)
        val privacyOpening = documentOpening(context, "legal/privacy.txt")
        var dismissals = 0
        rule.setContent {
            JuntaFirmaTheme {
                CompositionLocalProvider(LocalContext provides assetContext.value) {
                    LegalInformationDialog(onDismiss = { dismissals++ })
                }
            }
        }

        rule.onNodeWithText(context.getString(R.string.legal_privacy)).performScrollTo().performClick()
        waitForText(error)
        rule.onNodeWithTag("legal-document-text").assertIsDisplayed()
        rule.onNodeWithText("Synthetic legal assets unavailable").assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.legal_back)).performClick()
        rule.onNodeWithTag("legal-compatibility-feedback").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(error).assertDoesNotExist()

        // An unreadable index produces the empty-catalog UI, not a stuck loading indicator.
        rule.onNodeWithText(context.getString(R.string.legal_licenses)).performScrollTo().performClick()
        waitForText(error)
        rule.onNodeWithTag("legal-document-text").assertDoesNotExist()
        rule.onNodeWithTag("legal-compatibility-feedback").assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.legal_back)).performClick()
        rule.onNodeWithTag("legal-compatibility-feedback").performScrollTo().assertIsDisplayed()
        rule.runOnIdle { assertEquals(0, dismissals) }

        rule.runOnIdle { assetContext.value = context }
        rule.onNodeWithText(context.getString(R.string.legal_privacy)).performScrollTo().performClick()
        waitForText(privacyOpening)
        rule.onNodeWithText(error).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.legal_back)).performClick()
        rule.onNodeWithTag("legal-compatibility-feedback").performScrollTo().assertIsDisplayed()
        rule.runOnIdle { assertEquals(0, dismissals) }
    }

    private fun documentOpening(context: Context, asset: String): String =
        context.assets.open(asset).bufferedReader(Charsets.UTF_8).use { reader ->
            reader.readText().split("\n\n").first(String::isNotBlank).take(120)
        }

    private fun waitForText(text: String) {
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(text, substring = true).assertIsDisplayed()
    }
}

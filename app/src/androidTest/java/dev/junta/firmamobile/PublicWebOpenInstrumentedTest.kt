package dev.junta.firmamobile

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.junta.firmamobile.ui.PublicWebOpenDialog
import java.net.URI
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PublicWebOpenInstrumentedTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun dialogRejectsUnsafeInputAndPreservesTheAcceptedRequestAddress() {
        var selected: URI? = null
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent { PublicWebOpenDialog({ selected = it }, {}) } }
            rule.onNodeWithTag("public-web-open-confirm").assertIsNotEnabled()
            for (input in listOf("http://public.example", "https://user@public.example", "https://127.0.0.1/")) {
                rule.onNodeWithTag("public-web-address").performTextReplacement(input)
                rule.onNodeWithTag("public-web-open-confirm").assertIsNotEnabled()
                rule.runOnIdle { assertNull(selected) }
            }
            val address = "https://unlisted.synthetic.example/form?state=a%2Fb#section"
            rule.onNodeWithTag("public-web-address").performTextReplacement(address)
            rule.onNodeWithTag("public-web-open-confirm").assertIsEnabled().performClick()
            rule.runOnIdle { assertEquals(address, selected!!.toASCIIString()) }
        }
    }

    @Test fun theActualCatalogCanOpenAnUnlistedAddressWithoutSelectingACertificate() {
        ActivityScenario.launch(MainActivity::class.java).use {
            rule.waitUntil(timeoutMillis = 15_000) {
                rule.onAllNodes(androidx.compose.ui.test.hasText("Explorar sedes sin desbloquear el certificado")).fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithText("Explorar sedes sin desbloquear el certificado").performScrollTo().performClick()
            rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("public-web-address"))
            rule.onNodeWithTag("public-web-address").performTextReplacement("https://unlisted.synthetic.example/form")
            rule.onNodeWithTag("public-web-open-confirm").assertIsEnabled().performClick()
            rule.onNodeWithTag("public-browsing-notice").assertDoesNotExist()
            rule.onNodeWithContentDescription("Más opciones").performClick()
            rule.onNodeWithText("Información del sitio").performClick()
            rule.onNodeWithTag("public-browsing-notice").assertIsDisplayed()
            rule.onNodeWithTag("browser-site-information-close").performClick()
            rule.onNodeWithTag("public-web-open-dialog").assertDoesNotExist()
            rule.onNodeWithTag("native-afirma-confirm").assertDoesNotExist()
            rule.onNodeWithTag("interactive-client-auth-confirm").assertDoesNotExist()
        }
    }
}

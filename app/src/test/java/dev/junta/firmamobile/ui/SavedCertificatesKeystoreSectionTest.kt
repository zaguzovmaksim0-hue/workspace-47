package dev.junta.firmamobile.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performClick
import dev.junta.firmamobile.certificate.KeystoreSigningKeyImporter
import dev.junta.firmamobile.certificate.StoredCertificateEntry
import dev.junta.firmamobile.certificate.StoredKeystoreLevel
import dev.junta.firmamobile.certificate.StoredSigningKeyReference
import dev.junta.firmamobile.testing.createComposeRule
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
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
class SavedCertificatesKeystoreSectionTest {
    @get:Rule
    val rule = createComposeRule()

    private val key = StoredCertificateEntry(
        id = "key-only",
        revision = 1,
        reference = null,
        signingKey = StoredSigningKeyReference(
            1, KeystoreSigningKeyImporter.ALIAS_PREFIX + "a".repeat(32),
            StoredKeystoreLevel.STRONGBOX, "b".repeat(64), "key-only.p12",
        ),
    )

    @Test
    fun keyOnlyEntryIsListedAndCanBeSelected() {
        val calls = showKey()

        rule.onNodeWithTag("saved-certificate-key-only").assertExists()
        rule.onNodeWithContentDescription("Usar el certificado key-only.p12").performClick()

        rule.runOnIdle { assertEquals(listOf("switch key-only"), calls) }
    }

    @Test
    fun keyDeletionRunsOnlyAfterExplicitConfirmation() {
        val calls = showKey()

        rule.onNodeWithContentDescription("Quitar el certificado key-only.p12").performClick()
        rule.runOnIdle { assertEquals(emptyList<String>(), calls) }
        rule.onNodeWithTag("confirm-key-deletion").performClick()

        rule.runOnIdle { assertEquals(listOf("remove key-only"), calls) }
    }

    @Test
    fun cancellingKeyDeletionDoesNotRemoveTheEntry() {
        val calls = showKey()

        rule.onNodeWithContentDescription("Quitar el certificado key-only.p12").performClick()
        rule.onNodeWithTag("cancel-key-deletion").performClick()

        rule.onNodeWithTag("confirm-key-deletion").assertDoesNotExist()
        rule.onNodeWithTag("saved-certificate-key-only").assertExists()
        rule.runOnIdle { assertEquals(emptyList<String>(), calls) }
    }

    @Test
    fun selectedKeyCannotBeSelectedAgainButStillRequiresConfirmationForDeletion() {
        val calls = showKey(selected = true)
        rule.onNodeWithText("Seleccionado").assertExists()
        rule.onNodeWithContentDescription("Usar el certificado key-only.p12").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Quitar el certificado key-only.p12").performClick()
        rule.runOnIdle { assertEquals(emptyList<String>(), calls) }
        rule.onNodeWithTag("confirm-key-deletion").performClick()
        rule.runOnIdle { assertEquals(listOf("remove key-only"), calls) }
    }

    @Test
    fun mainPanelShowsTheSelectedKeyWithoutAFilePasswordField() {
        val calls = showKey(selected = true, fullPanel = true)
        rule.onNodeWithTag("selected-keystore-certificate").assertExists()
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        rule.onNodeWithContentDescription(context.getString(dev.junta.firmamobile.R.string.certificate_password))
            .assertDoesNotExist()
        rule.runOnIdle { assertEquals(emptyList<String>(), calls) }
    }

    private fun showKey(selected: Boolean = false, fullPanel: Boolean = false): MutableList<String> {
        val calls = mutableListOf<String>()
        val actions = SavedCertificatesActions(
            state = SavedCertificatesUiState(listOf(key), activeKey = key.takeIf { selected }),
            onAdd = { calls += "add" },
            onSwitch = { calls += "switch ${it.id}" },
            onRemove = { calls += "remove ${it.id}" },
        )
        rule.setContent {
            CompositionLocalProvider(LocalSavedCertificates provides actions) {
                JuntaFirmaTheme {
                    if (fullPanel) AppRoot() else savedCertificatesSection(CertificateUiState.NoCertificate())
                }
            }
        }
        return calls
    }
}

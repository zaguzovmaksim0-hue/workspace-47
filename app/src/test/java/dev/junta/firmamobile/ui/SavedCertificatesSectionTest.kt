package dev.junta.firmamobile.ui

import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import dev.junta.firmamobile.testing.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.junta.firmamobile.certificate.StoredCertificateEntry
import dev.junta.firmamobile.certificate.StoredCertificateReference
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
class SavedCertificatesSectionTest {
    @get:Rule
    val rule = createComposeRule()

    private val active = entry("active")
    private val saved = entry("saved")

    @Test
    fun listsOtherFileAndKeystoreEntriesAndForwardsTheirActions() {
        val calls = mutableListOf<String>()
        val keyed = entry("keyed").copy(signingKey = dev.junta.firmamobile.certificate.StoredSigningKeyReference(
            1, dev.junta.firmamobile.certificate.KeystoreSigningKeyImporter.ALIAS_PREFIX + "a".repeat(32),
            dev.junta.firmamobile.certificate.StoredKeystoreLevel.TEE, "b".repeat(64), "keyed.p12",
        ))
        val actions = SavedCertificatesActions(
            state = SavedCertificatesUiState(listOf(active, saved, keyed)),
            onAdd = { calls += "add" },
            onSwitch = { calls += "switch ${it.id}" },
            onRemove = { calls += "remove ${it.id}" },
        )
        rule.setContent {
            CompositionLocalProvider(LocalSavedCertificates provides actions) {
                JuntaFirmaTheme {
                    AppRoot(state = CertificateUiState.Locked(checkNotNull(active.reference), null, null))
                }
            }
        }

        rule.onNodeWithTag("saved-certificate-active").assertDoesNotExist()
        rule.onNodeWithTag("saved-certificate-keyed").assertExists()
        rule.onNodeWithContentDescription("Usar el certificado keyed.p12").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Usar el certificado saved.p12").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Quitar el certificado saved.p12").performScrollTo().performClick()
        rule.onNodeWithTag("add-saved-certificate").performScrollTo().performClick()

        rule.runOnIdle { assertEquals(listOf("switch keyed", "switch saved", "remove saved", "add"), calls) }
    }

    @Test
    fun panelWithoutCatalogueShowsNoSection() {
        rule.setContent { JuntaFirmaTheme { AppRoot() } }

        rule.onNodeWithTag("saved-certificates").assertDoesNotExist()
    }

    @Test
    fun fullCatalogueShowsAnActionableMessage() = assertCatalogError(
        "CATALOG_FULL",
        "El catálogo de certificados está lleno. Elimina un certificado guardado para añadir otro.",
    )

    @Test
    fun duplicateDocumentShowsAlreadySavedInsteadOfTheGenericError() = assertCatalogError(
        "ALREADY_SAVED", "Este documento ya está guardado en el catálogo.",
    )

    private fun assertCatalogError(name: String, message: String) {
        val actions = SavedCertificatesActions(
            SavedCertificatesUiState(listOf(active, saved), CertificateUiError.valueOf(name)),
            onAdd = {}, onSwitch = {}, onRemove = {},
        )
        rule.setContent {
            CompositionLocalProvider(LocalSavedCertificates provides actions) {
                JuntaFirmaTheme { savedCertificatesSection(CertificateUiState.NoCertificate()) }
            }
        }
        rule.onNodeWithText(message).assertExists()
    }

    private fun entry(name: String) = StoredCertificateEntry(
        name, 1, StoredCertificateReference(Uri.parse("content://documents/$name"), "$name.p12",
            "application/x-pkcs12", 100, null),
    )
}

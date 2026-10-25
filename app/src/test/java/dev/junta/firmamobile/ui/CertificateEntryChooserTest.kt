package dev.junta.firmamobile.ui

import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.junta.firmamobile.certificate.CertificateEntryChoice
import dev.junta.firmamobile.certificate.CertificateSummary
import dev.junta.firmamobile.certificate.StoredCertificateReference
import dev.junta.firmamobile.testing.createComposeRule
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
import java.time.Instant
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
class CertificateEntryChooserTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun choicesReplaceThePasswordFieldAndForwardTheChosenEntry() {
        val calls = mutableListOf<String>()
        val choices = listOf(1, 2).map {
            CertificateEntryChoice(it, CertificateSummary("Titular $it", "Emisor", Instant.EPOCH, Instant.EPOCH), "h$it")
        }
        val reference = StoredCertificateReference(
            Uri.parse("content://documents/several"), "several.p12", "application/x-pkcs12", null, null,
        )
        val actions = CertificateEntryChoiceActions(onChoose = { calls += it.certificateSha256 }, onCancel = { calls += "cancel" })
        rule.setContent {
            CompositionLocalProvider(LocalCertificateEntryChoice provides actions) {
                JuntaFirmaTheme { AppRoot(state = CertificateUiState.Locked(reference, null, null, choices = choices)) }
            }
        }

        rule.onNodeWithText("Elige el certificado de several.p12").assertExists()
        rule.onNodeWithText("Contraseña del certificado").assertDoesNotExist()
        rule.onNodeWithTag("certificate-entry-2").performScrollTo().performClick()
        rule.onNodeWithText("Cancelar elección").performScrollTo().performClick()

        rule.runOnIdle { assertEquals(listOf("h2", "cancel"), calls) }
    }
}

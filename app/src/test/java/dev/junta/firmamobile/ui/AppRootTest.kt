package dev.junta.firmamobile.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import android.net.Uri
import dev.junta.firmamobile.certificate.CertificateSummary
import dev.junta.firmamobile.certificate.StoredCertificateReference
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
import java.time.Instant
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
class AppRootTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun noCertificateInvokesSafSelection() {
        var selected = false
        rule.setContent {
            JuntaFirmaTheme {
                AppRoot(
                    state = CertificateUiState.NoCertificate(),
                    onSelectCertificate = { selected = true },
                )
            }
        }

        rule.onNodeWithText("Seleccionar certificado").performClick()
        rule.runOnIdle { check(selected) }
    }

    @Test
    fun lockedCertificateConsumesPasswordWithoutExposingText() {
        var submitted: CharArray? = null
        rule.setContent {
            JuntaFirmaTheme {
                AppRoot(
                    state = CertificateUiState.Locked(reference(), null, null),
                    onUnlock = { submitted = it.copyOf() },
                )
            }
        }

        rule.onNodeWithContentDescription("Contraseña del certificado")
            .performTextInput("secret-canary")
        rule.onNodeWithText("Desbloquear certificado").performClick()

        rule.runOnIdle {
            check(submitted.contentEquals("secret-canary".toCharArray()))
        }
        rule.onNodeWithText("secret-canary").assertDoesNotExist()
    }

    @Test
    fun unlockedCertificateShowsSafeSummaryAndActions() {
        val summary = summary()
        rule.setContent {
            JuntaFirmaTheme {
                AppRoot(
                    state = CertificateUiState.Unlocked(reference(summary), summary),
                )
            }
        }

        rule.onNodeWithText("Certificado encontrado").assertIsDisplayed()
        rule.onNodeWithText("Persona de Prueba", substring = true).assertIsDisplayed()
        rule.onNodeWithText("CA de Prueba", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Continuar").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Elegir otro").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Bloquear certificado").performScrollTo().assertIsDisplayed()
    }

    private fun reference(summary: CertificateSummary? = null) = StoredCertificateReference(
        Uri.parse("content://documents/synthetic"),
        "synthetic.p12",
        "application/x-pkcs12",
        4096,
        summary,
    )

    private fun summary() = CertificateSummary(
        ownerName = "Persona de Prueba",
        issuerName = "CA de Prueba",
        validFrom = Instant.parse("2030-01-01T00:00:00Z"),
        validUntil = Instant.parse("2031-01-01T00:00:00Z"),
    )

    private fun CharArray?.contentEquals(other: CharArray): Boolean =
        this != null && java.util.Arrays.equals(this, other)
}

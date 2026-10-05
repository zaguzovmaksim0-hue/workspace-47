package dev.junta.firmamobile.ui

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import dev.junta.firmamobile.testing.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
    fun browsingDoesNotRequireImportingOrUnlockingACertificate() {
        var continued = false
        var selected = false
        rule.setContent {
            JuntaFirmaTheme {
                AppRoot(
                    state = CertificateUiState.NoCertificate(),
                    onSelectCertificate = { selected = true },
                    onContinue = { continued = true },
                )
            }
        }
        rule.onNodeWithTag("browse-without-certificate").performScrollTo().performClick()
        rule.runOnIdle { check(continued); check(!selected) }
    }

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
    fun firstRunUsesReferenceStructureAndCurrentCertificateStatus() {
        rule.setContent {
            JuntaFirmaTheme {
                AppRoot(state = CertificateUiState.NoCertificate())
            }
        }

        rule.onNodeWithTag("jfm-home-background").assertExists()
        rule.onNodeWithTag("jfm-brand-title").assertExists()
        rule.onNodeWithTag("jfm-certificate-card").assertExists()
        rule.onNodeWithTag("jfm-home-navigation").assertDoesNotExist()
        rule.onNodeWithText("Firma Mobile").assertIsDisplayed()
        rule.onNodeWithText("Selección segura disponible")
            .assertIsDisplayed()
        rule.onNodeWithText(
            "La selección de certificados estará disponible en la fase 2.",
        ).assertDoesNotExist()
        rule.onNodeWithText("Inicio").assertDoesNotExist()
        rule.onNodeWithText("Historial").assertDoesNotExist()
        rule.onNodeWithText("Ajustes").assertDoesNotExist()
        rule.onNodeWithText("Ayuda").assertDoesNotExist()
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
            .performScrollTo()
            .performTextInput("secret-canary")
        rule.onNodeWithText("Desbloquear certificado")
            .performScrollTo()
            .performClick()

        rule.runOnIdle {
            check(submitted.contentEquals("secret-canary".toCharArray()))
        }
        rule.onNodeWithText("secret-canary").assertDoesNotExist()
    }

    @Test
    fun certificateErrorIsAnAssertiveLiveRegion() {
        rule.setContent {
            JuntaFirmaTheme {
                AppRoot(
                    state = CertificateUiState.Locked(
                        reference(),
                        null,
                        CertificateUiError.PASSWORD_INVALID_OR_FILE,
                    ),
                )
            }
        }

        rule.onNodeWithText(
            "La contraseña no es correcta o el archivo PKCS#12 no es válido.",
        ).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.LiveRegion,
                LiveRegionMode.Assertive,
            ),
        )
    }

    @Test
    fun unlockedCertificateShowsSafeSummaryAndActions() {
        val summary = summary()
        var locked = false
        rule.setContent {
            JuntaFirmaTheme {
                AppRoot(
                    state = CertificateUiState.Unlocked(reference(summary), summary),
                    onLock = { locked = true },
                )
            }
        }

        rule.onNodeWithText("Certificado encontrado")
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithText("Persona de Prueba", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithText("CA de Prueba", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithText("Continuar").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Elegir otro").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Bloquear certificado").assertDoesNotExist()
        rule.onNodeWithContentDescription("Opciones del certificado").performScrollTo().performClick()
        rule.onNodeWithText("Bloquear certificado").assertIsDisplayed().performClick()
        rule.runOnIdle { check(locked) }
        rule.onNodeWithText("Bloquear certificado").assertDoesNotExist()
        rule.onNodeWithText("Olvidar certificado").performScrollTo().assertIsDisplayed()
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

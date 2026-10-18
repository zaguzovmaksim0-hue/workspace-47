package dev.junta.firmamobile.ui

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.junta.firmamobile.certificate.CertificateSummary
import dev.junta.firmamobile.certificate.StoredCertificateReference
import dev.junta.firmamobile.testing.createComposeRule
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
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
class CertificateExpirationNoticeTest {
    @get:Rule
    val rule = createComposeRule()

    private val now = Instant.parse("2026-10-18T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun noNoticeIsShownBeyondThirtyDays() {
        showNotice("2026-11-18T12:00:00Z")
        rule.onNodeWithTag("certificate-expiration-notice").assertDoesNotExist()
    }

    @Test
    fun thirtyDayNoticeHasSpanishCopyAndAPoliteMergedLiveRegion() {
        showNotice("2026-11-17T12:00:00Z")
        rule.onNodeWithTag("certificate-expiration-notice")
            .assertIsDisplayed()
            .assertTextContains("Tu certificado caduca pronto")
            .assertTextContains("Quedan 30 días naturales.", substring = true)
            .assertTextContains("17/11/2026 12:00:00 Z", substring = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun sevenDayNoticeUsesTheMoreUrgentSpanishHeading() {
        showNotice("2026-10-25T12:00:00Z")
        rule.onNodeWithTag("certificate-expiration-notice")
            .assertTextContains("Tu certificado caduca en los próximos 7 días")
            .assertTextContains("Quedan 7 días naturales.", substring = true)
    }

    @Test
    fun oneDayCopyUsesTheSpanishSingular() {
        showNotice("2026-10-19T12:00:00Z")
        rule.onNodeWithTag("certificate-expiration-notice")
            .assertTextContains("Queda 1 día natural.", substring = true)
    }

    @Test
    fun expiryTodayIsNotPresentedAsAlreadyExpiredBeforeItsTimestamp() {
        showNotice("2026-10-18T12:01:00Z")
        rule.onNodeWithTag("certificate-expiration-notice")
            .assertTextContains("Tu certificado caduca hoy")
            .assertTextContains("18/10/2026 12:01:00 Z", substring = true)
        rule.onNodeWithText("Tu certificado ha caducado").assertDoesNotExist()
    }

    @Test
    fun aPastTimestampIsPresentedAsExpired() {
        showNotice("2026-10-18T11:59:59Z")
        rule.onNodeWithTag("certificate-expiration-notice")
            .assertTextContains("Tu certificado ha caducado")
    }

    @Test
    fun displayedTimestampAndTodayClassificationUseTheSameZone() {
        showNotice(
            "2026-10-19T01:00:00Z",
            Clock.fixed(Instant.parse("2026-10-18T23:00:00Z"), ZoneId.of("Europe/Madrid")),
        )
        rule.onNodeWithTag("certificate-expiration-notice")
            .assertTextContains("Tu certificado caduca hoy")
            .assertTextContains("19/10/2026 03:00:00 +02:00", substring = true)
    }

    @Test
    fun changingTheCurrentExpiryRemovesThePreviousNotice() {
        var validUntil by mutableStateOf(Instant.parse("2026-11-07T12:00:00Z"))
        rule.setContent {
            JuntaFirmaTheme { CertificateExpirationNotice(validUntil, clock = clock) }
        }
        rule.onNodeWithTag("certificate-expiration-notice").assertExists()
        rule.runOnIdle { validUntil = Instant.parse("2027-01-01T12:00:00Z") }
        rule.onNodeWithTag("certificate-expiration-notice").assertDoesNotExist()
    }

    @Test
    fun lockedCurrentCertificateMetadataActuallyContainsTheNotice() {
        val summary = summary(Instant.now().plusSeconds(3L * 86_400L))
        rule.setContent {
            JuntaFirmaTheme {
                AppRoot(state = CertificateUiState.Locked(reference(summary), summary, null))
            }
        }
        rule.onNodeWithTag("certificate-expiration-notice")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Tu certificado caduca en los próximos 7 días")
        rule.onNodeWithText("Desbloquear certificado").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun unlockedCurrentCertificateShowsTheNoticeWithoutChangingTheContinueAction() {
        val summary = summary(Instant.now().plusSeconds(3L * 86_400L))
        var continued = false
        rule.setContent {
            JuntaFirmaTheme {
                AppRoot(
                    state = CertificateUiState.Unlocked(reference(summary), summary),
                    onContinue = { continued = true },
                )
            }
        }
        rule.onNodeWithTag("certificate-expiration-notice").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Continuar").performScrollTo().performClick()
        rule.runOnIdle { check(continued) }
    }

    @Test
    fun theExistingExpiredErrorRemainsAssertiveAlongsideTheInformationalNotice() {
        val summary = summary(Instant.now().minusSeconds(60L))
        rule.setContent {
            JuntaFirmaTheme {
                AppRoot(
                    state = CertificateUiState.Locked(
                        reference(summary), summary, CertificateUiError.CERTIFICATE_EXPIRED,
                    ),
                )
            }
        }
        rule.onNodeWithTag("certificate-expiration-notice")
            .performScrollTo()
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        rule.onNodeWithText("El certificado ha caducado.")
            .performScrollTo()
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive))
    }

    @Test
    fun thereIsNoNoticeWithoutACurrentCertificate() {
        rule.setContent { JuntaFirmaTheme { AppRoot(state = CertificateUiState.NoCertificate()) } }
        rule.onNodeWithTag("certificate-expiration-notice").assertDoesNotExist()
    }

    private fun showNotice(validUntil: String, clock: Clock = this.clock) {
        rule.setContent {
            JuntaFirmaTheme { CertificateExpirationNotice(Instant.parse(validUntil), clock = clock) }
        }
    }

    private fun summary(validUntil: Instant) = CertificateSummary(
        ownerName = "Persona sintética",
        issuerName = "Emisor sintético",
        validFrom = Instant.now().minusSeconds(365L * 86_400L),
        validUntil = validUntil,
    )

    private fun reference(summary: CertificateSummary) = StoredCertificateReference(
        Uri.parse("content://documents/synthetic-expiry"),
        "synthetic.p12",
        "application/x-pkcs12",
        4096,
        summary,
    )
}

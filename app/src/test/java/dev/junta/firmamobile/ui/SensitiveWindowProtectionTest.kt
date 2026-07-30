package dev.junta.firmamobile.ui

import android.net.Uri
import android.view.WindowManager.LayoutParams.FLAG_SECURE
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import dev.junta.firmamobile.certificate.CertificateSummary
import dev.junta.firmamobile.certificate.StoredCertificateReference
import dev.junta.firmamobile.signing.SigningErrorCode
import dev.junta.firmamobile.signing.SigningUiState
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class SensitiveWindowProtectionTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun statePolicyProtectsPasswordUnlockedBrowserAndSigningStates() {
        val reference = StoredCertificateReference(
            uri = Uri.parse("content://tests/certificate.p12"),
            displayName = "certificate.p12",
            mimeType = "application/x-pkcs12",
            size = 128L,
            summary = null,
        )
        val summary = CertificateSummary(
            ownerName = "Test Person",
            issuerName = "Test CA",
            validFrom = Instant.parse("2026-01-01T00:00:00Z"),
            validUntil = Instant.parse("2027-01-01T00:00:00Z"),
        )
        val idle = SigningUiState.Idle

        assertEquals(
            false,
            SensitiveWindowStatePolicy.requiresSecureWindow(
                CertificateUiState.LoadingReference,
                idle,
            ),
        )
        assertEquals(
            false,
            SensitiveWindowStatePolicy.requiresSecureWindow(
                CertificateUiState.NoCertificate(),
                idle,
            ),
        )
        assertEquals(
            true,
            SensitiveWindowStatePolicy.requiresSecureWindow(
                CertificateUiState.Locked(reference, null, null),
                idle,
            ),
        )
        assertEquals(
            true,
            SensitiveWindowStatePolicy.requiresSecureWindow(
                CertificateUiState.Unlocking(reference, null),
                idle,
            ),
        )
        assertEquals(
            true,
            SensitiveWindowStatePolicy.requiresSecureWindow(
                CertificateUiState.Unlocked(reference, summary),
                idle,
            ),
        )
        assertEquals(
            true,
            SensitiveWindowStatePolicy.requiresSecureWindow(
                CertificateUiState.NoCertificate(),
                SigningUiState.Signing(UUID.fromString("00000000-0000-0000-0000-000000000001")),
            ),
        )
        assertEquals(
            true,
            SensitiveWindowStatePolicy.requiresSecureWindow(
                CertificateUiState.NoCertificate(),
                SigningUiState.Failed(null, SigningErrorCode.INVALID_REQUEST),
            ),
        )
    }

    @Test
    fun realWindowFlagCanBeEnabledAndCleared() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java)
            .setup()
            .get()

        WindowSecureFlagPolicy.apply(activity.window, sensitive = true)
        assertTrue(activity.window.attributes.flags and FLAG_SECURE != 0)

        WindowSecureFlagPolicy.apply(activity.window, sensitive = false)
        assertEquals(0, activity.window.attributes.flags and FLAG_SECURE)
    }

    @Test
    fun sensitiveEffectClearsProtectionWhenItLeavesComposition() {
        val visible = mutableStateOf(true)
        val events = mutableListOf<Boolean>()
        rule.setContent {
            if (visible.value) {
                SensitiveWindowProtection(enabled = true) { events += it }
            }
        }
        rule.runOnIdle {
            assertEquals(true, events.last())
            visible.value = false
        }

        rule.runOnIdle {
            assertEquals(false, events.last())
        }
    }
}

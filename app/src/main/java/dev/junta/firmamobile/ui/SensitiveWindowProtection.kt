package dev.junta.firmamobile.ui

import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import dev.junta.firmamobile.signing.SigningUiState

internal object SensitiveWindowStatePolicy {
    @Suppress("UNUSED_PARAMETER")
    fun requiresSecureWindow(
        certificateState: CertificateUiState,
        signingState: SigningUiState,
    ): Boolean = false
}

object WindowSecureFlagPolicy {
    @Suppress("UNUSED_PARAMETER")
    fun apply(window: Window, sensitive: Boolean) {
        // Screen capture is intentionally allowed across the whole app.
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

@Composable
internal fun SensitiveWindowProtection(
    enabled: Boolean,
    updateSecure: (Boolean) -> Unit,
) {
    val currentUpdate by rememberUpdatedState(updateSecure)
    DisposableEffect(enabled) {
        currentUpdate(enabled)
        onDispose { currentUpdate(false) }
    }
}

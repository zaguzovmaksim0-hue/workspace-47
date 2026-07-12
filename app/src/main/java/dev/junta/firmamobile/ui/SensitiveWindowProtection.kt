package dev.junta.firmamobile.ui

import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

object WindowSecureFlagPolicy {
    fun apply(window: Window, sensitive: Boolean) {
        if (sensitive) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
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

package dev.junta.firmamobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos

internal const val CONSENT_ARM_DELAY_MS = 600L

@Composable
internal fun rememberConsentArmed(key: Any): Boolean {
    var armed by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        val startedAt = withFrameNanos { it }
        var elapsedNanos = 0L
        while (elapsedNanos < CONSENT_ARM_DELAY_MS * 1_000_000L) {
            elapsedNanos = withFrameNanos { it - startedAt }
        }
        armed = true
    }
    return armed
}

package dev.junta.firmamobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

internal const val CONSENT_ARM_DELAY_MS = 600L

@Composable
internal fun rememberConsentArmed(key: Any): Boolean {
    var armed by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        delay(CONSENT_ARM_DELAY_MS)
        armed = true
    }
    return armed
}

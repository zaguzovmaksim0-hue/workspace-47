package dev.junta.firmamobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006C51),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF8AF8CE),
    onPrimaryContainer = Color(0xFF002117),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF6DDBB3),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF00513D),
    onPrimaryContainer = Color(0xFF8AF8CE),
)

@Composable
fun JuntaFirmaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColorScheme else LightColorScheme,
        content = content,
    )
}

package dev.junta.firmamobile.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Semantic accent roles. The names predate the device design; the colours are now its tokens:
 * orange accent, LED green, graphite, amber, slate and warm grey.
 */
@Suppress("MagicNumber") // Fixed sRGB device stops.
internal enum class JuntaTone(val start: Long, val end: Long) {
    CORAL(0xFFE2571E, 0xFFB8441A),
    MINT(0xFF4CAF57, 0xFF2E7D38),
    VIOLET(0xFF4A4945, 0xFF2B2B2B),
    SAFFRON(0xFFE0A030, 0xFF9A6A12),
    SKY(0xFF5B6168, 0xFF3A4046),
    ROSE(0xFF8C7F73, 0xFF5F5650),
}

internal val JuntaTone.brush: Brush
    get() = Brush.linearGradient(listOf(Color(start), Color(end)))

internal val JuntaTone.accent: Color
    get() = Color(end)

internal val JuntaTone.light: Color
    get() = Color(start)

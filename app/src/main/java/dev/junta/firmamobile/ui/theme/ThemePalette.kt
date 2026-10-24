package dev.junta.firmamobile.ui.theme

/** Opaque sRGB tokens, independent of Android so fallback contrast is JVM-testable. */
internal data class JuntaPalette(
    val paper: Long,
    val paperElevated: Long,
    val ink: Long,
    val teal: Long,
    val tealBright: Long,
    val tealDark: Long,
    val mutedInk: Long,
    val hairline: Long,
    val primaryContainer: Long,
    val onPrimaryContainer: Long,
    val surfaceVariant: Long,
    val error: Long,
    val onError: Long,
)

internal val JuntaLightPalette = JuntaPalette(
    paper = 0xFFF2E5D1,
    paperElevated = 0xFFF8EDDB,
    ink = 0xFF101310,
    teal = 0xFF125854,
    tealBright = 0xFF14736B,
    tealDark = 0xFF073C3A,
    mutedInk = 0xFF4F524C,
    hairline = 0xFFB8AD9B,
    primaryContainer = 0xFFD9E5DE,
    onPrimaryContainer = 0xFF101310,
    surfaceVariant = 0xFFE7DBC8,
    error = 0xFF8A2424,
    onError = 0xFFFFFFFF,
)

internal val JuntaDarkPalette = JuntaPalette(
    paper = 0xFF101916,
    paperElevated = 0xFF1B2621,
    ink = 0xFFF1E8D7,
    teal = 0xFF8CD8CF,
    tealBright = 0xFF93E4D9,
    tealDark = 0xFF78C3BD,
    mutedInk = 0xFFC2C9BF,
    hairline = 0xFF88988B,
    primaryContainer = 0xFF174E48,
    onPrimaryContainer = 0xFFB7F1E6,
    surfaceVariant = 0xFF303B33,
    error = 0xFFFFB4AB,
    onError = 0xFF690005,
)

internal enum class JuntaThemeVariant(val dynamic: Boolean) {
    LIGHT(false),
    DARK(false),
    DYNAMIC_LIGHT(true),
    DYNAMIC_DARK(true),
}

private const val DYNAMIC_COLOR_MIN_SDK = 31

internal fun selectJuntaTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    sdkInt: Int,
): JuntaThemeVariant = when {
    dynamicColor && sdkInt >= DYNAMIC_COLOR_MIN_SDK -> {
        if (darkTheme) JuntaThemeVariant.DYNAMIC_DARK else JuntaThemeVariant.DYNAMIC_LIGHT
    }
    darkTheme -> JuntaThemeVariant.DARK
    else -> JuntaThemeVariant.LIGHT
}

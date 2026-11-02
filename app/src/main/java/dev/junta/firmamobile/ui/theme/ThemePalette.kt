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
    paper = 0xFFF5F3EE,
    paperElevated = 0xFFFFFFFF,
    ink = 0xFF1A1C1B,
    teal = 0xFF0F5C56,
    tealBright = 0xFF17756D,
    tealDark = 0xFF0A403C,
    mutedInk = 0xFF5A615D,
    hairline = 0xFFDDD8CE,
    primaryContainer = 0xFFD3EDE7,
    onPrimaryContainer = 0xFF06302C,
    surfaceVariant = 0xFFECE8DF,
    error = 0xFFB3261E,
    onError = 0xFFFFFFFF,
)

internal val JuntaDarkPalette = JuntaPalette(
    paper = 0xFF0E1413,
    paperElevated = 0xFF182120,
    ink = 0xFFE6EAE7,
    teal = 0xFF7FD3C8,
    tealBright = 0xFF93E4D9,
    tealDark = 0xFF6CC2B8,
    mutedInk = 0xFFB8C3BF,
    hairline = 0xFF34403D,
    primaryContainer = 0xFF184C46,
    onPrimaryContainer = 0xFFB9F0E6,
    surfaceVariant = 0xFF253130,
    error = 0xFFFFB4AB,
    onError = 0xFF690005,
)

/** Remaining Material roles, derived from the brand palette for dialogs, menus and fields. */
internal data class JuntaSchemeExtras(
    val inversePrimary: Long,
    val secondaryContainer: Long,
    val onSecondaryContainer: Long,
    val tertiary: Long,
    val onTertiary: Long,
    val tertiaryContainer: Long,
    val onTertiaryContainer: Long,
    val inverseSurface: Long,
    val inverseOnSurface: Long,
    val errorContainer: Long,
    val onErrorContainer: Long,
    val outline: Long,
    val surfaceDim: Long,
    val surfaceBright: Long,
    val surfaceContainerLowest: Long,
    val surfaceContainerLow: Long,
    val surfaceContainer: Long,
    val surfaceContainerHigh: Long,
    val surfaceContainerHighest: Long,
)

internal val JuntaLightExtras = JuntaSchemeExtras(
    inversePrimary = 0xFF7FD3C8,
    secondaryContainer = 0xFFDCEBE7,
    onSecondaryContainer = 0xFF0A2E2A,
    tertiary = 0xFF7A5A1E,
    onTertiary = 0xFFFFFFFF,
    tertiaryContainer = 0xFFF6E3BD,
    onTertiaryContainer = 0xFF2A1C00,
    inverseSurface = 0xFF2E3230,
    inverseOnSurface = 0xFFEFF1EE,
    errorContainer = 0xFFF9DEDC,
    onErrorContainer = 0xFF410E0B,
    outline = 0xFF767D79,
    surfaceDim = 0xFFE3DFD7,
    surfaceBright = 0xFFFFFFFF,
    surfaceContainerLowest = 0xFFFFFFFF,
    surfaceContainerLow = 0xFFFAF9F6,
    surfaceContainer = 0xFFFFFFFF,
    surfaceContainerHigh = 0xFFFCFBF8,
    surfaceContainerHighest = 0xFFECE8E0,
)

internal val JuntaDarkExtras = JuntaSchemeExtras(
    inversePrimary = 0xFF0F5C56,
    secondaryContainer = 0xFF22413C,
    onSecondaryContainer = 0xFFCDE8E2,
    tertiary = 0xFFE6C17D,
    onTertiary = 0xFF3F2D00,
    tertiaryContainer = 0xFF5A4300,
    onTertiaryContainer = 0xFFFFDEA6,
    inverseSurface = 0xFFE0E3E0,
    inverseOnSurface = 0xFF2B302E,
    errorContainer = 0xFF8C1D18,
    onErrorContainer = 0xFFF9DEDC,
    outline = 0xFF8A9490,
    surfaceDim = 0xFF0E1413,
    surfaceBright = 0xFF333C3A,
    surfaceContainerLowest = 0xFF090E0D,
    surfaceContainerLow = 0xFF141B1A,
    surfaceContainer = 0xFF1A2322,
    surfaceContainerHigh = 0xFF202A28,
    surfaceContainerHighest = 0xFF2A3432,
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

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

// "F" device palette: warm grey body, graphite ink and a deep orange that stays legible as text.
internal val JuntaLightPalette = JuntaPalette(
    paper = 0xFFE7E5E0,
    paperElevated = 0xFFF4F2ED,
    ink = 0xFF2B2B2B,
    teal = 0xFFA93C13,
    tealBright = 0xFFB03F12,
    tealDark = 0xFF8A3110,
    mutedInk = 0xFF615E57,
    hairline = 0xFFCFCBC3,
    primaryContainer = 0xFFF3DCCF,
    onPrimaryContainer = 0xFF5E200A,
    surfaceVariant = 0xFFDCD9D2,
    error = 0xFFB3261E,
    onError = 0xFFFFFFFF,
)

// Graphite night variant of the same device: dark body, warm light ink and a lighter orange.
internal val JuntaDarkPalette = JuntaPalette(
    paper = 0xFF1E1F1D,
    paperElevated = 0xFF2A2B28,
    ink = 0xFFECE9E2,
    teal = 0xFFF08A5D,
    tealBright = 0xFFF49A70,
    tealDark = 0xFFEC7D4C,
    mutedInk = 0xFFABA79E,
    hairline = 0xFF3A3B37,
    primaryContainer = 0xFF5A2410,
    onPrimaryContainer = 0xFFFFDBCC,
    surfaceVariant = 0xFF171816,
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
    inversePrimary = 0xFFFFB59A,
    secondaryContainer = 0xFFDCD9D2,
    onSecondaryContainer = 0xFF2B2B2B,
    tertiary = 0xFF2E6B36,
    onTertiary = 0xFFFFFFFF,
    tertiaryContainer = 0xFFD3E8D2,
    onTertiaryContainer = 0xFF0F3A16,
    inverseSurface = 0xFF2E2E2B,
    inverseOnSurface = 0xFFF1EFEA,
    errorContainer = 0xFFF6DCD7,
    onErrorContainer = 0xFF5C1410,
    outline = 0xFF7E7B74,
    surfaceDim = 0xFFD8D5CE,
    surfaceBright = 0xFFF7F6F2,
    surfaceContainerLowest = 0xFFFBFAF6,
    surfaceContainerLow = 0xFFEEECE7,
    surfaceContainer = 0xFFF1EFEA,
    surfaceContainerHigh = 0xFFF4F2ED,
    surfaceContainerHighest = 0xFFE2DFD8,
)

internal val JuntaDarkExtras = JuntaSchemeExtras(
    inversePrimary = 0xFFA93C13,
    secondaryContainer = 0xFF2F302C,
    onSecondaryContainer = 0xFFECE9E2,
    tertiary = 0xFF8FD694,
    onTertiary = 0xFF0B3A12,
    tertiaryContainer = 0xFF1E4A24,
    onTertiaryContainer = 0xFFCDEFCD,
    inverseSurface = 0xFFE6E3DC,
    inverseOnSurface = 0xFF2B2B2B,
    errorContainer = 0xFF5C1410,
    onErrorContainer = 0xFFFFDAD4,
    outline = 0xFF8D8980,
    surfaceDim = 0xFF1E1F1D,
    surfaceBright = 0xFF3A3B37,
    surfaceContainerLowest = 0xFF141513,
    surfaceContainerLow = 0xFF232421,
    surfaceContainer = 0xFF272825,
    surfaceContainerHigh = 0xFF2A2B28,
    surfaceContainerHighest = 0xFF34352F,
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

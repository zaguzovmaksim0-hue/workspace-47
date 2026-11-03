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
    paper = 0xFFF6F3FF,
    paperElevated = 0xFFFFFFFF,
    ink = 0xFF1B1736,
    teal = 0xFF5236E8,
    tealBright = 0xFF5F3DF0,
    tealDark = 0xFF3B22B8,
    mutedInk = 0xFF5C5878,
    hairline = 0xFFE3DDF7,
    primaryContainer = 0xFFECE6FF,
    onPrimaryContainer = 0xFF2A1585,
    surfaceVariant = 0xFFEFEBFB,
    error = 0xFFC2264E,
    onError = 0xFFFFFFFF,
)

internal val JuntaDarkPalette = JuntaPalette(
    paper = 0xFF0F0C1F,
    paperElevated = 0xFF1B1733,
    ink = 0xFFF0EDFF,
    teal = 0xFFB8A9FF,
    tealBright = 0xFFCBBFFF,
    tealDark = 0xFFA493FF,
    mutedInk = 0xFFBAB4D9,
    hairline = 0xFF302A50,
    primaryContainer = 0xFF352782,
    onPrimaryContainer = 0xFFE6DFFF,
    surfaceVariant = 0xFF262143,
    error = 0xFFFF8FAB,
    onError = 0xFF5E0022,
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
    inversePrimary = 0xFFB8A9FF,
    secondaryContainer = 0xFFE6E0FF,
    onSecondaryContainer = 0xFF1F1452,
    tertiary = 0xFF9C2F6B,
    onTertiary = 0xFFFFFFFF,
    tertiaryContainer = 0xFFFFDDEB,
    onTertiaryContainer = 0xFF3D0726,
    inverseSurface = 0xFF2C2848,
    inverseOnSurface = 0xFFF2EFFF,
    errorContainer = 0xFFFFDDE3,
    onErrorContainer = 0xFF5C0E24,
    outline = 0xFF77729A,
    surfaceDim = 0xFFE4DFF5,
    surfaceBright = 0xFFFFFFFF,
    surfaceContainerLowest = 0xFFFFFFFF,
    surfaceContainerLow = 0xFFFBF9FF,
    surfaceContainer = 0xFFFFFFFF,
    surfaceContainerHigh = 0xFFFFFFFF,
    surfaceContainerHighest = 0xFFEAE5F8,
)

internal val JuntaDarkExtras = JuntaSchemeExtras(
    inversePrimary = 0xFF5236E8,
    secondaryContainer = 0xFF2E2563,
    onSecondaryContainer = 0xFFE6E0FF,
    tertiary = 0xFFFFADD2,
    onTertiary = 0xFF5A0F3B,
    tertiaryContainer = 0xFF7A1F54,
    onTertiaryContainer = 0xFFFFDDEB,
    inverseSurface = 0xFFE8E4FA,
    inverseOnSurface = 0xFF26223D,
    errorContainer = 0xFF5C0E24,
    onErrorContainer = 0xFFFFDDE3,
    outline = 0xFF8F89B3,
    surfaceDim = 0xFF0F0C1F,
    surfaceBright = 0xFF36305A,
    surfaceContainerLowest = 0xFF0A0817,
    surfaceContainerLow = 0xFF161229,
    surfaceContainer = 0xFF1B1733,
    surfaceContainerHigh = 0xFF221D3D,
    surfaceContainerHighest = 0xFF2C2650,
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

package dev.junta.firmamobile.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val LocalJuntaPalette = staticCompositionLocalOf { JuntaLightPalette }

// Keep existing UI call sites theme-aware without mutating process-global colors.
val JuntaPaper: Color
    @Composable
    @ReadOnlyComposable
    get() = Color(LocalJuntaPalette.current.paper)

val JuntaPaperElevated: Color
    @Composable
    @ReadOnlyComposable
    get() = Color(LocalJuntaPalette.current.paperElevated)

val JuntaInk: Color
    @Composable
    @ReadOnlyComposable
    get() = Color(LocalJuntaPalette.current.ink)

val JuntaTeal: Color
    @Composable
    @ReadOnlyComposable
    get() = Color(LocalJuntaPalette.current.teal)

val JuntaTealBright: Color
    @Composable
    @ReadOnlyComposable
    get() = Color(LocalJuntaPalette.current.tealBright)

val JuntaTealDark: Color
    @Composable
    @ReadOnlyComposable
    get() = Color(LocalJuntaPalette.current.tealDark)

val JuntaMutedInk: Color
    @Composable
    @ReadOnlyComposable
    get() = Color(LocalJuntaPalette.current.mutedInk)

val JuntaHairline: Color
    @Composable
    @ReadOnlyComposable
    get() = Color(LocalJuntaPalette.current.hairline)

/** Monospaced system face for file names, domains, dates and other "printed" device text. */
internal val FirmaMono: FontFamily = FontFamily.Monospace

internal val JuntaLightColorScheme = fallbackColorScheme(JuntaLightPalette, JuntaLightExtras, darkTheme = false)
internal val JuntaDarkColorScheme = fallbackColorScheme(JuntaDarkPalette, JuntaDarkExtras, darkTheme = true)

private fun fallbackColorScheme(palette: JuntaPalette, extras: JuntaSchemeExtras, darkTheme: Boolean): ColorScheme {
    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = Color(palette.teal),
        surfaceTint = Color(palette.teal),
        onPrimary = Color(palette.paperElevated),
        primaryContainer = Color(palette.primaryContainer),
        onPrimaryContainer = Color(palette.onPrimaryContainer),
        inversePrimary = Color(extras.inversePrimary),
        secondary = Color(palette.tealDark),
        onSecondary = Color(palette.paperElevated),
        secondaryContainer = Color(extras.secondaryContainer),
        onSecondaryContainer = Color(extras.onSecondaryContainer),
        tertiary = Color(extras.tertiary),
        onTertiary = Color(extras.onTertiary),
        tertiaryContainer = Color(extras.tertiaryContainer),
        onTertiaryContainer = Color(extras.onTertiaryContainer),
        background = Color(palette.paper),
        onBackground = Color(palette.ink),
        surface = Color(palette.paperElevated),
        onSurface = Color(palette.ink),
        surfaceVariant = Color(palette.surfaceVariant),
        onSurfaceVariant = Color(palette.mutedInk),
        inverseSurface = Color(extras.inverseSurface),
        inverseOnSurface = Color(extras.inverseOnSurface),
        outline = Color(extras.outline),
        outlineVariant = Color(palette.hairline),
        error = Color(palette.error),
        onError = Color(palette.onError),
        errorContainer = Color(extras.errorContainer),
        onErrorContainer = Color(extras.onErrorContainer),
        surfaceDim = Color(extras.surfaceDim),
        surfaceBright = Color(extras.surfaceBright),
        surfaceContainerLowest = Color(extras.surfaceContainerLowest),
        surfaceContainerLow = Color(extras.surfaceContainerLow),
        surfaceContainer = Color(extras.surfaceContainer),
        surfaceContainerHigh = Color(extras.surfaceContainerHigh),
        surfaceContainerHighest = Color(extras.surfaceContainerHighest),
    )
}

private fun ColorScheme.juntaPalette() = JuntaPalette(
    paper = background.argb(),
    paperElevated = surface.argb(),
    ink = onSurface.argb(),
    teal = primary.argb(),
    tealBright = primary.argb(),
    tealDark = secondary.argb(),
    mutedInk = onSurfaceVariant.argb(),
    hairline = outlineVariant.argb(),
    primaryContainer = primaryContainer.argb(),
    onPrimaryContainer = onPrimaryContainer.argb(),
    surfaceVariant = surfaceVariant.argb(),
    error = error.argb(),
    onError = onError.argb(),
)

private const val UNSIGNED_ARGB_MASK = 0xFFFFFFFFL

private fun Color.argb(): Long = toArgb().toLong() and UNSIGNED_ARGB_MASK

private val MaterialTypography = Typography()

// Device-label hierarchy: medium-weight titles, regular body text and a light display face.
private val JuntaTypography = MaterialTypography.copy(
    displayLarge = MaterialTypography.displayLarge.copy(
        fontWeight = FontWeight.Light,
        fontSize = 64.sp,
        lineHeight = 64.sp,
        letterSpacing = (-1).sp,
    ),
    headlineLarge = MaterialTypography.headlineLarge.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineMedium = MaterialTypography.headlineMedium.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.2).sp,
    ),
    headlineSmall = MaterialTypography.headlineSmall.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.1).sp,
    ),
    titleLarge = MaterialTypography.titleLarge.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = MaterialTypography.titleMedium.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 16.5.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = MaterialTypography.titleSmall.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    labelLarge = MaterialTypography.labelLarge.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    labelMedium = MaterialTypography.labelMedium.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = MaterialTypography.bodyLarge.copy(
        fontSize = 16.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyMedium = MaterialTypography.bodyMedium.copy(
        fontSize = 15.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.1.sp,
    ),
    bodySmall = MaterialTypography.bodySmall.copy(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),
)

// Keys and plates use moderate radii; only dialogs and sheets keep a larger corner.
private val JuntaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

@Composable
fun JuntaFirmaTheme(content: @Composable () -> Unit) {
    juntaFirmaTheme(
        darkTheme = isSystemInDarkTheme(),
        // The device palette is intentional; wallpaper colours would repaint keys, display and LEDs.
        dynamicColor = false,
        content = content,
    )
}

@Composable
internal fun juntaFirmaTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val variant = selectJuntaTheme(darkTheme, dynamicColor, Build.VERSION.SDK_INT)
    val context = LocalContext.current
    // Retain a direct SDK guard at the platform API call, as well as the pure selector.
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && variant.dynamic) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) JuntaDarkColorScheme else JuntaLightColorScheme
    }
    val palette = when (variant) {
        JuntaThemeVariant.LIGHT -> JuntaLightPalette
        JuntaThemeVariant.DARK -> JuntaDarkPalette
        else -> colorScheme.juntaPalette()
    }
    val device = if (darkTheme) FirmaDarkColors else FirmaLightColors
    CompositionLocalProvider(LocalJuntaPalette provides palette, LocalFirmaColors provides device) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = JuntaTypography,
            shapes = JuntaShapes,
            content = content,
        )
    }
}

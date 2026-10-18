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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.junta.firmamobile.R

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

val JuntaDisplayFont = FontFamily(
    Font(
        resId = R.font.bebas_neue_regular,
        weight = FontWeight.Normal,
    ),
)

internal val JuntaLightColorScheme = fallbackColorScheme(JuntaLightPalette, darkTheme = false)
internal val JuntaDarkColorScheme = fallbackColorScheme(JuntaDarkPalette, darkTheme = true)

private fun fallbackColorScheme(palette: JuntaPalette, darkTheme: Boolean): ColorScheme {
    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = Color(palette.teal),
        surfaceTint = Color(palette.teal),
        onPrimary = Color(palette.paperElevated),
        primaryContainer = Color(palette.primaryContainer),
        onPrimaryContainer = Color(palette.onPrimaryContainer),
        secondary = Color(palette.tealDark),
        onSecondary = Color(palette.paperElevated),
        background = Color(palette.paper),
        onBackground = Color(palette.ink),
        surface = Color(palette.paperElevated),
        onSurface = Color(palette.ink),
        surfaceVariant = Color(palette.surfaceVariant),
        onSurfaceVariant = Color(palette.mutedInk),
        outline = Color(palette.ink),
        outlineVariant = Color(palette.hairline),
        error = Color(palette.error),
        onError = Color(palette.onError),
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

private fun Color.argb(): Long = toArgb().toLong() and 0xFFFFFFFFL

private val JuntaTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = JuntaDisplayFont,
        fontWeight = FontWeight.Normal,
        fontSize = 62.sp,
        lineHeight = 56.sp,
        letterSpacing = 1.3.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = JuntaDisplayFont,
        fontWeight = FontWeight.Normal,
        fontSize = 40.sp,
        lineHeight = 42.sp,
        letterSpacing = 0.9.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = JuntaDisplayFont,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 35.sp,
        letterSpacing = 0.8.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = JuntaDisplayFont,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 31.sp,
        letterSpacing = 0.7.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.2.sp,
    ),
    bodyLarge = TextStyle(
        fontSize = 18.sp,
        lineHeight = 25.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
)

private val JuntaShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(3.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

@Composable
fun JuntaFirmaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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
    CompositionLocalProvider(LocalJuntaPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = JuntaTypography,
            shapes = JuntaShapes,
            content = content,
        )
    }
}

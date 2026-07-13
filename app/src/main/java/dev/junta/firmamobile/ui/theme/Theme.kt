package dev.junta.firmamobile.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.junta.firmamobile.R

val JuntaPaper = Color(0xFFF2E5D1)
val JuntaPaperElevated = Color(0xFFF8EDDB)
val JuntaInk = Color(0xFF101310)
val JuntaTeal = Color(0xFF125854)
val JuntaTealBright = Color(0xFF14736B)
val JuntaTealDark = Color(0xFF073C3A)
val JuntaMutedInk = Color(0xFF4F524C)
val JuntaHairline = Color(0xFFB8AD9B)

val JuntaDisplayFont = FontFamily(
    Font(
        resId = R.font.bebas_neue_regular,
        weight = FontWeight.Normal,
    ),
)

private val JuntaColorScheme = lightColorScheme(
    primary = JuntaTeal,
    onPrimary = JuntaPaperElevated,
    primaryContainer = Color(0xFFD9E5DE),
    onPrimaryContainer = JuntaInk,
    secondary = JuntaTealDark,
    onSecondary = JuntaPaperElevated,
    background = JuntaPaper,
    onBackground = JuntaInk,
    surface = JuntaPaperElevated,
    onSurface = JuntaInk,
    surfaceVariant = Color(0xFFE7DBC8),
    onSurfaceVariant = JuntaMutedInk,
    outline = JuntaInk,
    outlineVariant = JuntaHairline,
    error = Color(0xFF8A2424),
    onError = Color.White,
)

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
fun JuntaFirmaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JuntaColorScheme,
        typography = JuntaTypography,
        shapes = JuntaShapes,
        content = content,
    )
}

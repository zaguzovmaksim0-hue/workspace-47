package dev.junta.firmamobile.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Physical tokens of the "F" device: the plastic body and its recesses, the inserted card,
 * the dark display, the three key families and the status LEDs. Fixed sRGB, like the palette.
 */
@Immutable
@Suppress("LongParameterList") // One flat token table keeps both variants side by side.
internal class FirmaColors(
    val bodyTop: Color,
    val bodyBottom: Color,
    val recess: Color,
    val recessShade: Color,
    val recessLight: Color,
    val card: Color,
    val cardEdge: Color,
    val cardInk: Color,
    val cardMuted: Color,
    val cardShade: Color,
    val chip: Color,
    val chipLine: Color,
    val slot: Color,
    val slotLip: Color,
    val display: Color,
    val displayText: Color,
    val displayMuted: Color,
    val displayOff: Color,
    val displayAccent: Color,
    val keyGraphite: Color,
    val keyGraphiteDepth: Color,
    val keyGraphiteInk: Color,
    val keyLight: Color,
    val keyLightDepth: Color,
    val keyLightInk: Color,
    val keyOrange: Color,
    val keyOrangeDepth: Color,
    val keyOrangeInk: Color,
    val keyCaption: Color,
    val accent: Color,
    val accentDepth: Color,
    val led: Color,
    val ledOff: Color,
    val ledBusy: Color,
    val ledAlert: Color,
    val cautionInk: Color,
)

@Suppress("MagicNumber") // Fixed sRGB device tokens.
internal val FirmaLightColors = FirmaColors(
    bodyTop = Color(0xFFEAE8E3),
    bodyBottom = Color(0xFFE3E1DB),
    recess = Color(0xFFD8D5CE),
    recessShade = Color(0x29000000),
    recessLight = Color(0xCCFFFFFF),
    card = Color(0xFFFBFAF6),
    cardEdge = Color(0xFFE2DFD8),
    cardInk = Color(0xFF2B2B2B),
    cardMuted = Color(0xFF6E6B64),
    cardShade = Color(0x1A000000),
    chip = Color(0xFFDCD5C4),
    chipLine = Color(0xFFBFB6A1),
    slot = Color(0xFF1C1B19),
    slotLip = Color(0xFF3A3935),
    display = Color(0xFF22262A),
    displayText = Color(0xFFEDEAE3),
    displayMuted = Color(0xFF959A9F),
    displayOff = Color(0xFF3A4046),
    displayAccent = Color(0xFFF06A30),
    keyGraphite = Color(0xFF34332F),
    keyGraphiteDepth = Color(0xFF1C1B19),
    keyGraphiteInk = Color(0xFFF4F2ED),
    keyLight = Color(0xFFF4F2ED),
    keyLightDepth = Color(0xFFC4C0B8),
    keyLightInk = Color(0xFF2B2B2B),
    keyOrange = Color(0xFFC94C17),
    keyOrangeDepth = Color(0xFF8F3410),
    keyOrangeInk = Color(0xFFFFFFFF),
    keyCaption = Color(0xFF55524C),
    accent = Color(0xFFE2571E),
    accentDepth = Color(0xFFB8441A),
    led = Color(0xFF3E9F4A),
    ledOff = Color(0xFFB9B5AD),
    ledBusy = Color(0xFFE0A030),
    ledAlert = Color(0xFFD9402B),
    cautionInk = Color(0xFF85590C),
)

@Suppress("MagicNumber") // Fixed sRGB device tokens.
internal val FirmaDarkColors = FirmaColors(
    bodyTop = Color(0xFF222321),
    bodyBottom = Color(0xFF1B1C1A),
    recess = Color(0xFF151614),
    recessShade = Color(0x73000000),
    recessLight = Color(0x14FFFFFF),
    card = Color(0xFFE6E3DC),
    cardEdge = Color(0xFFCFCBC3),
    cardInk = Color(0xFF2B2B2B),
    cardMuted = Color(0xFF5F5C56),
    cardShade = Color(0x24000000),
    chip = Color(0xFFD3CCBA),
    chipLine = Color(0xFFAFA690),
    slot = Color(0xFF0B0B0A),
    slotLip = Color(0xFF2E2F2B),
    display = Color(0xFF111315),
    displayText = Color(0xFFEDEAE3),
    displayMuted = Color(0xFF8B9095),
    displayOff = Color(0xFF2A2F34),
    displayAccent = Color(0xFFF06A30),
    keyGraphite = Color(0xFF3A3A36),
    keyGraphiteDepth = Color(0xFF0E0E0D),
    keyGraphiteInk = Color(0xFFECE9E2),
    keyLight = Color(0xFF3F403B),
    keyLightDepth = Color(0xFF121311),
    keyLightInk = Color(0xFFECE9E2),
    keyOrange = Color(0xFFC94C17),
    keyOrangeDepth = Color(0xFF6E270B),
    keyOrangeInk = Color(0xFFFFFFFF),
    keyCaption = Color(0xFFB5B1A8),
    accent = Color(0xFFE2571E),
    accentDepth = Color(0xFF8F3410),
    led = Color(0xFF4CBF5A),
    ledOff = Color(0xFF4A4B46),
    ledBusy = Color(0xFFE9AE3C),
    ledAlert = Color(0xFFFF6B55),
    cautionInk = Color(0xFFE9AE3C),
)

internal val LocalFirmaColors = staticCompositionLocalOf { FirmaLightColors }

/** Device tokens bound by [JuntaFirmaTheme]; light outside a theme, like the palette. */
internal val Firma: FirmaColors
    @Composable
    @ReadOnlyComposable
    get() = LocalFirmaColors.current

package dev.junta.firmamobile.ui.theme

import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePaletteTest {
    @Test
    fun api26Through30AlwaysUseTheMatchingStaticFallback() {
        for (sdk in 26..30) {
            for (dynamic in listOf(false, true)) {
                assertEquals(JuntaThemeVariant.LIGHT, selectJuntaTheme(false, dynamic, sdk))
                assertEquals(JuntaThemeVariant.DARK, selectJuntaTheme(true, dynamic, sdk))
                assertFalse(selectJuntaTheme(false, dynamic, sdk).dynamic)
                assertFalse(selectJuntaTheme(true, dynamic, sdk).dynamic)
            }
        }
    }

    @Test
    fun api31EnablesDynamicLightOnlyWhenRequested() {
        assertEquals(JuntaThemeVariant.DYNAMIC_LIGHT, selectJuntaTheme(false, true, 31))
        assertTrue(selectJuntaTheme(false, true, 31).dynamic)
    }

    @Test
    fun api31EnablesDynamicDarkOnlyWhenRequested() {
        assertEquals(JuntaThemeVariant.DYNAMIC_DARK, selectJuntaTheme(true, true, 31))
        assertTrue(selectJuntaTheme(true, true, 31).dynamic)
    }

    @Test
    fun dynamicOptOutKeepsStaticPalettesOnSupportedApis() {
        for (sdk in listOf(31, 32, 36)) {
            assertEquals(JuntaThemeVariant.LIGHT, selectJuntaTheme(false, false, sdk))
            assertEquals(JuntaThemeVariant.DARK, selectJuntaTheme(true, false, sdk))
        }
    }

    @Test
    fun lightFallbackPreservesTheExistingBrandTokens() {
        assertEquals(
            listOf(0xFFF2E5D1, 0xFFF8EDDB, 0xFF101310, 0xFF125854,
                0xFF14736B, 0xFF073C3A, 0xFF4F524C, 0xFFB8AD9B),
            with(JuntaLightPalette) {
                listOf(paper, paperElevated, ink, teal, tealBright, tealDark, mutedInk, hairline)
            },
        )
    }

    @Test
    fun darkFallbackChangesBothBackgroundAndForeground() {
        assertNotEquals(JuntaLightPalette.paper, JuntaDarkPalette.paper)
        assertNotEquals(JuntaLightPalette.ink, JuntaDarkPalette.ink)
        assertTrue(themeLuminance(JuntaDarkPalette.paper) < 0.05)
        assertTrue(themeLuminance(JuntaDarkPalette.ink) > 0.75)
    }

    @Test
    fun lightFallbackNormalTextAndButtonLabelsMeetAaContrast() {
        assertJuntaTextContrast(JuntaLightPalette)
    }

    @Test
    fun darkFallbackNormalTextAndButtonLabelsMeetAaContrast() {
        assertJuntaTextContrast(JuntaDarkPalette)
    }

    @Test
    fun fallbackTokensAreOpaqueSrgbValues() {
        for (palette in listOf(JuntaLightPalette, JuntaDarkPalette)) {
            with(palette) {
                listOf(paper, paperElevated, ink, teal, tealBright, tealDark, mutedInk,
                    hairline, primaryContainer, onPrimaryContainer, surfaceVariant, error, onError)
                    .forEach { assertEquals(0xFFL, it ushr 24) }
            }
        }
    }
}

/** WCAG relative luminance, not a comparison against the production selector or renderer. */
internal fun themeLuminance(argb: Long): Double {
    fun channel(shift: Int): Double {
        val value = ((argb shr shift) and 0xFF) / 255.0
        return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}

internal fun themeContrastRatio(foreground: Long, background: Long): Double {
    val a = themeLuminance(foreground)
    val b = themeLuminance(background)
    return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
}

internal fun assertJuntaTextContrast(palette: JuntaPalette) {
    with(palette) {
        val pairs = buildList {
            for (background in listOf(paper, paperElevated)) {
                for (foreground in listOf(ink, mutedInk, teal, tealBright, tealDark)) {
                    add(foreground to background)
                }
            }
            for (button in listOf(ink, teal, tealBright, tealDark)) add(paperElevated to button)
            add(mutedInk to surfaceVariant)
            add(onPrimaryContainer to primaryContainer)
            add(onError to error)
        }
        for ((foreground, background) in pairs) {
            val ratio = themeContrastRatio(foreground, background)
            assertTrue("Normal text contrast $ratio is below 4.5:1", ratio >= 4.5)
        }
    }
}

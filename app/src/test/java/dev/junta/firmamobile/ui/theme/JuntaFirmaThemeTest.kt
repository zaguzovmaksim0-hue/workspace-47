package dev.junta.firmamobile.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import dev.junta.firmamobile.testing.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class JuntaFirmaThemeTest {
    @get:Rule
    val rule = createComposeRule()
    private lateinit var snapshot: Snapshot

    @Test
    @Config(sdk = [30], qualifiers = "notnight")
    fun systemLightUsesStaticFallbackBelowApi31() {
        rule.setContent { JuntaFirmaTheme { ThemeProbe() } }
        rule.runOnIdle {
            assertSame(JuntaLightColorScheme, snapshot.scheme)
            assertEquals(JuntaLightPalette, snapshot.palette)
            assertBoundColorsAndContrast()
        }
    }

    @Test
    @Config(sdk = [30], qualifiers = "night")
    fun systemDarkUsesStaticFallbackBelowApi31() {
        rule.setContent { JuntaFirmaTheme { ThemeProbe() } }
        rule.runOnIdle {
            assertSame(JuntaDarkColorScheme, snapshot.scheme)
            assertEquals(JuntaDarkPalette, snapshot.palette)
            assertBoundColorsAndContrast()
        }
    }

    @Test
    @Config(sdk = [31], qualifiers = "notnight")
    fun supportedSystemLightUsesPlatformDynamicColors() {
        rule.setContent { JuntaFirmaTheme { ThemeProbe() } }
        rule.runOnIdle {
            val expected = dynamicLightColorScheme(RuntimeEnvironment.getApplication())
            assertEquals(expected.primary, snapshot.scheme.primary)
            assertEquals(expected.background, snapshot.scheme.background)
            assertEquals(expected.onSurface, snapshot.scheme.onSurface)
            assertBoundColorsAndContrast()
        }
    }

    @Test
    @Config(sdk = [31], qualifiers = "night")
    fun supportedSystemDarkUsesPlatformDynamicColors() {
        rule.setContent { JuntaFirmaTheme { ThemeProbe() } }
        rule.runOnIdle {
            val expected = dynamicDarkColorScheme(RuntimeEnvironment.getApplication())
            assertEquals(expected.primary, snapshot.scheme.primary)
            assertEquals(expected.background, snapshot.scheme.background)
            assertEquals(expected.onSurface, snapshot.scheme.onSurface)
            assertBoundColorsAndContrast()
        }
    }

    @Test
    @Config(sdk = [31], qualifiers = "notnight")
    fun explicitDarkAndDynamicOptOutUseTheStaticDarkPalette() {
        rule.setContent { juntaFirmaTheme(darkTheme = true, dynamicColor = false) { ThemeProbe() } }
        rule.runOnIdle {
            assertSame(JuntaDarkColorScheme, snapshot.scheme)
            assertEquals(JuntaDarkPalette, snapshot.palette)
            assertBoundColorsAndContrast()
        }
    }

    @Test
    @Config(sdk = [30], qualifiers = "notnight")
    fun changingThemeUpdatesCustomColorsWithoutStaleGlobalTokens() {
        var dark by mutableStateOf(false)
        rule.setContent { juntaFirmaTheme(darkTheme = dark, dynamicColor = false) { ThemeProbe() } }
        rule.runOnIdle {
            assertEquals(JuntaLightPalette, snapshot.palette)
            dark = true
        }
        rule.runOnIdle {
            assertSame(JuntaDarkColorScheme, snapshot.scheme)
            assertEquals(JuntaDarkPalette, snapshot.palette)
            assertBoundColorsAndContrast()
            dark = false
        }
        rule.runOnIdle {
            assertSame(JuntaLightColorScheme, snapshot.scheme)
            assertEquals(JuntaLightPalette, snapshot.palette)
            assertBoundColorsAndContrast()
        }
    }

    @Suppress("FunctionNaming")
    @Composable
    private fun ThemeProbe() {
        val observed = Snapshot(
            MaterialTheme.colorScheme,
            LocalJuntaPalette.current,
            listOf(JuntaPaper, JuntaPaperElevated, JuntaInk, JuntaTeal,
                JuntaTealBright, JuntaTealDark, JuntaMutedInk, JuntaHairline),
        )
        SideEffect { snapshot = observed }
        Box(Modifier.testTag("theme-probe"))
    }

    private fun assertBoundColorsAndContrast() {
        with(snapshot.palette) {
            assertEquals(listOf(paper, paperElevated, ink, teal, tealBright, tealDark, mutedInk, hairline),
                snapshot.customColors.map { it.argb() })
        }
        assertEquals(snapshot.scheme.primary, snapshot.scheme.surfaceTint)
        assertEquals(snapshot.scheme.background.argb(), snapshot.palette.paper)
        assertEquals(snapshot.scheme.surface.argb(), snapshot.palette.paperElevated)
        assertEquals(snapshot.scheme.onSurface.argb(), snapshot.palette.ink)
        assertJuntaTextContrast(snapshot.palette)
        with(snapshot.scheme) {
            listOf(onBackground to background, onSurface to surface,
                onSurfaceVariant to surfaceVariant, onPrimary to primary,
                onSecondary to secondary, onPrimaryContainer to primaryContainer, onError to error)
                .forEach { (foreground, background) ->
                    val ratio = themeContrastRatio(foreground.argb(), background.argb())
                    assertTrue("Material text contrast $ratio is below 4.5:1", ratio >= 4.5)
                }
        }
    }

    private fun Color.argb(): Long = toArgb().toLong() and 0xFFFFFFFFL

    private data class Snapshot(
        val scheme: ColorScheme,
        val palette: JuntaPalette,
        val customColors: List<Color>,
    )
}

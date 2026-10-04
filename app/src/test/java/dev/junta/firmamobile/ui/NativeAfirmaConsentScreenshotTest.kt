package dev.junta.firmamobile.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import dev.junta.firmamobile.testing.createComposeRule
import dev.junta.firmamobile.afirma.servlet.AfirmaConsentDetails
import dev.junta.firmamobile.afirma.servlet.AfirmaConsentPhase
import dev.junta.firmamobile.afirma.servlet.AfirmaConsentPrompt
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
import java.io.File
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

/** Actual Compose rendering with synthetic data only. Never uses a personal certificate. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-mdpi")
class NativeAfirmaConsentScreenshotTest {
    @get:Rule val rule = createComposeRule()
    @Test fun renderSyntheticConsentForReview() {
        val prompt = AfirmaConsentPrompt(
            UUID.randomUUID(),
            AfirmaConsentDetails("https://sede.ejemplo.es", "https://firma.ejemplo.es",
                "sign", "CAdES", "SHA256withRSA", 12480, "a".repeat(64)),
            AfirmaConsentPhase.REVIEW, "Certificado de ejemplo", null,
        )
        rule.setContent { JuntaFirmaTheme {
            NativeAfirmaConsentDialog(prompt, {}, {}, {}, {})
        } }
        rule.waitForIdle()
        val output = File("build/test-results/consent-preview/afirma-consent.png")
        output.parentFile.mkdirs()
        rule.runOnIdle {
            // PixelCopy requires a device compositor. Draw the real synthetic
            // dialog view through Robolectric native graphics instead.
            val view = WindowInspector.getGlobalWindowViews().last { it.width > 0 && it.height > 0 }
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        }
    }
}

package dev.junta.firmamobile.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
        output.outputStream().use {
            check(rule.onNodeWithTag("native-afirma-consent").captureToImage()
                .asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}

package dev.junta.firmamobile.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import dev.junta.firmamobile.R
import dev.junta.firmamobile.afirma.servlet.AfirmaConsentDetails
import dev.junta.firmamobile.afirma.servlet.AfirmaConsentPhase
import dev.junta.firmamobile.afirma.servlet.AfirmaConsentPrompt
import dev.junta.firmamobile.afirma.servlet.NativeMultiPhaseFormat
import dev.junta.firmamobile.certificate.CertificateDisplayNamePolicy
import dev.junta.firmamobile.signing.SigningOperationDescription
import dev.junta.firmamobile.testing.createComposeRule
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
import java.util.Locale
import java.util.UUID
import org.junit.Assert.assertEquals
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
@Config(sdk = [35])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class RemainingStringsI18nTest {
    @get:Rule val rule = createComposeRule()
    private data class Case(val language: String, val count: Int, val kind: NativeMultiPhaseFormat)

    @Test fun typedTextsResolveEveryLocaleAndKeepLiteralValues() {
        val selected = mutableStateOf(Case("es", 1, NativeMultiPhaseFormat.TRIPHASE))
        rule.setContent {
            val app = RuntimeEnvironment.getApplication()
            val config = Configuration(app.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(selected.value.language))
            }
            val context = app.createConfigurationContext(config)
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config) {
                Column {
                    Text(signingOperationText(SigningOperationDescription.Batch(selected.value.count)), Modifier.testTag("batch"))
                    Text(checkNotNull(nativeFormatText(details(selected.value.kind))), Modifier.testTag("format"))
                    Text(certificateDisplayNameText(CertificateDisplayNamePolicy.DEFAULT_DISPLAY_NAME), Modifier.testTag("fallback"))
                    Text(certificateDisplayNameText("identity.p12"), Modifier.testTag("name"))
                    Text(signingOperationText(SigningOperationDescription.Literal("Stable contract")), Modifier.testTag("literal"))
                }
            }
        }
        val names = mapOf("es" to "Certificado seleccionado", "en" to "Unnamed certificate",
            "ca" to "Certificat sense nom", "eu" to "Izenik gabeko ziurtagiria", "gl" to "Certificado sen nome")
        for ((language, name) in names) for (count in listOf(1, 2, 128)) for (kind in NativeMultiPhaseFormat.entries) {
            rule.runOnIdle { selected.value = Case(language, count, kind) }
            val app = RuntimeEnvironment.getApplication()
            val config = Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
            val context = app.createConfigurationContext(config)
            val batch = context.resources.getQuantityString(R.plurals.signing_batch_operation_description, count, count)
            val format = when (kind) {
                NativeMultiPhaseFormat.TRIPHASE -> context.getString(R.string.native_format_triphase, "CAdEStri")
                NativeMultiPhaseFormat.LOCAL_BATCH -> context.getString(R.string.native_format_batch_local)
                NativeMultiPhaseFormat.REMOTE_BATCH -> context.getString(R.string.native_format_batch_remote)
                NativeMultiPhaseFormat.LOCAL_CADES_COSIGN_BATCH -> context.getString(R.string.native_format_batch_local_cades_cosign)
            }
            if (language == "es") {
                assertEquals("Firma por lotes ($count ${if (count == 1) "documento" else "documentos"})", batch)
                assertEquals(when (kind) {
                    NativeMultiPhaseFormat.TRIPHASE -> "CAdEStri · trifásico"
                    NativeMultiPhaseFormat.LOCAL_BATCH -> "Batch · local"
                    NativeMultiPhaseFormat.REMOTE_BATCH -> "Batch · trifásico"
                    NativeMultiPhaseFormat.LOCAL_CADES_COSIGN_BATCH -> "Batch · local · CAdES co-sign"
                }, format)
            }
            rule.onNodeWithTag("batch").assertTextEquals(batch)
            rule.onNodeWithTag("format").assertTextEquals(format)
            rule.onNodeWithTag("fallback").assertTextEquals(name)
            rule.onNodeWithTag("name").assertTextEquals("identity.p12")
            rule.onNodeWithTag("literal").assertTextEquals("Stable contract")
        }
    }

    @Test fun typedBatchCosignStillShowsTheSafetyNotice() {
        val prompt = mutableStateOf(AfirmaConsentPrompt(UUID.randomUUID(),
            details(NativeMultiPhaseFormat.LOCAL_CADES_COSIGN_BATCH), AfirmaConsentPhase.REVIEW, "Synthetic owner", null))
        rule.setContent { JuntaFirmaTheme { NativeAfirmaConsentDialog(prompt.value, {}, {}, {}, {}) } }
        rule.onNodeWithTag("native-cades-cosign-notice").assertExists()
        rule.runOnIdle { prompt.value = prompt.value.copy(details = details(NativeMultiPhaseFormat.LOCAL_BATCH)) }
        rule.onNodeWithTag("native-cades-cosign-notice").assertDoesNotExist()
    }

    private fun details(kind: NativeMultiPhaseFormat) = AfirmaConsentDetails(
        sourceOrigin = "https://source.example", destination = "https://store.example/put",
        operation = if (kind == NativeMultiPhaseFormat.TRIPHASE) "sign" else "batch",
        format = if (kind == NativeMultiPhaseFormat.TRIPHASE) "CAdEStri" else "Batch",
        algorithm = "SHA256withRSA", payloadBytes = 3, payloadSha256 = null, multiPhaseFormat = kind,
    )
}

package dev.junta.firmamobile.i18n

import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.onNodeWithText
import dev.junta.firmamobile.R
import dev.junta.firmamobile.certificate.CertificateRepository
import dev.junta.firmamobile.certificate.StoredCertificateReference
import dev.junta.firmamobile.testing.createComposeRule
import dev.junta.firmamobile.ui.AppRoot
import dev.junta.firmamobile.ui.CertificateUiState
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
import org.w3c.dom.Element

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class RemainingStringsI18nRegressionTest {
    @get:Rule val rule = createComposeRule()

    @Test fun allNewKeysAreDeclaredInEveryLanguageInsteadOfFallingBackToSpanish() {
        val required = setOf("native_format_batch_local", "native_format_batch_remote",
            "native_format_batch_local_cades_cosign", "native_format_triphase", "certificate_default_name")
        val root = File("src/main/res").takeIf { it.isDirectory } ?: File("app/src/main/res")
        for (language in listOf("values", "values-ca", "values-en", "values-eu", "values-gl")) {
            val file = File(root, "$language/strings.xml")
            assertTrue(file.path, file.isFile)
            val factory = DocumentBuilderFactory.newInstance()
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            val document = factory.newDocumentBuilder().parse(file)
            val strings = document.getElementsByTagName("string")
            val names = (0 until strings.length).map { (strings.item(it) as Element).getAttribute("name") }.toSet()
            assertTrue("Missing keys in $language: ${required - names}", names.containsAll(required))
            val plurals = document.getElementsByTagName("plurals")
            val batch = (0 until plurals.length).map { plurals.item(it) as Element }
                .singleOrNull { it.getAttribute("name") == "signing_batch_operation_description" }
            assertNotNull("Missing batch plural in $language", batch)
            val items = checkNotNull(batch).getElementsByTagName("item")
            assertEquals(setOf("one", "other"),
                (0 until items.length).map { (items.item(it) as Element).getAttribute("quantity") }.toSet())
        }
    }

    @Test fun persistedFallbackNameIsRenderedInEnglishWithoutChangingStoredMetadata() {
        val app = RuntimeEnvironment.getApplication()
        val configuration = Configuration(app.resources.configuration).apply { setLocale(Locale.ENGLISH) }
        val context = app.createConfigurationContext(configuration)
        val reference = StoredCertificateReference(Uri.parse("content://documents/synthetic"),
            CertificateRepository.DEFAULT_DISPLAY_NAME, "application/x-pkcs12", null, null)
        rule.setContent {
            val registryOwner = checkNotNull(LocalActivityResultRegistryOwner.current)
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides configuration,
                LocalActivityResultRegistryOwner provides registryOwner,
            ) {
                JuntaFirmaTheme { AppRoot(CertificateUiState.Locked(reference, null, null)) }
            }
        }
        rule.onNodeWithText(context.getString(R.string.certificate_selected, "Unnamed certificate")).assertExists()
        assertEquals("Certificado seleccionado", reference.displayName)
    }
}

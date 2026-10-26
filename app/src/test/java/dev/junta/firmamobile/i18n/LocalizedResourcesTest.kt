package dev.junta.firmamobile.i18n

import android.content.Context
import android.content.res.Configuration
import dev.junta.firmamobile.R
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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
@Config(sdk = [28, 33])
class LocalizedResourcesTest {
    private val translatedLanguages = listOf("en", "ca", "gl", "eu")

    @Test
    fun ordinaryRobolectricFixturesUseExplicitSpanishBaseline() {
        val context = RuntimeEnvironment.getApplication()
        assertEquals("es", context.resources.configuration.locales[0].language)
        assertEquals("Selecciona tu archivo .p12 o .pfx.", context.getString(R.string.certificate_copy))
        assertNotEquals(context.getString(R.string.certificate_copy), localizedContext("en").getString(R.string.certificate_copy))
    }

    @Test
    fun supportedLanguagesResolveTranslatedSecurityAndExpirationText() {
        val spanish = localizedContext("es")
        for (language in translatedLanguages) {
            val context = localizedContext(language)
            assertEquals(language, context.resources.configuration.locales[0].language)
            for (id in listOf(
                R.string.signing_document_metadata_unavailable, R.string.certificate_expiration_soon_title,
                R.string.error_catalog_full, R.string.error_already_saved,
            )) {
                val translated = context.getString(id)
                assertTrue("Empty resource for $language", translated.isNotBlank())
                assertNotEquals("Spanish fallback for $language", spanish.getString(id), translated)
            }
        }
    }

    @Test
    fun translatedDocumentFormatsPreserveArguments() {
        for (language in translatedLanguages) {
            val context = localizedContext(language)
            val bytes = context.getString(R.string.signing_document_bytes, 4096)
            val digest = context.getString(R.string.signing_document_sha256, "synthetic-digest")
            assertTrue(bytes.contains("4096"))
            assertTrue(digest.contains("synthetic-digest"))
            assertTrue(digest.contains("SHA-256"))
            assertFalse(bytes.contains("%1"))
            assertFalse(digest.contains("%1"))
        }
    }

    @Test
    fun translatedExpirationPluralsPreserveCountAndTimestamp() {
        val timestamp = "2030-01-02T12:00Z"
        for (language in translatedLanguages) {
            val resources = localizedContext(language).resources
            val singular = resources.getQuantityString(R.plurals.certificate_expiration_days, 1, 1, timestamp)
            val plural = resources.getQuantityString(R.plurals.certificate_expiration_days, 2, 2, timestamp)
            assertTrue(singular.contains("1"))
            assertTrue(plural.contains("2"))
            assertTrue(singular.contains(timestamp))
            assertTrue(plural.contains(timestamp))
            assertNotEquals(singular, plural)
        }
    }

    @Test
    fun unprovidedLanguageRetainsSpanishDefault() {
        val spanish = localizedContext("es").getString(R.string.signing_document_metadata_unavailable)
        val unsupported = localizedContext("fr").getString(R.string.signing_document_metadata_unavailable)
        assertEquals(spanish, unsupported)
    }

    private fun localizedContext(language: String): Context {
        val application = RuntimeEnvironment.getApplication()
        val configuration = Configuration(application.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(language))
        return application.createConfigurationContext(configuration)
    }
}

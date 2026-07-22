package dev.junta.firmamobile.catalog

import androidx.test.core.app.ApplicationProvider
import dev.junta.firmamobile.R
import dev.junta.firmamobile.profile.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class PublicPortalCatalogParserTest {
    private val json by lazy {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.resources.openRawResource(R.raw.public_portal_catalog_v1)
            .bufferedReader().use { it.readText() }
    }

    @Test
    fun `bundled catalog contains the complete inventory without expanding trust bindings`() {
        val catalog = PublicPortalCatalogParser.parse(json)

        assertEquals(1, catalog.schemaVersion)
        val inventoryCount = catalog.entries.count { it.inventoryId != null }
        assertTrue(inventoryCount >= 180)
        assertEquals(inventoryCount + 2, catalog.entries.size)
        assertEquals(6, catalog.entries.count { it.profileId != null })
        assertEquals(catalog.entries.size, catalog.entries.map { it.portalId }.toSet().size)
        assertEquals(catalog.entries.size, catalog.entries.map { it.entryUrl }.toSet().size)
        assertEquals(
            setOf(
                ProfileId("junta-andalucia"),
                ProfileId("reg-age-redsara"),
                ProfileId("unizar-tramitador"),
                ProfileId("carne-joven-andalucia"),
                ProfileId("junta-ofvirtual"),
                ProfileId("educacion-convocatoria"),
            ),
            catalog.entries.mapNotNull { it.profileId }.toSet(),
        )
        assertTrue(catalog.entries.count { it.catalogStatus == PublicCatalogStatus.DISCOVERED } >= 70)
        assertTrue(catalog.entries.count { it.inventoryStatus == PortalInventoryStatus.BROWSE_ONLY } >= 160)
    }

    @Test
    fun `metadata-only portal records observed mechanisms but grants no profile binding`() {
        val catalog = PublicPortalCatalogParser.parse(json)
        val aeat = catalog.entries.single { it.portalId == PortalId("aeat-sede") }

        assertNull(aeat.profileId)
        assertEquals(PortalInventoryStatus.BROWSE_ONLY, aeat.inventoryStatus)
        assertTrue(PortalMechanism.CERTIFICATE_ACCESS in aeat.observedMechanisms)
        assertFalse(aeat.observedSignatureFormats.isNotEmpty())
    }

    @Test
    fun `strict parser rejects unknown duplicate insecure and malformed records`() {
        listOf(
            json.replaceFirst("\"schemaVersion\": 1", "\"schemaVersion\": 1, \"unknown\": true"),
            json.replaceFirst("\"catalogVersion\": 1", "\"catalogVersion\": 1, \"catalogVersion\": 1"),
            json.replaceFirst("https://sede.administracion.gob.es/", "http://sede.administracion.gob.es/"),
            json.replaceFirst("https://sede.administracion.gob.es/", "https://user@sede.administracion.gob.es/"),
            json.replaceFirst("https://sede.administracion.gob.es/", "https://sede.administracion.gob.es/#fragment"),
            json.replaceFirst("https://sede.administracion.gob.es/", "https://sede.administracion.gob.es:443/"),
            json.replaceFirst("https://sede.administracion.gob.es/", "https://sede.administracion.gob.es/a/../"),
            json.replaceFirst("\"portalId\": \"age-pag-reg\"", "\"portalId\": \"INVALID\""),
            json.replaceFirst("\"portalId\": \"age-reg-redsara\"", "\"portalId\": \"age-pag-reg\""),
            json.replaceFirst(
                "\"profileId\": \"unizar-tramitador\"",
                "\"profileId\": \"reg-age-redsara\"",
            ),
            json.replaceFirst("\"catalogStatus\": \"CATALOGED\"", "\"catalogStatus\": \"IMPLEMENTED\""),
        ).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) {
                PublicPortalCatalogParser.parse(invalid)
            }
        }

        assertThrows(IllegalArgumentException::class.java) {
            PublicPortalCatalogParser.parse(
                json + " ".repeat(PublicPortalCatalogParser.MAX_CATALOG_CHARS - json.length + 1),
            )
        }
    }
}

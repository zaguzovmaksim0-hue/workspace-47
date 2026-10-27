package dev.junta.firmamobile.browser

import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ExactOrigin
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SiteProfileCatalogParser
import dev.junta.firmamobile.signing.AccedaPadesAdapter
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// ACCEDA bridge/signing facts agree with the catalogue, while parser pins remain independent.
class AccedaOriginSourceTest {
    @Test
    fun signingFactsAgreeWithTheIndependentlyValidatedCatalog() {
        val catalog = SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON)
        val profile = catalog.profiles.single { it.profileId == ProfileId(PROFILE_ID) }

        assertEquals("sede.administracionespublicas.gob.es", HOST)
        assertEquals("https://sede.administracionespublicas.gob.es", ORIGIN)
        assertEquals(
            "https://sede.administracionespublicas.gob.es/certificado/info/idp/82/ida/0/language/es_ES",
            START_URL,
        )
        assertEquals(setOf(ExactOrigin.parse(ORIGIN)), profile.initiatorOrigins)
        assertEquals(START_URL, profile.startUrl.toASCIIString())
        assertEquals(HOST, URI(ORIGIN).host)
        assertEquals(HOST, profile.startUrl.host)
    }

    @Test
    fun parserRejectsCoherentHostRebindingInsteadOfTrustingTheCatalogAlone() {
        val original = BuiltInSiteProfiles.JSON
        val mutated = original.replace(HOST, "unreviewed.example.es")
        assertTrue(original != mutated)
        assertRejected(mutated, "coherent host rebinding")
    }

    @Test
    fun parserRejectsChangedOrNoncanonicalInitiatorOrigins() {
        val invalidOrigins = listOf(
            "https://evil.example",
            "https://sede.administracionespublicas.gob.es.evil.example",
            "http://sede.administracionespublicas.gob.es",
            "https://sede.administracionespublicas.gob.es:443",
            "https://sede.administracionespublicas.gob.es:8443",
            "https://sede.administracionespublicas.gob.es/extra",
            "https://user@sede.administracionespublicas.gob.es",
            "https://sede.administracionespublicas.gob.es?extra=1",
            "https://sede.administracionespublicas.gob.es#extra",
        )
        invalidOrigins.forEach { origin ->
            assertRejected(replaceUniqueQuotedValue(ORIGIN, origin), origin)
        }
    }

    @Test
    fun parserRejectsChangedStartUrlsIncludingOtherwiseValidHttpsUrls() {
        val invalidPages = listOf(
            "https://sede.administracionespublicas.gob.es/certificado/info/idp/82/ida/0/language/en_ES",
            "$START_URL/extra",
            "$START_URL?extra=1",
            START_URL.replace("https://", "http://"),
            START_URL.replace(HOST, "$HOST:443"),
            START_URL.replace(HOST, "$HOST:8443"),
        )
        invalidPages.forEach { page ->
            val original = BuiltInSiteProfiles.JSON
            val mutated = original.replace(START_URL, page)
            assertTrue(original != mutated)
            assertRejected(mutated, page)
        }
    }

    private fun replaceUniqueQuotedValue(original: String, replacement: String): String {
        val json = BuiltInSiteProfiles.JSON
        val quoted = "\"$original\""
        assertEquals(1, Regex(Regex.escape(quoted)).findAll(json).count())
        return json.replace(quoted, "\"$replacement\"")
    }

    private fun assertRejected(json: String, label: String) {
        val failure = runCatching { SiteProfileCatalogParser.parse(json) }.exceptionOrNull()
        assertTrue(label, failure is IllegalArgumentException)
    }

    private companion object {
        const val PROFILE_ID = AccedaPadesAdapter.PROFILE_ID
        const val HOST = "sede.administracionespublicas.gob.es"
        const val ORIGIN = AccedaPadesAdapter.INITIATOR_ORIGIN
        const val START_URL = AccedaPadesAdapter.START_URL
    }
}

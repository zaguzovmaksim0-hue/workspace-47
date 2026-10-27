package dev.junta.firmamobile.browser

import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ExactOrigin
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SiteProfileCatalogParser
import dev.junta.firmamobile.signing.PoliciaXadesDetachedAdapter
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Bridge facts agree with the catalogue, while parser pins remain independent of the bridge.
class PoliciaOriginSourceTest {
    @Test
    fun bridgeFactsAgreeWithTheIndependentlyValidatedCatalog() {
        val catalog = SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON)
        val profile = catalog.profiles.single { it.profileId == ProfileId(PROFILE_ID) }

        assertEquals(HOST, HOST_FROM_ORIGIN)
        assertEquals(ORIGIN, ORIGIN_FROM_SIGNING)
        assertEquals(START_URL, START_URL_FROM_SIGNING)
        assertEquals(PROCEDURE_PAGE, PROCEDURE_PAGE_FROM_SIGNING)
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
            "https://sede.policia.gob.es.evil.example",
            "http://sede.policia.gob.es",
            "https://sede.policia.gob.es:443",
            "https://sede.policia.gob.es:8443",
            "https://sede.policia.gob.es/extra",
            "https://user@sede.policia.gob.es",
            "https://sede.policia.gob.es?extra=1",
            "https://sede.policia.gob.es#extra",
        )
        invalidOrigins.forEach { origin ->
            assertRejected(replaceUniqueQuotedValue(ORIGIN, origin), origin)
        }
    }

    @Test
    fun parserRejectsChangedStartUrlsIncludingOtherwiseValidHttpsUrls() {
        val invalidPages = listOf(
            "https://sede.policia.gob.es/portalCiudadano/_es/other.xhtml",
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
        const val PROFILE_ID = PoliciaXadesDetachedAdapter.PROFILE_ID
        private val HOST = PoliciaXadesDetachedAdapter.HOST
        const val ORIGIN = PoliciaXadesDetachedAdapter.ORIGIN
        const val START_URL = PoliciaXadesDetachedAdapter.START_URL
        const val PROCEDURE_PAGE = PoliciaXadesDetachedAdapter.PROCEDURE_PAGE
        private val HOST_FROM_ORIGIN = URI(ORIGIN).host
        const val ORIGIN_FROM_SIGNING = "https://sede.policia.gob.es"
        const val START_URL_FROM_SIGNING = "https://sede.policia.gob.es/"
        const val PROCEDURE_PAGE_FROM_SIGNING =
            "https://sede.policia.gob.es/portalCiudadano/_es/solicitudGenerica.xhtml"
    }
}

package dev.junta.firmamobile.browser

import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ExactOrigin
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SiteProfileCatalogParser
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Bridge facts agree with the catalogue, while parser pins remain independent of the bridge.
class IsciiiCertificateSelectionOriginSourceTest {
    @Test
    fun bridgeFactsAgreeWithTheIndependentlyValidatedCatalog() {
        val catalog = SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON)
        val profile = catalog.profiles.single { it.profileId == ProfileId(PROFILE_ID) }

        assertEquals("sede.isciii.gob.es", HOST)
        assertEquals("https://sede.isciii.gob.es", ORIGIN)
        assertEquals("https://sede.isciii.gob.es/cargaApplet.jsp?accion=generico&recurso.opcion=null", START_URL)
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
            "https://sede.isciii.gob.es.evil.example",
            "http://sede.isciii.gob.es",
            "https://sede.isciii.gob.es:443",
            "https://sede.isciii.gob.es:8443",
            "https://sede.isciii.gob.es/extra",
            "https://user@sede.isciii.gob.es",
            "https://sede.isciii.gob.es?extra=1",
            "https://sede.isciii.gob.es#extra",
        )
        invalidOrigins.forEach { origin ->
            assertRejected(replaceUniqueQuotedValue(ORIGIN, origin), origin)
        }
    }

    @Test
    fun parserRejectsChangedStartUrlsIncludingOtherwiseValidHttpsUrls() {
        val invalidPages = listOf(
            "https://sede.isciii.gob.es/other.jsp",
            "$START_URL/extra",
            "$START_URL&extra=1",
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
        const val PROFILE_ID = IsciiiCertificateSelectionBridgeAdapter.PROFILE_ID
        const val HOST = IsciiiCertificateSelectionBridgeAdapter.HOST
        const val ORIGIN = IsciiiCertificateSelectionBridgeAdapter.ORIGIN
        const val START_URL = IsciiiCertificateSelectionBridgeAdapter.START_URL
    }
}

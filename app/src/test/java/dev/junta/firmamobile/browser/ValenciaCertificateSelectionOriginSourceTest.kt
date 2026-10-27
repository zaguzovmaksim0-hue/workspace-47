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
class ValenciaCertificateSelectionOriginSourceTest {
    @Test
    fun bridgeFactsAgreeWithTheIndependentlyValidatedCatalog() {
        val catalog = SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON)
        val profile = catalog.profiles.single { it.profileId == ProfileId(PROFILE_ID) }

        assertEquals("portafirmas.dival.es", HOST)
        assertEquals("https://portafirmas.dival.es", ORIGIN)
        assertEquals("https://portafirmas.dival.es/signingpad/xhtml/login.xhtml", START_URL)
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
            "https://portafirmas.dival.es.evil.example",
            "http://portafirmas.dival.es",
            "https://portafirmas.dival.es:443",
            "https://portafirmas.dival.es:8443",
            "https://portafirmas.dival.es/extra",
            "https://user@portafirmas.dival.es",
            "https://portafirmas.dival.es?extra=1",
            "https://portafirmas.dival.es#extra",
        )
        invalidOrigins.forEach { origin ->
            assertRejected(replaceUniqueQuotedValue(ORIGIN, origin), origin)
        }
    }

    @Test
    fun parserRejectsChangedStartUrlsIncludingOtherwiseValidHttpsUrls() {
        val invalidPages = listOf(
            "https://portafirmas.dival.es/signingpad/xhtml/other.xhtml",
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
        const val PROFILE_ID = ValenciaCertificateSelectionBridgeAdapter.PROFILE_ID
        const val HOST = ValenciaCertificateSelectionBridgeAdapter.HOST
        const val ORIGIN = ValenciaCertificateSelectionBridgeAdapter.ORIGIN
        const val START_URL = ValenciaCertificateSelectionBridgeAdapter.START_URL
    }
}

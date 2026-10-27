package dev.junta.firmamobile.browser

import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ExactOrigin
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SiteProfileCatalogParser
import dev.junta.firmamobile.signing.CanariasCertificateLoginCadesAdapter
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Bridge facts agree with the catalogue, while parser pins remain independent of the bridge.
class CanariasOriginSourceTest {
    @Test
    fun bridgeFactsAgreeWithTheIndependentlyValidatedCatalog() {
        val catalog = SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON)
        val profile = catalog.profiles.single { it.profileId == ProfileId(PROFILE_ID) }

        assertEquals("sede.gobiernodecanarias.org", HOST)
        assertEquals("https://sede.gobiernodecanarias.org", ORIGIN)
        assertEquals("https://sede.gobiernodecanarias.org/sede/la_sede", START_URL)
        assertEquals("https://sede.gobiernodecanarias.org/sede/identificacion", SIGNING_PAGE_URL)
        assertEquals("https://sede.gobiernodecanarias.org/platino/servlet_afirma/SignatureService", SERVER_URL)
        assertEquals(setOf(ExactOrigin.parse(ORIGIN)), profile.initiatorOrigins)
        assertEquals(START_URL, profile.startUrl.toASCIIString())
        assertEquals(HOST, URI(ORIGIN).host)
        assertEquals(HOST, profile.startUrl.host)
        assertEquals(emptyMap<String, String>(), profile.endpoints)
        assertEquals(SERVER_URL, profile.operationPolicies.getValue(dev.junta.firmamobile.profile.ProtocolOperation.SIGN).fixedExtraProperties["serverUrl"])
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
            "https://sede.gobiernodecanarias.org.evil.example",
            "http://sede.gobiernodecanarias.org",
            "https://sede.gobiernodecanarias.org:443",
            "https://sede.gobiernodecanarias.org:8443",
            "https://sede.gobiernodecanarias.org/extra",
            "https://user@sede.gobiernodecanarias.org",
            "https://sede.gobiernodecanarias.org?extra=1",
            "https://sede.gobiernodecanarias.org#extra",
        )
        invalidOrigins.forEach { origin ->
            assertRejected(replaceUniqueQuotedValue(ORIGIN, origin), origin)
        }
    }

    @Test
    fun parserRejectsChangedStartUrlsIncludingOtherwiseValidHttpsUrls() {
        val invalidPages = listOf(
            "https://sede.gobiernodecanarias.org/sede/other",
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

    @Test
    fun parserRejectsChangedServerUrlFixedProperty() {
        val invalidServerUrls = listOf(
            "https://sede.gobiernodecanarias.org/platino/servlet_afirma/OtherService",
            "https://evil.example/platino/servlet_afirma/SignatureService",
            "$SERVER_URL/extra",
        )
        invalidServerUrls.forEach { serverUrl ->
            val original = BuiltInSiteProfiles.JSON
            val mutated = original.replace(SERVER_URL, serverUrl)
            assertTrue(original != mutated)
            assertRejected(mutated, serverUrl)
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
        const val PROFILE_ID = CanariasCertificateLoginCadesAdapter.PROFILE_ID
        const val HOST = "sede.gobiernodecanarias.org"
        const val ORIGIN = CanariasCertificateLoginCadesAdapter.INITIATOR_ORIGIN
        const val START_URL = CanariasCertificateLoginCadesAdapter.PUBLIC_START_URL
        const val SIGNING_PAGE_URL = CanariasCertificateLoginCadesAdapter.SIGNING_PAGE_URL
        const val SERVER_URL = CanariasCertificateLoginCadesAdapter.SERVER_URL
    }
}

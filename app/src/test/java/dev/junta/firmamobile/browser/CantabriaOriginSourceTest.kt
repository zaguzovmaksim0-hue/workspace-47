package dev.junta.firmamobile.browser

import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ExactOrigin
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SiteProfileCatalogParser
import dev.junta.firmamobile.signing.CantabriaRecCertificateLoginCadesAdapter
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Bridge origin facts agree with the catalogue, while parser pins remain independent.
class CantabriaOriginSourceTest {
    @Test
    fun bridgeFactsAgreeWithTheIndependentlyValidatedCatalog() {
        val catalog = SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON)
        val profile = catalog.profiles.single {
            it.profileId == ProfileId(PROFILE_ID)
        }

        assertEquals("rec.cantabria.es", HOST)
        assertEquals("https://rec.cantabria.es", ORIGIN)
        assertEquals("https://rec.cantabria.es/rec/bienvenida.htm", START_URL)
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
            "https://rec.cantabria.es.evil.example",
            "http://rec.cantabria.es",
            "https://rec.cantabria.es:443",
            "https://rec.cantabria.es:8443",
            "https://rec.cantabria.es/extra",
            "https://user@rec.cantabria.es",
            "https://rec.cantabria.es?extra=1",
            "https://rec.cantabria.es#extra",
        )
        invalidOrigins.forEach { origin ->
            assertRejected(replaceUniqueInitiatorOrigin(origin), origin)
        }
    }

    @Test
    fun parserRejectsChangedStartUrlsIncludingOtherwiseValidHttpsUrls() {
        val invalidPages = listOf(
            "https://rec.cantabria.es/rec/other.htm",
            "$START_URL/extra",
            "$START_URL?extra=1",
            START_URL.replace("https://", "http://"),
            START_URL.replace(HOST, "$HOST:443"),
            START_URL.replace(HOST, "$HOST:8443"),
        )
        invalidPages.forEach { page ->
            assertRejected(replaceUniqueStartUrl(page), page)
        }
    }

    @Test
    fun bridgeKeepsTheExactEmptyFiltersAndImplicitCadesContract() {
        assertEquals("filters=\nmode=implicit", EXTRA_PROPERTIES)
        assertEquals(40, CHALLENGE_BYTES)
    }

    private fun replaceUniqueInitiatorOrigin(replacement: String): String {
        val json = BuiltInSiteProfiles.JSON
        val block = profileBlock(PROFILE_ID)
        val originalField = "\"initiatorOrigins\": [\"$ORIGIN\"]"
        assertEquals(1, Regex(Regex.escape(originalField)).findAll(block).count())
        return json.replace(
            originalField,
            "\"initiatorOrigins\": [\"$replacement\"]",
        )
    }

    private fun replaceUniqueStartUrl(replacement: String): String {
        val json = BuiltInSiteProfiles.JSON
        val block = profileBlock(PROFILE_ID)
        val originalField = "\"startUrl\": \"$START_URL\""
        assertEquals(1, Regex(Regex.escape(originalField)).findAll(block).count())
        return json.replace(originalField, "\"startUrl\": \"$replacement\"")
    }

    private fun profileBlock(profileId: String): String {
        val json = BuiltInSiteProfiles.JSON
        val idIndex = json.indexOf("\"$profileId\"")
        assertTrue(idIndex >= 0)
        var start = json.lastIndexOf('{', idIndex)
        var depth = 0
        for (index in start until json.length) {
            when (json[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return json.substring(start, index + 1)
                }
            }
        }
        error("Unterminated profile block")
    }

    private fun assertRejected(json: String, label: String) {
        val failure = runCatching {
            SiteProfileCatalogParser.parse(json)
        }.exceptionOrNull()
        assertTrue(label, failure is IllegalArgumentException)
    }

    private companion object {
        const val PROFILE_ID = CantabriaRecCertificateLoginCadesAdapter.PROFILE_ID
        const val HOST = "rec.cantabria.es"
        const val ORIGIN = CantabriaRecCertificateLoginCadesAdapter.INITIATOR_ORIGIN
        const val START_URL = CantabriaRecCertificateLoginCadesAdapter.START_URL
        const val EXTRA_PROPERTIES =
            CantabriaRecCertificateLoginCadesAdapter.EXPECTED_EXTRA_PROPERTIES
        const val CHALLENGE_BYTES = CantabriaRecCertificateLoginCadesAdapter.CHALLENGE_BYTES
    }
}

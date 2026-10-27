package dev.junta.firmamobile.browser

import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ExactOrigin
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SiteProfileCatalogParser
import dev.junta.firmamobile.signing.MitesCertificateLoginCadesAdapter
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Bridge origin facts agree with the catalogue, while parser pins remain independent.
class MitesOriginSourceTest {
    @Test
    fun bridgeFactsAgreeWithTheIndependentlyValidatedCatalog() {
        val catalog = SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON)
        val profile = catalog.profiles.single {
            it.profileId == ProfileId(PROFILE_ID)
        }

        assertEquals("sede.mites.gob.es", HOST)
        assertEquals("https://sede.mites.gob.es", ORIGIN)
        assertEquals("https://sede.mites.gob.es/", START_URL)
        assertEquals("https://sede.mites.gob.es/auth", LOGIN_PAGE_URL)
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
            "https://sede.mites.gob.es.evil.example",
            "http://sede.mites.gob.es",
            "https://sede.mites.gob.es:443",
            "https://sede.mites.gob.es:8443",
            "https://sede.mites.gob.es/extra",
            "https://user@sede.mites.gob.es",
            "https://sede.mites.gob.es?extra=1",
            "https://sede.mites.gob.es#extra",
        )
        invalidOrigins.forEach { origin ->
            assertRejected(replaceUniqueProfileValue(ORIGIN, origin), origin)
        }
    }

    @Test
    fun parserRejectsChangedStartUrlsIncludingOtherwiseValidHttpsUrls() {
        val invalidPages = listOf(
            "https://sede.mites.gob.es/other",
            "$START_URL/extra",
            "$START_URL?extra=1",
            START_URL.replace("https://", "http://"),
            START_URL.replace(HOST, "$HOST:443"),
            START_URL.replace(HOST, "$HOST:8443"),
        )
        invalidPages.forEach { page ->
            assertRejected(replaceUniqueProfileValue(START_URL, page), page)
        }
    }

    @Test
    fun bridgeRejectsEveryNonAuthPageAndKeepsTheExactContract() {
        assertEquals("/auth", LOGIN_PAGE_URL.removePrefix(ORIGIN))
        assertEquals(10, CHALLENGE_BYTES)
        assertEquals(
            "mode=implicit\nfilters.1=" +
                "signingCert:;keyusage.nonrepudiation:true;nonexpired:",
            EXTRA_PROPERTIES,
        )
    }

    private fun replaceUniqueProfileValue(original: String, replacement: String): String {
        val json = BuiltInSiteProfiles.JSON
        val block = profileBlock(PROFILE_ID)
        val quoted = "\"$original\""
        assertEquals(1, Regex(Regex.escape(quoted)).findAll(block).count())
        return json.replace(quoted, "\"$replacement\"")
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
        const val PROFILE_ID = MitesCertificateLoginCadesAdapter.PROFILE_ID
        const val HOST = "sede.mites.gob.es"
        const val ORIGIN = MitesCertificateLoginCadesAdapter.INITIATOR_ORIGIN
        const val START_URL = MitesCertificateLoginCadesAdapter.START_URL
        const val LOGIN_PAGE_URL = MitesCertificateLoginCadesAdapter.LOGIN_PAGE_URL
        const val EXTRA_PROPERTIES =
            MitesCertificateLoginCadesAdapter.EXPECTED_EXTRA_PROPERTIES
        const val CHALLENGE_BYTES = MitesCertificateLoginCadesAdapter.CHALLENGE_BYTES
    }
}

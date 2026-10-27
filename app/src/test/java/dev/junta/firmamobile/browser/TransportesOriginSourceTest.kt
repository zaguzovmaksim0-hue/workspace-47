package dev.junta.firmamobile.browser

import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ExactOrigin
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SiteProfileCatalogParser
import dev.junta.firmamobile.signing.TransportesXadesEnvelopedAdapter
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// The Transportes bridge derives its URLs from its signing origin; the catalogue and the
// independent parser pin must continue to agree with that exact contract.
class TransportesOriginSourceTest {
    @Test
    fun bridgeFactsAgreeWithTheIndependentlyValidatedCatalog() {
        val catalog = SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON)
        val profile = catalog.profiles.single { it.profileId == ProfileId(PROFILE_ID) }

        assertEquals(HOST, URI(ORIGIN).host)
        assertEquals(ORIGIN, TransportesXadesEnvelopedAdapter.INITIATOR_ORIGIN)
        assertEquals(START_URL, TransportesXadesEnvelopedAdapter.START_URL)
        assertEquals(AUTH_PAGE_URL, TransportesXadesEnvelopedAdapter.AUTH_PAGE_URL)
        assertEquals(setOf(ExactOrigin.parse(ORIGIN)), profile.initiatorOrigins)
        assertEquals(START_URL, profile.startUrl.toASCIIString())
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
            "https://sede.transportes.gob.es.evil.example",
            "http://sede.transportes.gob.es",
            "https://sede.transportes.gob.es:443",
            "https://sede.transportes.gob.es:8443",
            "https://sede.transportes.gob.es/extra",
            "https://user@sede.transportes.gob.es",
            "https://sede.transportes.gob.es?extra=1",
            "https://sede.transportes.gob.es#extra",
        )
        invalidOrigins.forEach { origin ->
            assertRejected(replaceTransportesInitiatorOrigin(origin), origin)
        }
    }

    @Test
    fun parserRejectsChangedStartUrlsIncludingOtherwiseValidHttpsUrls() {
        val invalidPages = listOf(
            "https://sede.transportes.gob.es/MFOM.genericprocedure.web/?id=7003",
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
    fun sharedOriginDoesNotGrantSepesTheTransportesSigningContract() {
        val sepesProfile = SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON).profiles
            .single { it.profileId == ProfileId("sepes-transportes-public-complaints") }

        assertEquals(setOf(ExactOrigin.parse(ORIGIN)), sepesProfile.initiatorOrigins)
        assertTrue(sepesProfile.operationPolicies.keys.none { it.name == "SIGN" })
    }

    private fun replaceTransportesInitiatorOrigin(replacement: String): String {
        val json = BuiltInSiteProfiles.JSON
        val profileStart = json.indexOf("\"profileId\": \"$PROFILE_ID\"")
        assertTrue(profileStart >= 0)
        val profileEnd = json.indexOf("\"profileId\":", profileStart + 1).let { if (it < 0) json.length else it }
        val profile = json.substring(profileStart, profileEnd)
        val original = "\"initiatorOrigins\": [\"$ORIGIN\"]"
        assertEquals(1, Regex(Regex.escape(original)).findAll(profile).count())
        val mutatedProfile = profile.replace(
            original,
            "\"initiatorOrigins\": [\"$replacement\"]",
        )
        return json.substring(0, profileStart) + mutatedProfile + json.substring(profileEnd)
    }

    private fun assertRejected(json: String, label: String) {
        val failure = runCatching { SiteProfileCatalogParser.parse(json) }.exceptionOrNull()
        assertTrue(label, failure is IllegalArgumentException)
    }

    private companion object {
        const val PROFILE_ID = TransportesXadesEnvelopedAdapter.PROFILE_ID
        const val HOST = "sede.transportes.gob.es"
        const val ORIGIN = TransportesXadesEnvelopedAdapter.INITIATOR_ORIGIN
        val START_URL = TransportesXadesEnvelopedAdapter.START_URL
        val AUTH_PAGE_URL = TransportesXadesEnvelopedAdapter.AUTH_PAGE_URL
    }
}

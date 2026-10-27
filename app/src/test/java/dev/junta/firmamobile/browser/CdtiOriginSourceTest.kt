package dev.junta.firmamobile.browser

import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ExactOrigin
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SiteProfileCatalogParser
import dev.junta.firmamobile.signing.CdtiXadesEnvelopingAdapter
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// CDTI bridge/signing facts agree with the catalogue, while parser pins remain independent.
class CdtiOriginSourceTest {
    @Test
    fun signingFactsAgreeWithTheIndependentlyValidatedCatalog() {
        val catalog = SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON)
        val profile = catalog.profiles.single { it.profileId == ProfileId(PROFILE_ID) }

        assertEquals("sede.cdti.gob.es", HOST)
        assertEquals("https://sede.cdti.gob.es", ORIGIN)
        assertEquals(
            "https://sede.cdti.gob.es/AreaPrivada/Expedientes/Common/Certificados/ValidarCertificado.aspx",
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
            "https://sede.cdti.gob.es.evil.example",
            "http://sede.cdti.gob.es",
            "https://sede.cdti.gob.es:443",
            "https://sede.cdti.gob.es:8443",
            "https://sede.cdti.gob.es/extra",
            "https://user@sede.cdti.gob.es",
            "https://sede.cdti.gob.es?extra=1",
            "https://sede.cdti.gob.es#extra",
        )
        invalidOrigins.forEach { origin ->
            assertRejected(replaceUniqueQuotedValue(ORIGIN, origin), origin)
        }
    }

    @Test
    fun parserRejectsChangedStartUrlsIncludingOtherwiseValidHttpsUrls() {
        val invalidPages = listOf(
            "https://sede.cdti.gob.es/AreaPrivada/Expedientes/Common/Certificados/Other.aspx",
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
        const val PROFILE_ID = CdtiXadesEnvelopingAdapter.PROFILE_ID
        const val HOST = "sede.cdti.gob.es"
        const val ORIGIN = CdtiXadesEnvelopingAdapter.INITIATOR_ORIGIN
        const val START_URL = CdtiXadesEnvelopingAdapter.START_URL
    }
}

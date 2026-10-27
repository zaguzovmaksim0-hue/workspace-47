package dev.junta.firmamobile.browser

import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ExactOrigin
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SiteProfileCatalogParser
import dev.junta.firmamobile.signing.EivissaCadesDetachedAdapter
import java.net.URI
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Bridge facts agree with the catalogue, while parser pins remain independent of the bridge.
class EivissaOriginSourceTest {
    @Test
    fun bridgeFactsAgreeWithTheIndependentlyValidatedCatalog() {
        val catalog = SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON)
        val profile = catalog.profiles.single { it.profileId == ProfileId(PROFILE_ID) }

        assertEquals(HOST, URI(ORIGIN).host)
        assertEquals(HOST, URI(START_URL).host)
        assertEquals(ORIGIN, EivissaCadesDetachedAdapter.INITIATOR_ORIGIN)
        assertEquals(START_URL, EivissaCadesDetachedAdapter.START_URL)
        assertEquals(setOf(ExactOrigin.parse(ORIGIN)), profile.initiatorOrigins)
        assertEquals(START_URL, profile.startUrl.toASCIIString())
    }

    @Test
    fun parserRejectsCoherentHostRebindingInsteadOfTrustingTheCatalogAlone() {
        val mutated = BuiltInSiteProfiles.JSON.replace(HOST, "unreviewed.example.es")
        assertTrue(mutated != BuiltInSiteProfiles.JSON)
        assertParserRejected(mutated, "coherent host rebinding")
    }

    @Test
    fun parserRejectsChangedOrNoncanonicalInitiatorOrigins() {
        listOf(
            "https://evil.example",
            "https://seu.conselldeivissa.es.evil.example",
            "http://seu.conselldeivissa.es",
            "https://seu.conselldeivissa.es:443",
            "https://seu.conselldeivissa.es:8443",
            "https://seu.conselldeivissa.es/extra",
            "https://user@seu.conselldeivissa.es",
            "https://seu.conselldeivissa.es?extra=1",
            "https://seu.conselldeivissa.es#extra",
        ).forEach { origin ->
            assertParserRejected(replaceUniqueQuotedValue(ORIGIN, origin), origin)
        }
    }

    @Test
    fun parserRejectsChangedStartUrlsIncludingOtherwiseValidHttpsUrls() {
        listOf(
            "https://seu.conselldeivissa.es/other",
            "$START_URL/extra",
            "$START_URL?extra=1",
            START_URL.replace("https://", "http://"),
            START_URL.replace(HOST, "$HOST:443"),
            START_URL.replace(HOST, "$HOST:8443"),
        ).forEach { page ->
            val original = BuiltInSiteProfiles.JSON
            val startUrlField = "\"startUrl\": \"$START_URL\""
            assertEquals(1, Regex(Regex.escape(startUrlField)).findAll(original).count())
            val mutated = original.replace(startUrlField, "\"startUrl\": \"$page\"")
            assertTrue(mutated != original)
            assertParserRejected(mutated, page)
        }
    }

    private fun eivissaMessage(): String {
        val certificate = Base64.getEncoder().encodeToString(ByteArray(48) { (it + 1).toByte() })
        val properties =
            "headless=true\nfilter=encodedcert:$certificate;filter=nonexpired:\nmode=implicit\n"
        return """{
            |"type":"MINIAPPLET_SIGN",
            |"documentId":"123e4567-e89b-42d3-a456-426614174001",
            |"requestId":"123e4567-e89b-42d3-a456-426614174000",
            |"dataB64":"${Base64.getEncoder().encodeToString("xsig".encodeToByteArray())}",
            |"algorithm":"SHA256withRSA",
            |"format":"CAdES",
            |"extraProperties":"$properties"
            |}
        """.trimMargin()
    }
    private fun replaceUniqueQuotedValue(original: String, replacement: String): String {
        val json = BuiltInSiteProfiles.JSON
        val quoted = "\"$original\""
        assertEquals(1, Regex(Regex.escape(quoted)).findAll(json).count())
        return json.replace(quoted, "\"$replacement\"")
    }

    private fun assertParserRejected(json: String, label: String) {
        val failure = runCatching { SiteProfileCatalogParser.parse(json) }.exceptionOrNull()
        assertTrue(label, failure is IllegalArgumentException)
    }

    private companion object {
        const val PROFILE_ID = EivissaCadesDetachedAdapter.PROFILE_ID
        private val HOST = URI(EivissaCadesDetachedAdapter.INITIATOR_ORIGIN).host
        const val ORIGIN = EivissaCadesDetachedAdapter.INITIATOR_ORIGIN
        private val START_URL = EivissaCadesDetachedAdapter.START_URL
        const val SIGNING_PAGE =
            "https://seu.conselldeivissa.es/sta/reg/tramite/${EivissaCadesDetachedAdapter.PROCEDURE_ID}/" +
                "formulario/summary/referencia/123e4567-e89b-42d3-a456-426614174002"
    }
}

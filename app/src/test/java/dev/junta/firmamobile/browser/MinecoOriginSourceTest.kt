package dev.junta.firmamobile.browser

import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ExactOrigin
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SiteProfileCatalogParser
import dev.junta.firmamobile.signing.MinecoPadesAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Bridge facts agree with the catalogue, while parser pins remain independent of the bridge.
class MinecoOriginSourceTest {
    @Test
    fun bridgeFactsAgreeWithTheIndependentlyValidatedCatalog() {
        val catalog = SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON)
        val profile = catalog.profiles.single { it.profileId == ProfileId(PROFILE_ID) }

        assertEquals("https://serviciosede.mineco.gob.es", ORIGIN)
        assertEquals("https://serviciosede.mineco.gob.es/FB/Home.aspx?control=161_IG", START_URL)
        assertEquals(ORIGIN, MinecoPadesAdapter.INITIATOR_ORIGIN)
        assertEquals(START_URL, MinecoPadesAdapter.PUBLIC_START_URL)
        assertEquals(setOf(ExactOrigin.parse(ORIGIN)), profile.initiatorOrigins)
        assertEquals(START_URL, profile.startUrl.toASCIIString())
        assertEquals("https://pasarela.clave.gob.es", MinecoPadesAdapter.CLAVE_BROWSE_ORIGIN)
        assertEquals("https://pasarela-ident.clave.gob.es", MinecoPadesAdapter.CLAVE_IDENT_BROWSE_ORIGIN)
        assertEquals(
            setOf(
                ExactOrigin.parse("https://pasarela.clave.gob.es"),
                ExactOrigin.parse("https://pasarela-ident.clave.gob.es"),
            ),
            profile.trustedBrowseOrigins,
        )
        assertEquals(MinecoPadesAdapter.TRUSTED_BROWSE_ORIGINS, profile.trustedBrowseOrigins)
    }

    @Test
    fun parserRejectsCoherentHostRebindingInsteadOfTrustingTheCatalogAlone() {
        val mutated = mutateProfileBlock { block ->
            val occurrences = Regex(Regex.escape(HOST)).findAll(block).count()
            assertTrue("Mineco host must occur in origin and URLs", occurrences >= 3)
            val replaced = block.replace(HOST, "unreviewed.example.es")
            assertEquals(0, Regex(Regex.escape(HOST)).findAll(replaced).count())
            replaced
        }
        assertRejected(mutated, "coherent host rebinding")
    }

    @Test
    fun parserRejectsChangedOrNoncanonicalInitiatorOrigins() {
        val invalidOrigins = listOf(
            "https://evil.example",
            "https://serviciosede.mineco.gob.es.evil.example",
            "http://serviciosede.mineco.gob.es",
            "https://serviciosede.mineco.gob.es:443",
            "https://serviciosede.mineco.gob.es:8443",
            "https://serviciosede.mineco.gob.es/extra",
            "https://user@serviciosede.mineco.gob.es",
            "https://serviciosede.mineco.gob.es?extra=1",
            "https://serviciosede.mineco.gob.es#extra",
        )
        invalidOrigins.forEach { origin ->
            assertRejected(
                replaceUniqueInProfile(
                    "\"initiatorOrigins\": [\"$ORIGIN\"]",
                    "\"initiatorOrigins\": [\"$origin\"]",
                ),
                origin,
            )
        }
    }

    @Test
    fun parserRejectsChangedStartUrlsIncludingOtherwiseValidHttpsUrls() {
        val invalidPages = listOf(
            "https://serviciosede.mineco.gob.es/FB/Home.aspx?control=161_OTHER",
            "$START_URL/extra",
            "$START_URL?extra=1",
            START_URL.replace("https://", "http://"),
            START_URL.replace(HOST, "$HOST:443"),
            START_URL.replace(HOST, "$HOST:8443"),
        )
        invalidPages.forEach { page ->
            assertRejected(
                replaceUniqueInProfile("\"startUrl\": \"$START_URL\"", "\"startUrl\": \"$page\""),
                page,
            )
        }
    }

    @Test
    fun parserRejectsTrustedBrowseOriginExtension() {
        val browseArray =
            "[\"https://pasarela.clave.gob.es\", \"https://pasarela-ident.clave.gob.es\"]"
        val extended =
            "[\"https://pasarela.clave.gob.es\", \"https://pasarela-ident.clave.gob.es\", " +
                "\"https://evil.example\"]"
        assertRejected(replaceUniqueInProfile(browseArray, extended), "browse origin extension")
    }

    private fun mutateProfileBlock(transform: (String) -> String): String {
        val json = BuiltInSiteProfiles.JSON
        val marker = "\"profileId\": \"$PROFILE_ID\""
        assertEquals(1, Regex(Regex.escape(marker)).findAll(json).count())
        val start = json.indexOf(marker)
        val endMarker = "\n    },"
        val end = json.indexOf(endMarker, start)
        assertTrue(end > start)
        val block = json.substring(start, end + endMarker.length)
        val mutatedBlock = transform(block)
        assertTrue(block != mutatedBlock)
        return json.replaceRange(start, end + endMarker.length, mutatedBlock)
    }

    private fun replaceUniqueInProfile(original: String, replacement: String): String =
        mutateProfileBlock { block ->
            assertEquals(1, Regex(Regex.escape(original)).findAll(block).count())
            block.replace(original, replacement)
        }

    private fun assertRejected(json: String, label: String) {
        val failure = runCatching { SiteProfileCatalogParser.parse(json) }.exceptionOrNull()
        assertTrue(label, failure is IllegalArgumentException)
    }

    private companion object {
        const val PROFILE_ID = MinecoPadesAdapter.PROFILE_ID
        const val HOST = "serviciosede.mineco.gob.es"
        const val ORIGIN = MinecoPadesAdapter.INITIATOR_ORIGIN
        const val START_URL = MinecoPadesAdapter.PUBLIC_START_URL
    }
}

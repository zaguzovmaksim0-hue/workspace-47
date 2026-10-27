package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ExactOrigin
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SiteProfileCatalogParser
import dev.junta.firmamobile.signing.GranCanariaPadesAdapter
import java.net.URI
import java.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

// Runtime consumers share one origin; the catalogue still has independent parser pins.
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class GranCanariaOriginSourceTest {
    @Test
    fun derivedUrlsKeepTheirExactObservedValues() {
        assertEquals("https://sede.grancanaria.com", GranCanariaPadesAdapter.INITIATOR_ORIGIN)
        assertEquals(
            "https://sede.grancanaria.com/sede-privado/instancia-general?inicio",
            GranCanariaPadesAdapter.PUBLIC_START_URL,
        )
        assertEquals(
            "https://sede.grancanaria.com/sede-privado/auth",
            GranCanariaPadesAdapter.SIGNING_PAGE_URL,
        )
    }

    @Test
    fun runtimeOriginAndStartUrlAgreeWithIndependentlyParsedCatalogue() {
        val profile = SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON).profiles.single {
            it.profileId == ProfileId(GranCanariaPadesAdapter.PROFILE_ID)
        }
        val origin = GranCanariaPadesAdapter.INITIATOR_ORIGIN
        assertEquals(setOf(ExactOrigin.parse(origin)), profile.initiatorOrigins)
        assertEquals(GranCanariaPadesAdapter.PUBLIC_START_URL, profile.startUrl.toASCIIString())
        assertEquals(URI(origin).host, profile.startUrl.host)
    }

    @Test
    fun parserRejectsCoordinatedOriginRebindingEvenWhenEveryProfileUrlAgrees() {
        listOf(
            "https://other.example",
            "https://sede.grancanaria.com.evil.example",
            "https://sede.grancanaria.com:444",
            "https://sede.grancanaria.com:443",
            "http://sede.grancanaria.com",
            "https://sede.grancanaria.com/extra",
            "https://sede.grancanaria.com?extra=1",
            "https://sede.grancanaria.com#extra",
            "https://user@sede.grancanaria.com",
        ).forEach { replacement ->
            assertRejected(replacement) { block ->
                block.replace("https://sede.grancanaria.com", replacement)
            }
        }
    }

    @Test
    fun parserRejectsStartPathQueryAndFragmentChanges() {
        listOf("/other", "/", "?extra=1", "?inicio=", "#extra").forEach { suffix ->
            assertRejected("start $suffix") { block ->
                block.replace(
                    GranCanariaPadesAdapter.PUBLIC_START_URL,
                    GranCanariaPadesAdapter.PUBLIC_START_URL + suffix,
                )
            }
        }
    }

    @Test
    fun bridgeRejectsForgedOriginsWhileKeepingContractChecks() {
        val pdf = "%PDF-1.4\n% synthetic Gran Canaria request\n".encodeToByteArray()
        val adapter = MiniAppletBridgeAdapter(
            activeProfileId = { ProfileId(GranCanariaPadesAdapter.PROFILE_ID) },
        )
        listOf(
            "https://other.example",
            "https://sede.grancanaria.com.evil.example",
            "https://sede.grancanaria.com:444",
            "http://sede.grancanaria.com",
            "https://user@sede.grancanaria.com",
        ).forEach { origin ->
            val result = adapter.route(
                rawMessage = message(pdf),
                sourceOrigin = Uri.parse(origin),
                isMainFrame = true,
                navigationEpoch = 9,
                currentPageUrl = GranCanariaPadesAdapter.SIGNING_PAGE_URL,
            )
            assertTrue(origin, result is MiniAppletBridgeRouteResult.Rejected)
        }
        pdf.fill(0)
    }

    private fun message(pdf: ByteArray): String = JSONObject()
        .put("type", "MINIAPPLET_SIGN")
        .put("documentId", "123e4567-e89b-42d3-a456-426614174001")
        .put("requestId", "123e4567-e89b-42d3-a456-426614174000")
        .put("dataB64", Base64.getEncoder().encodeToString(pdf))
        .put("algorithm", "SHA512withRSA")
        .put("format", "PAdES")
        .put("extraProperties", "headless=true\nfilters=nonexpired:")
        .toString()

    private fun assertRejected(label: String, transform: (String) -> String) {
        val json = BuiltInSiteProfiles.JSON
        val id = Regex.escape(GranCanariaPadesAdapter.PROFILE_ID)
        val start = Regex("\"profileId\"\\s*:\\s*\"$id\"").find(json)!!.range.first
        val end = Regex("\"profileId\"\\s*:").find(json, start + 1)?.range?.first ?: json.length
        val block = json.substring(start, end)
        val changed = transform(block)
        assertTrue("mutation must change Gran Canaria: $label", block != changed)
        try {
            SiteProfileCatalogParser.parse(json.substring(0, start) + changed + json.substring(end))
            fail("parser accepted $label")
        } catch (_: IllegalArgumentException) {
            // Independent parser pins must reject the whole catalogue.
        }
    }
}

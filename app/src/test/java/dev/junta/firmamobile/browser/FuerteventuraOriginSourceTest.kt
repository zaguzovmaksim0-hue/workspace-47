package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ExactOrigin
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SiteProfileCatalogParser
import dev.junta.firmamobile.signing.FuerteventuraPadesAdapter
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
class FuerteventuraOriginSourceTest {
    @Test
    fun derivedUrlsKeepTheirExactObservedValues() {
        assertEquals("https://sede.cabildofuer.es", FuerteventuraPadesAdapter.INITIATOR_ORIGIN)
        assertEquals(
            "https://sede.cabildofuer.es/eAdmin/Registrar.do?action=comenzar&tipoReg=1",
            FuerteventuraPadesAdapter.PUBLIC_START_URL,
        )
        assertEquals(
            "https://sede.cabildofuer.es/eAdmin/Registrar.do?action=verYfirmar&modo=cert",
            FuerteventuraPadesAdapter.SIGNING_PAGE_URL,
        )
    }

    @Test
    fun runtimeOriginStartAndSigningPageAgreeWithIndependentlyParsedCatalogue() {
        val profile = SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON).profiles.single {
            it.profileId == ProfileId(FuerteventuraPadesAdapter.PROFILE_ID)
        }
        val origin = FuerteventuraPadesAdapter.INITIATOR_ORIGIN
        assertEquals(setOf(ExactOrigin.parse(origin)), profile.initiatorOrigins)
        assertEquals(FuerteventuraPadesAdapter.PUBLIC_START_URL, profile.startUrl.toASCIIString())
        assertEquals(URI(origin).host, profile.startUrl.host)
        assertTrue(
            profile.evidence.any {
                it.url.toASCIIString() == FuerteventuraPadesAdapter.SIGNING_PAGE_URL
            },
        )
    }

    @Test
    fun parserRejectsCoordinatedOriginRebindingEvenWhenEveryProfileUrlAgrees() {
        listOf(
            "https://other.example",
            "https://sede.cabildofuer.es.evil.example",
            "https://sede.cabildofuer.es:444",
            "https://sede.cabildofuer.es:443",
            "http://sede.cabildofuer.es",
            "https://sede.cabildofuer.es/extra",
            "https://sede.cabildofuer.es?extra=1",
            "https://sede.cabildofuer.es#extra",
            "https://user@sede.cabildofuer.es",
        ).forEach { replacement ->
            assertRejected(replacement) { block ->
                block.replace("https://sede.cabildofuer.es", replacement)
            }
        }
    }

    @Test
    fun parserRejectsStartSigningPageQueryAndFragmentChanges() {
        listOf(
            FuerteventuraPadesAdapter.PUBLIC_START_URL to "/other",
            FuerteventuraPadesAdapter.PUBLIC_START_URL to "?extra=1",
            FuerteventuraPadesAdapter.PUBLIC_START_URL to "#extra",
            FuerteventuraPadesAdapter.SIGNING_PAGE_URL to "/other",
            FuerteventuraPadesAdapter.SIGNING_PAGE_URL to "?extra=1",
            FuerteventuraPadesAdapter.SIGNING_PAGE_URL to "#extra",
        ).forEach { (url, suffix) ->
            assertRejected("$url$suffix") { block -> block.replace(url, url + suffix) }
        }
    }

    @Test
    fun bridgeRejectsForgedOriginsWhileKeepingContractChecks() {
        val pdf = "%PDF-1.4\n% synthetic Fuerteventura request\n".encodeToByteArray()
        val adapter = MiniAppletBridgeAdapter(
            activeProfileId = { ProfileId(FuerteventuraPadesAdapter.PROFILE_ID) },
        )
        listOf(
            "https://other.example",
            "https://sede.cabildofuer.es.evil.example",
            "https://sede.cabildofuer.es:444",
            "http://sede.cabildofuer.es",
            "https://user@sede.cabildofuer.es",
        ).forEach { origin ->
            val result = adapter.route(
                rawMessage = message(pdf),
                sourceOrigin = Uri.parse(origin),
                isMainFrame = true,
                navigationEpoch = 9,
                currentPageUrl = FuerteventuraPadesAdapter.SIGNING_PAGE_URL,
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
        .put("algorithm", "SHA256withRSA")
        .put("format", "PAdES")
        .put("extraProperties", expectedProperties())
        .toString()

    private fun expectedProperties(): String = listOf(
        "signaturePositionOnPageLowerLeftX = 50",
        "signaturePositionOnPageLowerLeftY = 15",
        "signaturePositionOnPageUpperRightX = 150",
        "signaturePositionOnPageUpperRightY = 50",
        "signaturePages = all",
        "layer2Text= Firmado por \$\$SUBJECTCN\$\$ el día \$\$SIGNDATE=dd/MM/yyyy\$\$ \$\$ORGANIZATION\$\$",
        "layer2FontSize= 6",
        "layer2FontFamily= 0",
        "layer2FontStyle= 0",
        "signatureRotation= 0",
        "includeQuestionMark= false",
        "obfuscateCertText= true",
    ).joinToString(separator = "\n", postfix = "\n")

    private fun assertRejected(label: String, transform: (String) -> String) {
        val json = BuiltInSiteProfiles.JSON
        val id = Regex.escape(FuerteventuraPadesAdapter.PROFILE_ID)
        val start = Regex("\"profileId\"\\s*:\\s*\"$id\"").find(json)!!.range.first
        val end = Regex("\"profileId\"\\s*:").find(json, start + 1)?.range?.first ?: json.length
        val block = json.substring(start, end)
        val changed = transform(block)
        assertTrue("mutation must change Fuerteventura: $label", block != changed)
        try {
            SiteProfileCatalogParser.parse(json.substring(0, start) + changed + json.substring(end))
            fail("parser accepted $label")
        } catch (_: IllegalArgumentException) {
            // Independent parser pins must reject the whole catalogue.
        }
    }
}

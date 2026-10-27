package dev.junta.firmamobile.browser

import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ExactOrigin
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.ProtocolOperation
import dev.junta.firmamobile.profile.SiteProfileCatalogParser
import dev.junta.firmamobile.signing.XuntaPadesTriPhaseAdapter
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

// Runtime consumers share one origin; the catalogue still has independent parser pins.
class XuntaOriginSourceTest {
    @Test
    fun derivedUrlsAndBridgeHostKeepTheirExactObservedValues() {
        assertEquals("https://sede.xunta.gal", XuntaPadesTriPhaseAdapter.INITIATOR_ORIGIN)
        assertEquals("sede.xunta.gal", XuntaCertificateSelectionBridgeAdapter.HOST)
        assertEquals(
            "https://sede.xunta.gal/tramites-e-servizos/solicitude-xenerica",
            XuntaPadesTriPhaseAdapter.PUBLIC_START_URL,
        )
        assertEquals(
            "https://sede.xunta.gal/presenta/novo/PR004A_2025_1",
            XuntaPadesTriPhaseAdapter.SIGNING_PAGE_URL,
        )
        assertEquals(
            "https://sede.xunta.gal/presenta/sinatura/SignatureService",
            XuntaPadesTriPhaseAdapter.ENDPOINT,
        )
    }

    @Test
    fun runtimeOriginStartPageAndEndpointAgreeWithIndependentlyParsedCatalogue() {
        val profile = SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON).profiles.single {
            it.profileId == ProfileId(XuntaPadesTriPhaseAdapter.PROFILE_ID)
        }
        val origin = XuntaPadesTriPhaseAdapter.INITIATOR_ORIGIN
        assertEquals(URI(origin).host, XuntaCertificateSelectionBridgeAdapter.HOST)
        assertEquals(setOf(ExactOrigin.parse(origin)), profile.initiatorOrigins)
        assertEquals(XuntaPadesTriPhaseAdapter.PUBLIC_START_URL, profile.startUrl.toASCIIString())
        val sign = profile.operationPolicies.getValue(ProtocolOperation.SIGN)
        val endpoint = profile.endpoints.getValue(checkNotNull(sign.endpointId))
        assertEquals(XuntaPadesTriPhaseAdapter.ENDPOINT, endpoint.url.toASCIIString())
        assertEquals(XuntaPadesTriPhaseAdapter.ENDPOINT, sign.fixedExtraProperties["serverUrl"])
        assertEquals(origin, "${endpoint.url.scheme}://${endpoint.url.host}")
        assertTrue(profile.evidence.any { it.url.toASCIIString() == XuntaPadesTriPhaseAdapter.SIGNING_PAGE_URL })
    }

    @Test
    fun parserRejectsCoordinatedOriginRebindingEvenWhenEveryProfileUrlAgrees() {
        listOf(
            "https://other.example",
            "https://sede.xunta.gal.evil.example",
            "https://sede.xunta.gal:444",
            "https://sede.xunta.gal:443",
            "http://sede.xunta.gal",
            "https://sede.xunta.gal/extra",
            "https://sede.xunta.gal?extra=1",
            "https://sede.xunta.gal#extra",
            "https://user@sede.xunta.gal",
        ).forEach { replacement ->
            assertRejected(replacement) { block -> block.replace("https://sede.xunta.gal", replacement) }
        }
    }

    @Test
    fun parserRejectsSameOriginStartPathQueryAndFragmentChanges() {
        listOf("/other", "/", "?extra=1", "#extra").forEach { suffix ->
            assertRejected("start $suffix") { block ->
                block.replace(
                    XuntaPadesTriPhaseAdapter.PUBLIC_START_URL,
                    XuntaPadesTriPhaseAdapter.PUBLIC_START_URL + suffix,
                )
            }
        }
    }

    @Test
    fun parserRejectsEndpointAndServerUrlRebindingTogether() {
        listOf(
            "https://other.example/presenta/sinatura/SignatureService",
            "https://sede.xunta.gal/presenta/sinatura/OtherService",
            "https://sede.xunta.gal:444/presenta/sinatura/SignatureService",
            "http://sede.xunta.gal/presenta/sinatura/SignatureService",
        ).forEach { replacement ->
            assertRejected(replacement) { block -> block.replace(XuntaPadesTriPhaseAdapter.ENDPOINT, replacement) }
        }
    }

    @Test
    fun parserRejectsSigningPageEvidenceRebinding() {
        assertRejected("signing page") { block ->
            block.replace(XuntaPadesTriPhaseAdapter.SIGNING_PAGE_URL, XuntaPadesTriPhaseAdapter.SIGNING_PAGE_URL + "/other")
        }
    }

    private fun assertRejected(label: String, transform: (String) -> String) {
        val json = BuiltInSiteProfiles.JSON
        val id = Regex.escape(XuntaPadesTriPhaseAdapter.PROFILE_ID)
        val start = Regex("\"profileId\"\\s*:\\s*\"$id\"").find(json)!!.range.first
        val end = Regex("\"profileId\"\\s*:").find(json, start + 1)?.range?.first ?: json.length
        val block = json.substring(start, end)
        val changed = transform(block)
        assertTrue("mutation must change Xunta: $label", block != changed)
        try {
            SiteProfileCatalogParser.parse(json.substring(0, start) + changed + json.substring(end))
            fail("parser accepted $label")
        } catch (_: IllegalArgumentException) {
            // Independent parser pins must reject the whole catalogue.
        }
    }
}

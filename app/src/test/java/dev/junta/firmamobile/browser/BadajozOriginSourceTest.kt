package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ExactOrigin
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SiteProfileCatalogParser
import dev.junta.firmamobile.signing.DiputacionBadajozCadesAdapter
import java.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

// Bridge facts agree with the catalogue, while parser pins remain independent of the bridge.
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class BadajozOriginSourceTest {
    @Test
    fun bridgeFactsAgreeWithTheIndependentlyValidatedCatalog() {
        val catalog = SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON)
        val profile = catalog.profiles.single { it.profileId == ProfileId(PROFILE_ID) }

        assertEquals(HOST, URI_HOST)
        assertEquals(DiputacionBadajozCadesAdapter.INITIATOR_ORIGIN, ORIGIN)
        assertEquals(DiputacionBadajozCadesAdapter.START_URL, START_URL)
        assertEquals(DiputacionBadajozCadesAdapter.LOGIN_PAGE_URL, LOGIN_PAGE_URL)
        assertEquals(setOf(ExactOrigin.parse(ORIGIN)), profile.initiatorOrigins)
        assertEquals(START_URL, profile.startUrl.toASCIIString())
        assertEquals(HOST, profile.startUrl.host)
    }

    @Test
    fun parserRejectsCoherentHostRebindingInsteadOfTrustingTheCatalogAlone() {
        val mutated = BuiltInSiteProfiles.JSON.replace(HOST, "unreviewed.example.es")
        assertTrue(mutated != BuiltInSiteProfiles.JSON)
        assertRejected(mutated, "coherent host rebinding")
    }

    @Test
    fun parserRejectsChangedOrNoncanonicalInitiatorOrigins() {
        listOf(
            "https://evil.example",
            "https://sede.dip-badajoz.es.evil.example",
            "http://sede.dip-badajoz.es",
            "https://sede.dip-badajoz.es:443",
            "https://sede.dip-badajoz.es:8443",
            "https://sede.dip-badajoz.es/extra",
            "https://user@sede.dip-badajoz.es",
            "https://sede.dip-badajoz.es?extra=1",
            "https://sede.dip-badajoz.es#extra",
        ).forEach { origin ->
            assertRejected(replaceUniqueQuotedValue(ORIGIN, origin), origin)
        }
    }

    @Test
    fun parserRejectsChangedStartUrlsIncludingOtherwiseValidHttpsUrls() {
        listOf(
            "https://sede.dip-badajoz.es/portal",
            "$START_URL/extra",
            "$START_URL?extra=1",
            START_URL.replace("https://", "http://"),
            START_URL.replace(HOST, "$HOST:443"),
            START_URL.replace(HOST, "$HOST:8443"),
        ).forEach { page ->
            val mutated = BuiltInSiteProfiles.JSON.replace(START_URL, page)
            assertTrue(mutated != BuiltInSiteProfiles.JSON)
            assertRejected(mutated, page)
        }
    }

    @Test
    fun bridgeRejectsForeignOriginPageAndContractMutations() {
        val adapter = ProfileMiniAppletBridgeAdapter(
            activeProfileId = { ProfileId(PROFILE_ID) },
        )
        assertTrue(
            adapter.route(
                message(),
                Uri.parse("https://sede.dip-badajoz.es.evil.example"),
                isMainFrame = true,
                currentPageUrl = LOGIN_PAGE_URL,
            ) is MiniAppletBridgeRouteResult.Rejected,
        )
        assertTrue(
            adapter.route(
                message(),
                Uri.parse(ORIGIN),
                isMainFrame = true,
                currentPageUrl = "https://sede.dip-badajoz.es/portal/inicio.do",
            ) is MiniAppletBridgeRouteResult.Rejected,
        )
        listOf(
            message(algorithm = "SHA1withRSA"),
            message(algorithm = "SHA512withRSA"),
            message(format = "XAdES Detached"),
            message(extraProperties = "mode=implicit"),
            message(extraProperties = JSONObject.NULL),
            message(dataB64 = Base64.getEncoder().encodeToString(byteArrayOf(0, 1))),
        ).forEach { rawMessage ->
            assertTrue(
                adapter.route(
                    rawMessage,
                    Uri.parse(ORIGIN),
                    isMainFrame = true,
                    currentPageUrl = LOGIN_PAGE_URL,
                ) is MiniAppletBridgeRouteResult.Rejected,
            )
        }
    }

    private fun message(
        dataB64: String = Base64.getEncoder().encodeToString(CHALLENGE.encodeToByteArray()),
        algorithm: String = "SHA256withRSA",
        format: String = "CAdES",
        extraProperties: Any = DiputacionBadajozCadesAdapter.EXPECTED_EXTRA_PROPERTIES,
    ): String = JSONObject()
        .put("type", "MINIAPPLET_SIGN")
        .put("documentId", DOCUMENT_ID)
        .put("requestId", REQUEST_ID)
        .put("dataB64", dataB64)
        .put("algorithm", algorithm)
        .put("format", format)
        .put("extraProperties", extraProperties)
        .toString()

    private fun replaceUniqueQuotedValue(original: String, replacement: String): String {
        val json = BuiltInSiteProfiles.JSON
        val quoted = "\"$original\""
        assertEquals(3, Regex(Regex.escape(quoted)).findAll(json).count())
        return json.replace(quoted, "\"$replacement\"")
    }

    private fun assertRejected(json: String, label: String) {
        val failure = runCatching { SiteProfileCatalogParser.parse(json) }.exceptionOrNull()
        assertTrue(label, failure is IllegalArgumentException)
    }

    private companion object {
        const val PROFILE_ID = DiputacionBadajozCadesAdapter.PROFILE_ID
        const val HOST = "sede.dip-badajoz.es"
        const val URI_HOST = HOST
        const val ORIGIN = DiputacionBadajozCadesAdapter.INITIATOR_ORIGIN
        const val START_URL = DiputacionBadajozCadesAdapter.START_URL
        const val LOGIN_PAGE_URL = DiputacionBadajozCadesAdapter.LOGIN_PAGE_URL
        const val REQUEST_ID = "123e4567-e89b-42d3-a456-426614174000"
        const val DOCUMENT_ID = "123e4567-e89b-42d3-a456-426614174001"
        const val CHALLENGE = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    }
}

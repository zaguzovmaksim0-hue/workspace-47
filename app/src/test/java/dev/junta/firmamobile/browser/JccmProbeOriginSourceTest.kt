package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ExactOrigin
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SiteProfileCatalogParser
import java.net.URI
import java.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

// Bridge facts agree with the catalogue, while parser pins remain independent of the bridge.
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class JccmProbeOriginSourceTest {
    @Test
    fun bridgeFactsAgreeWithTheIndependentlyValidatedCatalog() {
        val catalog = SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON)
        val profiles = catalog.profiles.filter { it.profileId == ProfileId(PROFILE_ID) }

        assertEquals(1, profiles.count())
        val profile = profiles.single()
        assertEquals("ventanillaelectronica.jccm.es", HOST)
        assertEquals("https://ventanillaelectronica.jccm.es", ORIGIN)
        assertEquals(
            "https://ventanillaelectronica.jccm.es/administracion_electronica/" +
                "formularios/identificacion.phtml",
            START_URL,
        )
        assertEquals(setOf(ExactOrigin.parse(ORIGIN)), profile.initiatorOrigins)
        assertEquals(START_URL, profile.startUrl.toASCIIString())
        assertEquals(HOST, URI(ORIGIN).host)
        assertEquals(HOST, profile.startUrl.host)
    }

    @Test
    fun bridgeAcceptsOnlyTheCataloguedOriginStartAndContract() {
        val result = adapterFor(PROFILE_ID).route(
            rawMessage = message(),
            sourceOrigin = Uri.parse(ORIGIN),
            isMainFrame = true,
            navigationEpoch = 45,
            currentPageUrl = START_URL,
        ) as MiniAppletBridgeRouteResult.Accepted

        result.request.normalized.use { request ->
            assertEquals(PROFILE_ID, request.context.profileId)
            assertEquals(URI(ORIGIN).host, request.context.origin.host)
        }
    }

    @Test
    fun bridgeRejectsForeignOriginsAndPages() {
        listOf(
            "https://ventanillaelectronica.jccm.es.evil.example",
            "https://evil.example",
            "http://ventanillaelectronica.jccm.es",
            "https://ventanillaelectronica.jccm.es:8443",
        ).forEach { origin ->
            assertTrue(
                origin,
                adapterFor(PROFILE_ID).route(
                    rawMessage = message(),
                    sourceOrigin = Uri.parse(origin),
                    isMainFrame = true,
                    currentPageUrl = START_URL,
                ) is MiniAppletBridgeRouteResult.Rejected,
            )
        }
        listOf(
            "https://ventanillaelectronica.jccm.es/administracion_electronica/other.phtml",
            "$START_URL/extra",
            "$START_URL?extra=1",
            "$START_URL#fragment",
        ).forEach { page ->
            assertTrue(
                page,
                adapterFor(PROFILE_ID).route(
                    rawMessage = message(),
                    sourceOrigin = Uri.parse(ORIGIN),
                    isMainFrame = true,
                    currentPageUrl = page,
                ) is MiniAppletBridgeRouteResult.Rejected,
            )
        }
    }

    @Test
    fun parserRejectsCoherentHostRebindingInsteadOfTrustingTheCatalogAlone() {
        val mutated = BuiltInSiteProfiles.JSON.replace(HOST, "unreviewed.example.es")
        assertTrue(BuiltInSiteProfiles.JSON != mutated)
        assertRejected(mutated, "coherent host rebinding")
    }

    @Test
    fun parserRejectsChangedOrNoncanonicalInitiatorOrigins() {
        listOf(
            "https://evil.example",
            "https://ventanillaelectronica.jccm.es.evil.example",
            "http://ventanillaelectronica.jccm.es",
            "https://ventanillaelectronica.jccm.es:443",
            "https://ventanillaelectronica.jccm.es:8443",
            "https://ventanillaelectronica.jccm.es/extra",
            "https://user@ventanillaelectronica.jccm.es",
            "https://ventanillaelectronica.jccm.es?extra=1",
            "https://ventanillaelectronica.jccm.es#extra",
        ).forEach { origin ->
            assertRejected(replaceUniqueQuotedValue(ORIGIN, origin), origin)
        }
    }

    @Test
    fun parserRejectsChangedStartUrlsIncludingOtherwiseValidHttpsUrls() {
        listOf(
            "https://ventanillaelectronica.jccm.es/administracion_electronica/other.phtml",
            "$START_URL/extra",
            "$START_URL?extra=1",
            START_URL.replace("https://", "http://"),
            START_URL.replace(HOST, "$HOST:443"),
            START_URL.replace(HOST, "$HOST:8443"),
        ).forEach { page ->
            val mutated = BuiltInSiteProfiles.JSON.replace(START_URL, page)
            assertTrue(BuiltInSiteProfiles.JSON != mutated)
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

    private fun adapterFor(profileId: String): MiniAppletBridgeAdapter = MiniAppletBridgeAdapter(
        activeProfileId = { ProfileId(profileId) },
    )

    private fun message(): String = JSONObject()
        .put("type", "MINIAPPLET_SIGN")
        .put("documentId", "123e4567-e89b-42d3-a456-426614174001")
        .put("requestId", "22222222-2222-4222-8222-222222222222")
        .put("dataB64", Base64.getEncoder().encodeToString("ABCDE".encodeToByteArray()))
        .put("algorithm", "SHA1withRSA")
        .put("format", "CAdES")
        .put("extraProperties", "")
        .toString()

    private companion object {
        const val PROFILE_ID = "jccm-certificate-login-probe"
        const val HOST = "ventanillaelectronica.jccm.es"
        const val ORIGIN = "https://ventanillaelectronica.jccm.es"
        const val START_URL =
            "https://ventanillaelectronica.jccm.es/administracion_electronica/" +
                "formularios/identificacion.phtml"
    }
}

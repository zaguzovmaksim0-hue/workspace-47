package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ExactOrigin
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SiteProfileCatalogParser
import dev.junta.firmamobile.profile.TlsProfileMutation
import dev.junta.firmamobile.profile.TlsProfileMutations
import dev.junta.firmamobile.signing.AirefXadesEnvelopingAdapter
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

// AIReF has three distinct origin roles: public portal, Cl@ve source and client-TLS target.
// The runtime literals remain independent; the parser must reject any coordinated host or URL mutation.
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class AirefOriginSourceTest {
    private val profileId = ProfileId(AirefXadesEnvelopingAdapter.PROFILE_ID)
    private val portalOrigin = "https://sede.airef.es"
    private val claveSourceOrigin = "https://pasarela.clave.gob.es"
    private val tlsTargetOrigin = "https://pasarela-ident.clave.gob.es"
    private val publicStartUrl =
        "$portalOrigin/invesiteRE/action/inicio?authMethod=Clave&organismo=AIREF&tramite=AF-01"
    private val sourceUrl = "$claveSourceOrigin/Proxy2/ServiceProvider"
    private val targetUrl = "$tlsTargetOrigin/IdP2/AuthenticateCitizen"

    @Test
    fun runtimeOriginsAndStartMatchCatalogAndParserPins() {
        assertEquals(AirefXadesEnvelopingAdapter.INITIATOR_ORIGIN, portalOrigin)
        assertEquals(AirefXadesEnvelopingAdapter.CLIENT_AUTH_SOURCE_ORIGIN, claveSourceOrigin)
        assertEquals(AirefXadesEnvelopingAdapter.CLIENT_AUTH_REQUEST_ORIGIN, tlsTargetOrigin)
        assertEquals(AirefXadesEnvelopingAdapter.PUBLIC_START_URL, publicStartUrl)
        assertEquals(AirefXadesEnvelopingAdapter.CLIENT_AUTH_SOURCE_URL, sourceUrl)

        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }
        val policy = checkNotNull(profile.clientAuthPolicy)
        assertEquals(java.net.URI(publicStartUrl), profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse(portalOrigin)), profile.initiatorOrigins)
        assertEquals(setOf(ExactOrigin.parse(claveSourceOrigin)), profile.redirectOrigins)
        assertEquals(setOf(ExactOrigin.parse(tlsTargetOrigin)), policy.requestOrigins)
        assertEquals(setOf(java.net.URI(sourceUrl)), policy.sourceUrls)
        assertEquals("/IdP2/AuthenticateCitizen", policy.requestPath)

        assertEquals(
            BuiltInSiteProfiles.catalog,
            SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON),
        )
    }

    @Test
    fun parserRejectsRoleSpecificHostAndUrlMutations() {
        val mutations = listOf(
            TlsProfileMutation(
                "portal start host",
                profileId.value,
                "\"startUrl\"\\s*:\\s*\"https://sede\\.airef\\.es/invesiteRE/action/inicio\\?authMethod=Clave&organismo=AIREF&tramite=AF-01\"",
                "\"startUrl\": \"https://sede.airef.es.evil.example/invesiteRE/action/inicio?authMethod=Clave&organismo=AIREF&tramite=AF-01\"",
            ),
            TlsProfileMutation(
                "portal initiator host",
                profileId.value,
                Regex.escape("\"https://sede.airef.es\""),
                "\"https://sede.airef.es.evil.example\"",
            ),
            TlsProfileMutation(
                "clave source host",
                profileId.value,
                Regex.escape("https://pasarela.clave.gob.es/Proxy2/ServiceProvider"),
                "https://pasarela.clave.gob.es.evil.example/Proxy2/ServiceProvider",
            ),
            TlsProfileMutation(
                "clave source endpoint",
                profileId.value,
                Regex.escape("/Proxy2/ServiceProvider"),
                "/Proxy2/ServiceRedirect",
            ),
            TlsProfileMutation(
                "clave redirect host",
                profileId.value,
                Regex.escape("\"https://pasarela.clave.gob.es\""),
                "\"https://pasarela.clave.gob.es.evil.example\"",
            ),
            TlsProfileMutation(
                "tls target host",
                profileId.value,
                Regex.escape("https://pasarela-ident.clave.gob.es"),
                "https://pasarela-ident.clave.gob.es.evil.example",
            ),
            TlsProfileMutation(
                "tls target path",
                profileId.value,
                Regex.escape("/IdP2/AuthenticateCitizen"),
                "/IdP2/AuthenticateCitizen/",
            ),
        )

        assertTrue(TlsProfileMutations.accepted(mutations).isEmpty())
    }

    @Test
    fun bridgeAcceptsOnlyPortalOriginAndBoundedDynamicSigningPages() {
        val adapter = ProfileMiniAppletBridgeAdapter(activeProfileId = { profileId })
        for (page in listOf(
            "$portalOrigin/invesiteRE/action/solicitud/view?id=1",
            "$portalOrigin/invesiteRE/action/solicitud/view?id=12345678901234567890",
            "$portalOrigin:443/invesiteRE/action/solicitud/view?id=42",
        )) {
            val result = adapter.route(
                rawMessage = message(),
                sourceOrigin = Uri.parse(portalOrigin),
                isMainFrame = true,
                navigationEpoch = 54,
                currentPageUrl = page,
            )
            assertTrue(page, result is MiniAppletBridgeRouteResult.Accepted)
        }
    }

    @Test
    fun bridgeRejectsForeignOriginAndPage() {
        val adapter = ProfileMiniAppletBridgeAdapter(activeProfileId = { profileId })
        val page = "$portalOrigin/invesiteRE/action/solicitud/view?id=42"
        for (origin in listOf(
            Uri.parse("https://sede.airef.es.evil.example"),
            Uri.parse(claveSourceOrigin),
            Uri.parse(tlsTargetOrigin),
        )) {
            val result = adapter.route(
                rawMessage = message(),
                sourceOrigin = origin,
                isMainFrame = true,
                navigationEpoch = 55,
                currentPageUrl = page,
            )
            assertTrue(result is MiniAppletBridgeRouteResult.Rejected)
        }
        for (badPage in listOf(
            null,
            "$portalOrigin/invesiteRE/action/solicitud/view",
            "$portalOrigin/invesiteRE/action/solicitud/view?id=abc",
            "$portalOrigin/invesiteRE/action/solicitud/view?id=123456789012345678901",
            "$portalOrigin/invesiteRE/action/solicitud/view?id=42&extra=1",
            "$portalOrigin/invesiteRE/action/solicitud/view?id=42#fragment",
        )) {
            val result = adapter.route(
                rawMessage = message(),
                sourceOrigin = Uri.parse(portalOrigin),
                isMainFrame = true,
                navigationEpoch = 55,
                currentPageUrl = badPage,
            )
            assertTrue("$badPage", result is MiniAppletBridgeRouteResult.Rejected)
        }
    }

    private fun message(): String = JSONObject()
        .put("type", "MINIAPPLET_SIGN")
        .put("documentId", "123e4567-e89b-42d3-a456-426614174001")
        .put("requestId", "123e4567-e89b-42d3-a456-426614174002")
        .put("dataB64", Base64.getEncoder().encodeToString(ByteArray(32) { (it + 1).toByte() }))
        .put("algorithm", "SHA1withRSA")
        .put("format", "XAdES")
        .put("extraProperties", JSONObject.NULL)
        .toString()
}

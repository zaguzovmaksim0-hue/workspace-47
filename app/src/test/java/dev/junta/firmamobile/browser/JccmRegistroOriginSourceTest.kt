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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

// The JCCM Registro bridge derives portal facts from its portal origin; SSO redirect,
// Cl@ve source and TLS target remain distinct runtime roles pinned by the parser.
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class JccmRegistroOriginSourceTest {
    @Test
    fun bridgeFactsAgreeWithTheIndependentlyValidatedCatalog() {
        val catalog = SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON)
        val profile = catalog.profiles.single { it.profileId == ProfileId(PROFILE_ID) }

        assertEquals(HOST, URI(ORIGIN).host)
        assertEquals(START_URL, "$ORIGIN/registrounicociudadanos/acceso.do?id=SJLZ")
        assertEquals(PROTECTED_PAGE, "$ORIGIN/registrounicociudadanos/accesoclvd.do")
        assertEquals(setOf(ExactOrigin.parse(ORIGIN)), profile.initiatorOrigins)
        assertEquals(START_URL, profile.startUrl.toASCIIString())
        assertEquals(HOST, profile.startUrl.host)
        assertEquals(
            setOf(ExactOrigin.parse(SSO_REDIRECT_ORIGIN), ExactOrigin.parse(CLAVE_SOURCE_ORIGIN)),
            profile.redirectOrigins,
        )
        val clientAuth = requireNotNull(profile.clientAuthPolicy)
        assertEquals(setOf(ExactOrigin.parse(TLS_TARGET_ORIGIN)), clientAuth.requestOrigins)
        assertEquals(setOf(URI(CLAVE_SOURCE_URL)), clientAuth.sourceUrls)
        assertEquals(TLS_TARGET_PATH, clientAuth.requestPath)
    }

    @Test
    fun bridgeAcceptsOnlyTheCataloguedOriginStartAndContract() {
        val result = adapterFor(PROFILE_ID).route(
            rawMessage = message(),
            sourceOrigin = Uri.parse(ORIGIN),
            isMainFrame = true,
            navigationEpoch = 45,
            currentPageUrl = PROTECTED_PAGE,
        ) as MiniAppletBridgeRouteResult.Accepted

        result.request.normalized.use { request ->
            assertEquals(PROFILE_ID, request.context.profileId)
            assertEquals(HOST, request.context.origin.host)
        }
    }

    @Test
    fun bridgeRejectsForeignOriginsAndPages() {
        listOf(
            "https://registrounicociudadanos.jccm.es.evil.example",
            "https://evil.example",
            "http://registrounicociudadanos.jccm.es",
            "https://registrounicociudadanos.jccm.es:8443",
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
            "https://registrounicociudadanos.jccm.es/registrounicociudadanos/other.do",
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
    fun bridgeRejectsTheOldProbeProfileContract() {
        assertTrue(
            adapterFor(OLD_PROBE_PROFILE_ID).route(
                rawMessage = message(),
                sourceOrigin = Uri.parse(ORIGIN),
                isMainFrame = true,
                currentPageUrl = START_URL,
            ) is MiniAppletBridgeRouteResult.Rejected,
        )
    }

    @Test
    fun parserRejectsCoherentPortalHostRebindingInsteadOfTrustingTheCatalogAlone() {
        val mutated = BuiltInSiteProfiles.JSON.replace(HOST, "unreviewed.example.es")
        assertTrue(BuiltInSiteProfiles.JSON != mutated)
        assertRejected(mutated, "coherent portal host rebinding")
    }

    @Test
    fun parserRejectsChangedOrNoncanonicalPortalOrigins() {
        listOf(
            "https://evil.example",
            "https://registrounicociudadanos.jccm.es.evil.example",
            "http://registrounicociudadanos.jccm.es",
            "https://registrounicociudadanos.jccm.es:443",
            "https://registrounicociudadanos.jccm.es:8443",
            "https://registrounicociudadanos.jccm.es/extra",
            "https://user@registrounicociudadanos.jccm.es",
            "https://registrounicociudadanos.jccm.es?extra=1",
            "https://registrounicociudadanos.jccm.es#extra",
        ).forEach { origin ->
            assertRejected(
                replaceInProfile(
                    original = "\"initiatorOrigins\": [\"$ORIGIN\"]",
                    replacement = "\"initiatorOrigins\": [\"$origin\"]",
                ),
                origin,
            )
        }
    }

    @Test
    fun parserRejectsChangedPortalStartUrlsIncludingOtherwiseValidHttpsUrls() {
        listOf(
            "https://registrounicociudadanos.jccm.es/registrounicociudadanos/other.do?id=SJLZ",
            "$START_URL/extra",
            "$START_URL?extra=1",
            START_URL.replace("https://", "http://"),
            START_URL.replace(HOST, "$HOST:443"),
            START_URL.replace(HOST, "$HOST:8443"),
        ).forEach { page ->
            assertRejected(
                replaceInProfile(
                    original = "\"startUrl\": \"$START_URL\"",
                    replacement = "\"startUrl\": \"$page\"",
                ),
                page,
            )
        }
    }

    @Test
    fun parserRejectsRoleSpecificOriginMutations() {
        assertRejected(
            replaceMatchingInProfile(
                pattern = Regex("\"redirectOrigins\"\\s*:\\s*\\[\\s*\"$SSO_REDIRECT_ORIGIN\"\\s*,\\s*\"$CLAVE_SOURCE_ORIGIN\"\\s*\\]"),
                replacement = "\"redirectOrigins\": [\"https://sso.jccm.es.evil.example\", \"$CLAVE_SOURCE_ORIGIN\"]",
            ),
            "SSO redirect host rebinding",
        )
        assertRejected(
            replaceMatchingInProfile(
                pattern = Regex("\"sourceUrls\"\\s*:\\s*\\[\\s*\"$CLAVE_SOURCE_URL\"\\s*\\]"),
                replacement = "\"sourceUrls\": [\"https://pasarela.clave.gob.es.evil.example/Proxy2/ServiceRedirect\"]",
            ),
            "Cl@ve source host rebinding",
        )
        assertRejected(
            replaceMatchingInProfile(
                pattern = Regex("\"requestOrigins\"\\s*:\\s*\\[\\s*\"$TLS_TARGET_ORIGIN\"\\s*\\]"),
                replacement = "\"requestOrigins\": [\"https://pasarela-ident.clave.gob.es.evil.example\"]",
            ),
            "TLS target host rebinding",
        )
    }

    private fun replaceInProfile(original: String, replacement: String): String =
        replaceMatchingInProfile(
            pattern = Regex(Regex.escape(original)),
            replacement = replacement,
        )

    private fun replaceMatchingInProfile(pattern: Regex, replacement: String): String {
        val json = BuiltInSiteProfiles.JSON
        val profileStart = json.indexOf("\"profileId\": \"$PROFILE_ID\"")
        assertTrue(profileStart >= 0)
        val profileEnd = json.indexOf("\"profileId\":", profileStart + 1).let { if (it < 0) json.length else it }
        val profile = json.substring(profileStart, profileEnd)
        assertEquals(1, pattern.findAll(profile).count())
        val mutatedProfile = profile.replace(pattern, replacement)
        return json.substring(0, profileStart) + mutatedProfile + json.substring(profileEnd)
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
        .put("algorithm", "SHA512withRSA")
        .put("format", "XADES")
        .put("extraProperties", "format=XAdES Detached\nmode=implicit")
        .toString()

    private companion object {
        const val PROFILE_ID = "jccm-registro-generico"
        const val OLD_PROBE_PROFILE_ID = "jccm-certificate-login-probe"
        const val HOST = "registrounicociudadanos.jccm.es"
        const val ORIGIN = "https://registrounicociudadanos.jccm.es"
        const val START_URL =
            "https://registrounicociudadanos.jccm.es/registrounicociudadanos/acceso.do?id=SJLZ"
        const val PROTECTED_PAGE =
            "https://registrounicociudadanos.jccm.es/registrounicociudadanos/accesoclvd.do"
        const val SSO_REDIRECT_ORIGIN = "https://sso.jccm.es"
        const val CLAVE_SOURCE_ORIGIN = "https://pasarela.clave.gob.es"
        const val CLAVE_SOURCE_URL =
            "https://pasarela.clave.gob.es/Proxy2/ServiceRedirect"
        const val TLS_TARGET_ORIGIN = "https://pasarela-ident.clave.gob.es"
        const val TLS_TARGET_PATH = "/IdP2/AuthenticateCitizen"
    }
}

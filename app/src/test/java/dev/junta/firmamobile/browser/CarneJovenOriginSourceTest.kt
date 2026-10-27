package dev.junta.firmamobile.browser

import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ExactOrigin
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SiteProfileCatalogParser
import java.net.URI
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The retry controller keeps one source-origin literal; the catalogue and its independent
// parser pins must agree with the complete runtime contract, so neither side can widen it.
class CarneJovenOriginSourceTest {
    @Test
    fun runtimeContractAgreesWithIndependentlyValidatedCatalog() {
        val catalog = SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON)
        val profile = catalog.profiles.single { it.profileId == ProfileId(PROFILE_ID) }
        val policy = checkNotNull(profile.clientAuthPolicy)

        assertEquals(SOURCE_ORIGIN, "https://ws104.juntadeandalucia.es")
        assertEquals(SOURCE_URL, "$SOURCE_ORIGIN/carneJoven/servlet/CallAuthenticationServlet")
        assertEquals(TARGET_HOST, "ws235.juntadeandalucia.es")
        assertTrue(TARGET_URL.startsWith("https://$TARGET_HOST/authenticationFacade?"))
        assertEquals(COME_BACK_URL, EXPECTED_COME_BACK_URL)
        assertEquals(setOf(ExactOrigin.parse(SOURCE_ORIGIN)), profile.initiatorOrigins)
        assertEquals(setOf(URI(SOURCE_URL)), policy.sourceUrls)
        assertEquals(setOf(ExactOrigin.parse("https://$TARGET_HOST")), policy.requestOrigins)
        assertEquals(COME_BACK_URL, policy.fixedQueryParameters.getValue("comeBackURL"))
        assertTrue(
            CarneJovenPreTlsRetryController().matchesRetryContract(
                authorizedTarget(profile.profileId, policy),
            ),
        )
    }

    @Test
    fun retryContractRejectsForeignTargetHostHttpSchemeAndOtherPath() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single {
            it.profileId == ProfileId(PROFILE_ID)
        }
        val policy = checkNotNull(profile.clientAuthPolicy)
        val controller = CarneJovenPreTlsRetryController()

        assertFalse(
            controller.matchesRetryContract(
                authorizedTarget(
                    profile.profileId,
                    policy,
                    targetUrl = TARGET_URL.replace(TARGET_HOST, "ws236.juntadeandalucia.es"),
                ),
            ),
        )
        assertFalse(
            controller.matchesRetryContract(
                authorizedTarget(
                    profile.profileId,
                    policy,
                    targetUrl = TARGET_URL.replace("https://", "http://"),
                ),
            ),
        )
        assertFalse(
            controller.matchesRetryContract(
                authorizedTarget(
                    profile.profileId,
                    policy,
                    targetUrl = TARGET_URL.replace("/authenticationFacade", "/otherFacade"),
                ),
            ),
        )
    }

    @Test
    fun parserRejectsCoherentSourceHostRebinding() {
        val mutated = BuiltInSiteProfiles.JSON.replace(
            "ws104.juntadeandalucia.es",
            "unreviewed.example.es",
        )
        assertTrue(BuiltInSiteProfiles.JSON != mutated)
        assertRejected(mutated, "coherent ws104 host rebinding")
    }

    @Test
    fun parserRejectsCoherentTargetHostRebinding() {
        val mutated = BuiltInSiteProfiles.JSON.replace(
            "ws235.juntadeandalucia.es",
            "unreviewed.example.es",
        )
        assertTrue(BuiltInSiteProfiles.JSON != mutated)
        assertRejected(mutated, "coherent ws235 host rebinding")
    }

    @Test
    fun parserRejectsChangedSourceUrls() {
        val original = BuiltInSiteProfiles.JSON
        val sourceUrlsFragment = "\"sourceUrls\":[\"$SOURCE_URL\"]"
        assertEquals(1, Regex(Regex.escape(sourceUrlsFragment)).findAll(original).count())
        val mutated = original.replace(
            sourceUrlsFragment,
            "\"sourceUrls\":[\"https://ws104.juntadeandalucia.es/carneJoven/servlet/CallOtro\"]",
        )
        assertTrue(original != mutated)
        assertRejected(mutated, "changed source URL")
    }

    @Test
    fun parserRejectsChangedStartUrls() {
        val startUrl = "https://ws104.juntadeandalucia.es/carneJoven/cjservlet/portal/index.jsp"
        val original = BuiltInSiteProfiles.JSON
        val quoted = "\"$startUrl\""
        assertEquals(1, Regex(Regex.escape(quoted)).findAll(original).count())
        val mutated = original.replace(
            quoted,
            "\"https://ws104.juntadeandalucia.es/carneJoven/cjservlet/portal/other.jsp\"",
        )
        assertTrue(original != mutated)
        assertRejected(mutated, "changed start URL")
    }

    private fun authorizedTarget(
        profileId: ProfileId,
        policy: dev.junta.firmamobile.profile.ClientAuthPolicy,
        targetUrl: String = TARGET_URL,
    ) = AuthorizedClientAuthTarget(
        profileId = profileId,
        source = URI(SOURCE_URL),
        target = URI(targetUrl),
        policy = policy,
        certificateRules = BuiltInSiteProfiles.catalog.profiles
            .single { it.profileId == profileId }
            .certificateRules,
        observedAtMonotonicNanos = 0L,
        lifetimeNanos = Duration.ofSeconds(policy.grantTtlSeconds.toLong()).toNanos(),
    )

    private fun assertRejected(json: String, label: String) {
        val failure = runCatching { SiteProfileCatalogParser.parse(json) }.exceptionOrNull()
        assertTrue(label, failure is IllegalArgumentException)
    }

    private companion object {
        const val PROFILE_ID = "carne-joven-andalucia"
        const val SOURCE_ORIGIN = "https://ws104.juntadeandalucia.es"
        const val SOURCE_URL = "$SOURCE_ORIGIN/carneJoven/servlet/CallAuthenticationServlet"
        const val TARGET_HOST = "ws235.juntadeandalucia.es"
        const val COME_BACK_URL =
            "aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4v" +
                "c2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ="
        const val EXPECTED_COME_BACK_URL =
            "aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4v" +
                "c2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ="
        const val TARGET_URL =
            "https://$TARGET_HOST/authenticationFacade?action=validateCert" +
                "&appId=IAJ.CARNETJOVEN&comeBackURL=$COME_BACK_URL" +
                "&ticketId=synthetic-ticket&webSessionId=synthetic-session"
    }
}

package dev.junta.firmamobile.browser

import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.BuildTrustPolicy
import dev.junta.firmamobile.profile.Capability
import dev.junta.firmamobile.profile.ClientAuthPolicy
import dev.junta.firmamobile.profile.ClientAuthTransitionMode
import dev.junta.firmamobile.profile.CompatibilityStatus
import dev.junta.firmamobile.profile.ExactOrigin
import dev.junta.firmamobile.profile.ProfileActivation
import dev.junta.firmamobile.profile.SiteProfileRegistry
import dev.junta.firmamobile.profile.ProfileId
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClientAuthNavigationAuthorizerTest {
    private val clock = MutableClock(Instant.parse("2030-01-01T00:00:00Z"))
    private val authorizer = ClientAuthNavigationAuthorizer(
        BuiltInSiteProfiles.qaRegistry,
        clock,
    )

    @Test
    fun exactDirectSourceTransitionProducesOneBoundedAeatTarget() {
        val direct = aeatAuthorizer()

        val result = direct.observeTopLevelNavigation(
            activeProfileId = AEAT_PROFILE,
            currentUrl = AEAT_SOURCE,
            targetUrl = AEAT_TARGET,
            currentEpoch = 40,
            isModernMainFrameRequest = true,
        )

        assertEquals(AEAT_PROFILE, result?.profileId)
        assertEquals("www1.agenciatributaria.gob.es", result?.target?.host)
        assertEquals("/wlpl/BUGC-JDIT/MdcAcceso", result?.target?.rawPath)
        assertNull(
            direct.observeTopLevelNavigation(
                AEAT_PROFILE, AEAT_SOURCE, AEAT_TARGET, 40, true,
            ),
        )
    }

    @Test
    fun hostileNavigationCannotResetConsumedDirectGrantInTheSameEpoch() {
        val direct = aeatAuthorizer()
        assertEquals(
            AEAT_PROFILE,
            direct.observeTopLevelNavigation(
                AEAT_PROFILE, AEAT_SOURCE, AEAT_TARGET, 70, true,
            )?.profileId,
        )

        assertNull(
            direct.observeTopLevelNavigation(
                AEAT_PROFILE, AEAT_SOURCE, "$AEAT_TARGET?extra=1", 70, true,
            ),
        )
        assertNull(
            direct.observeTopLevelNavigation(
                AEAT_PROFILE, AEAT_SOURCE, AEAT_TARGET, 70, true,
            ),
        )
    }

    @Test
    fun aeatDirectTransitionRejectsLegacySubframeWrongProfileAndWrongSource() {
        val invalidCalls = listOf<(ClientAuthNavigationAuthorizer) -> AuthorizedClientAuthTarget?>(
            { it.observeTopLevelNavigation(AEAT_PROFILE, AEAT_SOURCE, AEAT_TARGET, 50, false) },
            { it.observeTopLevelNavigation(null, AEAT_SOURCE, AEAT_TARGET, 50, true) },
            { it.observeTopLevelNavigation(PROFILE, AEAT_SOURCE, AEAT_TARGET, 50, true) },
            { it.observeTopLevelNavigation(AEAT_PROFILE, null, AEAT_TARGET, 50, true) },
            {
                it.observeTopLevelNavigation(
                    AEAT_PROFILE,
                    "https://sede.agenciatributaria.gob.es/Sede/other.html",
                    AEAT_TARGET,
                    50,
                    true,
                )
            },
            {
                it.observeTopLevelNavigation(
                    AEAT_PROFILE,
                    "https://sede.agenciatributaria.gob.es.evil.example/Sede/mi-area-personal.html",
                    AEAT_TARGET,
                    50,
                    true,
                )
            },
        )

        invalidCalls.forEach { call -> assertNull(call(aeatAuthorizer())) }
    }

    @Test
    fun aeatDirectTransitionRejectsEveryTargetExpansion() {
        val invalidTargets = listOf(
            AEAT_TARGET.replace("www1.agenciatributaria.gob.es", "www1.agenciatributaria.gob.es.evil.example"),
            AEAT_TARGET.replace("/MdcAcceso", "/Other"),
            AEAT_TARGET.replace("/MdcAcceso", "/MdcAcceso%2Fother"),
            AEAT_TARGET.replace("www1.agenciatributaria.gob.es", "www1.agenciatributaria.gob.es:8443"),
            "$AEAT_TARGET#fragment",
            "$AEAT_TARGET?extra=1",
            "$AEAT_TARGET?",
        )

        invalidTargets.forEach { target ->
            assertNull(
                target,
                aeatAuthorizer().observeTopLevelNavigation(
                    AEAT_PROFILE, AEAT_SOURCE, target, 60, true,
                ),
            )
        }
    }

    @Test
    fun exactTwoStageTopLevelRedirectProducesOneBoundedTarget() {
        assertNull(arm(epoch = 4))
        authorizer.onTopLevelPageStarted(SOURCE, 5)

        val result = authorize(TARGET, epoch = 5)

        assertEquals(ProfileId("carne-joven-andalucia"), result?.profileId)
        assertEquals("ws235.juntadeandalucia.es", result?.target?.host)
        assertEquals("/authenticationFacade", result?.target?.path)
        assertNull(authorize(TARGET, epoch = 5))
    }

    @Test
    fun immediateTargetAtArmingEpochNAuthorizesOnce() {
        assertNull(arm(epoch = 4))

        val result = authorize(TARGET, epoch = 4)

        assertEquals(ProfileId("carne-joven-andalucia"), result?.profileId)
        assertEquals("ws235.juntadeandalucia.es", result?.target?.host)
        assertEquals("/authenticationFacade", result?.target?.path)
        assertNull(authorize(TARGET, epoch = 4))
    }

    @Test
    fun directLegacyIframeWrongProfileEpochAndExpiredSourceNeverAuthorize() {
        assertNull(authorize(TARGET, epoch = 7))
        assertNull(
            authorizer.observeTopLevelNavigation(
                PROFILE,
                INDEX,
                SOURCE,
                7,
                isModernMainFrameRequest = false,
            ),
        )
        assertNull(authorize(TARGET, epoch = 8))

        arm(epoch = 10)
        assertNull(authorize(TARGET, epoch = 12))

        arm(epoch = 20)
        clock.advance(Duration.ofSeconds(16))
        assertNull(authorize(TARGET, epoch = 21))

        assertNull(
            authorizer.observeTopLevelNavigation(
                ProfileId("junta-andalucia"), INDEX, SOURCE, 30, true,
            ),
        )
        assertNull(authorize(TARGET, epoch = 31))
    }

    @Test
    fun wrongFixedEmptyDuplicateAndExtraQueryParametersFailClosed() {
        val attacks = listOf(
            TARGET.replace("validateCert", "other"),
            TARGET.replace("ticketId=synthetic-ticket", "ticketId="),
            TARGET.replace(
                "aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4v" +
                    "c2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ%3D",
                "https%3A%2F%2Fws104.juntadeandalucia.es%2FcarneJoven%2Fservlet%2F" +
                    "ReturnAuthenticationServlet",
            ),
            "$TARGET&extra=1",
            "$TARGET&ticketId=duplicate",
            TARGET.replace("/authenticationFacade", "/other"),
            TARGET.replace("ws235.juntadeandalucia.es", "ws235.juntadeandalucia.es.evil.example"),
        )
        attacks.forEachIndexed { index, attack ->
            val epoch = 100L + index * 2
            arm(epoch)
            authorizer.onTopLevelPageStarted(SOURCE, epoch + 1)
            assertNull(attack, authorize(attack, epoch + 1))
        }
    }

    @Test
    fun unrelatedPageStartInvalidatesThePendingSource() {
        arm(200)
        authorizer.onTopLevelPageStarted("https://ws104.juntadeandalucia.es/other", 201)
        assertNull(authorize(TARGET, 201))
    }

    @Test
    fun invalidationEncodedDuplicatesFragmentsPortsAndWrongCurrentOriginFailClosed() {
        arm(300)
        authorizer.invalidate()
        authorizer.onTopLevelPageStarted(SOURCE, 301)
        assertNull(authorize(TARGET, 301))

        val attacks = listOf(
            "$TARGET#fragment",
            TARGET.replace("ws235.juntadeandalucia.es", "ws235.juntadeandalucia.es:8443"),
            "$TARGET&%74icketId=encoded-duplicate",
            TARGET.replace("/authenticationFacade", "/authenticationFacade%2Fother"),
        )
        attacks.forEachIndexed { index, attack ->
            val epoch = 310L + index * 2
            arm(epoch)
            authorizer.onTopLevelPageStarted(SOURCE, epoch + 1)
            assertNull(attack, authorize(attack, epoch + 1))
        }

        assertNull(
            authorizer.observeTopLevelNavigation(
                PROFILE,
                "https://example.org/",
                SOURCE,
                400,
                true,
            ),
        )
        assertNull(authorize(TARGET, 401))
    }

    @Test
    fun paddedUnpaddedAndUrlSafeBase64ComeBackUrlAreEquivalent() {
        val stdPaddedTarget = TARGET
        val stdUnpaddedTarget = TARGET.replace(
            "aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ%3D",
            "aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ",
        )
        val urlSafePaddedTarget = TARGET.replace(
            "aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ%3D",
            "aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ%3D".replace('/', '_').replace('+', '-'),
        )
        val urlSafeUnpaddedTarget = TARGET.replace(
            "aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ%3D",
            "aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ".replace('/', '_').replace('+', '-'),
        )

        listOf(stdPaddedTarget, stdUnpaddedTarget, urlSafePaddedTarget, urlSafeUnpaddedTarget).forEachIndexed { index, target ->
            val epoch = 500L + index * 2
            arm(epoch)
            authorizer.onTopLevelPageStarted(SOURCE, epoch + 1)
            val result = authorize(target, epoch + 1)
            assertEquals(PROFILE, result?.profileId)
            assertEquals("ws235.juntadeandalucia.es", result?.target?.host)
        }
    }

    @Test
    fun nonEquivalentAndInvalidBase64ComeBackUrlFailClosed() {
        val nonEquivalentTarget = TARGET.replace(
            "aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ%3D",
            "aHR0cHM6Ly9ldmlsLmV4YW1wbGUvUmV0dXJuQXV0aGVudGljYXRpb25TZXJ2bGV0",
        )
        val invalidBase64Target = TARGET.replace(
            "aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ%3D",
            "!!!invalid-base64!!!",
        )
        val leadingWhitespaceTarget = TARGET.replace(
            "aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ%3D",
            "%20aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ%3D",
        )
        val trailingWhitespaceTarget = TARGET.replace(
            "aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ%3D",
            "aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ%3D%20",
        )
        val invalidMod1LengthTarget = TARGET.replace(
            "aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ%3D",
            "aHR0c",
        )

        val invalidTargets = listOf(
            nonEquivalentTarget,
            invalidBase64Target,
            leadingWhitespaceTarget,
            trailingWhitespaceTarget,
            invalidMod1LengthTarget,
        )

        invalidTargets.forEachIndexed { index, attack ->
            val epoch = 600L + index * 2
            arm(epoch)
            authorizer.onTopLevelPageStarted(SOURCE, epoch + 1)
            assertNull(attack, authorize(attack, epoch + 1))
        }
    }

    @Test
    fun subframeRequestClearsPendingAndFailsClosed() {
        arm(700)
        assertNull(
            authorizer.observeTopLevelNavigation(
                PROFILE,
                SOURCE,
                TARGET,
                700,
                isModernMainFrameRequest = false,
            ),
        )
        assertNull(authorize(TARGET, 700))
        assertNull(authorize(TARGET, 701))
    }

    private fun aeatAuthorizer(): ClientAuthNavigationAuthorizer {
        val base = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == PROFILE }
        val profile = base.copy(
            profileId = AEAT_PROFILE,
            profileVersion = 1,
            displayName = "AEAT — Mis datos censales",
            compatibilityStatus = CompatibilityStatus.VERIFIED_CONTRACT,
            activation = ProfileActivation.QA_ONLY,
            startUrl = URI(AEAT_SOURCE),
            initiatorOrigins = setOf(ExactOrigin.parse("https://sede.agenciatributaria.gob.es")),
            redirectOrigins = emptySet(),
            trustedBrowseOrigins = emptySet(),
            endpoints = emptyMap(),
            operationPolicies = emptyMap(),
            capabilities = setOf(Capability.CLIENT_TLS_AUTH),
            clientAuthPolicy = ClientAuthPolicy(
                transitionMode = ClientAuthTransitionMode.DIRECT_FROM_SOURCE,
                requestOrigins = setOf(ExactOrigin.parse("https://www1.agenciatributaria.gob.es")),
                sourceUrls = setOf(URI(AEAT_SOURCE)),
                requestPath = "/wlpl/BUGC-JDIT/MdcAcceso",
                fixedQueryParameters = emptyMap(),
                requiredEphemeralQueryParameters = emptySet(),
                allowEmptyIssuerList = false,
                grantTtlSeconds = 15,
            ),
            evidence = emptyList(),
        )
        val registry = SiteProfileRegistry(
            BuiltInSiteProfiles.catalog.copy(profiles = listOf(profile)),
            BuildTrustPolicy.QA,
        )
        return ClientAuthNavigationAuthorizer(registry, clock)
    }

    private fun arm(epoch: Long) = authorizer.observeTopLevelNavigation(
        activeProfileId = PROFILE,
        currentUrl = INDEX,
        targetUrl = SOURCE,
        currentEpoch = epoch,
        isModernMainFrameRequest = true,
    )

    private fun authorize(target: String, epoch: Long) = authorizer.observeTopLevelNavigation(
        activeProfileId = PROFILE,
        currentUrl = SOURCE,
        targetUrl = target,
        currentEpoch = epoch,
        isModernMainFrameRequest = true,
    )

    private class MutableClock(private var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = instant
        fun advance(duration: Duration) {
            instant = instant.plus(duration)
        }
    }

    private companion object {
        val PROFILE = ProfileId("carne-joven-andalucia")
        val AEAT_PROFILE = ProfileId("aeat-mis-datos-censales")
        const val AEAT_SOURCE =
            "https://sede.agenciatributaria.gob.es/Sede/mi-area-personal.html"
        const val AEAT_TARGET =
            "https://www1.agenciatributaria.gob.es/wlpl/BUGC-JDIT/MdcAcceso"
        const val INDEX = "https://ws104.juntadeandalucia.es/carneJoven/cjservlet/portal/index.jsp"
        const val SOURCE =
            "https://ws104.juntadeandalucia.es/carneJoven/servlet/CallAuthenticationServlet"
        const val TARGET =
            "https://ws235.juntadeandalucia.es/authenticationFacade?action=validateCert&ticketId=synthetic-ticket&appId=IAJ.CARNETJOVEN&webSessionId=synthetic-session&comeBackURL=aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ%3D"
    }
}

package dev.junta.firmamobile.browser

import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientAuthNavigationAuthorizerTest {
    private val clock = MutableClock(Instant.parse("2030-01-01T00:00:00Z"))
    private val authorizer = ClientAuthNavigationAuthorizer(
        BuiltInSiteProfiles.qaRegistry,
        clock,
    )

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
        assertNull(authorize(TARGET, epoch = 10))

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
            assertNull(authorize(attack, epoch + 1))
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
        const val INDEX = "https://ws104.juntadeandalucia.es/carneJoven/cjservlet/portal/index.jsp"
        const val SOURCE =
            "https://ws104.juntadeandalucia.es/carneJoven/servlet/CallAuthenticationServlet"
        const val TARGET =
            "https://ws235.juntadeandalucia.es/authenticationFacade?action=validateCert&ticketId=synthetic-ticket&appId=IAJ.CARNETJOVEN&webSessionId=synthetic-session&comeBackURL=aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ%3D"
    }
}

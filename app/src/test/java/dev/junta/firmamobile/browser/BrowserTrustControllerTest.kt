package dev.junta.firmamobile.browser

import dev.junta.firmamobile.profile.BuildTrustPolicy
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.Capability
import dev.junta.firmamobile.profile.ClientAuthPolicy
import dev.junta.firmamobile.profile.ExactOrigin
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SiteProfileRegistry
import dev.junta.firmamobile.profile.TrustMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserTrustControllerTest {
    private val invalidations = mutableListOf<BrowserTransitionReason>()
    private val registry = SiteProfileRegistry(BuiltInSiteProfiles.catalog, BuildTrustPolicy.RELEASE)
    private val controller = BrowserTrustController(
        BrowserUrlPolicy(registry),
        SensitiveFlowInvalidator(invalidations::add),
    )

    @Test
    fun resolvesInitiatorAndOnlyElevatesRedirectFromActiveProfile() {
        val start = controller.navigate(BuiltInSiteProfiles.catalog.profiles.single().startUrl.toString())
        assertEquals(TrustMode.TRUSTED_SIGNING, start.resolution.trustMode)
        assertEquals(ProfileId("junta-andalucia"), start.activeProfileId)

        val redirect = controller.navigate("https://sede.juntadeandalucia.es/path")
        assertEquals(TrustMode.TRUSTED_BROWSE, redirect.resolution.trustMode)

        controller.switchProfile(null)
        val direct = controller.navigate("https://sede.juntadeandalucia.es/path")
        assertEquals(TrustMode.BROWSE_ONLY, direct.resolution.trustMode)
        assertNull(direct.activeProfileId)
    }

    @Test
    fun unknownHttpsIsBrowseOnlyAndUnsafeSchemesOrAuthoritiesAreBlocked() {
        assertEquals(TrustMode.BROWSE_ONLY, controller.navigate("https://example.org/path?q=1").resolution.trustMode)
        listOf(
            "http://example.org", "file:///tmp/a", "data:text/plain,a", "javascript:alert(1)",
            "https://user@example.org", "https://127.0.0.1", "https://example.org:8443",
        ).forEach { raw -> assertEquals(raw, TrustMode.BLOCKED, controller.navigate(raw).resolution.trustMode) }
    }

    @Test
    fun resolvesAllSixTrustModesWithoutCapabilityInheritance() {
        val base = BuiltInSiteProfiles.catalog.profiles.single()
        val clientAuthProfile = base.copy(
            profileId = ProfileId("client-auth-fixture"),
            startUrl = java.net.URI("https://start.client-auth.example/"),
            initiatorOrigins = setOf(ExactOrigin.parse("https://start.client-auth.example")),
            redirectOrigins = emptySet(),
            trustedBrowseOrigins = emptySet(),
            endpoints = emptyMap(),
            operationPolicies = emptyMap(),
            capabilities = setOf(Capability.CLIENT_TLS_AUTH),
            clientAuthPolicy = ClientAuthPolicy(
                setOf(ExactOrigin.parse("https://tls.client-auth.example")),
            ),
        )
        val clientRegistry = SiteProfileRegistry(
            BuiltInSiteProfiles.catalog.copy(profiles = listOf(clientAuthProfile)),
            BuildTrustPolicy.RELEASE,
        )
        assertEquals(
            TrustMode.TRUSTED_CLIENT_AUTH,
            BrowserUrlPolicy(clientRegistry).resolve("https://tls.client-auth.example/").trustMode,
        )

        val policy = BrowserUrlPolicy(
            registry,
            externalOnlyOrigins = setOf(ExactOrigin.parse("https://external.example")),
        )
        assertEquals(TrustMode.TRUSTED_SIGNING, policy.resolve("https://www.juntadeandalucia.es/").trustMode)
        assertEquals(TrustMode.TRUSTED_BROWSE, policy.resolve("https://ws024.juntadeandalucia.es/").trustMode)
        assertEquals(TrustMode.BROWSE_ONLY, policy.resolve("https://unknown.example/").trustMode)
        assertEquals(TrustMode.EXTERNAL_ONLY, policy.resolve("https://external.example/").trustMode)
        assertEquals(TrustMode.BLOCKED, policy.resolve("javascript:alert(1)").trustMode)
    }

    @Test
    fun everyTransitionInvalidatesBeforeAdvancingTheEpoch() {
        controller.navigate("https://example.org")
        controller.reload()
        controller.back()
        controller.forward()
        controller.switchProfile(ProfileId("junta-andalucia"))

        assertEquals(
            listOf(
                BrowserTransitionReason.NAVIGATE, BrowserTransitionReason.RELOAD,
                BrowserTransitionReason.BACK, BrowserTransitionReason.FORWARD,
                BrowserTransitionReason.PROFILE_SWITCH,
            ),
            invalidations,
        )
        assertEquals(5L, controller.current().epoch)
        assertNull(controller.current().activeProfileId)
        assertEquals(TrustMode.BLOCKED, controller.current().resolution.trustMode)
    }
}

package dev.junta.firmamobile.browser

import dev.junta.firmamobile.profile.BuildTrustPolicy
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.Capability
import dev.junta.firmamobile.profile.ClientAuthPolicy
import dev.junta.firmamobile.profile.ClientAuthTransitionMode
import dev.junta.firmamobile.profile.ExactOrigin
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SiteProfileRegistry
import dev.junta.firmamobile.profile.TrustMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserTrustControllerTest {
    private val invalidations = mutableListOf<BrowserTransitionReason>()
    private val registry = SiteProfileRegistry(BuiltInSiteProfiles.catalog, BuildTrustPolicy.QA)
    private val controller = BrowserTrustController(
        BrowserUrlPolicy(registry, ProfileId("junta-andalucia")),
        SensitiveFlowInvalidator(invalidations::add),
    )

    @Test
    fun resolvesInitiatorAndOnlyElevatesRedirectFromActiveProfile() {
        val start = controller.navigate(BuiltInSiteProfiles.catalog.profiles.single {
            it.profileId == ProfileId("junta-andalucia")
        }.startUrl.toString())
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
    fun sharedClaveOriginIsTrustedOnlyThroughTheActiveSelectedProfile() {
        val claveUrl = "https://pasarela.clave.gob.es/Proxy2/ServiceProvider"
        val airefId = ProfileId("airef-instancia-general")
        val minecoId = ProfileId("ministerio-economia-instancia-generica")

        val airef = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == airefId }
        val airefController = BrowserTrustController(
            BrowserUrlPolicy(registry, airefId),
            SensitiveFlowInvalidator {},
        )
        assertEquals(airefId, airefController.navigate(airef.startUrl.toASCIIString()).activeProfileId)
        val airefClave = airefController.navigate(claveUrl)
        assertEquals(TrustMode.TRUSTED_BROWSE, airefClave.resolution.trustMode)
        assertEquals(airefId, airefClave.activeProfileId)

        val mineco = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == minecoId }
        val minecoController = BrowserTrustController(
            BrowserUrlPolicy(registry, minecoId),
            SensitiveFlowInvalidator {},
        )
        assertEquals(minecoId, minecoController.navigate(mineco.startUrl.toASCIIString()).activeProfileId)
        val minecoClave = minecoController.navigate(claveUrl)
        assertEquals(TrustMode.TRUSTED_BROWSE, minecoClave.resolution.trustMode)
        assertEquals(minecoId, minecoClave.activeProfileId)

        val freshAirefPolicy = BrowserUrlPolicy(registry, airefId)
        assertEquals(TrustMode.BROWSE_ONLY, freshAirefPolicy.resolve(claveUrl).trustMode)
    }

    @Test
    fun directNavigationToAnotherActiveCatalogProfileFailsClosed() {
        val qaRegistry = SiteProfileRegistry(
            BuiltInSiteProfiles.catalog,
            BuildTrustPolicy.QA,
        )
        val isolatedController = BrowserTrustController(
            BrowserUrlPolicy(qaRegistry, ProfileId("junta-andalucia")),
            SensitiveFlowInvalidator {},
        )
        val redSara = BuiltInSiteProfiles.catalog.profiles.single {
            it.profileId == ProfileId("reg-age-redsara")
        }

        val result = isolatedController.navigate(redSara.startUrl.toASCIIString())

        assertEquals(TrustMode.BLOCKED, result.resolution.trustMode)
        assertNull(result.activeProfileId)
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
        val base = BuiltInSiteProfiles.catalog.profiles.single {
            it.profileId == ProfileId("junta-andalucia")
        }
        val clientAuthProfile = base.copy(
            profileId = ProfileId("client-auth-fixture"),
            compatibilityStatus = dev.junta.firmamobile.profile.CompatibilityStatus.VERIFIED_E2E,
            startUrl = java.net.URI("https://start.client-auth.example/"),
            initiatorOrigins = setOf(ExactOrigin.parse("https://start.client-auth.example")),
            redirectOrigins = emptySet(),
            trustedBrowseOrigins = emptySet(),
            endpoints = emptyMap(),
            operationPolicies = emptyMap(),
            capabilities = setOf(Capability.CLIENT_TLS_AUTH),
            clientAuthPolicy = ClientAuthPolicy(
                transitionMode = ClientAuthTransitionMode.REDIRECT_AFTER_SOURCE,
                requestOrigins = setOf(ExactOrigin.parse("https://tls.client-auth.example")),
                sourceUrls = setOf(java.net.URI("https://start.client-auth.example/auth")),
                requestPath = "/facade",
                fixedQueryParameters = mapOf("app" to "test"),
                requiredEphemeralQueryParameters = setOf("ticket"),
                allowEmptyIssuerList = true,
                grantTtlSeconds = 15,
            ),
        )
        val clientRegistry = SiteProfileRegistry(
            BuiltInSiteProfiles.catalog.copy(profiles = listOf(clientAuthProfile)),
            BuildTrustPolicy.RELEASE,
        )
        assertEquals(
            TrustMode.BROWSE_ONLY,
            BrowserUrlPolicy(clientRegistry, clientAuthProfile.profileId).resolve("https://tls.client-auth.example/").trustMode,
        )
        val clientStart = BrowserTrustController(
            BrowserUrlPolicy(clientRegistry, clientAuthProfile.profileId),
            SensitiveFlowInvalidator {},
        ).navigate("https://start.client-auth.example/")
        assertEquals(TrustMode.TRUSTED_CLIENT_AUTH, clientStart.resolution.trustMode)
        assertEquals(ProfileId("client-auth-fixture"), clientStart.activeProfileId)

        val policy = BrowserUrlPolicy(
            registry = registry,
            selectedProfileId = ProfileId("junta-andalucia"),
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

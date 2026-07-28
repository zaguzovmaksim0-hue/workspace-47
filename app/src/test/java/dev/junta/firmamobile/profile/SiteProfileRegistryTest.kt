package dev.junta.firmamobile.profile

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SiteProfileRegistryTest {
    @Test
    fun `release and qa registries activate verified carne joven profile`() {
        val carneId = ProfileId("carne-joven-andalucia")

        val releaseProfile = BuiltInSiteProfiles.releaseRegistry.profile(carneId)
        assertNotNull(releaseProfile)
        assertEquals(CompatibilityStatus.VERIFIED_E2E, releaseProfile?.compatibilityStatus)
        assertEquals(ProfileActivation.ENABLED, releaseProfile?.activation)

        val qaProfile = BuiltInSiteProfiles.qaRegistry.profile(carneId)
        assertNotNull(qaProfile)
        assertEquals(CompatibilityStatus.VERIFIED_E2E, qaProfile?.compatibilityStatus)
        assertEquals(ProfileActivation.ENABLED, qaProfile?.activation)
    }

    @Test
    fun `resolves carne joven start url and facade url in release registry`() {
        val startUri = URI("https://ws104.juntadeandalucia.es/carneJoven/cjservlet/portal/index.jsp")
        val facadeUri = URI("https://ws235.juntadeandalucia.es/authenticationFacade")

        val startResolved = BuiltInSiteProfiles.releaseRegistry.resolve(startUri)
        assertNotNull(startResolved)
        assertEquals(ProfileId("carne-joven-andalucia"), startResolved?.profile?.profileId)
        assertEquals(TrustMode.TRUSTED_CLIENT_AUTH, startResolved?.trustMode)

        val facadeResolved = BuiltInSiteProfiles.releaseRegistry.resolve(facadeUri)
        assertNotNull(facadeResolved)
        assertEquals(ProfileId("carne-joven-andalucia"), facadeResolved?.profile?.profileId)
        assertEquals(TrustMode.BROWSE_ONLY, facadeResolved?.trustMode)
    }
    @Test
    fun `qa resolves aragon siraw while release excludes it`() {
        val profileId = ProfileId("aragon-siraw")
        val startUri = URI("https://aplicaciones.aragon.es/siraw/pages/login.xhtml?origen=siefw")

        val qaProfile = BuiltInSiteProfiles.qaRegistry.profile(profileId)
        assertNotNull(qaProfile)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, qaProfile?.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, qaProfile?.activation)
        assertEquals(TrustMode.TRUSTED_SIGNING, BuiltInSiteProfiles.qaRegistry.resolve(startUri)?.trustMode)

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(startUri))
    }

}

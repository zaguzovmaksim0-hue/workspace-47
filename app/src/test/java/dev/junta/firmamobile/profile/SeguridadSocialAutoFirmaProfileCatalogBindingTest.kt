package dev.junta.firmamobile.profile

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeguridadSocialAutoFirmaProfileCatalogBindingTest {
    @Test
    fun qaProfileBindsOnlyCertificateLoginAndOwnsNoLocalSigningCapability() {
        val profile = BuiltInSiteProfiles.qaRegistry.profile(ProfileId(PROFILE_ID))!!

        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(URI(START_URL), profile.startUrl)
        assertEquals(
            setOf(ExactOrigin.parse(ORIGIN), ExactOrigin.parse("https://idp.seg-social.es")), profile.initiatorOrigins,
        )
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        val policy = checkNotNull(profile.clientAuthPolicy)
        assertEquals(ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE, policy.transitionMode)
        assertEquals(HttpMethod.POST, policy.requestMethod)
        assertEquals(setOf(URI("https://idp.seg-social.es/PGIS/Login")), policy.sourceUrls)
        assertEquals(mapOf("seleccion" to "IPCE"), policy.sourceFixedQueryParameters)
        assertEquals(setOf(ExactOrigin.parse("https://ipce.seg-social.es")), policy.requestOrigins)
        assertEquals("/IPCE/Login", policy.requestPath)
        assertFalse(Capability.SIGN in profile.capabilities)
        assertFalse(Capability.AFIRMA_URI in profile.capabilities)
    }

    @Test
    fun releaseRegistryDoesNotExposeTheQaOnlyAutoFirmaHandoffProfile() {
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(ProfileId(PROFILE_ID)))
    }

    private companion object {
        const val PROFILE_ID = "seguridad-social-sede-autofirma"
        const val ORIGIN = "https://sede.seg-social.gob.es"
        const val START_URL =
            "https://sede.seg-social.gob.es/wps/portal/sede/sede/Inicio/RegistroElectronicoApod/NREASS_3?changeLanguage=es"
    }
}

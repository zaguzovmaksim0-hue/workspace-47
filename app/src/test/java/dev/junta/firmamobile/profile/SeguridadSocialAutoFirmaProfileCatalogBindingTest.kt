package dev.junta.firmamobile.profile

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeguridadSocialAutoFirmaProfileCatalogBindingTest {
    @Test
    fun qaProfileBindsOnlyPublicEntryAndOwnsNoLocalSigningCapability() {
        val profile = BuiltInSiteProfiles.qaRegistry.profile(ProfileId(PROFILE_ID))!!

        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(URI(START_URL), profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse(ORIGIN)), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
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

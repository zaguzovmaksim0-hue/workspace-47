package dev.junta.firmamobile.profile

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MitesProfileCatalogBindingTest {
    @Test
    fun preservesExactQaOnlyMitesCertificateLoginContract() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single {
            it.profileId == ProfileId("mites-certificate-login")
        }
        val operation = profile.operationPolicies.getValue(ProtocolOperation.SIGN)

        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(URI("https://sede.mites.gob.es/"), profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://sede.mites.gob.es")), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertEquals(setOf(Capability.SIGN), profile.capabilities)
        assertEquals(setOf(SignatureAlgorithm.SHA512_WITH_RSA), operation.algorithms)
        assertEquals(SignatureFormat.CADES, operation.format)
        assertEquals(SignaturePackaging.DETACHED, operation.packaging)
        assertEquals(SignatureMode.IMPLICIT, operation.mode)
        assertEquals(
            linkedMapOf(
                "mode" to "implicit",
                "filters.1" to "signingCert:;keyusage.nonrepudiation:true;nonexpired:",
            ),
            operation.fixedExtraProperties,
        )
        assertEquals("autoscript-sign-callback-v1", operation.callbackContractId.value)
    }
}

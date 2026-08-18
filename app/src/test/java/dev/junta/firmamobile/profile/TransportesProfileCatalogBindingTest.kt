package dev.junta.firmamobile.profile

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportesProfileCatalogBindingTest {
    @Test
    fun preservesExactQaOnlyTransportesQysLoginContract() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single {
            it.profileId == ProfileId("transportes-qys-cert-login")
        }
        val operation = profile.operationPolicies.getValue(ProtocolOperation.SIGN)

        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(
            URI("https://sede.transportes.gob.es/MFOM.genericprocedure.web/?id=7002"),
            profile.startUrl,
        )
        assertEquals(
            setOf(ExactOrigin.parse("https://sede.transportes.gob.es")),
            profile.initiatorOrigins,
        )
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertEquals(setOf(Capability.SIGN, Capability.LEGACY_SHA1), profile.capabilities)
        assertEquals(setOf(SignatureAlgorithm.SHA1_WITH_RSA), operation.algorithms)
        assertEquals(SignatureFormat.XADES, operation.format)
        assertEquals(SignaturePackaging.ATTACHED, operation.packaging)
        assertEquals(null, operation.mode)
        assertEquals(
            linkedMapOf(
                "format" to "XAdES Enveloped",
                "includeOnlySigningCertificate" to "true",
                "nodeToSign" to "tag1",
                "applySystemDate" to "false",
                "filters.1" to "keyusage.digitalsignature:true;nonexpired:",
                "sticky" to "true",
            ),
            operation.fixedExtraProperties,
        )
        assertEquals("autoscript-sign-callback-v1", operation.callbackContractId.value)
    }
}

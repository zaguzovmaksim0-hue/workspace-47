package dev.junta.firmamobile.profile

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Test

class CdtiProfileCatalogBindingTest {
    @Test
    fun preservesExactQaOnlyCdtiCertificateValidationContract() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single {
            it.profileId == ProfileId("cdti-certificate-validation")
        }
        val operation = profile.operationPolicies.getValue(ProtocolOperation.SIGN)

        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(
            URI("https://sede.cdti.gob.es/AreaPrivada/Expedientes/Common/Certificados/ValidarCertificado.aspx"),
            profile.startUrl,
        )
        assertEquals(setOf(ExactOrigin.parse("https://sede.cdti.gob.es")), profile.initiatorOrigins)
        assertEquals(setOf(Capability.SIGN), profile.capabilities)
        assertEquals(setOf(SignatureAlgorithm.SHA512_WITH_RSA), operation.algorithms)
        assertEquals(SignatureFormat.XADES, operation.format)
        assertEquals(SignaturePackaging.ATTACHED, operation.packaging)
        assertEquals(null, operation.mode)
        assertEquals(mapOf("filters" to "nonexpired"), operation.fixedExtraProperties)
        assertEquals("autoscript-sign-callback-v1", operation.callbackContractId.value)
    }
}

package dev.junta.firmamobile.network

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeNetworkUrlPolicyTest {
    private val policy = SafeNetworkUrlPolicy()

    @Test
    fun exactObservedJuntaEndpointIsTheOnlyAcceptedServerUrl() {
        val accepted = policy.validateEndpoint(SafeNetworkUrlPolicy.JUNTA_TRIPHASE_ENDPOINT)

        assertTrue(accepted is NetworkUrlValidation.Allowed)
        assertEquals(
            SafeNetworkUrlPolicy.JUNTA_TRIPHASE_ENDPOINT,
            (accepted as NetworkUrlValidation.Allowed).url.uri.toASCIIString(),
        )
    }

    @Test
    fun endpointRejectsEveryAuthoritySchemePathAndMetadataVariation() {
        val blocked = listOf(
            "http://ws024.juntadeandalucia.es/afirma-validator-miniapplet-1_4/sign/TriPhaseSignatureService",
            "https://user:password@ws024.juntadeandalucia.es/afirma-validator-miniapplet-1_4/sign/TriPhaseSignatureService",
            "https://ws024.juntadeandalucia.es:444/afirma-validator-miniapplet-1_4/sign/TriPhaseSignatureService",
            "https://evil.example/afirma-validator-miniapplet-1_4/sign/TriPhaseSignatureService",
            "https://127.0.0.1/afirma-validator-miniapplet-1_4/sign/TriPhaseSignatureService",
            "https://ws024.juntadeandalucia.es./afirma-validator-miniapplet-1_4/sign/TriPhaseSignatureService",
            "https://ws024.juntadeandalucia.es/other",
            "https://ws024.juntadeandalucia.es/afirma-validator-miniapplet-1_4/sign/%54riPhaseSignatureService",
            SafeNetworkUrlPolicy.JUNTA_TRIPHASE_ENDPOINT + "?op=pre",
            SafeNetworkUrlPolicy.JUNTA_TRIPHASE_ENDPOINT + "#fragment",
        )

        blocked.forEach { candidate ->
            assertTrue(candidate, policy.validateEndpoint(candidate) is NetworkUrlValidation.Blocked)
        }
    }

    @Test
    fun networkRequestKeepsParametersOutOfTheValidatedEndpointUrl() {
        val accepted = policy.validateRequest(URI(SafeNetworkUrlPolicy.JUNTA_TRIPHASE_ENDPOINT))
        val queryOnWire = policy.validateRequest(
            URI(SafeNetworkUrlPolicy.JUNTA_TRIPHASE_ENDPOINT + "?op=pre&cop=sign"),
        )
        val wrongPath = policy.validateRequest(
            URI("https://ws024.juntadeandalucia.es/other"),
        )

        assertTrue(accepted is NetworkUrlValidation.Allowed)
        assertTrue(queryOnWire is NetworkUrlValidation.Blocked)
        assertTrue(wrongPath is NetworkUrlValidation.Blocked)
    }
}

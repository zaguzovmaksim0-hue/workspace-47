package dev.junta.firmamobile.profile

import org.junit.Assert.assertTrue
import org.junit.Test

// Non-443 ports skip the shared origin check, so these profiles need their own exact pins.
class PortTlsProfilePinTest {
    private data class Mutation(val name: String, val anchor: String, val replacement: String)

    private val mutations = listOf(
        Mutation("toledo port 843->844", "\"requestPort\":843", "\"requestPort\":844"),
        Mutation("toledo port 843->443", "\"requestPort\":843", "\"requestPort\":443"),
        Mutation("valladolid port 21460->21461", "\"requestPort\":21460", "\"requestPort\":21461"),
        Mutation("valladolid port 21460->443", "\"requestPort\":21460", "\"requestPort\":443"),
        Mutation(
            "toledo requestOrigins host",
            "\"requestOrigins\":[\"https://diputacion.toledo.gob.es\"]",
            "\"requestOrigins\":[\"https://otro.example.es\"]",
        ),
        Mutation(
            "valladolid requestOrigins host",
            "\"requestOrigins\":[\"https://www.sede.diputaciondevalladolid.es\"]",
            "\"requestOrigins\":[\"https://otro.example.es\"]",
        ),
        Mutation(
            "toledo requestPath",
            "\"requestPath\":\"/SIGEM_AutenticacionWeb/validacionCertificado.do\",\"fixedQueryParameters\":{},\"requiredEphemeralQueryParameters\":[],\"allowEmptyIssuerList\":false,\"grantTtlSeconds\":15,\"requestPort\":843",
            "\"requestPath\":\"/SIGEM_AutenticacionWeb/otro.do\",\"fixedQueryParameters\":{},\"requiredEphemeralQueryParameters\":[],\"allowEmptyIssuerList\":false,\"grantTtlSeconds\":15,\"requestPort\":843",
        ),
        Mutation(
            "valladolid requestPath",
            "\"requestPath\":\"/c/portal/cert-login\"",
            "\"requestPath\":\"/c/portal/otro\"",
        ),
        Mutation(
            "toledo grantTtlSeconds 15->14",
            "\"allowEmptyIssuerList\":false,\"grantTtlSeconds\":15,\"requestPort\":843",
            "\"allowEmptyIssuerList\":false,\"grantTtlSeconds\":14,\"requestPort\":843",
        ),
        Mutation(
            "valladolid grantTtlSeconds 15->14",
            "\"allowEmptyIssuerList\":true,\"grantTtlSeconds\":15,\"requestPort\":21460",
            "\"allowEmptyIssuerList\":true,\"grantTtlSeconds\":14,\"requestPort\":21460",
        ),
        Mutation(
            "toledo allowEmptyIssuerList flip",
            "\"allowEmptyIssuerList\":false,\"grantTtlSeconds\":15,\"requestPort\":843",
            "\"allowEmptyIssuerList\":true,\"grantTtlSeconds\":15,\"requestPort\":843",
        ),
        Mutation(
            "valladolid allowEmptyIssuerList flip",
            "\"allowEmptyIssuerList\":true,\"grantTtlSeconds\":15,\"requestPort\":21460",
            "\"allowEmptyIssuerList\":false,\"grantTtlSeconds\":15,\"requestPort\":21460",
        ),
        Mutation(
            "toledo sourceUrl TRAM_31->TRAM_32",
            "sourceUrls\":[\"https://diputacion.toledo.gob.es/SIGEM_AutenticacionWeb/seleccionEntidad.do?REDIRECCION=RegistroTelematico&tramiteId=TRAM_31",
            "sourceUrls\":[\"https://diputacion.toledo.gob.es/SIGEM_AutenticacionWeb/seleccionEntidad.do?REDIRECCION=RegistroTelematico&tramiteId=TRAM_32",
        ),
    )

    @Test
    fun canonicalCatalogStillParses() {
        SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON)
    }

    @Test
    fun portAndTlsMutationsAreRejected() {
        val accepted = mutableListOf<String>()
        for (m in mutations) {
            val occurrences = BuiltInSiteProfiles.JSON.split(m.anchor).size - 1
            assertTrue("anchor not unique for ${m.name}: $occurrences", occurrences == 1)
            val mutated = BuiltInSiteProfiles.JSON.replace(m.anchor, m.replacement)
            try {
                SiteProfileCatalogParser.parse(mutated)
                accepted += m.name
            } catch (_: IllegalArgumentException) {
                // rejected as required
            }
        }
        assertTrue("accepted: $accepted", accepted.isEmpty())
    }
}

package dev.junta.firmamobile.profile

import org.junit.Assert.assertTrue
import org.junit.Test

// These Cl@ve profiles define their client-auth contract only in the JSON catalogue.
class ClaveTlsProfilePinTest {
    private val ids = listOf(
        "asturias-miprincipado",
        "diputacion-ourense-sede",
        "diputacion-sevilla-sede",
        "diputacion-a-coruna-solicitud-general",
    )

    private val mutations = ids.flatMap { id ->
        listOf(
            TlsProfileMutation("$id requestOrigins host", id, """"requestOrigins"\s*:\s*\["[^"]*"""", """"requestOrigins":["https://otro.example.es""""),
            TlsProfileMutation("$id requestPath", id, """"requestPath"\s*:\s*"[^"]*"""", """"requestPath":"/IdP2/Otro""""),
            TlsProfileMutation("$id sourceUrl", id, """("sourceUrls"\s*:\s*\["[^"]*Proxy2/)Service[A-Za-z]+""", "$1ServiceOtro"),
            TlsProfileMutation("$id grantTtlSeconds 15->14", id, """"grantTtlSeconds"\s*:\s*15""", """"grantTtlSeconds":14"""),
            TlsProfileMutation("$id allowEmptyIssuerList flip", id, """"allowEmptyIssuerList"\s*:\s*true""", """"allowEmptyIssuerList":false"""),
            TlsProfileMutation("$id requestPort 8443", id, """"requestPort"\s*:\s*443""", """"requestPort":8443"""),
            TlsProfileMutation("$id fixedQueryParameters", id, """"fixedQueryParameters"\s*:\s*\{\}""", """"fixedQueryParameters":{"a":"b"}"""),
            TlsProfileMutation("$id certificate rules", id, """"requireDigitalSignatureKeyUsage"\s*:\s*true""", """"requireDigitalSignatureKeyUsage":false"""),
        )
    }

    @Test
    fun canonicalCatalogStillParses() {
        SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON)
    }

    @Test
    fun claveTlsMutationsAreRejected() {
        val accepted = TlsProfileMutations.accepted(mutations)
        assertTrue("accepted: $accepted", accepted.isEmpty())
    }
}

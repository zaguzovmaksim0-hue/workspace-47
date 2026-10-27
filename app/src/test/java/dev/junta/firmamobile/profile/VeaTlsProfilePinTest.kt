package dev.junta.firmamobile.profile

import org.junit.Assert.assertTrue
import org.junit.Test

// The VEA PEG in-place contract (source constraints, six continuation hops, return URLs) lives only in JSON.
class VeaTlsProfilePinTest {
    private val id = "junta-andalucia-vea-peg"
    private val main = """("requestPath"\s*:\s*"/authenticationFacade",\s*"fixedQueryParameters"\s*:\s*\{"action":"validateCert","appId":")"""

    private val mutations = listOf(
        TlsProfileMutation("profileVersion", id, """"profileVersion"\s*:\s*3""", """"profileVersion":4"""),
        TlsProfileMutation("startUrl", id, """"startUrl"\s*:\s*"[^"]*PEG_VEA"""", """"startUrl":"https://veaja.cloud.juntadeandalucia.es/otro""""),
        TlsProfileMutation("requestOrigins host", id, """"requestOrigins"\s*:\s*\["[^"]*"""", """"requestOrigins":["https://otro.example.es""""),
        TlsProfileMutation("requestPath", id, """"requestPath"\s*:\s*"/authenticationFacade"""", """"requestPath":"/otro""""),
        TlsProfileMutation("sourceUrl", id, """("sourceUrls"\s*:\s*\["https://api-veaja\.cloud\.juntadeandalucia\.es)/auth/login""", "$1/auth/otro"),
        TlsProfileMutation("main appId", id, main + "CHIE\\.VEA", "$1OTRO"),
        TlsProfileMutation("grantTtlSeconds 60->61", id, """"grantTtlSeconds"\s*:\s*60""", """"grantTtlSeconds":61"""),
        TlsProfileMutation("requestPort 8443", id, """"requestPort"\s*:\s*443""", """"requestPort":8443"""),
        TlsProfileMutation("allowEmptyIssuerList flip", id, """"allowEmptyIssuerList"\s*:\s*true""", """"allowEmptyIssuerList":false"""),
        TlsProfileMutation("requireOfferedKeyTypeMatch flip", id, """"requireOfferedKeyTypeMatch"\s*:\s*false""", """"requireOfferedKeyTypeMatch":true"""),
        TlsProfileMutation("requireTlsClientAuthExtendedKeyUsage flip", id, """"requireTlsClientAuthExtendedKeyUsage"\s*:\s*false""", """"requireTlsClientAuthExtendedKeyUsage":true"""),
        TlsProfileMutation("requestMethod POST", id, """"requestMethod"\s*:\s*"GET"""", """"requestMethod":"POST""""),
        TlsProfileMutation("sourceFixed modoAcceso", id, """"modoAcceso":"afirma"""", """"modoAcceso":"otro""""),
        TlsProfileMutation("sourceRequiredEphemeral", id, """"sourceRequiredEphemeralQueryParameters"\s*:\s*\["redirectUrl"\]""", """"sourceRequiredEphemeralQueryParameters":["redirectUrl","extra"]"""),
        TlsProfileMutation("base64 constraint origin", id, """("redirectUrl"\s*:\s*\{"origin":"https://)veaja""", "$1otro"),
        TlsProfileMutation("base64 constraint path", id, """"path":"/inicio/procedimiento-detalle/PEG_VEA"""", """"path":"/inicio/otro""""),
        TlsProfileMutation("base64 constraint fixed", id, """"iniciarSolicitud":"true"""", """"iniciarSolicitud":"false""""),
        TlsProfileMutation("base64 constraint required", id, """"procedureId","versionId"\]""", """"procedureId","versionId","extra"]"""),
        TlsProfileMutation("continuation origin", id, """ws235-1\.juntadeandalucia""", "ws235-9.juntadeandalucia"),
        TlsProfileMutation("continuation path", id, """"/authenticationFacade/2"""", """"/authenticationFacade/9""""),
        TlsProfileMutation("continuation appId", id, """("path":"/authenticationFacade/2","fixedQueryParameters":\{"action":"validateCert","appId":")CHIE\.VEA""", "$1OTRO"),
        TlsProfileMutation(
            "continuation dropped",
            id,
            """,\s*\{"origin":"https://ws235-6\.juntadeandalucia\.es"[^\n]*\}(?=\s*\])""",
            "",
        ),
        TlsProfileMutation(
            "continuation added",
            id,
            """("requestContinuationUrlConstraints"\s*:\s*\[)""",
            """$1{"origin":"https://otro.example.es","path":"/x","fixedQueryParameters":{},"requiredEphemeralQueryParameters":[]},""",
        ),
        TlsProfileMutation("return path", id, """"/auth/endLogin"""", """"/auth/otro""""),
        TlsProfileMutation("return required", id, """"requiredEphemeralQueryParameters":\["resCode"\]""", """"requiredEphemeralQueryParameters":["resCode","extra"]"""),
        TlsProfileMutation(
            "return added",
            id,
            """("returnUrlConstraints"\s*:\s*\[)""",
            """$1{"origin":"https://otro.example.es","path":"/x","fixedQueryParameters":{},"requiredEphemeralQueryParameters":[]},""",
        ),
        TlsProfileMutation("certificate rules", id, """"requireDigitalSignatureKeyUsage":false""", """"requireDigitalSignatureKeyUsage":true"""),
    )

    @Test
    fun canonicalCatalogStillParses() {
        SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON)
    }

    @Test
    fun veaTlsMutationsAreRejected() {
        val accepted = TlsProfileMutations.accepted(mutations)
        assertTrue("accepted: $accepted", accepted.isEmpty())
    }
}

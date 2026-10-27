package dev.junta.firmamobile.profile

import org.junit.Assert.assertTrue
import org.junit.Test

// Carné Joven is the only client-auth profile enabled in release, so its TLS contract is pinned in Kotlin.
class CarneJovenTlsProfilePinTest {
    private val id = "carne-joven-andalucia"

    private val mutations = listOf(
        TlsProfileMutation("host ws235->ws236", id, """ws235\.juntadeandalucia\.es""", "ws236.juntadeandalucia.es"),
        TlsProfileMutation("other https host", id, """"requestOrigins"\s*:\s*\["[^"]*"""", """"requestOrigins":["https://otro.example.es""""),
        TlsProfileMutation("requestPath", id, """"requestPath"\s*:\s*"[^"]*"""", """"requestPath":"/otro""""),
        TlsProfileMutation("comeBackURL", id, """"comeBackURL"\s*:\s*"aHR0cHM6""", """"comeBackURL":"aHR0cDov"""),
        TlsProfileMutation("appId", id, """IAJ\.CARNETJOVEN""", "IAJ.OTRO"),
        TlsProfileMutation("action", id, """"action"\s*:\s*"validateCert"""", """"action":"otro""""),
        TlsProfileMutation("extra ephemeral parameter", id, """"ticketId","webSessionId"\]""", """"ticketId","webSessionId","extra"]"""),
        TlsProfileMutation("grantTtlSeconds 15->14", id, """"grantTtlSeconds"\s*:\s*15""", """"grantTtlSeconds":14"""),
        TlsProfileMutation("allowEmptyIssuerList flip", id, """"allowEmptyIssuerList"\s*:\s*true""", """"allowEmptyIssuerList":false"""),
        TlsProfileMutation("requestPort 8443", id, """"grantTtlSeconds"\s*:\s*15""", """"grantTtlSeconds":15,"requestPort":8443"""),
        TlsProfileMutation("sourceUrl", id, """CallAuthenticationServlet"\]""", """CallOtro"]"""),
        TlsProfileMutation("transitionMode", id, """REDIRECT_AFTER_SOURCE""", "DIRECT_FROM_SOURCE"),
        TlsProfileMutation("certificate algorithms", id, """"allowedKeyAlgorithms"\s*:\s*\["RSA"\]""", """"allowedKeyAlgorithms":["RSA","EC"]"""),
        TlsProfileMutation("initiator origin", id, """"initiatorOrigins"\s*:\s*\["[^"]*"""", """"initiatorOrigins":["https://otro.example.es""""),
    )

    @Test
    fun canonicalCatalogStillParses() {
        SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON)
    }

    @Test
    fun carneJovenTlsMutationsAreRejected() {
        val accepted = TlsProfileMutations.accepted(mutations)
        assertTrue("accepted: $accepted", accepted.isEmpty())
    }
}

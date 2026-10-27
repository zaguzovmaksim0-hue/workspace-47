package dev.junta.firmamobile.profile

import org.junit.Assert.assertTrue
import org.junit.Test

// These direct profiles carry their client-auth contract only in the JSON catalogue,
// so the parser must pin every reviewed value on its own.
class DirectTlsProfilePinTest {
    private data class Mutation(val name: String, val profileId: String, val pattern: String, val replacement: String)

    private val aeat = "aeat-mis-datos-censales"
    private val importass = "tgss-importass-client-auth"

    private val mutations = listOf(
        Mutation("aeat requestOrigins host", aeat, """"requestOrigins"\s*:\s*\["[^"]*"""", """"requestOrigins":["https://otro.example.es""""),
        Mutation("aeat requestPath", aeat, """"requestPath"\s*:\s*"[^"]*"""", """"requestPath":"/wlpl/BUGC-JDIT/Otro""""),
        Mutation("aeat grantTtlSeconds 15->14", aeat, """"grantTtlSeconds"\s*:\s*15""", """"grantTtlSeconds":14"""),
        Mutation("aeat allowEmptyIssuerList flip", aeat, """"allowEmptyIssuerList"\s*:\s*false""", """"allowEmptyIssuerList":true"""),
        Mutation("aeat sourceUrl", aeat, """("sourceUrls"\s*:\s*\["[^"]*)mi-area-personal""", "$1otra-area"),
        Mutation("aeat requestPort 8443", aeat, """"grantTtlSeconds"\s*:\s*15""", """"grantTtlSeconds":15,"requestPort":8443"""),
        Mutation("aeat fixedQueryParameters", aeat, """"fixedQueryParameters"\s*:\s*\{\}""", """"fixedQueryParameters":{"a":"b"}"""),
        Mutation("importass requestOrigins host", importass, """"requestOrigins"\s*:\s*\["[^"]*"""", """"requestOrigins":["https://otro.example.es""""),
        Mutation("importass requestPath", importass, """"requestPath"\s*:\s*"[^"]*"""", """"requestPath":"/IPCE/Otro""""),
        Mutation("importass grantTtlSeconds 15->14", importass, """"grantTtlSeconds"\s*:\s*15""", """"grantTtlSeconds":14"""),
        Mutation("importass allowEmptyIssuerList flip", importass, """"allowEmptyIssuerList"\s*:\s*false""", """"allowEmptyIssuerList":true"""),
        Mutation("importass sourceUrl IPCE->OTHER", importass, """("sourceUrls"\s*:\s*\["[^"]*)seleccion=IPCE""", "$1seleccion=OTHER"),
        Mutation("importass requestPort 443->8443", importass, """"requestPort"\s*:\s*443""", """"requestPort":8443"""),
        Mutation("importass sourceFixedQueryParameters", importass, """"sourceFixedQueryParameters"\s*:\s*\{\}""", """"sourceFixedQueryParameters":{"a":"b"}"""),
        Mutation("importass initiatorOrigins idp host", importass, """"https://idp\.seg-social\.es"\s*\]""", """"https://otro.example.es"]"""),
    )

    @Test
    fun canonicalCatalogStillParses() {
        SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON)
    }

    @Test
    fun directTlsMutationsAreRejected() {
        val json = BuiltInSiteProfiles.JSON
        val accepted = mutableListOf<String>()
        for (m in mutations) {
            val start = Regex(""""profileId"\s*:\s*"${Regex.escape(m.profileId)}"""").find(json)!!.range.first
            val end = Regex(""""profileId"\s*:""").find(json, start + 1)?.range?.first ?: json.length
            val block = json.substring(start, end)
            val regex = Regex(m.pattern)
            val matches = regex.findAll(block).count()
            assertTrue("pattern not unique for ${m.name}: $matches", matches == 1)
            val mutated = json.substring(0, start) + regex.replace(block, m.replacement) + json.substring(end)
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

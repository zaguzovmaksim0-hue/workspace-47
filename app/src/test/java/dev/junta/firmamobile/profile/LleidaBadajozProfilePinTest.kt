package dev.junta.firmamobile.profile

import org.junit.Assert.assertTrue
import org.junit.Test

// Lleida and Badajoz carry their navigation contract only in the JSON catalogue,
// so the parser must pin the exact origin, start URL and initiator set on its own.
class LleidaBadajozProfilePinTest {
    private data class Mutation(
        val name: String,
        val profileId: String,
        val pattern: String,
        val replacement: String,
        val expectedMatches: Int,
    )

    private val lleida = "diputacion-lleida-sede"
    private val badajoz = "diputacion-badajoz-portal"

    private val mutations = listOf(
        Mutation(
            "lleida consistent host",
            lleida,
            """seu\.diputaciolleida\.cat""",
            "otro.example.es",
            4,
        ),
        Mutation(
            "lleida start path",
            lleida,
            """"startUrl"\s*:\s*"[^"]*"""",
            """"startUrl":"https://seu.diputaciolleida.cat/otro"""",
            1,
        ),
        Mutation(
            "lleida extra initiator",
            lleida,
            """"initiatorOrigins"\s*:\s*\["https://seu\.diputaciolleida\.cat"\]""",
            """"initiatorOrigins":["https://seu.diputaciolleida.cat","https://otro.example.es"]""",
            1,
        ),
        Mutation(
            "lleida initiator host",
            lleida,
            """"initiatorOrigins"\s*:\s*\["https://seu\.diputaciolleida\.cat"\]""",
            """"initiatorOrigins":["https://otro.example.es"]""",
            1,
        ),
        Mutation(
            "badajoz consistent host",
            badajoz,
            """sede\.dip-badajoz\.es""",
            "otro.example.es",
            4,
        ),
        Mutation(
            "badajoz start path",
            badajoz,
            """"startUrl"\s*:\s*"[^"]*"""",
            """"startUrl":"https://sede.dip-badajoz.es/otro"""",
            1,
        ),
        Mutation(
            "badajoz extra initiator",
            badajoz,
            """"initiatorOrigins"\s*:\s*\["https://sede\.dip-badajoz\.es"\]""",
            """"initiatorOrigins":["https://sede.dip-badajoz.es","https://otro.example.es"]""",
            1,
        ),
        Mutation(
            "badajoz initiator host",
            badajoz,
            """"initiatorOrigins"\s*:\s*\["https://sede\.dip-badajoz\.es"\]""",
            """"initiatorOrigins":["https://otro.example.es"]""",
            1,
        ),
    )

    @Test
    fun canonicalCatalogStillParses() {
        SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON)
    }

    @Test
    fun lleidaBadajozMutationsAreRejected() {
        val json = BuiltInSiteProfiles.JSON
        val accepted = mutableListOf<String>()
        for (m in mutations) {
            val start = Regex(""""profileId"\s*:\s*"${Regex.escape(m.profileId)}"""").find(json)!!.range.first
            val end = Regex(""""profileId"\s*:""").find(json, start + 1)?.range?.first ?: json.length
            val block = json.substring(start, end)
            val regex = Regex(m.pattern)
            val matches = regex.findAll(block).count()
            assertTrue("pattern match count for ${m.name}: $matches", matches == m.expectedMatches)
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

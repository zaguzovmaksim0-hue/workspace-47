package dev.junta.firmamobile.profile

import org.junit.Assert.assertTrue

// Applies one regex edit inside a single profile block of the built-in catalogue JSON.
data class TlsProfileMutation(val name: String, val profileId: String, val pattern: String, val replacement: String)

object TlsProfileMutations {
    /** Names of the mutations the parser still accepts; each pattern must match exactly once. */
    fun accepted(mutations: List<TlsProfileMutation>): List<String> {
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
        return accepted
    }
}

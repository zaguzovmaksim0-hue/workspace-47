package dev.junta.firmamobile.signing

/** A deliberately literal subset of Java properties, shared with the JS bridge.
 * Reorders keys and normalizes line endings only; never changes policy values,
 * interprets escapes, ignores unknown keys, or accepts ambiguous duplicates.
 */
object LiteralSigningProperties {
    fun canonicalize(raw: String, expected: String): String? {
        val required = parse(expected) ?: return null
        val actual = parse(raw) ?: return null
        return expected.takeIf { actual == required }
    }

    private fun parse(raw: String): Map<String, String>? {
        if (raw.length > 65_536) return null
        val lines = raw.split(Regex("\\r\\n|\\n|\\r"))
        if (lines.size > 64) return null
        val values = linkedMapOf<String, String>()
        for (line in lines) {
            if (line.isEmpty()) continue
            val separator = line.indexOf('=')
            if (separator <= 0 || line.any { it.isISOControl() || it == '\\' }) return null
            val key = line.substring(0, separator)
            if (!KEY.matches(key) || values.put(key, line.substring(separator + 1)) != null) return null
        }
        return values
    }

    private val KEY = Regex("[A-Za-z0-9_.-]+")
}

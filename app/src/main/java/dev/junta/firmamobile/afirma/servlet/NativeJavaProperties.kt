package dev.junta.firmamobile.afirma.servlet

import java.io.StringReader
import java.util.Properties

/** Java Properties grammar, not URL/form decoding. The platform parser owns
 * escaping/continuations; policy owns ambiguity, size and accepted parameters.
 * No XML parser, file, class loader, system property or network lookup is used. */
internal object NativeJavaProperties {
    const val MAX_TEXT_CHARS = 16_384
    const val MAX_ENTRIES = 64
    // ASCII serialization can expand each decoded UTF-16 unit to six chars,
    // plus '=' and LF for each pair. Input byte limits remain at the caller.
    const val MAX_ENCODED_BYTES = MAX_TEXT_CHARS * 6 + MAX_ENTRIES * 2

    enum class Failure { SIZE, DUPLICATE, SYNTAX, CHARACTERS }
    class Invalid(val failure: Failure) : IllegalArgumentException("Invalid property set: ${failure.name}")

    fun decode(text: String): Map<String, String> {
        if (text.length > MAX_TEXT_CHARS) throw Invalid(Failure.SIZE)
        validCharacters(text, value = true)
        val result = linkedMapOf<String, String>()
        var units = 0
        val loader = object : Properties() {
            override fun put(key: Any, value: Any): Any? {
                if (key !is String || value !is String || key.isEmpty()) throw Invalid(Failure.SYNTAX)
                validCharacters(key, value = false)
                validCharacters(value, value = true)
                if (result.containsKey(key)) throw Invalid(Failure.DUPLICATE)
                units += key.length + value.length
                if (result.size >= MAX_ENTRIES || units > MAX_TEXT_CHARS) throw Invalid(Failure.SIZE)
                result[key] = value
                return super.put(key, value)
            }
        }
        try {
            StringReader(text).use(loader::load)
            return LinkedHashMap(result)
        } catch (error: Invalid) {
            throw error
        } catch (_: IllegalArgumentException) {
            throw Invalid(Failure.SYNTAX)
        } finally { loader.clear() }
    }

    /** Stable Java-compatible representation. Literal LF/CR, '\\', separators,
     * leading spaces and Unicode cannot become another property when the
     * remote service loads this text. No timestamp/comment is introduced. */
    fun encode(properties: Map<String, String>): ByteArray {
        val snapshot = LinkedHashMap(properties)
        if (snapshot.size > MAX_ENTRIES || snapshot.entries.sumOf { it.key.length.toLong() + it.value.length } > MAX_TEXT_CHARS) {
            throw Invalid(Failure.SIZE)
        }
        val text = StringBuilder()
        snapshot.forEach { (key, value) ->
            if (key.isEmpty()) throw Invalid(Failure.SYNTAX)
            validCharacters(key, value = false); validCharacters(value, value = true)
            appendEscaped(text, key, key = true); text.append('=')
            appendEscaped(text, value, key = false); text.append('\n')
        }
        if (text.length > MAX_ENCODED_BYTES) throw Invalid(Failure.SIZE)
        return text.toString().toByteArray(Charsets.US_ASCII)
    }

    private fun appendEscaped(out: StringBuilder, text: String, key: Boolean) {
        text.forEachIndexed { index, char ->
            when (char) {
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\u000c' -> out.append("\\f")
                ' ' -> { if (key || index == 0) out.append('\\'); out.append(char) }
                '=', ':', '#', '!' -> out.append('\\').append(char)
                else -> if (char.code !in 0x20..0x7e) out.append("\\u").append(char.code.toString(16).padStart(4, '0')) else out.append(char)
            }
        }
    }

    private fun validCharacters(text: String, value: Boolean) {
        var index = 0
        while (index < text.length) {
            val char = text[index++]
            if (Character.isHighSurrogate(char)) {
                if (index >= text.length || !Character.isLowSurrogate(text[index++])) throw Invalid(Failure.CHARACTERS)
            } else if (Character.isLowSurrogate(char) ||
                Character.isISOControl(char) && !(value && char in "\r\n\t\u000c")) {
                throw Invalid(Failure.CHARACTERS)
            }
        }
    }
}

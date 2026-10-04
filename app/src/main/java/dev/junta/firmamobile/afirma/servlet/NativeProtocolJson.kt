package dev.junta.firmamobile.afirma.servlet

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Duplicate-rejecting bounded JSON, avoiding platform-version differences in
 * JSONObject's duplicate-name behavior. Only protocol data, never evaluation. */
internal object NativeProtocolJson {
    fun parse(bytes: ByteArray): Map<String, Any?> {
        require(bytes.size in 2..2_097_152)
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        return Reader(text).read()
    }
    fun encode(value: Any?): ByteArray {
        var count = 0
        fun write(v: Any?, depth: Int): String {
            require(depth <= 16 && ++count <= 10_000)
            return when (v) {
                null -> "null"
                is Boolean, is Long, is Int -> v.toString()
                is String -> buildString {
                    require(v.length <= 1_048_576)
                    append('"')
                    for (c in v) when (c) {
                        '"' -> append("\\\""); '\\' -> append("\\\\")
                        '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
                        else -> if (c.code < 32) append("\\u%04x".format(c.code)) else append(c)
                    }
                    append('"')
                }
                is Map<*, *> -> { require(v.size <= 128); v.entries.joinToString(",", "{", "}") { (k, item) -> require(k is String); write(k, depth + 1) + ":" + write(item, depth + 1) } }
                is List<*> -> { require(v.size <= 128); v.joinToString(",", "[", "]") { write(it, depth + 1) } }
                else -> error("Unsupported JSON value")
            }
        }
        return write(value, 0).toByteArray().also { require(it.size <= 2_097_152) }
    }
    private class Reader(private val text: String) {
        private var at = 0
        private var nodes = 0
        fun read(): Map<String, Any?> {
            val value = value(0); whitespace(); require(at == text.length)
            @Suppress("UNCHECKED_CAST")
            return value as? Map<String, Any?> ?: error("Expected object")
        }
        private fun value(depth: Int): Any? {
            whitespace(); require(depth <= 16 && ++nodes <= 10_000 && at < text.length)
            return when (text[at]) {
                '{' -> {
                    at++; val result = linkedMapOf<String, Any?>(); whitespace()
                    if (take('}')) result else {
                        do {
                            whitespace(); val name = string(); require(!result.containsKey(name) && result.size < 128)
                            whitespace(); require(take(':')); result[name] = value(depth + 1); whitespace()
                        } while (take(','))
                        require(take('}')); result
                    }
                }
                '[' -> {
                    at++; val result = mutableListOf<Any?>(); whitespace()
                    if (take(']')) result else {
                        do { require(result.size < 128); result += value(depth + 1); whitespace() } while (take(','))
                        require(take(']')); result
                    }
                }
                '"' -> string()
                't' -> { literal("true"); true }
                'f' -> { literal("false"); false }
                'n' -> { literal("null"); null }
                else -> {
                    val start = at
                    if (take('-')) require(at < text.length)
                    if (take('0')) require(at == text.length || !text[at].isDigit())
                    else { require(at < text.length && text[at] in '1'..'9'); while (at < text.length && text[at].isDigit()) at++ }
                    text.substring(start, at).toLong()
                }
            }
        }
        private fun string(): String {
            require(take('"'))
            val result = StringBuilder()
            while (at < text.length) {
                val c = text[at++]
                if (c == '"') return result.toString()
                require(c.code >= 32 && result.length < 1_048_576)
                if (c != '\\') result.append(c) else {
                    require(at < text.length)
                    when (val escape = text[at++]) {
                        '"', '\\', '/' -> result.append(escape)
                        'b' -> result.append('\b'); 'f' -> result.append('\u000c')
                        'n' -> result.append('\n'); 'r' -> result.append('\r'); 't' -> result.append('\t')
                        'u' -> { require(at + 4 <= text.length); result.append(text.substring(at, at + 4).toInt(16).toChar()); at += 4 }
                        else -> error("Invalid escape")
                    }
                }
            }
            error("Unclosed string")
        }
        private fun take(c: Char): Boolean = if (at < text.length && text[at] == c) { at++; true } else false
        private fun literal(word: String) { require(text.startsWith(word, at)); at += word.length }
        private fun whitespace() { while (at < text.length && text[at] in " \r\n\t") at++ }
    }
}

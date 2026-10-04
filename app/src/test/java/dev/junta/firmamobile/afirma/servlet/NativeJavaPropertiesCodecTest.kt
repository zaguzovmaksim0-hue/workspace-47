package dev.junta.firmamobile.afirma.servlet

import java.io.StringReader
import java.util.Properties
import org.junit.Assert.*
import org.junit.Test

class NativeJavaPropertiesCodecTest {
    @Test fun supportedGrammarMatchesThePlatformParserWithoutTrimmingValues() {
        val examples = listOf(
            "a=ordinary", "a : value  ", "a value", "a", "! note\n# second\na = b",
            "a=\\ leading", "a\\:b = c\\=d", "\\#notComment=\\!value", "a=\\u00f1\\u20ac",
            "a=\\uD83D\\uDE80", "a=\\b\\z", "a=before\\\n  after", "a=backslash\\\\\nb=value",
            "a=one\\r\\ntwo\\tthree", "\u000c a=space", "a=\\\\u0041", "a=1\rb=2", "a=1\r\nb=2",
        )
        for (text in examples) assertEquals(text, platform(text), NativeJavaProperties.decode(text))
    }

    @Test fun encodedLineBreaksAndSeparatorsStayInsideTheOriginalProperty() {
        val source = linkedMapOf("message" to "first\nserverUrl=https://not-a-new-endpoint.example\nheadless=true",
            " a:b=c#d! " to "  leading and trailing  ", "path" to "C:\\certs\\literal\\u0041",
            "text" to "Cádiz — Кириллица 🚀\tline\r\nnext\u000cend", "empty" to "")
        val before = LinkedHashMap(source)
        val bytes = NativeJavaProperties.encode(source)
        assertTrue(bytes.all { it.toInt() in 0..127 })
        val restored = platform(bytes.toString(Charsets.US_ASCII))
        assertEquals(source, restored); assertEquals(before, source)
        assertFalse(restored.containsKey("serverUrl")); assertFalse(restored.containsKey("headless"))
        assertArrayEquals(bytes, NativeJavaProperties.encode(source))
        assertEquals(source, NativeJavaProperties.decode(bytes.toString(Charsets.US_ASCII)))
    }

    @Test fun duplicateDecodedNamesAreRejectedRatherThanLastValueWinning() {
        for (text in listOf("a=1\na=2", "a=1\n\\u0061=2", "a=1\n\\a=2", "a\\:b=1\na\\u003ab=2")) {
            val failure = assertThrows(NativeJavaProperties.Invalid::class.java) { NativeJavaProperties.decode(text) }
            assertEquals(NativeJavaProperties.Failure.DUPLICATE, failure.failure)
        }
    }

    @Test fun invalidUnicodeControlsAndEmptyKeysAreNotRepairedSilently() {
        for (text in listOf("a=\\u00zz", "a=\\uu0041", "a=\\uD800", "a=\\uDC00", "a=\\u0000", "a=\u001b", "=value", "a\\nkey=value")) {
            assertThrows(text, NativeJavaProperties.Invalid::class.java) { NativeJavaProperties.decode(text) }
        }
        for (value in listOf("\u0000", "\u001b", "\ud800", "\udc00")) {
            assertThrows(NativeJavaProperties.Invalid::class.java) { NativeJavaProperties.encode(mapOf("name" to value)) }
        }
    }

    @Test fun boundsAreCheckedAtExactEntryAndInputEdges() {
        val accepted = (1..64).associate { "k$it" to "value" }
        assertEquals(accepted, NativeJavaProperties.decode(accepted.entries.joinToString("\n") { "${it.key}=${it.value}" }))
        assertThrows(NativeJavaProperties.Invalid::class.java) { NativeJavaProperties.encode(accepted + ("extra" to "value")) }
        assertThrows(NativeJavaProperties.Invalid::class.java) { NativeJavaProperties.decode((accepted + ("extra" to "value")).entries.joinToString("\n") { "${it.key}=${it.value}" }) }
        assertEquals(NativeJavaProperties.MAX_TEXT_CHARS - 2, NativeJavaProperties.decode("a=" + "x".repeat(NativeJavaProperties.MAX_TEXT_CHARS - 2))["a"]!!.length)
        assertThrows(NativeJavaProperties.Invalid::class.java) { NativeJavaProperties.decode("a=" + "x".repeat(NativeJavaProperties.MAX_TEXT_CHARS - 1)) }
    }

    @Test fun outputExpansionIsBoundedWithoutTruncatingAUnicodeValue() {
        val source = mapOf("k" to "\u20ac".repeat(NativeJavaProperties.MAX_TEXT_CHARS - 1))
        val encoded = NativeJavaProperties.encode(source)
        assertTrue(encoded.size <= NativeJavaProperties.MAX_ENCODED_BYTES)
        assertEquals(source, platform(encoded.toString(Charsets.US_ASCII)))
        assertThrows(NativeJavaProperties.Invalid::class.java) { NativeJavaProperties.encode(mapOf("k" to "x".repeat(NativeJavaProperties.MAX_TEXT_CHARS))) }
    }

    @Test fun parserReturnsIndependentStateAndErrorsDoNotEchoPropertySecrets() {
        val first = NativeJavaProperties.decode("a=one")
        val second = NativeJavaProperties.decode("a=two")
        assertEquals("one", first["a"]); assertEquals("two", second["a"])
        val error = assertThrows(NativeJavaProperties.Invalid::class.java) { NativeJavaProperties.decode("secret=private-value\nsecret=other") }
        assertFalse(error.toString().contains("private-value")); assertFalse(error.toString().contains("secret="))
    }

    @Test fun deterministicMixedCharactersRoundTripThroughTheIndependentPlatformReader() {
        val tokens = listOf("a", "=", ":", "#", "!", " ", "\\", "ñ", "Ж", "🚀", "\n", "\r", "\t", "\u000c")
        var count = 0
        for (left in tokens) for (right in tokens) {
            val expected = linkedMapOf("field:$count" to " ${left}middle${right} ", "other" to "\\u0041")
            val encoded = NativeJavaProperties.encode(expected)
            assertEquals(expected, platform(encoded.toString(Charsets.US_ASCII)))
            count++
        }
        assertEquals(196, count)
    }

    private fun platform(text: String): Map<String, String> {
        val properties = Properties().apply { StringReader(text).use { load(it) } }
        return properties.stringPropertyNames().associateWith { properties.getProperty(it) }
    }
}

package dev.junta.firmamobile.afirma.servlet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeXadesOptionsTest {
    private val variants = listOf(
        "Detached" to NativeXadesPackaging.DETACHED,
        "Enveloping" to NativeXadesPackaging.ENVELOPING,
        "Enveloped" to NativeXadesPackaging.ENVELOPED,
        "Externally Detached" to NativeXadesPackaging.EXTERNALLY_DETACHED
    )

    private fun options(
        properties: Map<String, String> = emptyMap(),
        format: String = "XAdES"
    ): NativeXadesOptions? = NativeXadesOptions.parse(format, properties)

    @Test
    fun formatMappingIsCaseInsensitiveAndBareFormatDefaultsToDetached() {
        val cases = listOf("XAdES" to NativeXadesPackaging.DETACHED) +
            variants.map { "XAdES ${it.first}" to it.second }
        for ((format, packaging) in cases) {
            for (spelling in listOf(format, format.lowercase(), format.uppercase())) {
                assertTrue(spelling, NativeXadesOptions.acceptsFormat(spelling))
                assertEquals(spelling, packaging, options(format = spelling)?.packaging)
            }
        }
        assertEquals(
            NativeXadesOptions(NativeXadesPackaging.DETACHED, "application/octet-stream", null),
            options()
        )
        val rejected = listOf("", "CAdES", "Detached", "XAdEStri", "XAdES TriPhase") +
            variants.map { "XAdES ${it.first} TriPhase" }
        for (format in rejected) {
            assertFalse(format, NativeXadesOptions.acceptsFormat(format))
            assertNull(format, options(format = format))
        }
    }

    @Test
    fun propertyFormatSelectsBarePackagingAndMustMatchExplicitPackaging() {
        for ((label, packaging) in variants) {
            val selectors = listOf(label.lowercase(), "XAdES $label".uppercase())
            for (selector in selectors) {
                val properties = mapOf("format" to selector)
                assertEquals(packaging, options(properties)?.packaging)
                assertEquals(packaging, options(properties, "XAdES $label")?.packaging)
                for ((other, _) in variants.filter { it.first != label }) {
                    assertNull(options(properties, "XAdES $other"))
                }
            }
        }
        for (selector in listOf("", "XAdES", "unknown", "XAdES TriPhase", "Detached TriPhase")) {
            assertNull(selector, options(mapOf("format" to selector)))
        }
    }

    @Test
    fun mimeAcceptsSafeAsciiNamesAndTheMaximumLength() {
        val accepted = listOf(
            "application/pdf", "application/vnd.example+json", "text/x.custom", "IMAGE/PNG",
            "application/x!#$&^_.+-", "a/" + "b".repeat(126)
        )
        for (mime in accepted) {
            assertEquals(mime, options(mapOf("mimeType" to mime))?.mimeType)
        }
        assertEquals("application/octet-stream", options()?.mimeType)
    }

    @Test
    fun mimeRejectsParametersControlsNonAsciiAndInvalidStructure() {
        val rejected = listOf(
            "", "text", "/plain", "text/", "text/plain/extra", "*/*",
            "text/plain; charset=utf-8", " text/plain", "text/plain ",
            "text /plain", "téxt/plain", "text/\"plain\"", "text\\plain",
            "text/pla\nin", "text/pla\rin", "text/pla\tin", "text/pla\u0000in",
            "text/pla\u007Fin", "text/pla\u0085in", "a/" + "b".repeat(127)
        )
        for (mime in rejected) {
            assertNull(options(mapOf("mimeType" to mime)))
        }
    }

    @Test
    fun descriptionsPreserveSafeTextAndRejectLengthIsoAndBidiControls() {
        assertNull(options()?.contentDescription)
        for (description in listOf("", "Résumé — документ ✓", "x".repeat(256))) {
            assertEquals(description, options(mapOf("contentDescription" to description))?.contentDescription)
        }
        assertNull(options(mapOf("contentDescription" to "x".repeat(257))))
        val forbidden = (0..31).toList() + (127..159) +
            listOf(0x061C, 0x200E, 0x200F) + (0x202A..0x202E) + (0x2066..0x2069)
        for (code in forbidden) {
            assertNull(
                "U+${code.toString(16)}",
                options(mapOf("contentDescription" to "a${code.toChar()}b"))
            )
        }
    }

    @Test
    fun modeIsValidatedWithoutChangingPackaging() {
        val formats = listOf("XAdES") + variants.map { "XAdES ${it.first}" }
        for (format in formats) {
            val expected = options(format = format)
            assertNotNull(expected)
            for (mode in listOf("explicit", "implicit")) {
                assertEquals(expected, options(mapOf("mode" to mode), format))
            }
        }
        assertEquals(
            NativeXadesPackaging.ENVELOPING,
            options(mapOf("format" to "Enveloping", "mode" to "explicit"))?.packaging
        )
        for (mode in listOf("", "auto", "EXPLICIT", " explicit", "implicit ")) {
            assertNull(mode, options(mapOf("mode" to mode)))
        }
    }

    @Test
    fun booleansAcceptOnlyLiteralValuesAndDoNotChangeReturnedOptions() {
        val expected = options()
        assertNotNull(expected)
        for (headless in listOf("true", "false")) {
            for (validate in listOf("true", "false")) {
                assertEquals(
                    expected,
                    options(mapOf("headless" to headless, "validatePkcs1" to validate))
                )
            }
        }
        for (key in listOf("headless", "validatePkcs1")) {
            for (value in listOf("true", "false")) {
                assertEquals(expected, options(mapOf(key to value)))
            }
            for (value in listOf("", "yes", "no", "1", "0", "TRUE", " true", "false ")) {
                assertNull("$key=$value", options(mapOf(key to value)))
            }
        }
    }

    @Test
    fun unknownPropertiesIncludingPolicyAndTimestampDemandsAreRejected() {
        for (key in listOf("unknown", "Format", "policy", "policyIdentifier", "tsaURL", "timestamp")) {
            for (value in listOf("", "false", "required")) {
                assertNull(key, options(mapOf("mimeType" to "text/plain", key to value)))
            }
        }
        assertNull(options(mapOf("policy" to "implied")))
        assertEquals(
            NativeXadesOptions(NativeXadesPackaging.ENVELOPED, "text/plain", "Signed document"),
            options(mapOf(
                "format" to "Enveloped", "mode" to "explicit", "mimeType" to "text/plain",
                "contentDescription" to "Signed document", "headless" to "true", "validatePkcs1" to "false"
            ))
        )
    }
}

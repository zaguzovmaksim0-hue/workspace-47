package dev.junta.firmamobile.afirma.servlet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativePadesOptionsTest {
    private val metadataKeys = listOf("signReason", "signatureProductionCity", "signerContact")
    private fun parse(vararg entries: Pair<String, String>) = NativePadesOptions.parse(mapOf(*entries))

    @Test fun defaultsAndFormatAliases() {
        val expected = NativePadesOptions(subFilter = "ETSI.CAdES.detached")
        assertEquals(expected, parse())
        for (format in listOf("PAdES", "pAdEs", "PAdES Detached", "pades detached", "Adobe PDF", "aDoBe PdF")) {
            assertTrue(format, NativePadesOptions.acceptsFormat(format))
            assertEquals(expected, parse("format" to format))
        }
        for (format in listOf("", " PAdES", "PAdES ", "PAdES  Detached", "AdobePDF", "XAdES")) {
            assertFalse(format, NativePadesOptions.acceptsFormat(format))
            assertNull(format, parse("format" to format))
        }
        for (mode in listOf("explicit", "implicit")) for (headless in listOf("true", "false")) {
            assertEquals(expected, parse("mode" to mode, "headless" to headless, "signingCertificateV2" to "true"))
        }
    }

    @Test fun subFiltersRequireExactSupportedValues() {
        for (value in listOf("ETSI.CAdES.detached", "adbe.pkcs7.detached")) {
            assertEquals(NativePadesOptions(subFilter = value), parse("signatureSubFilter" to value))
        }
        for (value in listOf("", "etsi.CAdES.detached", "adbe.pkcs7.Detached", "adbe.pkcs7.sha1", "ETSI.CAdES.detached ")) {
            assertNull(value, parse("signatureSubFilter" to value))
        }
    }

    @Test fun metadataPreservesUnicodeSpacesAndCase() {
        val reason = "  Révision №42 — 承認 🚀  "
        val city = "  A Coruña / 東京  "
        val contact = "  José@Example.ORG  "
        val expected = NativePadesOptions(reason = reason, location = city, contact = contact)
        assertEquals(expected, parse("signReason" to reason, "signatureProductionCity" to city, "signerContact" to contact))
    }

    @Test fun unknownPolicyVisibleSignatureAndTsaOptionsReject() {
        val unsupported = mapOf(
            "unknown" to "x", "policyIdentifier" to "urn:oid:1.2.3",
            "policyIdentifierHash" to "YWJj", "policyIdentifierHashAlgorithm" to "SHA-256",
            "signaturePage" to "1", "signaturePositionOnPageLowerLeftX" to "10",
            "signaturePositionOnPageLowerLeftY" to "20", "signaturePositionOnPageUpperRightX" to "200",
            "signaturePositionOnPageUpperRightY" to "100", "layer2Text" to "Signed",
            "tsaURL" to "https://tsa.example", "tsaPolicy" to "1.2.3"
        )
        for ((key, value) in unsupported) assertNull(key, parse("mode" to "implicit", key to value))
    }

    @Test fun invalidBooleansAndModesReject() {
        val invalid = mapOf(
            "mode" to listOf("", "auto", "detached", " explicit", "implicit "),
            "headless" to listOf("", "yes", "1", "0", " true", "false "),
            "signingCertificateV2" to listOf("", "false", "yes", "1", " true", "true ")
        )
        for ((key, values) in invalid) for (value in values) {
            assertNull("$key=$value", parse(key to value))
        }
    }

    @Test fun metadataLengthBoundaries() {
        for (length in listOf(0, 1, 255, 256)) {
            val value = "x".repeat(length)
            val expected = NativePadesOptions(reason = value, location = value, contact = value)
            assertEquals("length=$length", expected, NativePadesOptions.parse(metadataKeys.associateWith { value }))
        }
        for (key in metadataKeys) assertNull(key, parse(key to "x".repeat(257)))
    }

    @Test fun metadataRejectsEveryIsoControlAndForbiddenBidiCharacter() {
        val forbidden = (0..31) + (127..159) + (0x202A..0x202E) + (0x2066..0x2069)
        for (key in metadataKeys) for (code in forbidden) {
            assertNull("$key: U+${code.toString(16)}", parse(key to "a${code.toChar()}b"))
        }
    }

    @Test fun toStringReportsSubFilterAndPresenceWithoutMetadata() {
        val secrets = listOf("PRIVATE_REASON_Ω", "PRIVATE_CITY_東京", "PRIVATE_CONTACT@example.org")
        val options = listOf(
            NativePadesOptions(), NativePadesOptions(reason = secrets[0]),
            NativePadesOptions(location = secrets[1]), NativePadesOptions(contact = secrets[2]),
            NativePadesOptions("adbe.pkcs7.detached", secrets[0], secrets[1], secrets[2])
        )
        for ((index, option) in options.withIndex()) {
            val text = option.toString()
            assertTrue(text.contains("subFilter=${option.subFilter}"))
            assertTrue(text.contains("metadataPresent=${index > 0}"))
            for (value in secrets + listOf("reason=", "location=", "contact=")) assertFalse(text.contains(value))
        }
    }
}

package dev.junta.firmamobile.afirma.servlet

import java.net.URLEncoder
import org.junit.Assert.*
import org.junit.Test

class AfirmaConfigurationXmlTest {
    @Test fun officialSignAndLegacyOpRootsResolveTheSameParameters() {
        for (root in listOf("sign", "op", "SIGN", "OP")) {
            val parsed = parse("<$root><e k=\"id\" v=\"Request-123\"/><e k=\"format\" v=\"CAdES\"/></$root>")
            assertEquals(AfirmaServletOperation.SIGN, parsed.operation)
            assertEquals(mapOf("id" to "Request-123", "format" to "CAdES"), parsed.values)
        }
    }

    @Test fun selectcertHasItsOwnOperationAndAllowsFormattingWhitespace() {
        val parsed = parse("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<selectcert>\n <e k='id' v='Select-123'></e>\n</selectcert>\n")
        assertEquals(AfirmaServletOperation.SELECT_CERTIFICATE, parsed.operation)
        assertEquals("Select-123", parsed.values["id"])
    }

    @Test fun utf8AndXmlPredefinedEntitiesAreDecodedWithoutLosingPunctuation() {
        val parsed = parse("<sign><e k=\"appname\" v=\"${form("Administración & Atención \"Ejemplo\"")}\"/></sign>")
        assertEquals("Administración & Atención \"Ejemplo\"", parsed.values["appname"])
        assertEquals("a&b", parse("<sign><e k='appname' v='a&amp;b'/></sign>").values["appname"])
        assertEquals("€", parse("<sign><e k='appname' v='&#8364;'/></sign>").values["appname"])
    }

    @Test fun urlFormValuesAreDecodedExactlyOnceAndKeysKeepTheirCase() {
        val parsed = parse("<sign><e k='appname' v='A+B%2BC%2520D'/><e k='MODE' v='implicit'/></sign>")
        assertEquals("A B+C%20D", parsed.values["appname"])
        assertEquals("implicit", parsed.values["MODE"])
        assertNull(parsed.values["mode"])
    }

    @Test fun malformedPercentUtf8AndControlValuesAreRejected() {
        for (v in listOf("%", "%GG", "%A0", "%C0%80", "%00", "%0A", "%ED%A0%80")) {
            invalid("<sign><e k='appname' v='$v'/></sign>")
        }
        assertThrows(Exception::class.java) { AfirmaConfigurationXml.parse(byteArrayOf(0xff.toByte())) }
    }

    @Test fun duplicateParametersExtraAttributesAndNestedElementsAreRejected() {
        for (body in listOf("<e k='id' v='A'/><e k='id' v='B'/>", "<e k='id' v='A' extra='B'/>",
            "<e k='id'/>", "<e v='A'/>", "<e k='id' v='A'><e k='format' v='CAdES'/></e>",
            "<entry k='id' v='A'/>", "<e k='a:b' v='A'/>", "data", "<e k='id' v='A'>text</e>")) {
            invalid("<sign>$body</sign>")
        }
    }

    @Test fun unknownRootsNamespaceAndRootAttributesDoNotChangeOperationSemantics() {
        for (xml in listOf("<signandsave/>", "<unsupported/>", "<p:sign xmlns:p='urn:test'/>", "<sign xmlns='urn:test'/>",
            "<sign operation='selectcert'/>", "<sign/><selectcert/>", "<sign><e k='id' v='A'/></selectcert>")) invalid(xml)
    }

    @Test fun doctypeInternalExternalEntitiesAndProcessingInstructionsAreRejectedBeforeResolution() {
        for (xml in listOf(
            "<!DOCTYPE sign SYSTEM 'https://never-request.synthetic.example/data'><sign/>",
            "<!DOCTYPE sign [<!ENTITY x 'expanded'>]><sign><e k='id' v='&x;'/></sign>",
            "<!DOCTYPE sign [<!ENTITY x SYSTEM 'file:///not-a-real-fixture'>]><sign><e k='id' v='&x;'/></sign>",
            "<?xml-stylesheet href='https://never-request.synthetic.example/x'?><sign/>",
            "<sign><e k='id' v='&undefined;'/></sign>",
        )) invalid(xml)
    }

    @Test fun parametersAndEncodedDocumentHaveHardBudgets() {
        val allowed = (0 until 64).joinToString("") { "<e k='p$it' v='x'/>" }
        assertEquals(64, parse("<sign>$allowed</sign>").values.size)
        invalid("<sign>$allowed<e k='tooMany' v='x'/></sign>")
        assertThrows(Exception::class.java) { AfirmaConfigurationXml.parse(ByteArray(AfirmaConfigurationXml.MAX_BYTES + 1) { 32 }) }
        invalid("<sign><e k='${"x".repeat(65)}' v='x'/></sign>")
    }

    @Test fun safeCommentsAndUtf8BomDoNotBreakAnOtherwiseValidDescriptor() {
        val parsed = parse("\uFEFF<sign><!-- fixture --><e k='id' v='Request-123'/></sign>")
        assertEquals("Request-123", parsed.values["id"])
        assertFalse(parsed.toString().contains("Request-123"))
    }

    private fun form(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun parse(xml: String) = AfirmaConfigurationXml.parse(xml.toByteArray(Charsets.UTF_8))
    private fun invalid(xml: String) { assertThrows(Exception::class.java) { parse(xml) } }
}

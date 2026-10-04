package dev.junta.firmamobile.afirma.servlet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

@Suppress("UNCHECKED_CAST")
class NativeTriphaseXmlTest {
    private val canonicalXml = """<xml><firmas format="XAdES"><firma Id="doc" signid="one"><param n="PRE">YWJj</param></firma></firmas></xml>"""

    @Test
    fun xmlRoundTripPreservesIdentityParametersAndSpecialCharacters() {
        assertSessionEquals(
            canonicalSession(),
            requireNotNull(NativeTriphaseXml.parse(canonicalXml.toByteArray(Charsets.UTF_8)))
        )
        val session = NativeTriSession(
            "XAdES",
            listOf(
                NativeTriSign(
                    "doc<&>\"'",
                    "one<&>\"'",
                    linkedMapOf(
                        "PRE" to "YWJj",
                        "NEED_PRE" to "true",
                        "opaqstate" to "<state a=\"x&y\">'opaque'</state>"
                    )
                )
            )
        )
        val encoded = requireNotNull(NativeTriphaseXml.encode(session))
        val parsed = requireNotNull(NativeTriphaseXml.parse(encoded))
        assertSessionEquals(session, NativeTriphaseXml.validate(parsed))
    }

    @Test
    fun duplicateXmlParameterIsRejected() {
        val xml = canonicalXml.replace(
            "</firma>",
            """<param n="PRE">ZGVm</param></firma>"""
        )
        assertNull(NativeTriphaseXml.parse(xml.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun dtdIsRejectedEvenWithoutEntityReferences() {
        for (declaration in listOf(
            "<!DOCTYPE xml>",
            """<!DOCTYPE xml [<!ENTITY unused "safe">]>"""
        )) {
            val bytes = (declaration + canonicalXml).toByteArray(Charsets.UTF_8)
            assertNull(NativeTriphaseXml.parse(bytes))
        }
    }

    @Test
    fun duplicateIdentityIsRejectedButDifferentSignIdsAreAccepted() {
        val sign = canonicalSession().signs.single()
        val duplicates = NativeTriSession("XAdES", listOf(sign, sign))
        assertRejected { NativeTriphaseXml.validate(duplicates) }
        val xml = canonicalXml.replace(
            "</firmas>",
            """<firma Id="doc" signid="one"><param n="PRE">YWJj</param></firma></firmas>"""
        )
        assertNull(NativeTriphaseXml.parse(xml.toByteArray(Charsets.UTF_8)))
        val allowed = NativeTriSession(
            "XAdES",
            listOf(sign, NativeTriSign("doc", "two", mapOf("PRE" to "ZGVm")))
        )
        assertSessionEquals(allowed, NativeTriphaseXml.validate(allowed))
        val encoded = requireNotNull(NativeTriphaseXml.encode(allowed))
        assertSessionEquals(allowed, requireNotNull(NativeTriphaseXml.parse(encoded)))
    }

    @Test
    fun jsonRoundTripPreservesSigninfoAndParams() {
        val params = linkedMapOf(
            "PRE" to "YWJj",
            "NEED_PRE" to "true",
            "opaqstate" to "state<&>\"'"
        )
        val session = NativeTriSession("XAdES", listOf(NativeTriSign("doc", "one", params)))
        val bytes = NativeTriphaseCodec.encodeJson(session)
        val json = NativeProtocolJson.parse(bytes) as Map<String, Any?>
        val signinfo = json["signinfo"] as List<*>
        assertEquals(1, signinfo.size)
        val sign = signinfo.single() as Map<*, *>
        assertEquals(params, sign["params"])
        assertSessionEquals(session, NativeTriphaseCodec.parseJson(json))
        assertEquals(json, NativeProtocolJson.parse(NativeProtocolJson.encode(json)))
    }

    @Test
    fun nonStringJsonParameterValuesAreRejected() {
        val json = NativeProtocolJson.parse(
            NativeTriphaseCodec.encodeJson(canonicalSession())
        ) as Map<String, Any?>
        val sign = (json["signinfo"] as List<*>).single() as Map<String, Any?>
        for (invalid in listOf<Any?>(123, true, null, listOf("YWJj"))) {
            val malformed = json + (
                "signinfo" to listOf(sign + ("params" to mapOf("PRE" to invalid)))
            )
            assertRejected { NativeTriphaseCodec.parseJson(malformed) }
        }
    }

    private fun canonicalSession() = NativeTriSession(
        "XAdES", listOf(NativeTriSign("doc", "one", mapOf("PRE" to "YWJj")))
    )

    private fun assertSessionEquals(
        expected: NativeTriSession,
        actual: NativeTriSession
    ) {
        assertEquals(expected.format, actual.format)
        assertEquals(expected.signs.size, actual.signs.size)
        expected.signs.zip(actual.signs).forEach { (left, right) ->
            assertEquals(left.id, right.id)
            assertEquals(left.signatureId, right.signatureId)
            assertEquals(left.parameters, right.parameters)
        }
    }

    private fun assertRejected(block: () -> Unit) {
        try {
            block()
        } catch (_: Exception) {
            return
        }
        fail("Expected invalid triphase data to be rejected")
    }
}

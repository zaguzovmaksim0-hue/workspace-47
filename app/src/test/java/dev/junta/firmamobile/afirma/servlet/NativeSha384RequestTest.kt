package dev.junta.firmamobile.afirma.servlet

import java.net.URLEncoder
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

/** Parse the actual protocol, not a replacement test parser. No keys/network. */
class NativeSha384RequestTest {
    @Test fun directRequestsRetainExplicitSha384ForAllCommonFormats() {
        for (format in listOf("CAdES", "XAdES", "PAdES")) {
            val payload = if (format == "PAdES") nativePadesFixture() else "<document>synthetic</document>".toByteArray()
            val result = parse(format, payload)
            assertTrue("$format should admit the requested SHA-384 algorithm: $result", result is AfirmaServletParseResult.Accepted)
            (result as AfirmaServletParseResult.Accepted).invocation.use {
                assertEquals("SHA384_WITH_RSA", it.algorithm!!.name)
            }
        }
    }

    @Test fun triphaseRequestsDoNotSilentlyReplaceSha384WithAnotherDigest() {
        for (format in listOf("CAdEStri", "PAdEStri", "XAdEStri")) {
            val result = parse(format, "synthetic".toByteArray(), mapOf("serverurl" to "https://signer.synthetic.example/service"))
            assertTrue("$format: $result", result is AfirmaServletParseResult.Accepted)
            (result as AfirmaServletParseResult.Accepted).invocation.use {
                assertEquals("SHA384_WITH_RSA", it.algorithm!!.name)
                assertNotNull(it.remoteOptions)
            }
        }
    }

    @Test fun bothBatchDescriptorsAcceptSha384OnlyAsTheRequestedAlgorithm() {
        val json = """{"algorithm":"SHA384withRSA","format":"XAdES","singlesigns":[{"id":"one","datareference":"YWJj"}]}""".toByteArray()
        val xml = "<signbatch algorithm='SHA384withRSA'><singlesign id='one'><datasource>YWJj</datasource><format>XAdES</format><suboperation>sign</suboperation></singlesign></signbatch>".toByteArray()
        for ((bytes, isJson) in listOf(json to true, xml to false)) {
            val batch = NativeBatchDescriptor.parse(bytes, isJson)
            assertNotNull("SHA384 batch format json=$isJson", batch)
            assertEquals("SHA384_WITH_RSA", batch!!.algorithm.name)
        }
    }

    @Test fun rsaPssAndEcdsaAreNotGrantedByAddingPkcs1Sha384() {
        for (algorithm in listOf("SHA384withECDSA", "SHA384withRSA/PSS", "SHA384", "SHA3-384withRSA")) {
            assertFalse(parse("CAdES", "synthetic".toByteArray(), mapOf("algorithm" to algorithm)) is AfirmaServletParseResult.Accepted)
        }
    }

    private fun parse(format: String, data: ByteArray, extra: Map<String, String> = emptyMap()): AfirmaServletParseResult {
        val fields = mapOf("id" to "Sha384Test", "stservlet" to "https://storage.synthetic.example/put", "format" to format,
            "algorithm" to "SHA384withRSA", "dat" to Base64.getUrlEncoder().encodeToString(data)) + extra
        val query = fields.entries.joinToString("&") { encode(it.key) + "=" + encode(it.value) }
        return AfirmaServletInvocationParser.parse("afirma://sign?$query", "https://source.synthetic.example/form")
    }
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
}

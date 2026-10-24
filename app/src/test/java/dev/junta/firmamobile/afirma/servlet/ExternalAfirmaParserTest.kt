package dev.junta.firmamobile.afirma.servlet

import java.net.URLEncoder
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class ExternalAfirmaParserTest {
    @Test fun everyNativeOperationHasAnExternalContextWithoutAWebsiteOrProfile() {
        val fields = mapOf("id" to "synthetic", "stservlet" to "https://store.example/put?route=private")
        val sign = fields + mapOf("dat" to b64("abc"), "format" to "CAdES", "algorithm" to "SHA256withRSA")
        val batch = """{"algorithm":"SHA256withRSA","format":"CAdES","singlesigns":[{"id":"a","datareference":"YWJj"}]}"""
        val requests = mapOf(
            "selectcert" to fields,
            "sign" to sign,
            "cosign" to (sign + mapOf("format" to "CAdEStri", "serverurl" to "https://signer.example/tri?route=private")),
            "countersign" to (sign + mapOf("format" to "CAdEStri", "serverurl" to "https://signer.example/tri")),
            "batch" to (fields + mapOf("dat" to b64(batch), "jsonbatch" to "true", "localBatchProcess" to "true")),
        )
        for ((operation, values) in requests) {
            val parsed = AfirmaServletInvocationParser.parseExternal(uri(operation, values))
            assertTrue(operation, parsed is AfirmaServletParseResult.Accepted)
            (parsed as AfirmaServletParseResult.Accepted).invocation.use {
                assertNull(it.sourceOrigin)
                assertEquals(operation, if (it.operation == AfirmaServletOperation.SELECT_CERTIFICATE) "selectcert" else it.operation.name.lowercase())
                assertEquals(64, it.externalContext!!.requestSha256.length)
                assertFalse(it.toString().contains("private"))
            }
        }
    }

    @Test fun externalSyntaxRejectsIndirectHeadlessAmbiguousAndUnsupportedRequests() {
        val fields = mapOf("id" to "synthetic", "stservlet" to "https://store.example/put")
        val select = uri("selectcert", fields)
        val rejected = listOf(
            select + "&id=duplicate", select + "&unknown=true", select + "&x=%ZZ",
            select.replace("afirma:", "intent:"), select.replace("selectcert", "websocket"),
            select.replace("https%3A", "http%3A"), select + "&sticky=true",
            uri("selectcert", fields + mapOf("properties" to b64("headless=true"))),
            uri("selectcert", mapOf("fileid" to "remote", "rtservlet" to "https://store.example/get")),
            "afirma://selectcert?" + "x".repeat(1_048_576),
        )
        for (raw in rejected) {
            val parsed = AfirmaServletInvocationParser.parseExternal(raw)
            assertFalse(parsed.toString(), parsed is AfirmaServletParseResult.Accepted)
            assertFalse(parsed is AfirmaServletParseResult.Deferred)
        }
    }

    @Test fun delegatedRequestsCannotSmuggleConsentSuppressionProperties() {
        val fields = mapOf("id" to "synthetic", "stservlet" to "https://store.example/put",
            "dat" to b64("abc"), "format" to "CAdEStri", "algorithm" to "SHA256withRSA",
            "serverurl" to "https://signer.example/tri")
        for (name in listOf("headless", "silent", "autoSign", "stickycert", "skipConsent", "suppressConsent")) {
            val parsed = AfirmaServletInvocationParser.parseExternal(uri("sign", fields +
                ("properties" to b64("$name=true"))))
            assertTrue(name, parsed is AfirmaServletParseResult.Unsupported)
            val batch = """{"algorithm":"SHA256withRSA","format":"CAdES","extraparams":"${b64("$name=true")}","singlesigns":[{"id":"a","datareference":"YWJj"}]}"""
            val batchResult = AfirmaServletInvocationParser.parseExternal(uri("batch", mapOf(
                "id" to "synthetic", "stservlet" to "https://store.example/put", "dat" to b64(batch),
                "jsonbatch" to "true", "localBatchProcess" to "true")))
            assertTrue("batch $name", batchResult is AfirmaServletParseResult.Unsupported)
        }
    }

    @Test fun redeliveryGetsNewOwnershipAndChangedRecipientsChangeTheDigest() {
        fun context(raw: String): ExternalAfirmaContext {
            val result = AfirmaServletInvocationParser.parseExternal(raw) as AfirmaServletParseResult.Accepted
            return result.invocation.use { checkNotNull(it.externalContext) }
        }
        val raw = ExternalAfirmaControllerTest.SELECT
        val first = context(raw)
        val second = context(raw)
        assertNotEquals(first.deliveryId, second.deliveryId)
        assertEquals(first.requestSha256, second.requestSha256)
        assertNotEquals(first.requestSha256, context(raw.replace("private", "changed")).requestSha256)
        assertFalse(first.toString().contains("private"))
    }

    private fun b64(text: String) = Base64.getEncoder().encodeToString(text.toByteArray())
    private fun uri(operation: String, fields: Map<String, String>) = "afirma://$operation?" +
        fields.entries.joinToString("&") { it.key + "=" + URLEncoder.encode(it.value, "UTF-8") }
}

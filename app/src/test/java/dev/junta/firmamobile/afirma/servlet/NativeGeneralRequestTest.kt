package dev.junta.firmamobile.afirma.servlet

import java.net.URI
import java.net.URLEncoder
import org.junit.Assert.*
import org.junit.Test

class NativeGeneralRequestTest {
    @Test fun singleFormatsAndServicePropertyReachTheRightFactoryWithoutProfile() {
        for (format in listOf("XAdES", "XAdES Detached", "XAdES Enveloping", "XAdES Enveloped", "XAdES Externally Detached")) {
            val bytes = if (format.endsWith("Enveloped")) "<r/>".toByteArray() else "doc".toByteArray()
            acceptedGeneral("sign", generalFields(bytes, format)).use { invocation ->
                assertNotNull(invocation.xadesOptions); assertNull(invocation.remoteOptions)
                nativeOperation(invocation).use { assertFalse(it.details.delegatedSigning); assertTrue(it.details.format!!.startsWith("XAdES")) }
            }
        }
        for (op in listOf("sign", "cosign", "countersign")) {
            for (format in listOf("CAdEStri", "XAdEStri", "PAdEStri")) {
                acceptedGeneral(op, generalFields("doc".toByteArray(), format) + ("properties" to url64("serverUrl=https://signer.example/tri\npolicyIdentifier=1.2.3\ntarget=TREE".toByteArray()))).use { invocation ->
                    assertEquals(op, invocation.operation.name.lowercase())
                    assertEquals(URI("https://signer.example/tri"), invocation.remoteOptions!!.preUrl)
                    assertEquals("1.2.3", invocation.remoteOptions!!.properties["policyIdentifier"])
                    nativeOperation(invocation).use { assertTrue(it.details.delegatedSigning); assertEquals(listOf("https://signer.example/tri"), it.details.serviceDestinations) }
                }
            }
        }
    }
    @Test fun newXmlRootsRetainTheirOperationAndDoNotSubstituteItFromFields() {
        for (root in listOf("batch", "countersign")) {
            val parsed = AfirmaConfigurationXml.parse("<$root><e k='op' v='$root'/></$root>".toByteArray())
            assertEquals(root, parsed.operation.name.lowercase())
        }
        val mismatch = AfirmaServletInvocationParser.parseRetrieved(AfirmaServletOperation.COUNTERSIGN, "https://page.example",
            generalFields("doc".toByteArray(), "XAdEStri") + mapOf("op" to "sign", "serverurl" to "https://service.example/tri"))
        assertTrue(mismatch is AfirmaServletParseResult.Invalid)
    }
    @Test fun aRemoteConstraintNeverSilentlySelectsLocalSigning() {
        val base = generalFields("doc".toByteArray(), "XAdEStri")
        for (fields in listOf(base, base + ("serverurl" to "http://signer.example/tri"),
            base + mapOf("serverurl" to "https://one.example/tri", "properties" to url64("serverUrl=https://two.example/tri".toByteArray())))) {
            assertFalse(parse("sign", fields) is AfirmaServletParseResult.Accepted)
        }
        assertTrue(parse("sign", generalFields("doc".toByteArray(), "XAdES") + ("properties" to url64("policyIdentifier=1.2.3".toByteArray()))) is AfirmaServletParseResult.Unsupported)
        assertTrue(parse("countersign", generalFields("doc".toByteArray(), "XAdES")) is AfirmaServletParseResult.Unsupported)
    }
    @Test fun batchOnlyParametersAreNotIgnoredBySingleSigningOrCertificateSelection() {
        for (name in listOf("batchpresignerurl", "batchpostsignerurl", "jsonbatch", "localBatchProcess", "needcert")) {
            assertTrue(parse("sign", generalFields("doc".toByteArray(), "CAdES") + (name to "true")) is AfirmaServletParseResult.Invalid)
        }
        assertTrue(parse("selectcert", mapOf("id" to "cert123", "stservlet" to "https://store.example/put", "serverurl" to "https://signer.example")) is AfirmaServletParseResult.Invalid)
    }
    @Test fun localBatchDoesNotSilentlyAcceptRemoteEndpointsOrXml() {
        val descriptor = """{"algorithm":"SHA256withRSA","format":"CAdES","singlesigns":[{"id":"a","datareference":"YWJj"}]}""".toByteArray()
        val fields = mapOf("id" to "b123", "stservlet" to "https://store.example/put", "dat" to url64(descriptor), "jsonbatch" to "true", "localBatchProcess" to "true")
        acceptedGeneral("batch", fields).close()
        assertTrue(parse("batch", fields + ("batchpresignerurl" to "https://service.example/pre")) is AfirmaServletParseResult.Invalid)
        assertFalse(parse("batch", fields + ("jsonbatch" to "false")) is AfirmaServletParseResult.Accepted)
        assertTrue(parse("batch", fields + ("localBatchProcess" to "yes")) is AfirmaServletParseResult.Invalid)
    }
    private fun parse(op: String, fields: Map<String, String>): AfirmaServletParseResult {
        val query = fields.entries.joinToString("&") { it.key + "=" + URLEncoder.encode(it.value, "UTF-8").replace("+", "%20") }
        return AfirmaServletInvocationParser.parse("afirma://$op?$query", "https://source.example/")
    }
}

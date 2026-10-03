package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.SigningAlgorithm
import org.junit.Assert.*
import org.junit.Test

class NativeBatchDescriptorTest {
    @Test fun xmlPreservesOpaqueServiceDataAndCanonicalIds() {
        for (key in listOf("id", "Id")) {
            val xml = """<signbatch algorithm="SHA256withRSA" stoponerror="true"><singlesign $key="A"><datasource>YWJj</datasource><format>CAdES</format><suboperation>sign</suboperation><extraparams>bW9kZT1leHBsaWNpdA==</extraparams><signsaver><class>example.ServiceOnlySaver</class><config>YQ==</config></signsaver></singlesign></signbatch>"""
            val batch = checkNotNull(NativeBatchDescriptor.parse(xml.toByteArray(), false))
            assertFalse(batch.json); assertTrue(batch.stopOnError); assertEquals(SigningAlgorithm.SHA256_WITH_RSA, batch.algorithm)
            assertEquals("A", batch.items.single().id); assertEquals("YWJj", batch.items.single().dataReference)
            assertEquals(mapOf("mode" to "explicit"), batch.items.single().extraProperties)
        }
    }
    @Test fun jsonDefaultsAndPerDocumentOverridesAreDistinct() {
        val bytes = """{"algorithm":"SHA512withRSA","format":"XAdES","suboperation":"sign","singlesigns":[{"id":"A","datareference":"YWJj"},{"id":"B","format":"PAdES","suboperation":"cosign","datareference":"ZGVm"}]}""".toByteArray()
        val batch = checkNotNull(NativeBatchDescriptor.parse(bytes, true))
        assertTrue(batch.json); assertFalse(batch.stopOnError)
        assertEquals(listOf("XAdES", "PAdES"), batch.items.map { it.format })
        assertEquals(listOf("sign", "cosign"), batch.items.map { it.operation })
    }
    @Test fun ambiguousDuplicatesAndNonTextFieldsAreRejected() {
        assertNull(NativeBatchDescriptor.parse(json(2).replace("D1", "D0").toByteArray(), true))
        assertNull(NativeBatchDescriptor.parse(json(1).replace("\"algorithm\":", "\"algorithm\":\"SHA1\",\"algorithm\":").toByteArray(), true))
        assertNull(NativeBatchDescriptor.parse(json(1).replace("\"YWJj\"", "true").toByteArray(), true))
        assertNull(NativeBatchDescriptor.parse("<signbatch algorithm='SHA256'><singlesign id='A' Id='B'/></signbatch>".toByteArray(), false))
    }
    @Test fun limitsAndMissingRequirementsDoNotBecomePartialBatches() {
        assertEquals(32, NativeBatchDescriptor.parse(json(32).toByteArray(), true)!!.items.size)
        assertNull(NativeBatchDescriptor.parse(json(33).toByteArray(), true))
        assertNull(NativeBatchDescriptor.parse(json(0).toByteArray(), true))
        for (key in listOf("\"datareference\":\"YWJj\",", "\"algorithm\":\"SHA256\",", "\"format\":\"CAdES\",")) {
            assertNull(key, NativeBatchDescriptor.parse(json(1).replace(key, "").toByteArray(), true))
        }
        assertNull(NativeBatchDescriptor.parse(json(1).replace("SHA256", "SHA256withECDSA").toByteArray(), true))
    }
    @Test fun xmlDtdAndUnknownFieldsAreRejected() {
        assertNull(NativeBatchDescriptor.parse("<!DOCTYPE signbatch><signbatch/>".toByteArray(), false))
        assertNull(NativeBatchDescriptor.parse(json(1).replace("\"singlesigns\"", "\"unknown\":true,\"singlesigns\"").toByteArray(), true))
    }
    private fun json(count: Int): String = """{"algorithm":"SHA256","format":"CAdES","singlesigns":[${(0 until count).joinToString(",") { """{"datareference":"YWJj","id":"D$it"}""" }}]}"""
}

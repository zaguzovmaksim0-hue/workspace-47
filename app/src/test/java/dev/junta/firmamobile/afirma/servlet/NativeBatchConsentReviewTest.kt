package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.SigningAlgorithm
import org.junit.Assert.*
import org.junit.Test

class NativeBatchConsentReviewTest {
    @Test fun preparedLocalConsentUsesExactBytesInRequestOrderWithoutNetwork() {
        val bytes = NativeProtocolJson.encode(mapOf("algorithm" to "SHA256withRSA", "singlesigns" to listOf(
            mapOf("id" to "z", "format" to "CAdES", "datareference" to "YWJj"),
            mapOf("id" to "a", "format" to "CAdES", "datareference" to "ZA=="),
        )))
        val invocation = acceptedGeneral("batch", mapOf("id" to "ConsentBatch123",
            "stservlet" to "https://store.synthetic.example/storage", "jsonbatch" to "true",
            "localBatchProcess" to "true", "dat" to url64(bytes)))
        val operation = NativeMultiPhaseOperation(invocation,
            NativeSigningServiceTransport { _, _ -> error("No service during consent preparation") },
            AfirmaResultTransport { _, _, _ -> error("No upload during consent preparation") })
        try {
            assertEquals(2, operation.details.batchItems)
            val documents = operation.details.batchReview!!.documents
            assertEquals(listOf("z", "a"), documents.map { it.identifier })
            assertEquals(3, documents[0].evidence!!.bytes)
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                documents[0].evidence!!.sha256)
            assertEquals(1, documents[1].evidence!!.bytes)
            assertEquals("18ac3e7343f016890c510e93f935261169d9e3f565436429830faf0934f4f8e4",
                documents[1].evidence!!.sha256)
            assertNotEquals(operation.details.payloadSha256, documents[0].evidence!!.sha256)
        } finally { operation.close(); bytes.fill(0) }
    }

    @Test fun preparedDelegatedJsonAndXmlConsentNeverResolvesReferences() {
        val json = NativeProtocolJson.encode(mapOf("algorithm" to "SHA256withRSA", "singlesigns" to listOf(
            mapOf("id" to "z", "format" to "CAdES", "datareference" to "YWJj"),
            mapOf("id" to "a", "format" to "CAdES", "datareference" to "https://private.example/doc?secret=1"),
        )))
        val xml = """<signbatch algorithm="SHA256withRSA"><singlesign Id="z"><datasource>YWJj</datasource><format>CAdES</format></singlesign><singlesign Id="a"><datasource>https://private.example/doc?secret=1</datasource><format>CAdES</format></singlesign></signbatch>""".toByteArray()
        for ((isJson, bytes) in listOf(true to json, false to xml)) {
            val invocation = acceptedGeneral("batch", mapOf("id" to "ConsentBatch123",
                "stservlet" to "https://store.synthetic.example/storage", "jsonbatch" to "$isJson",
                "batchpresignerurl" to "https://service.example/pre",
                "batchpostsignerurl" to "https://service.example/post", "dat" to url64(bytes)))
            val operation = NativeMultiPhaseOperation(invocation,
                NativeSigningServiceTransport { _, _ -> error("No PRE/POST during review") },
                AfirmaResultTransport { _, _, _ -> error("No upload during review") })
            try {
                assertTrue(operation.details.delegatedSigning)
                assertEquals(2, operation.details.batchItems)
                val documents = operation.details.batchReview!!.documents
                assertEquals(listOf("z", "a"), documents.map { it.identifier })
                assertTrue(documents.all { it.evidence == null })
            } finally { operation.close(); bytes.fill(0) }
        }
    }

    @Test fun delegatedReferencesProvidedHashesAndExistingSignaturesAreNotDocumentEvidence() {
        val inputs = listOf(
            item("url", "https://private.example/document?secret=1"),
            item("digest", "YWJj", properties = mapOf(NativePrecalculatedHash.PROPERTY to "SHA-256")),
            item("cosign", "YWJj", operation = "cosign"),
            item("counter", "YWJj", operation = "countersign"),
            item("invalid", "YQ==="),
            item("noncanonical", "YR=="),
        )
        val descriptor = batch(inputs)
        assertTrue(NativeBatchConsentReview.from(descriptor, true).documents.all { it.evidence == null })
        // Even a base64-looking service reference is not a local document.
        assertNull(NativeBatchConsentReview.from(batch(listOf(item("service", "YWJj"))), false)
            .documents.single().evidence)
    }

    @Test fun displaySnapshotIsBoundedSanitizedAndIndependentOfMutableDescriptor() {
        val items = mutableListOf(item("z\u200B\n id", "YWJj"), item("x".repeat(200), "YWJj"), item("  ", "YWJj"))
        val review = NativeBatchConsentReview.from(batch(items), true)
        items.clear()
        assertEquals(listOf("z id", "x".repeat(119) + "…", ""), review.documents.map { it.identifier })
        assertThrows(UnsupportedOperationException::class.java) {
            (review.documents as MutableList<NativeBatchConsentReview.Document>).clear()
        }
    }

    @Test fun maximumBatchAndMissingOrOversizedDataStayBounded() {
        val review = NativeBatchConsentReview.from(batch((32 downTo 1).map { item("$it", "") }), true)
        assertEquals((32 downTo 1).map { "$it" }, review.documents.map { it.identifier })
        assertTrue(review.documents.all { it.evidence == null })
        assertNull(NativeBatchConsentReview.from(batch(listOf(item("large", "A".repeat(699_056)))), true)
            .documents.single().evidence)
        for (count in listOf(0, 33)) assertThrows(IllegalArgumentException::class.java) {
            NativeBatchConsentReview.from(batch(List(count) { item("$it", "YWJj") }), true)
        }
    }

    private fun batch(items: List<NativeBatchItem>) =
        NativeBatchDescriptor(true, SigningAlgorithm.SHA256_WITH_RSA, false, items)
    private fun item(id: String, reference: String, operation: String = "sign", properties: Map<String, String> = emptyMap()) =
        NativeBatchItem(id, "CAdES", operation, reference, properties)
}

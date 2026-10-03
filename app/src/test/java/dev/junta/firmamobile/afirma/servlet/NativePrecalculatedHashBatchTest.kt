package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.OpaqueRsaFixture
import dev.junta.firmamobile.signing.nonExportableSyntheticIdentity
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NativePrecalculatedHashBatchTest {
    @Test fun oneLocalPartyCanCombineOriginalContentProvidedHashAndPdf() = runBlocking<Unit> {
        val key = nonExportableSyntheticIdentity(); val original = NativePrecalculatedHashOperationTest.ORIGINAL
        val digest = MessageDigest.getInstance("SHA-256").digest(original)
        var authorizations = 0; var stores = 0
        val invocation = batch(listOf(
            item("document", original, properties = "mode=explicit"),
            item("hash", digest, properties = "precalculatedHashAlgorithm=SHA-256\nmode=implicit"),
            item("pdf", nativePadesFixture(), format = "PAdES"),
        ))
        val operation = NativeMultiPhaseOperation(invocation, NativeSigningServiceTransport { _, _ -> error("Local batch has no signing server") },
            AfirmaResultTransport { _, _, wire ->
                assertEquals(1, authorizations); stores++
                val rows = results(wire)
                assertEquals(listOf("document", "hash", "pdf"), rows.map { it["id"] })
                assertTrue(rows.all { it["result"] == "DONE_AND_SAVED" })
                for (index in 0..1) {
                    val cms = Base64.getDecoder().decode(rows[index]["signature"] as String)
                    try {
                        NativePrecalculatedHashOperationTest.assertDigestSignature(cms, original, digest, NativePrecalculatedHash.SHA256, key.identity)
                    } finally { cms.fill(0) }
                }
                val pdf = Base64.getDecoder().decode(rows[2]["signature"] as String)
                assertEquals("%PDF-", pdf.copyOfRange(0, 5).toString(Charsets.US_ASCII)); pdf.fill(0)
                AfirmaDeliveryResult.ACKNOWLEDGED
            }, generalClockFor(key.identity))
        try {
            assertEquals(1, operation.details.providedDigestItems); assertNull(operation.details.providedDigestAlgorithm)
            assertFalse(operation.details.delegatedSigning)
            assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, operation.execute(key.identity) { authorizations++ })
            assertEquals(1, stores); assertEquals(0, key.encodedReads.get())
            assertEquals(listOf("DONE_AND_SAVED", "DONE_AND_SAVED", "DONE_AND_SAVED"), operation.batchReceipt!!.entries.map { it.statusCode })
        } finally { operation.close() }
    }

    @Test fun aBadDigestLengthDoesNotBecomeARegularFileOrAnAllSuccessParty() = OpaqueRsaFixture.use { key ->
        runBlocking<Unit> {
            val digest = MessageDigest.getInstance("SHA-256").digest(NativePrecalculatedHashOperationTest.ORIGINAL)
            val operation = NativeMultiPhaseOperation(batch(listOf(
                item("bad", ByteArray(31), properties = "precalculatedHashAlgorithm=SHA-256"),
                item("good", digest, properties = "precalculatedHashAlgorithm=SHA-256"),
            )), NativeSigningServiceTransport { _, _ -> error("No service") }, AfirmaResultTransport { _, _, wire ->
                val rows = results(wire)
                assertEquals(listOf("ERROR_PRE", "DONE_AND_SAVED"), rows.map { it["result"] })
                assertFalse(rows[0].containsKey("signature")); assertTrue(rows[1].containsKey("signature"))
                AfirmaDeliveryResult.ACKNOWLEDGED
            }, generalClockFor(key.identity))
            try {
                assertEquals(2, operation.details.providedDigestItems)
                assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, operation.execute(key.identity) {})
                assertEquals(1, key.signatures.get()); assertEquals(0, key.encodingReads.get())
            } finally { operation.close() }
        }
    }

    @Test fun stopOnErrorPreventsKeyUseForTheRemainingDigestItems() = OpaqueRsaFixture.use { key ->
        runBlocking<Unit> {
            val operation = NativeMultiPhaseOperation(batch(listOf(
                item("bad", ByteArray(64), properties = "precalculatedHashAlgorithm=SHA-512"),
                item("remaining", ByteArray(32), properties = "precalculatedHashAlgorithm=SHA-256"),
            ), stopOnError = true), NativeSigningServiceTransport { _, _ -> error("No service") }, AfirmaResultTransport { _, _, wire ->
                assertEquals(listOf("ERROR_PRE", "SKIPPED"), results(wire).map { it["result"] })
                AfirmaDeliveryResult.ACKNOWLEDGED
            }, generalClockFor(key.identity))
            try {
                assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, operation.execute(key.identity) {})
                assertEquals(0, key.signatures.get()); assertEquals(0, key.encodingReads.get())
            } finally { operation.close() }
        }
    }

    @Test fun unsupportedHashOperationsAndFormatsNeverUseTheKey() = OpaqueRsaFixture.use { key ->
        runBlocking<Unit> {
            val supplied = "precalculatedHashAlgorithm=SHA-256"
            val operation = NativeMultiPhaseOperation(batch(listOf(
                item("cosign", ByteArray(32), operation = "cosign", properties = supplied),
                item("counter", ByteArray(32), operation = "countersign", properties = supplied),
                item("xml", ByteArray(32), format = "XAdES", properties = supplied),
                item("pdf", nativePadesFixture(), format = "PAdES", properties = supplied),
            )), NativeSigningServiceTransport { _, _ -> error("No service") }, AfirmaResultTransport { _, _, wire ->
                val rows = results(wire); assertEquals(4, rows.size)
                assertTrue(rows.all { it["result"] == "ERROR_PRE" && !it.containsKey("signature") })
                AfirmaDeliveryResult.ACKNOWLEDGED
            }, generalClockFor(key.identity))
            try {
                assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, operation.execute(key.identity) {})
                assertEquals(0, key.signatures.get()); assertEquals(0, key.encodingReads.get())
            } finally { operation.close() }
        }
    }

    private fun item(id: String, data: ByteArray, format: String = "CAdES", operation: String = "sign", properties: String = "") =
        mapOf("id" to id, "format" to format, "suboperation" to operation, "datareference" to url64(data), "extraparams" to url64(properties.toByteArray()))
    private fun batch(items: List<Map<String, String>>, stopOnError: Boolean = false): AfirmaServletInvocation =
        acceptedGeneral("batch", mapOf("id" to "HashBatch123", "stservlet" to NativePrecalculatedHashOperationTest.STORE,
            "jsonbatch" to "true", "localBatchProcess" to "true", "dat" to url64(NativeProtocolJson.encode(mapOf(
                "algorithm" to "SHA256withRSA", "stoponerror" to stopOnError, "singlesigns" to items)))))
    private fun results(wire: String): List<Map<String, Any?>> {
        val bytes = AfirmaIntermediateCipher.decode(wire, null)
        return try { (NativeProtocolJson.parse(bytes)["signs"] as List<*>).map(NativeTriphaseCodec::objectValue) }
        finally { bytes.fill(0) }
    }
}

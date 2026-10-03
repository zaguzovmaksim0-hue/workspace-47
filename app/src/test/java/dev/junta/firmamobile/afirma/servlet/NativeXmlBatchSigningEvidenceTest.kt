package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.nonExportableSyntheticIdentity
import java.net.URI
import java.security.Signature
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Actual remote-batch orchestration, generated test keys and in-memory services.
 * A missing XML PRE entry has no fabricated error code; it still has no PK1. */
class NativeXmlBatchSigningEvidenceTest {
    @Test fun postCannotClaimASignatureForAnXmlDocumentNeverOfferedToTheKey() = runBlocking<Unit> {
        val f = Fixture("OK", listOf(NativeTriSign("one", null, params())))
        try {
            assertEquals("An XML document without local PK1 cannot become signed in the accepted result",
                AfirmaDeliveryResult.UNCERTAIN, f.execute())
            assertEquals(2, f.exchanges); assertEquals(1, f.authorizations)
            assertEquals(listOf("one"), f.locallySignedIds); assertEquals(0, f.stores)
            assertNull(f.operation.resultSummary); assertNull(f.operation.batchReceipt)
            assertThrows(IllegalStateException::class.java) { runBlocking { f.execute() } }
        } finally { f.operation.close() }
    }

    @Test fun missingXmlPreMayStillHaveAnHonestServerFailureWithoutAnInventedCode() = runBlocking<Unit> {
        val f = Fixture("KO", listOf(NativeTriSign("one", null, params())))
        try {
            assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, f.execute())
            assertEquals(2, f.exchanges); assertEquals(1, f.stores)
            assertEquals(listOf("one"), f.locallySignedIds)
            assertArrayEquals(f.response, AfirmaIntermediateCipher.decode(checkNotNull(f.wire), null))
            assertEquals(listOf("OK", "KO"), f.operation.batchReceipt!!.entries.map { it.statusCode })
            assertEquals("Not prepared by the service", f.operation.batchReceipt!!.entries[1].description)
            assertEquals(0, f.key.encodedReads.get())
        } finally { f.operation.close() }
    }

    @Test fun multipleCounterSignatureTargetsOfOneDocumentDoNotInventAnotherSignedDocument() = runBlocking<Unit> {
        val f = Fixture("NOT_STARTED", listOf(
            NativeTriSign("one", "signature-a", params()), NativeTriSign("one", "signature-b", params()),
        ), "countersign")
        try {
            assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, f.execute())
            assertEquals(listOf("one", "one"), f.locallySignedIds)
            assertEquals(listOf("signature-a", "signature-b"), f.localSignatureIds)
            assertEquals(1, f.stores)
            assertArrayEquals(f.response, AfirmaIntermediateCipher.decode(checkNotNull(f.wire), null))
        } finally { f.operation.close() }
    }

    @Test fun genuinelySignedItemsMayReportSavingFailureWithoutRepeatingTheOperation() = runBlocking<Unit> {
        val f = Fixture("DONE_BUT_ERROR_SAVING", listOf(
            NativeTriSign("one", null, params()), NativeTriSign("two", null, params()),
        ))
        try {
            assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, f.execute())
            assertEquals(listOf("one", "two"), f.locallySignedIds)
            assertEquals(listOf("OK", "DONE_BUT_ERROR_SAVING"), f.operation.batchReceipt!!.entries.map { it.statusCode })
            assertEquals(2, f.exchanges); assertEquals(1, f.stores)
        } finally { f.operation.close() }
    }

    private class Fixture(status: String, offered: List<NativeTriSign>, operationName: String = "sign") {
        val key = nonExportableSyntheticIdentity()
        var exchanges = 0; var stores = 0; var authorizations = 0
        var locallySignedIds: List<String> = emptyList(); var localSignatureIds: List<String?> = emptyList()
        var wire: String? = null
        private val descriptor = ("<signbatch algorithm='SHA256withRSA' stoponerror='false'>" +
            listOf("one", "two").joinToString("") {
                "<singlesign id='$it'><datasource>YWJj</datasource><format>XAdES</format><suboperation>$operationName</suboperation></singlesign>"
            } + "</signbatch>").toByteArray()
        val response = ("<signs><sign id='one'><result>OK</result></sign>" +
            "<sign id='two'><result>$status</result><reason>Not prepared by the service</reason></sign></signs>").toByteArray()
        val operation = NativeMultiPhaseOperation(acceptedGeneral("batch", mapOf(
            "id" to "Evidence123", "stservlet" to "https://storage.example/put", "dat" to url64(descriptor),
            "jsonbatch" to "false", "batchpresignerurl" to "https://service.example/pre",
            "batchpostsignerurl" to "https://service.example/post",
        )), NativeSigningServiceTransport { endpoint, fields ->
            exchanges++
            assertArrayEquals(descriptor, Base64.getUrlDecoder().decode(fields.getValue("xml")))
            if (exchanges == 1) {
                assertEquals(URI("https://service.example/pre"), endpoint)
                AfirmaRetrievedBytes(checkNotNull(NativeTriphaseXml.encode(NativeTriSession("XAdES", offered))))
            } else {
                assertEquals(2, exchanges); assertEquals(URI("https://service.example/post"), endpoint)
                assertEquals(1, authorizations)
                val session = checkNotNull(NativeTriphaseXml.parse(Base64.getUrlDecoder().decode(fields.getValue("tridata"))))
                locallySignedIds = session.signs.map { it.id }; localSignatureIds = session.signs.map { it.signatureId }
                session.signs.forEach {
                    assertFalse(it.parameters.containsKey("PRE"))
                    assertTrue(Signature.getInstance("SHA256withRSA").run {
                        initVerify(key.identity.certificate.publicKey); update("synthetic PRE".toByteArray())
                        verify(Base64.getDecoder().decode(it.parameters.getValue("PK1")))
                    })
                }
                AfirmaRetrievedBytes(response.copyOf())
            }
        }, AfirmaResultTransport { _, _, value -> stores++; wire = value; AfirmaDeliveryResult.ACKNOWLEDGED }, generalClockFor(key.identity))
        suspend fun execute(): AfirmaDeliveryResult = operation.executeWithCheckpoints(key.identity, {}, { authorizations++ })
    }
    companion object { private fun params() = mapOf("PRE" to Base64.getEncoder().encodeToString("synthetic PRE".toByteArray()), "NEED_PRE" to "false") }
}

package dev.junta.firmamobile.afirma.servlet

import org.junit.Assert.*
import org.junit.Test

/** Independent status expectations; do not read the production allow-list. */
class NativeBatchSignedIdsTest {
    private val claimsSignature = listOf("OK", "DONE_AND_SAVED", "DONE_BUT_NOT_SAVED_YET",
        "DONE_BUT_SAVED_SKIPPED", "DONE_BUT_ERROR_SAVING", "SAVE_ROLLBACKED")
    private val noSignatureClaim = listOf("NOT_STARTED", "ERROR_PRE", "ERROR_POST", "SKIPPED", "KO", "NP")
    private val all = claimsSignature + noSignatureClaim

    @Test fun anUnpreparedDocumentCannotClaimAGeneratedSignatureInEitherWireFormat() {
        var combinations = 0
        for (json in listOf(false, true)) for (status in all) {
            val bytes = result(json, "OK", status); val before = bytes.copyOf()
            if (status in claimsSignature) {
                assertThrows(IllegalArgumentException::class.java) {
                    NativeBatchProtocol.results(bytes, descriptor(json), signedIds = setOf("one"))
                }
            } else {
                val parsed = NativeBatchProtocol.results(bytes, descriptor(json), signedIds = setOf("one"))
                assertEquals(listOf("one", "two"), parsed.map { it.id })
                assertEquals(listOf("OK", status), parsed.map { it.status })
            }
            assertArrayEquals(before, bytes); combinations++
        }
        assertEquals(24, combinations)
    }

    @Test fun aGenuinelySignedDocumentRetainsEverySupportedOutcomeWithoutRewritingTheResponse() {
        var combinations = 0
        for (json in listOf(false, true)) for (status in all) {
            val bytes = result(json, "OK", status); val before = bytes.copyOf()
            val parsed = NativeBatchProtocol.results(bytes, descriptor(json), signedIds = setOf("one", "two"))
            assertEquals(listOf("OK", status), parsed.map { it.status })
            assertArrayEquals(before, bytes); combinations++
        }
        assertEquals(24, combinations)
    }

    @Test fun signedEvidenceCannotAddForeignIdsOrOverlapAnExplicitPreFailure() {
        for (json in listOf(false, true)) {
            val bytes = result(json, "OK", "ERROR_PRE"); val batch = descriptor(json)
            val errors = listOf(NativeBatchProtocol.Outcome("two", "ERROR_PRE", "Original PRE failure"))
            for (ids in listOf(setOf("one", "foreign"), setOf("one", "two"))) {
                assertThrows(IllegalArgumentException::class.java) {
                    NativeBatchProtocol.results(bytes, batch, errors, ids)
                }
            }
            val parsed = NativeBatchProtocol.results(bytes, batch, errors, setOf("one"))
            assertEquals("Original PRE failure", parsed[1].description)
            assertEquals("ERROR_PRE", parsed[1].status)
        }
    }

    @Test fun evidenceBindsToTheExactIdRatherThanTheOrderOfServerRows() {
        val rows = listOf(NativeBatchProtocol.Outcome("two", "OK"), NativeBatchProtocol.Outcome("one", "SKIPPED"))
        for (json in listOf(false, true)) {
            val bytes = wire(json, rows); val batch = descriptor(json)
            assertEquals(rows, NativeBatchProtocol.results(bytes, batch, signedIds = setOf("two")))
            assertThrows(IllegalArgumentException::class.java) {
                NativeBatchProtocol.results(bytes, batch, signedIds = setOf("one"))
            }
        }
    }

    @Test fun absentParsingContextIsDifferentFromProofThatNoDocumentsWereSigned() {
        for (json in listOf(false, true)) {
            val batch = descriptor(json); val positive = result(json, "OK", "OK")
            assertEquals(2, NativeBatchProtocol.results(positive, batch).size)
            assertThrows(IllegalArgumentException::class.java) {
                NativeBatchProtocol.results(positive, batch, signedIds = emptySet())
            }
            val negative = result(json, "NOT_STARTED", "ERROR_PRE")
            assertEquals(listOf("NOT_STARTED", "ERROR_PRE"),
                NativeBatchProtocol.results(negative, batch, signedIds = emptySet()).map { it.status })
        }
    }

    private fun descriptor(json: Boolean): NativeBatchDescriptor {
        val bytes = if (json) NativeProtocolJson.encode(mapOf("algorithm" to "SHA256withRSA", "format" to "XAdES",
            "singlesigns" to listOf("one", "two").map { mapOf("id" to it, "datareference" to "YWJj") }))
        else ("<signbatch algorithm='SHA256withRSA'>" + listOf("one", "two").joinToString("") {
            "<singlesign id='$it'><datasource>YWJj</datasource><format>XAdES</format><suboperation>sign</suboperation></singlesign>"
        } + "</signbatch>").toByteArray()
        return checkNotNull(NativeBatchDescriptor.parse(bytes, json))
    }
    private fun result(json: Boolean, first: String, second: String) = wire(json, listOf(
        NativeBatchProtocol.Outcome("one", first), NativeBatchProtocol.Outcome("two", second),
    ))
    private fun wire(json: Boolean, rows: List<NativeBatchProtocol.Outcome>): ByteArray =
        if (json) NativeBatchProtocol.jsonReport(rows)
        else ("<signs>" + rows.joinToString("") { "<sign id='${it.id}'><result>${it.status}</result></sign>" } + "</signs>").toByteArray()
}

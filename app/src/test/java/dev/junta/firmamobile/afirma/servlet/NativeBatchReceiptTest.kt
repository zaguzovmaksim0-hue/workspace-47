package dev.junta.firmamobile.afirma.servlet

import org.junit.Assert.*
import org.junit.Test

class NativeBatchReceiptTest {
    @Test fun displayFollowsApprovedOrderAndPreservesTheOriginalResults() {
        val outcomes = listOf(NativeBatchProtocol.Outcome("two", "ERROR_PRE", "Service explanation"), NativeBatchProtocol.Outcome("one", "DONE_AND_SAVED"))
        val snapshot = NativeBatchReceipt.from(batch(), outcomes, NativeBatchReceipt.Origin.SERVICE)
        assertEquals(listOf("one", "two"), snapshot.entries.map { it.documentLabel })
        assertEquals(listOf("DONE_AND_SAVED", "ERROR_PRE"), snapshot.entries.map { it.statusCode })
        assertEquals("Service explanation", snapshot.entries[1].description)
        assertEquals(listOf("two", "one"), outcomes.map { it.id })
        assertEquals(NativeBatchReceipt.Origin.SERVICE, snapshot.origin)
    }
    @Test fun missingDuplicateAndForeignIdsNeverProduceAPartialLookingSuccessReceipt() {
        for (ids in listOf(listOf("one"), listOf("one", "one"), listOf("one", "other"))) {
            assertThrows(IllegalArgumentException::class.java) {
                NativeBatchReceipt.from(batch(), ids.map { NativeBatchProtocol.Outcome(it, "DONE_AND_SAVED") }, NativeBatchReceipt.Origin.SERVICE)
            }
        }
    }
    @Test fun snapshotCannotBeRewrittenByALaterListMutation() {
        val entries = mutableListOf(NativeBatchReceipt.Entry("first", "ERROR_PRE", null))
        val receipt = NativeBatchReceipt(NativeBatchReceipt.Origin.LOCAL, entries)
        entries[0] = NativeBatchReceipt.Entry("replaced", "DONE_AND_SAVED", null)
        entries.clear()
        assertEquals("first", receipt.entries.single().documentLabel)
        assertEquals("ERROR_PRE", receipt.entries.single().statusCode)
        assertThrows(UnsupportedOperationException::class.java) { (receipt.entries as MutableList<NativeBatchReceipt.Entry>).clear() }
    }
    @Test fun untrustedTextIsBoundedForDisplayWithoutChangingTheStatusCode() {
        val label = "\u202eprivate\u202c\n document"
        val description = "\u2066" + "😀".repeat(600) + "\u2069"
        val receipt = NativeBatchReceipt(NativeBatchReceipt.Origin.SERVICE, listOf(NativeBatchReceipt.Entry(label, "ERROR_PRE", description)))
        val entry = receipt.entries.single()
        assertEquals("private document", entry.documentLabel)
        assertEquals("ERROR_PRE", entry.statusCode)
        assertEquals(512, entry.description!!.codePointCount(0, entry.description.length))
        assertTrue(entry.description.endsWith("…")); assertFalse(entry.description.contains('\u2066'))
        assertTrue(label.contains('\u202e')); assertEquals(1202, description.length)
    }
    @Test fun diagnosticDescriptionsDoNotLeakDocumentIdentifiersOrReceivedText() {
        val entry = NativeBatchReceipt.Entry("private-document-token", "ERROR_POST", "private-description-token")
        val receipt = NativeBatchReceipt(NativeBatchReceipt.Origin.SERVICE, listOf(entry))
        assertFalse(receipt.toString().contains("private")); assertFalse(entry.toString().contains("private"))
        assertTrue(receipt.toString().contains("SERVICE")); assertTrue(entry.toString().contains("ERROR_POST"))
    }
    @Test fun receiptBoundsAndBlankLabelsAreExplicit() {
        val blank = NativeBatchReceipt(NativeBatchReceipt.Origin.LOCAL, listOf(NativeBatchReceipt.Entry("\u202e", "SKIPPED", "  ")))
        assertEquals("—", blank.entries.single().documentLabel); assertNull(blank.entries.single().description)
        assertThrows(IllegalArgumentException::class.java) { NativeBatchReceipt(NativeBatchReceipt.Origin.LOCAL, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { NativeBatchReceipt(NativeBatchReceipt.Origin.LOCAL, List(33) { NativeBatchReceipt.Entry("x", "OK", null) }) }
        assertThrows(IllegalArgumentException::class.java) { NativeBatchReceipt(NativeBatchReceipt.Origin.LOCAL, listOf(NativeBatchReceipt.Entry("x", "OK\nERROR_PRE", null))) }
    }
    private fun batch() = checkNotNull(NativeBatchDescriptor.parse(NativeProtocolJson.encode(mapOf(
        "algorithm" to "SHA256withRSA", "format" to "XAdES",
        "singlesigns" to listOf(mapOf("id" to "one", "datareference" to "YWJj"), mapOf("id" to "two", "datareference" to "ZGVm")),
    )), true))
}

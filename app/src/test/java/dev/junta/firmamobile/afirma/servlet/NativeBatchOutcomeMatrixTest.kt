package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.afirma.servlet.NativeBatchProtocol.Outcome
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NativeBatchOutcomeMatrixTest {
    private val batch = requireNotNull(NativeBatchDescriptor.parse(
        NativeProtocolJson.encode(mapOf(
            "algorithm" to "SHA256withRSA", "format" to "XAdES",
            "singlesigns" to listOf(
                mapOf("id" to "one", "datareference" to "YWJj"),
                mapOf("id" to "two", "datareference" to "ZGVm")
            )
        )), json = true
    ))
    private val preStatuses = listOf("ERROR_PRE", "SKIPPED", "NOT_STARTED")
    private val finalStatuses = listOf(
        "NOT_STARTED", "DONE_AND_SAVED", "DONE_BUT_NOT_SAVED_YET",
        "DONE_BUT_SAVED_SKIPPED", "DONE_BUT_ERROR_SAVING", "ERROR_PRE",
        "ERROR_POST", "SKIPPED", "SAVE_ROLLBACKED", "OK", "KO", "NP"
    )

    private fun rows(status: String, description: String? = null) = listOf(
        Outcome("one", "DONE_AND_SAVED"),
        Outcome("two", status, description)
    )
    private fun snapshot(values: List<Outcome>) =
        values.map { Triple(it.id, it.status, it.description) }

    @Test
    fun everyPreStatusRequiresItsExactPostStatus() {
        var comparisons = 0
        for (preStatus in preStatuses) {
            val pre = listOf(Outcome("two", preStatus, "synthetic PRE explanation"))
            for (postStatus in finalStatuses) {
                val wire = NativeBatchProtocol.jsonReport(rows(postStatus))
                if (postStatus == preStatus) {
                    val actual = NativeBatchProtocol.results(wire, batch, pre)
                    assertEquals(2, actual.size)
                    assertEquals("DONE_AND_SAVED", actual.single { it.id == "one" }.status)
                    assertEquals(preStatus, actual.single { it.id == "two" }.status)
                } else {
                    assertThrows(IllegalArgumentException::class.java) {
                        NativeBatchProtocol.results(wire, batch, pre)
                    }
                }
                comparisons++
            }
        }
        assertEquals(36, comparisons)
        for (status in finalStatuses) {
            val actual = NativeBatchProtocol.results(
                NativeBatchProtocol.jsonReport(rows(status)), batch
            )
            assertEquals(2, actual.size)
            assertEquals(status, actual.single { it.id == "two" }.status)
        }
    }

    @Test
    fun preExplanationIsFallbackAndInputsRemainUnchanged() {
        val pre = mutableListOf(Outcome("two", "ERROR_PRE", "synthetic PRE explanation"))
        val preBefore = snapshot(pre)
        for (description in listOf<String?>(null, "synthetic server explanation")) {
            val post = rows("ERROR_PRE", description)
            val postBefore = snapshot(post)
            val wire = NativeBatchProtocol.jsonReport(post)
            val keptCopy = wire.copyOf()
            val actual = NativeBatchProtocol.results(wire, batch, pre)
            val failed = actual.single { it.id == "two" }
            assertEquals("ERROR_PRE", failed.status)
            assertEquals(description ?: "synthetic PRE explanation", failed.description)
            assertEquals(preBefore, snapshot(pre))
            assertEquals(postBefore, snapshot(post))
            assertArrayEquals(keptCopy, wire)
            assertArrayEquals(keptCopy, NativeBatchProtocol.jsonReport(post))
        }
    }

    @Test
    fun reorderedPostMatchesPreFailureByExactId() {
        val pre = listOf(Outcome("two", "ERROR_PRE", "synthetic PRE explanation"))
        val post = listOf(
            Outcome("two", "ERROR_PRE"),
            Outcome("one", "DONE_AND_SAVED")
        )
        val actual = NativeBatchProtocol.results(NativeBatchProtocol.jsonReport(post), batch, pre)
        assertEquals(2, actual.size)
        val failed = actual.single { it.id == "two" }
        assertEquals("ERROR_PRE", failed.status)
        assertEquals("synthetic PRE explanation", failed.description)
        assertEquals("DONE_AND_SAVED", actual.single { it.id == "one" }.status)
        val swapped = listOf(
            Outcome("two", "DONE_AND_SAVED"),
            Outcome("one", "ERROR_PRE")
        )
        assertThrows(IllegalArgumentException::class.java) {
            NativeBatchProtocol.results(NativeBatchProtocol.jsonReport(swapped), batch, pre)
        }
    }

    @Test
    fun invalidPreRowsAreRejectedWithValidFinalRows() {
        val validWire = NativeBatchProtocol.jsonReport(rows("ERROR_PRE"))
        val invalidPreLists = listOf(
            listOf(Outcome("two", "ERROR_PRE"), Outcome("two", "ERROR_PRE")),
            listOf(Outcome("synthetic-foreign-id", "ERROR_PRE")),
            listOf(Outcome("one", "DONE_AND_SAVED"))
        )
        for (pre in invalidPreLists) {
            assertThrows(IllegalArgumentException::class.java) {
                NativeBatchProtocol.results(validWire, batch, pre)
            }
        }
    }
}

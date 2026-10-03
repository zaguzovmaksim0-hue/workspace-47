package dev.junta.firmamobile.afirma.servlet

import org.junit.Assert.*
import org.junit.Test

class NativeBatchProtocolContractTest {
    @Test fun validPartialResultPreservesReadyInputAndReplacesOnlyTheFailedRow() {
        val before = descriptorBytes()
        val batch = checkNotNull(NativeBatchDescriptor.parse(before, true))
        val response = NativeProtocolJson.encode(mapOf("td" to td("one"), "results" to listOf(row("two", "ERROR_PRE"))))
        val pre = NativeBatchProtocol.pre(response, batch)
        assertEquals(listOf("one"), pre.session!!.signs.map { it.id })
        val after = NativeProtocolJson.parse(NativeBatchProtocol.descriptorForPost(before, pre.errors))["singlesigns"] as List<*>
        val original = NativeProtocolJson.parse(before)["singlesigns"] as List<*>
        assertEquals(original[0], after[0])
        val failed = NativeTriphaseCodec.objectValue(after[1])
        assertEquals("two", failed["id"]); assertEquals("ERROR_PRE", failed["result"])
        assertFalse(failed.containsKey("datareference")); assertFalse(failed.containsKey("format"))
    }
    @Test fun preCannotOmitOrInventAnApprovedDocumentOrOverlapSuccessAndFailure() {
        val batch = checkNotNull(NativeBatchDescriptor.parse(descriptorBytes(), true))
        for (response in listOf(
            mapOf("td" to td("one")),
            mapOf("td" to td("unknown"), "results" to listOf(row("two", "ERROR_PRE"))),
            mapOf("td" to td("one"), "results" to listOf(row("one", "ERROR_PRE"), row("two", "ERROR_PRE"))),
            mapOf("td" to td("one"), "results" to listOf(row("two", "ERROR_PRE"), row("two", "ERROR_PRE"))),
        )) assertThrows(IllegalArgumentException::class.java) { NativeBatchProtocol.pre(NativeProtocolJson.encode(response), batch) }
    }
    @Test fun completeResultMustContainEachRequestedIdExactlyOnce() {
        val batch = checkNotNull(NativeBatchDescriptor.parse(descriptorBytes(), true))
        for (rows in listOf(listOf(row("one")), listOf(row("one"), row("one")), listOf(row("one"), row("foreign")))) {
            assertThrows(IllegalArgumentException::class.java) { NativeBatchProtocol.results(NativeProtocolJson.encode(mapOf("signs" to rows)), batch) }
        }
    }
    @Test fun unsupportedStatusIsNotCountedAsSuccess() {
        val batch = checkNotNull(NativeBatchDescriptor.parse(descriptorBytes(), true))
        assertThrows(IllegalArgumentException::class.java) {
            NativeBatchProtocol.results(NativeProtocolJson.encode(mapOf("signs" to listOf(row("one", "MAGIC_SUCCESS"), row("two")))), batch)
        }
    }
    @Test fun malformedOptionalValuesAreRejectedInsteadOfSilentlyDropped() {
        val batch = checkNotNull(NativeBatchDescriptor.parse(descriptorBytes(), true))
        for ((key, bad) in listOf("description" to 9, "signature" to mapOf("untrusted" to "object"))) {
            val rows = listOf(row("one") + (key to bad), row("two"))
            assertThrows(IllegalArgumentException::class.java) { NativeBatchProtocol.results(NativeProtocolJson.encode(mapOf("signs" to rows)), batch) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeBatchProtocol.pre(NativeProtocolJson.encode(mapOf("td" to td("one", "two"), "results" to "not-an-array")), batch)
        }
    }
    @Test fun xmlResultsRetainPartialSuccessWithoutPretendingEveryDocumentWasSaved() {
        val xml = "<signbatch algorithm='SHA256withRSA'>" + listOf("one", "two").joinToString("") {
            "<singlesign id='$it'><datasource>YWJj</datasource><format>XAdES</format><suboperation>sign</suboperation></singlesign>"
        } + "</signbatch>"
        val batch = checkNotNull(NativeBatchDescriptor.parse(xml.toByteArray(), false))
        val result = NativeBatchProtocol.results("<signs><sign id='one'><result>OK</result></sign><sign Id='two'><result>KO</result><reason>Local failure</reason></sign></signs>".toByteArray(), batch)
        assertEquals(listOf("one", "two"), result.map { it.id })
        assertEquals(listOf("OK", "KO"), result.map { it.status })
        assertTrue(NativeBatchProtocol.summary(result).contains("positivo: 1"))
        assertTrue(NativeBatchProtocol.summary(result).contains("otros resultados: 1"))
    }
    @Test fun invalidDescriptorPropertiesCannotRemoveConstraintsWithoutNotice() {
        for (bad in listOf(7, listOf("not-properties"), mapOf("policyIdentifier" to "must-not-be-ignored"))) {
            val value = mapOf("algorithm" to "SHA256withRSA", "format" to "XAdES", "extraparams" to bad,
                "singlesigns" to listOf(mapOf("id" to "one", "datareference" to "YWJj")))
            assertNull(NativeBatchDescriptor.parse(NativeProtocolJson.encode(value), true))
        }
    }
    @Test fun preFailureCannotBecomeSuccessOrChangeItsStatusAfterPost() {
        val batch = checkNotNull(NativeBatchDescriptor.parse(descriptorBytes(), true))
        for (status in listOf("DONE_AND_SAVED", "OK", "ERROR_POST", "SKIPPED")) {
            assertThrows(IllegalArgumentException::class.java) {
                NativeBatchProtocol.results(NativeProtocolJson.encode(mapOf("signs" to listOf(row("one"), row("two", status)))),
                    batch, listOf(NativeBatchProtocol.Outcome("two", "ERROR_PRE", "Prior failure")))
            }
        }
    }
    @Test fun preservedPreFailureKeepsItsDescriptionWhenPostOmitsIt() {
        val batch = checkNotNull(NativeBatchDescriptor.parse(descriptorBytes(), true))
        val result = NativeBatchProtocol.results(NativeProtocolJson.encode(mapOf("signs" to listOf(row("one"), row("two", "ERROR_PRE")))),
            batch, listOf(NativeBatchProtocol.Outcome("two", "ERROR_PRE", "Prior failure")))
        assertEquals("Prior failure", result[1].description)
        assertEquals(listOf("DONE_AND_SAVED", "ERROR_PRE"), result.map { it.status })
    }
    @Test fun xmlFailureReasonIsRetainedAndDuplicateReasonsAreNotSilentlyChosen() {
        val descriptor = "<signbatch algorithm='SHA256withRSA'><singlesign id='one'><datasource>YWJj</datasource><format>XAdES</format><suboperation>sign</suboperation></singlesign></signbatch>"
        val batch = checkNotNull(NativeBatchDescriptor.parse(descriptor.toByteArray(), false))
        val xml = "<signs><sign id='one'><result>KO</result><reason>Cannot save</reason><description>Storage unavailable</description></sign></signs>"
        assertEquals("Cannot save\nStorage unavailable", NativeBatchProtocol.results(xml.toByteArray(), batch).single().description)
        assertThrows(IllegalArgumentException::class.java) {
            NativeBatchProtocol.results(xml.replace("</reason>", "</reason><reason>Contradictory reason</reason>").toByteArray(), batch)
        }
    }

    private fun row(id: String, status: String = "DONE_AND_SAVED"): Map<String, Any?> = mapOf("id" to id, "result" to status)
    private fun td(vararg ids: String) = mapOf("format" to "XAdES", "signinfo" to ids.map { mapOf("id" to it, "params" to mapOf("PRE" to "YWJj")) })
    private fun descriptorBytes() = NativeProtocolJson.encode(mapOf("algorithm" to "SHA256withRSA", "format" to "XAdES",
        "singlesigns" to listOf(mapOf("id" to "one", "datareference" to "YWJj"), mapOf("id" to "two", "datareference" to "ZGVm"))))
}

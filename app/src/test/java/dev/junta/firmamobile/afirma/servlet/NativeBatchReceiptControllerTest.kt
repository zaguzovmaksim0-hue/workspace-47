package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.certificate.UnlockedIdentity
import dev.junta.firmamobile.signing.nonExportableSyntheticIdentity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NativeBatchReceiptControllerTest {
    @Test fun finalSnapshotSurvivesOperationCleanupButCannotAuthorizeAnotherRun() = runTest {
        val key = nonExportableSyntheticIdentity().identity; val owner = Any()
        val op = Operation(AfirmaDeliveryResult.ACKNOWLEDGED)
        val prompts = mutableListOf<AfirmaConsentPrompt?>()
        val controller = AfirmaConsentController(this, { candidate: Any, epoch -> candidate === owner && epoch == 1L }, { key }, { true }, { prompts += it }, { 0L })
        assertTrue(controller.offer(owner, 1L, op)); assertNull(prompts.last()!!.batchReceipt)
        assertTrue(controller.confirm(prompts.last()!!.token)); runCurrent()
        val finished = prompts.last()!!
        assertEquals(AfirmaConsentProblem.ACKNOWLEDGED, finished.problem)
        assertSame(op.snapshot, finished.batchReceipt); assertNull(op.batchReceipt)
        assertEquals(1, op.closes); assertEquals(1, op.executions)
        assertFalse(controller.confirm(finished.token)); assertFalse(finished.canConfirm)
        assertTrue(prompts.filterNotNull().filter { it.phase != AfirmaConsentPhase.FINISHED }.all { it.batchReceipt == null })
        controller.dismiss(finished.token); assertNull(prompts.last()); assertEquals(1, op.closes)
        controller.close()
    }
    @Test fun aServerResultDoesNotTurnUnknownReceiptDeliveryIntoAcknowledgement() = runTest {
        val key = nonExportableSyntheticIdentity().identity; val op = Operation(AfirmaDeliveryResult.UNCERTAIN)
        var prompt: AfirmaConsentPrompt? = null
        val controller = AfirmaConsentController(this, { _: Any, _: Long -> true }, { key }, { true }, { prompt = it }, { 0L })
        controller.offer(Any(), 0, op); controller.confirm(prompt!!.token); runCurrent()
        assertEquals(AfirmaConsentProblem.UNCERTAIN, prompt!!.problem)
        assertEquals(NativeBatchReceipt.Origin.SERVICE, prompt!!.batchReceipt!!.origin)
        assertEquals("DONE_AND_SAVED", prompt!!.batchReceipt!!.entries[0].statusCode)
        assertEquals("ERROR_PRE", prompt!!.batchReceipt!!.entries[1].statusCode)
        assertFalse(prompt!!.canConfirm); assertEquals(1, op.executions)
        controller.close(); assertNull(prompt)
    }
    @Test fun dismissingReviewDoesNotExposeOrExecuteAnUnrequestedResult() = runTest {
        val key = nonExportableSyntheticIdentity().identity; val op = Operation(AfirmaDeliveryResult.ACKNOWLEDGED)
        var prompt: AfirmaConsentPrompt? = null
        val controller = AfirmaConsentController(this, { _: Any, _: Long -> true }, { key }, { true }, { prompt = it }, { 0L })
        controller.offer(Any(), 0, op); assertNull(prompt!!.batchReceipt)
        controller.dismiss(prompt!!.token); runCurrent()
        assertNull(prompt); assertEquals(0, op.executions); assertEquals(1, op.closes)
        controller.close()
    }
    private class Operation(private val delivery: AfirmaDeliveryResult) : PreparedAfirmaOperation {
        val snapshot = NativeBatchReceipt(NativeBatchReceipt.Origin.SERVICE, listOf(
            NativeBatchReceipt.Entry("one", "DONE_AND_SAVED", null), NativeBatchReceipt.Entry("two", "ERROR_PRE", "Synthetic failure"),
        ))
        override val details = AfirmaConsentDetails("https://source.example", "https://storage.example/put", "batch", "Batch", "SHA256withRSA", 0, null, batchItems = 2)
        override var batchReceipt: NativeBatchReceipt? = null
        var executions = 0; var closes = 0
        override fun certificateCompatible(identity: UnlockedIdentity) = true
        override suspend fun execute(identity: UnlockedIdentity, authorizeUpload: suspend () -> Unit): AfirmaDeliveryResult {
            executions++; batchReceipt = snapshot; authorizeUpload(); return delivery
        }
        override fun close() { closes++; batchReceipt = null }
    }
}

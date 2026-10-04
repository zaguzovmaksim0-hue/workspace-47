package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.certificate.UnlockedIdentity
import dev.junta.firmamobile.signing.freshSyntheticIdentity
import dev.junta.firmamobile.signing.nonExportableSyntheticIdentity
import java.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AfirmaConsentControllerTest {
    @Test fun reviewDoesNotSignOrContactAServer() = runTest {
        val f = Fixture(this); assertTrue(f.offer())
        assertTrue(f.prompt!!.canConfirm); runCurrent()
        assertEquals(0, f.operation.executions); assertEquals(0, f.operation.uploads)
        f.controller.close(); assertEquals(1, f.operation.closes)
    }

    @Test fun unlockIsNotConsentAndOldDialogTokenCannotApprove() = runTest {
        val f = Fixture(this); f.identity = null; f.offer(); val old = f.prompt!!.token
        assertEquals(AfirmaConsentProblem.LOCKED, f.prompt!!.problem)
        assertFalse(f.controller.confirm(old))
        f.identity = f.synthetic.identity; f.controller.refreshIdentity()
        assertNotEquals(old, f.prompt!!.token); assertTrue(f.prompt!!.canConfirm)
        assertFalse(f.controller.confirm(old)); runCurrent(); assertEquals(0, f.operation.executions)
        assertTrue(f.controller.confirm(f.prompt!!.token)); runCurrent()
        assertEquals(1, f.operation.executions); assertEquals(1, f.operation.uploads)
        assertEquals(AfirmaConsentProblem.ACKNOWLEDGED, f.prompt!!.problem)
        assertEquals(0, f.synthetic.encodedReads.get())
    }

    @Test fun explicitConfirmationRunsOneOperationAndDoesNotOfferReplay() = runTest {
        val f = Fixture(this); f.offer(); val token = f.prompt!!.token
        assertTrue(f.controller.confirm(token)); assertFalse(f.controller.confirm(token)); runCurrent()
        assertFalse(f.controller.confirm(token)); assertEquals(1, f.operation.executions)
        assertEquals(1, f.operation.uploads); assertEquals(1, f.operation.closes)
        assertEquals(AfirmaConsentPhase.FINISHED, f.prompt!!.phase)
        f.controller.dismiss(token); assertNull(f.prompt); assertEquals(1, f.operation.closes)
    }

    @Test fun cancellationBeforeConfirmationClosesOwnedPayloadWithoutUploading() = runTest {
        val f = Fixture(this); f.offer(); f.controller.cancel(f.prompt!!.token); runCurrent()
        assertEquals(0, f.operation.executions); assertEquals(0, f.operation.uploads)
        assertEquals(1, f.operation.closes); assertEquals(AfirmaConsentProblem.CANCELLED, f.prompt!!.problem)
    }

    @Test fun navigationAndOwnerReplacementCannotApproveTheOldPayload() = runTest {
        for (changeOwner in listOf(true, false)) {
            val f = Fixture(this); f.offer(); val token = f.prompt!!.token
            if (changeOwner) f.currentOwner = Any() else f.epoch++
            assertFalse(f.controller.confirm(token)); runCurrent()
            assertEquals(0, f.operation.executions); assertEquals(1, f.operation.closes)
            assertEquals(AfirmaConsentProblem.EXPIRED, f.prompt!!.problem)
        }
    }

    @Test fun certificateChangedAtClickCannotBeSilentlySubstituted() = runTest {
        val f = Fixture(this); f.offer(); val token = f.prompt!!.token; f.identity = freshSyntheticIdentity()
        assertFalse(f.controller.confirm(token)); assertNotEquals(token, f.prompt!!.token)
        runCurrent(); assertEquals(0, f.operation.executions)
        assertTrue(f.controller.confirm(f.prompt!!.token)); runCurrent(); assertEquals(1, f.operation.uploads)
    }

    @Test fun aChangedIdentityBeforeUploadStopsTheResultTransfer() = runTest {
        val f = Fixture(this); f.operation.beforeGate = CompletableDeferred(); f.offer()
        f.controller.confirm(f.prompt!!.token); runCurrent(); assertEquals(1, f.operation.executions)
        f.identity = freshSyntheticIdentity(); f.operation.beforeGate!!.complete(Unit); runCurrent()
        assertEquals(0, f.operation.uploads); assertEquals(AfirmaConsentProblem.CANCELLED, f.prompt!!.problem)
    }

    @Test fun navigationBetweenCryptographyAndUploadDoesNotLeakResultToOldServer() = runTest {
        val f = Fixture(this); f.operation.beforeGate = CompletableDeferred(); f.offer()
        f.controller.confirm(f.prompt!!.token); runCurrent(); f.epoch++
        f.operation.beforeGate!!.complete(Unit); runCurrent()
        assertEquals(0, f.operation.uploads); assertEquals(1, f.operation.closes)
    }

    @Test fun expiredRequestIsNotExtendedByUnlockOrReturningFromPicker() = runTest {
        val f = Fixture(this); f.identity = null; f.offer(); f.controller.onBackground(true)
        f.now += Duration.ofMinutes(5).toNanos(); f.identity = f.synthetic.identity
        f.controller.onForeground(true); runCurrent()
        assertEquals(AfirmaConsentProblem.EXPIRED, f.prompt!!.problem)
        assertFalse(f.prompt!!.canConfirm); assertEquals(0, f.operation.executions)
    }

    @Test fun tickInvalidatesExpiredReviewAndClockRollback() = runTest {
        for (delta in listOf(-1L, Duration.ofMinutes(5).toNanos())) {
            val f = Fixture(this); f.offer(); f.now += delta; f.controller.tick()
            assertEquals(AfirmaConsentProblem.EXPIRED, f.prompt!!.problem); assertEquals(1, f.operation.closes)
        }
    }

    @Test fun expectedPickerReturnRetainsReviewButDoesNotAutomaticallyConfirm() = runTest {
        val f = Fixture(this); f.offer(); f.foreground = false; f.controller.onBackground(true)
        assertEquals(0, f.operation.closes)
        assertFalse(f.controller.confirm(f.prompt!!.token))
        f.foreground = true; f.controller.onForeground(true); runCurrent()
        assertTrue(f.prompt!!.canConfirm); assertEquals(0, f.operation.executions)
    }

    @Test fun unexpectedBackgroundCancelsEvenWithAnUnlockedCertificate() = runTest {
        val f = Fixture(this); f.offer(); f.controller.onBackground(false); runCurrent()
        assertEquals(AfirmaConsentProblem.CANCELLED, f.prompt!!.problem); assertEquals(0, f.operation.executions)
    }

    @Test fun backgroundDuringSendingIsReportedAsUncertainAndNeverRetried() = runTest {
        val f = Fixture(this); f.operation.afterUpload = CompletableDeferred(); f.offer()
        val token = f.prompt!!.token; f.controller.confirm(token); runCurrent()
        assertEquals(AfirmaConsentPhase.SENDING, f.prompt!!.phase); assertEquals(1, f.operation.uploads)
        f.controller.onBackground(true); runCurrent()
        assertEquals(AfirmaConsentProblem.UNCERTAIN, f.prompt!!.problem)
        assertFalse(f.controller.confirm(token)); assertEquals(1, f.operation.uploads); assertEquals(1, f.operation.closes)
    }

    @Test fun transportResultsArePreservedWithoutPromotingToGovernmentAcceptance() = runTest {
        val expected = mapOf(AfirmaDeliveryResult.ACKNOWLEDGED to AfirmaConsentProblem.ACKNOWLEDGED,
            AfirmaDeliveryResult.NOT_SENT to AfirmaConsentProblem.NOT_SENT,
            AfirmaDeliveryResult.REJECTED to AfirmaConsentProblem.REJECTED,
            AfirmaDeliveryResult.UNCERTAIN to AfirmaConsentProblem.UNCERTAIN)
        for ((response, result) in expected) {
            val f = Fixture(this); f.operation.delivery = response; f.offer()
            f.controller.confirm(f.prompt!!.token); runCurrent()
            assertEquals(result, f.prompt!!.problem); assertEquals(1, f.operation.executions)
        }
    }

    @Test fun failingSigningNeverReportsTransferSuccess() = runTest {
        val f = Fixture(this); f.operation.throwBeforeUpload = true; f.offer()
        f.controller.confirm(f.prompt!!.token); runCurrent()
        assertEquals(AfirmaConsentProblem.FAILED, f.prompt!!.problem)
        assertEquals(0, f.operation.uploads); assertEquals(1, f.operation.closes)
    }

    @Test fun secondRequestDoesNotReplacePendingConsentAndReleasesItsOwnBytes() = runTest {
        val f = Fixture(this); f.offer(); val token = f.prompt!!.token; val other = FakeOperation()
        assertFalse(f.controller.offer(f.owner, f.epoch, other)); assertEquals(1, other.closes)
        assertEquals(token, f.prompt!!.token); assertEquals(0, f.operation.closes)
        f.controller.close()
    }

    @Test fun destroyingControllerCancelsInFlightOperationAndClearsUi() = runTest {
        val f = Fixture(this); f.operation.beforeGate = CompletableDeferred(); f.offer()
        f.controller.confirm(f.prompt!!.token); runCurrent(); f.controller.close(); runCurrent()
        assertNull(f.prompt); assertEquals(0, f.operation.uploads); assertEquals(1, f.operation.closes)
        val another = FakeOperation(); assertFalse(f.controller.offer(f.owner, f.epoch, another)); assertEquals(1, another.closes)
    }

    @Test fun reentrantCancellationDuringWorkingNotificationCannotStartExecution() = runTest {
        val f = Fixture(this); f.offer()
        f.observer = { prompt -> if (prompt?.phase == AfirmaConsentPhase.WORKING) f.controller.cancel(prompt.token) }
        assertFalse(f.controller.confirm(f.prompt!!.token)); runCurrent()
        assertEquals(0, f.operation.executions); assertEquals(0, f.operation.uploads)
        assertEquals(AfirmaConsentProblem.CANCELLED, f.prompt!!.problem)
    }

    @Test fun reentrantCancellationDuringSendingNotificationCannotStartThePost() = runTest {
        val f = Fixture(this); f.offer()
        f.observer = { prompt -> if (prompt?.phase == AfirmaConsentPhase.SENDING) f.controller.cancel(prompt.token) }
        f.controller.confirm(f.prompt!!.token); runCurrent()
        assertEquals(1, f.operation.executions); assertEquals(0, f.operation.uploads)
        assertEquals(AfirmaConsentProblem.UNCERTAIN, f.prompt!!.problem)
    }

    @Test fun reentrantOwnerInvalidationDuringSendingCannotLeakTheResult() = runTest {
        val f = Fixture(this); f.offer()
        f.observer = { prompt -> if (prompt?.phase == AfirmaConsentPhase.SENDING) { f.currentOwner = Any(); f.controller.invalidate() } }
        f.controller.confirm(f.prompt!!.token); runCurrent()
        assertEquals(0, f.operation.uploads); assertNull(f.prompt)
    }

    @Test fun aLateAcknowledgementCannotOverwriteAnUncertainCancellation() = runTest {
        val f = Fixture(this); f.operation.afterUpload = CompletableDeferred(); f.operation.ignoreCancellation = true
        f.offer(); f.controller.confirm(f.prompt!!.token); runCurrent()
        assertEquals(1, f.operation.uploads)
        f.controller.cancel(f.prompt!!.token); runCurrent()
        assertEquals(AfirmaConsentProblem.UNCERTAIN, f.prompt!!.problem)
        assertEquals(1, f.operation.closes)
    }

    @Test fun ownerChangeDuringWorkingPublicationImmediatelyFinishesWithoutHoldingPayload() = runTest {
        val f = Fixture(this); f.offer()
        f.observer = { if (it?.phase == AfirmaConsentPhase.WORKING) f.currentOwner = Any() }
        assertFalse(f.controller.confirm(f.prompt!!.token)); runCurrent()
        assertEquals(AfirmaConsentPhase.FINISHED, f.prompt!!.phase)
        assertEquals(AfirmaConsentProblem.EXPIRED, f.prompt!!.problem)
        assertEquals(1, f.operation.closes); assertEquals(0, f.operation.executions)
    }

    @Test fun losingForegroundDuringWorkingPublicationCancelsWithoutStartingExecution() = runTest {
        val f = Fixture(this); f.offer()
        f.observer = { if (it?.phase == AfirmaConsentPhase.WORKING) f.foreground = false }
        assertFalse(f.controller.confirm(f.prompt!!.token)); runCurrent()
        assertEquals(AfirmaConsentProblem.CANCELLED, f.prompt!!.problem)
        assertEquals(1, f.operation.closes); assertEquals(0, f.operation.executions)
    }

    @Test fun parentCancellationBeforeTheCoroutineBodyStillClosesAndFinishesTheRequest() = runTest {
        val parent = kotlinx.coroutines.Job()
        val f = Fixture(CoroutineScope(coroutineContext + parent)); f.offer()
        assertTrue(f.controller.confirm(f.prompt!!.token))
        parent.cancel(); runCurrent()
        assertEquals(AfirmaConsentPhase.FINISHED, f.prompt!!.phase)
        assertEquals(AfirmaConsentProblem.CANCELLED, f.prompt!!.problem)
        assertEquals(1, f.operation.closes); assertEquals(0, f.operation.executions)
        f.controller.close(); assertEquals(1, f.operation.closes)
    }

    @Test fun uncertainSendingResultSurvivesBackgroundAndReturnUntilExplicitDismissal() = runTest {
        val f = Fixture(this); f.operation.afterUpload = CompletableDeferred(); f.offer()
        f.controller.confirm(f.prompt!!.token); runCurrent()
        f.controller.onBackground(false); runCurrent(); f.controller.onForeground(false)
        assertEquals(AfirmaConsentPhase.FINISHED, f.prompt!!.phase)
        assertEquals(AfirmaConsentProblem.UNCERTAIN, f.prompt!!.problem)
        f.controller.cancel(f.prompt!!.token)
        assertEquals(AfirmaConsentProblem.UNCERTAIN, f.prompt!!.problem)
        f.controller.dismiss(f.prompt!!.token); assertNull(f.prompt)
    }

    @Test fun acknowledgedReceiptSurvivesAnUnrelatedBackgroundReturn() = runTest {
        val f = Fixture(this); f.offer(); f.controller.confirm(f.prompt!!.token); runCurrent()
        f.controller.onBackground(false); f.controller.onForeground(false)
        assertEquals(AfirmaConsentProblem.ACKNOWLEDGED, f.prompt!!.problem)
        f.controller.close()
    }

    private class Fixture(scope: CoroutineScope) {
        val synthetic = nonExportableSyntheticIdentity()
        var identity: UnlockedIdentity? = synthetic.identity
        val owner = Any(); var currentOwner = owner; var epoch = 1L; var foreground = true; var now = 100L
        val operation = FakeOperation(); var prompt: AfirmaConsentPrompt? = null
        var observer: (AfirmaConsentPrompt?) -> Unit = {}
        val controller = AfirmaConsentController<Any>(scope,
            isCurrent = { candidate, version -> candidate === currentOwner && version == epoch },
            identityProvider = { identity }, canRespond = { foreground }, onPrompt = { prompt = it; observer(it) }, monotonicNanos = { now })
        fun offer() = controller.offer(owner, epoch, operation)
    }
    private class FakeOperation : PreparedAfirmaOperation {
        override val details = AfirmaConsentDetails("https://page.example", "https://store.example/service", "sign", "CAdES detached", "SHA256withRSA", 4, "1".repeat(64))
        var executions = 0; var uploads = 0; var closes = 0; var throwBeforeUpload = false
        var delivery = AfirmaDeliveryResult.ACKNOWLEDGED
        var ignoreCancellation = false
        var beforeGate: CompletableDeferred<Unit>? = null; var afterUpload: CompletableDeferred<Unit>? = null
        override fun certificateCompatible(identity: UnlockedIdentity) = true
        override suspend fun execute(identity: UnlockedIdentity, authorizeUpload: suspend () -> Unit): AfirmaDeliveryResult {
            executions++; beforeGate?.await(); check(!throwBeforeUpload)
            authorizeUpload(); uploads++
            try { afterUpload?.await() } catch (error: CancellationException) { if (!ignoreCancellation) throw error }
            return delivery
        }
        override fun close() { closes++ }
    }
}

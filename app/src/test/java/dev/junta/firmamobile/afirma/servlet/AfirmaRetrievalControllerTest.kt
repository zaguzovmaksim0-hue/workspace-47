package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.certificate.UnlockedIdentity
import java.net.URI
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AfirmaRetrievalControllerTest {
    @Test fun resolvedBytesAreHandedToConsentButNeverExecutedAutomatically() = runTest {
        val f = Fixture(this)
        assertTrue(f.offer()); assertTrue(f.prompt!!.loading); assertEquals(0, f.resolutions)
        runCurrent()
        assertEquals(1, f.resolutions); assertEquals(1, f.handoffs); assertEquals(0, f.op.executions)
        assertEquals(0, f.op.closes); assertNull(f.prompt); assertFalse(f.controller.hasPending)
        f.op.close()
    }

    @Test fun overlappingCallIsRejectedWithoutReplacingTheActiveLoader() = runTest {
        val f = Fixture(this); f.gate = CompletableDeferred(); f.offer(); val token = f.prompt!!.token
        assertFalse(f.offer("Other")); runCurrent()
        assertEquals(token, f.prompt!!.token); assertEquals(1, f.resolutions)
        f.controller.cancel(token); runCurrent(); assertEquals(0, f.handoffs)
    }

    @Test fun cancellationBeforeBodyNeverInvokesResolverAndClosesRequest() = runTest {
        val f = Fixture(this); val request = request("A")
        f.controller.offer(f.owner, f.epoch, request); f.controller.cancel(f.prompt!!.token); runCurrent()
        assertEquals(0, f.resolutions); assertEquals(0, f.handoffs)
        assertEquals(AfirmaRetrievalProblem.CANCELLED, f.prompt!!.problem)
        assertThrows(IllegalStateException::class.java) { request.begin() }
    }

    @Test fun cancelledParentBeforeCoroutineEntryStillLeavesNoActiveLoader() = runTest {
        val parent = Job(); val f = Fixture(CoroutineScope(coroutineContext + parent))
        f.offer(); parent.cancel(); runCurrent()
        assertFalse(f.prompt!!.loading); assertEquals(AfirmaRetrievalProblem.CANCELLED, f.prompt!!.problem)
        assertEquals(0, f.resolutions)
    }

    @Test fun cancelDuringFetchDoesNotCreateConsentOrRepeatTheRead() = runTest {
        val f = Fixture(this); f.gate = CompletableDeferred(); f.offer(); runCurrent()
        val token = f.prompt!!.token; f.controller.cancel(token); runCurrent()
        assertEquals(1, f.resolutions); assertEquals(0, f.handoffs)
        assertFalse(f.prompt!!.loading); assertEquals(AfirmaRetrievalProblem.CANCELLED, f.prompt!!.problem)
        f.controller.dismiss(token); assertTrue(f.offer()); runCurrent()
        assertEquals(1, f.resolutions); assertEquals(AfirmaRetrievalProblem.UNAVAILABLE, f.prompt!!.problem)
    }

    @Test fun repeatedSameFileAfterSuccessfulHandoffIsNotFetchedAgain() = runTest {
        val f = Fixture(this); f.offer(); runCurrent(); f.op.close()
        f.offer(); runCurrent()
        assertEquals(1, f.resolutions); assertEquals(1, f.handoffs)
        assertEquals(AfirmaRetrievalProblem.UNAVAILABLE, f.prompt!!.problem)
    }

    @Test fun changingOnlyTheResponseIdDoesNotBypassConsumedFileDeduplication() = runTest {
        val f = Fixture(this); f.offer(); runCurrent(); f.op.close()
        f.controller.offer(f.owner, f.epoch, request("File-123", "Different-response")); runCurrent()
        assertEquals(1, f.resolutions)
    }

    @Test fun distinctFileIdsRemainUsableInTheSameDocument() = runTest {
        val f = Fixture(this); f.offer("A"); runCurrent(); f.op.close()
        f.offer("B"); runCurrent()
        assertEquals(2, f.resolutions); assertEquals(2, f.handoffs)
    }

    @Test fun completedNewNavigationGetsItsOwnRequestBudget() = runTest {
        val f = Fixture(this); f.offer(); runCurrent(); f.op.close()
        f.epoch++; f.offer(); runCurrent()
        assertEquals(2, f.resolutions)
    }

    @Test fun canceledLateResolverResultIsClosedEvenIfResolverIgnoresCancellation() = runTest {
        val f = Fixture(this)
        f.beforeReturn = { f.controller.cancel(f.prompt!!.token) }
        f.offer(); runCurrent()
        assertEquals(1, f.op.closes); assertEquals(0, f.handoffs)
        assertEquals(AfirmaRetrievalProblem.CANCELLED, f.prompt!!.problem)
    }

    @Test fun navigationDuringFetchPreventsHandoffAndClearsItsLoader() = runTest {
        val f = Fixture(this); f.beforeReturn = { f.epoch++; f.controller.invalidate() }
        f.offer(); runCurrent()
        assertEquals(1, f.op.closes); assertEquals(0, f.handoffs); assertNull(f.prompt)
    }

    @Test fun changedOwnerWithoutAnExplicitInvalidateIsCaughtAtHandoff() = runTest {
        val f = Fixture(this); f.beforeReturn = { f.currentOwner = Any() }
        f.offer(); runCurrent()
        assertEquals(1, f.op.closes); assertEquals(0, f.handoffs)
        assertEquals(AfirmaRetrievalProblem.CANCELLED, f.prompt!!.problem)
    }

    @Test fun reentrantNavigationWhileTheLoaderClosesCannotApproveTheResult() = runTest {
        val f = Fixture(this); f.observer = { if (it == null) f.epoch++ }
        f.offer(); runCurrent()
        assertEquals(1, f.op.closes); assertEquals(0, f.handoffs)
    }

    @Test fun reentrantNewRequestDuringHandoffCannotReplaceOldOperation() = runTest {
        val f = Fixture(this); var newOffer: Boolean? = null
        f.observer = { if (it == null) newOffer = f.offer("B") }
        f.offer(); runCurrent()
        assertEquals(false, newOffer); assertEquals(1, f.resolutions); assertEquals(1, f.handoffs)
    }

    @Test fun receiverDecliningOwnershipCausesOneCloseAndNoSigning() = runTest {
        val f = Fixture(this); f.accept = false; f.offer(); runCurrent()
        assertEquals(1, f.op.closes); assertEquals(0, f.op.executions)
        assertEquals(AfirmaRetrievalProblem.CANCELLED, f.prompt!!.problem)
    }

    @Test fun errorDoesNotIncludeServerBodyOrAutomaticallyRetry() = runTest {
        val f = Fixture(this); f.problem = AfirmaRetrievalProblem.UNSUPPORTED; f.offer(); runCurrent()
        assertEquals(AfirmaRetrievalProblem.UNSUPPORTED, f.prompt!!.problem)
        assertFalse(f.prompt.toString().contains("secret-query"))
        assertEquals(0, f.handoffs); assertEquals(0, f.op.executions)
    }

    @Test fun backgroundDuringDownloadCancelsInsteadOfContinuingWithoutTheUser() = runTest {
        val f = Fixture(this); f.gate = CompletableDeferred(); f.offer(); runCurrent()
        f.foreground = false; f.controller.onBackground(); runCurrent()
        f.foreground = true; f.controller.onForeground(); runCurrent()
        assertEquals(AfirmaRetrievalProblem.CANCELLED, f.prompt!!.problem)
        assertEquals(1, f.resolutions); assertEquals(0, f.handoffs)
    }

    @Test fun staleTokenCannotCancelANewerRequest() = runTest {
        val f = Fixture(this); f.gate = CompletableDeferred(); f.offer("A"); val old = f.prompt!!.token
        f.controller.cancel(old); f.controller.dismiss(old)
        f.offer("B"); val current = f.prompt!!.token; f.controller.cancel(old)
        assertEquals(current, f.prompt!!.token); assertTrue(f.prompt!!.loading)
        f.controller.close(); runCurrent()
    }

    @Test fun timeoutAndMonotonicRollbackAbortWaitingResult() = runTest {
        for (expired in listOf(true, false)) {
            val f = Fixture(this); f.gate = CompletableDeferred(); f.offer(); runCurrent()
            f.now = if (expired) f.now + Duration.ofMinutes(5).toNanos() else f.now - 1
            f.controller.tick(); runCurrent()
            assertEquals(AfirmaRetrievalProblem.EXPIRED, f.prompt!!.problem)
            assertEquals(0, f.handoffs)
        }
    }

    @Test fun alreadyOffscreenStaleOrClosedControllerDoesNoNetworking() = runTest {
        val f = Fixture(this); f.foreground = false; assertFalse(f.offer())
        f.foreground = true; f.currentOwner = Any(); assertFalse(f.offer())
        f.currentOwner = f.owner; f.controller.close(); assertFalse(f.offer())
        runCurrent(); assertEquals(0, f.resolutions)
    }

    @Test fun scopeAndPayloadAreReleasedWhenScreenIsClosed() = runTest {
        val f = Fixture(this); f.gate = CompletableDeferred(); f.offer(); runCurrent(); f.controller.close(); runCurrent()
        assertNull(f.prompt); assertFalse(f.controller.hasPending); assertEquals(0, f.handoffs)
    }

    @Test fun equivalentHttpsAuthoritiesCannotRepeatTheConsumedFileRequest() = runTest {
        val f = Fixture(this); f.offer(); runCurrent(); f.op.close()
        val same = AfirmaDeferredInvocation(AfirmaServletOperation.SIGN, "https://page.example",
            URI("HTTPS://RETRIEVE.EXAMPLE:443/get?secret-query=not-in-ui"), "File-123", "Response",
            URI("https://store.example/put"), "12345678", null)
        f.controller.offer(f.owner, f.epoch, same); runCurrent()
        assertEquals(1, f.resolutions)
        assertEquals(AfirmaRetrievalProblem.UNAVAILABLE, f.prompt!!.problem)
    }

    private class Fixture(scope: CoroutineScope) {
        val owner = Any(); var currentOwner = owner; var epoch = 2L; var now = 100L; var foreground = true
        var prompt: AfirmaRetrievalPrompt? = null
        var observer: (AfirmaRetrievalPrompt?) -> Unit = {}
        var resolutions = 0; var handoffs = 0; val op = FakeOperation()
        var gate: CompletableDeferred<Unit>? = null
        var beforeReturn: () -> Unit = {}
        var accept = true; var problem: AfirmaRetrievalProblem? = null
        val controller = AfirmaRetrievalController<Any>(scope,
            resolver = AfirmaRequestResolver { resolutions++; gate?.await(); problem?.let { throw AfirmaRetrievalException(it) }; beforeReturn(); op },
            isCurrent = { o, e -> o === currentOwner && e == epoch }, canRespond = { foreground },
            onPrompt = { prompt = it; observer(it) }, onPrepared = { o, e, value ->
                assertSame(currentOwner, o); assertEquals(epoch, e); assertSame(op, value)
                if (accept) handoffs++ else value.close()
                accept
            }, monotonicNanos = { now },
        )
        fun offer(fileId: String = "File-123") = controller.offer(owner, epoch, request(fileId))
    }

    private class FakeOperation : PreparedAfirmaOperation {
        var closes = 0; var executions = 0
        override val details = AfirmaConsentDetails("https://page.example", "https://store.example/put", "sign", "CAdES", "SHA256withRSA", 2, "a".repeat(64))
        override fun certificateCompatible(identity: UnlockedIdentity) = true
        override suspend fun execute(identity: UnlockedIdentity, authorizeUpload: suspend () -> Unit): AfirmaDeliveryResult { executions++; return AfirmaDeliveryResult.ACKNOWLEDGED }
        override fun close() { closes++ }
    }
    private companion object {
        fun request(fileId: String, rid: String = "Response") = AfirmaDeferredInvocation(AfirmaServletOperation.SIGN,
            "https://page.example", URI("https://retrieve.example/get?secret-query=not-in-ui"), fileId, rid,
            URI("https://store.example/put"), "12345678", null)
    }
}

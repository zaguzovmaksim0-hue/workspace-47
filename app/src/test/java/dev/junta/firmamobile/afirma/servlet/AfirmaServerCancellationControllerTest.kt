package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.certificate.UnlockedIdentity
import dev.junta.firmamobile.signing.nonExportableSyntheticIdentity
import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** Integrator-owned ownership tests for the subagent cancellation outcome component. */
class AfirmaServerCancellationControllerTest {
    @Test fun lockedCancellationAndRefreshNeverReadTheCertificateAgain() = runTest {
        val f = Fixture(this); f.offer(); assertEquals(AfirmaConsentProblem.LOCKED, f.prompt!!.problem)
        val reads = f.reads; f.rejectIdentityRead = true; f.operation.before = CompletableDeferred()
        assertTrue(f.controller.requestCancellation(f.prompt!!.token)); runCurrent()
        f.controller.refreshIdentity(); assertEquals(reads, f.reads)
        f.operation.before!!.complete(Unit); runCurrent()
        assertEquals(AfirmaConsentProblem.CANCEL_ACKNOWLEDGED, f.prompt!!.problem)
        assertEquals(1, f.operation.notifications); assertEquals(1, f.operation.uploads); assertEquals(1, f.operation.closes)
        f.controller.close()
    }

    @Test fun wrongTokenOwnerEpochExpiryAndBackgroundNeverStartNotification() = runTest {
        for (invalid in listOf("token", "owner", "epoch", "expiry", "foreground")) {
            val f = Fixture(this); f.offer(); val token = f.prompt!!.token
            when (invalid) {
                "owner" -> f.activeOwner = Any()
                "epoch" -> f.epoch++
                "expiry" -> f.now += Duration.ofMinutes(5).toNanos()
                "foreground" -> f.foreground = false
            }
            assertFalse(invalid, f.controller.requestCancellation(if (invalid == "token") UUID.randomUUID() else token))
            runCurrent(); assertEquals(invalid, 0, f.operation.notifications); f.controller.close()
        }
    }

    @Test fun repeatedClicksCannotCreateAnotherCoroutineOrStartASignature() = runTest {
        val f = Fixture(this); f.operation.after = CompletableDeferred(); f.offer(); val token = f.prompt!!.token
        assertTrue(f.controller.requestCancellation(token)); assertFalse(f.controller.requestCancellation(token))
        assertFalse(f.controller.confirm(token)); runCurrent()
        assertEquals(1, f.operation.notifications); assertEquals(0, f.operation.signatures)
        f.operation.after!!.complete(Unit); runCurrent()
        assertFalse(f.controller.requestCancellation(token)); assertFalse(f.controller.confirm(token))
        f.controller.close(); assertEquals(1, f.operation.closes)
    }

    @Test fun timeoutBeforeAuthorizationIsNotSentButAfterItIsUncertain() = runTest {
        for (after in listOf(false, true)) {
            val f = Fixture(this); val gate = CompletableDeferred<Unit>()
            if (after) f.operation.after = gate else f.operation.before = gate
            f.offer(); f.controller.requestCancellation(f.prompt!!.token); runCurrent()
            advanceTimeBy(15_000L); runCurrent()
            assertEquals(AfirmaConsentPhase.FINISHED, f.prompt!!.phase)
            assertEquals(if (after) AfirmaConsentProblem.CANCEL_UNCERTAIN else AfirmaConsentProblem.CANCEL_NOT_SENT, f.prompt!!.problem)
            assertEquals(if (after) 1 else 0, f.operation.uploads)
            assertEquals(1, f.operation.closes); f.controller.close()
        }
    }

    @Test fun parentCancellationBeforeCoroutineBodyStillClosesOnce() = runTest {
        val parent = Job(); val f = Fixture(CoroutineScope(coroutineContext + parent)); f.offer()
        assertTrue(f.controller.requestCancellation(f.prompt!!.token)); parent.cancel(); runCurrent()
        assertEquals(AfirmaConsentProblem.CANCEL_NOT_SENT, f.prompt!!.problem)
        assertEquals(0, f.operation.notifications); assertEquals(1, f.operation.closes)
        f.controller.close(); assertEquals(1, f.operation.closes)
    }

    @Test fun reentrantPublicationCanCancelReplaceOwnershipOrDisposeWithoutLeaking() = runTest {
        for (action in listOf("cancel", "owner", "dismiss")) {
            val f = Fixture(this); f.offer(); val token = f.prompt!!.token
            f.observer = { prompt ->
                if (prompt?.phase == AfirmaConsentPhase.CANCELLING) when (action) {
                    "cancel" -> f.controller.cancel(token)
                    "owner" -> f.activeOwner = Any()
                    else -> f.controller.dismiss(token)
                }
            }
            assertFalse(f.controller.requestCancellation(token)); runCurrent()
            assertEquals(0, f.operation.notifications); assertEquals(1, f.operation.closes)
            if (action != "dismiss") assertEquals(AfirmaConsentProblem.CANCEL_NOT_SENT, f.prompt!!.problem)
            else assertNull(f.prompt)
            f.controller.close()
        }
    }

    @Test fun localLifecycleMethodsNeverNotifyForUnsignedReview() = runTest {
        for (event in listOf("cancel", "background", "dismiss", "invalidate", "close", "expire")) {
            val f = Fixture(this); f.offer(); val token = f.prompt!!.token
            when (event) {
                "cancel" -> f.controller.cancel(token)
                "background" -> f.controller.onBackground(false)
                "dismiss" -> f.controller.dismiss(token)
                "invalidate" -> f.controller.invalidate()
                "expire" -> { f.now += Duration.ofMinutes(5).toNanos(); f.controller.tick() }
                else -> f.controller.close()
            }
            runCurrent(); assertEquals(event, 0, f.operation.notifications); f.controller.close()
        }
    }

    @Test fun signatureAlreadyWorkingOrSendingCannotBeOverwrittenByCancelNotice() = runTest {
        for (sending in listOf(false, true)) {
            val f = Fixture(this); f.identity = nonExportableSyntheticIdentity().identity
            val gate = CompletableDeferred<Unit>()
            if (sending) f.operation.signAfter = gate else f.operation.signBefore = gate
            f.offer(); val token = f.prompt!!.token; assertTrue(f.controller.confirm(token)); runCurrent()
            assertEquals(if (sending) AfirmaConsentPhase.SENDING else AfirmaConsentPhase.WORKING, f.prompt!!.phase)
            assertFalse(f.controller.requestCancellation(token)); assertEquals(0, f.operation.notifications)
            f.controller.cancel(token); runCurrent(); assertEquals(0, f.operation.notifications)
            assertEquals(if (sending) AfirmaConsentProblem.UNCERTAIN else AfirmaConsentProblem.CANCELLED, f.prompt!!.problem)
            f.controller.close()
        }
    }

    @Test fun acknowledgedCancellationReceiptRemainsAcrossBackgroundReturn() = runTest {
        val f = Fixture(this); f.offer(); f.controller.requestCancellation(f.prompt!!.token); runCurrent()
        f.controller.onBackground(false); f.controller.onForeground(false)
        assertEquals(AfirmaConsentProblem.CANCEL_ACKNOWLEDGED, f.prompt!!.problem)
        f.controller.dismiss(f.prompt!!.token); assertNull(f.prompt)
    }

    @Test fun changingOwnershipJustBeforeUploadRefusesNotification() = runTest {
        val f = Fixture(this); f.operation.before = CompletableDeferred(); f.offer()
        f.controller.requestCancellation(f.prompt!!.token); runCurrent(); f.epoch++
        f.operation.before!!.complete(Unit); runCurrent()
        assertEquals(0, f.operation.uploads)
        assertEquals(AfirmaConsentProblem.CANCEL_NOT_SENT, f.prompt!!.problem); f.controller.close()
    }

    private class Fixture(scope: CoroutineScope) {
        val owner = Any(); var activeOwner = owner; var epoch = 3L; var now = 100L; var foreground = true
        var identity: UnlockedIdentity? = null; var reads = 0; var rejectIdentityRead = false
        var prompt: AfirmaConsentPrompt? = null; var observer: (AfirmaConsentPrompt?) -> Unit = {}
        val operation = Operation()
        val controller = AfirmaConsentController<Any>(scope,
            { view, generation -> view === activeOwner && generation == epoch },
            { check(!rejectIdentityRead); reads++; identity }, { foreground },
            { prompt = it; observer(it) }, { now })
        fun offer() { assertTrue(controller.offer(owner, epoch, operation)) }
    }
    private class Operation : PreparedAfirmaOperation {
        override val details = AfirmaConsentDetails("https://page.example", "https://storage.example/put", "sign", "CAdES", "SHA256withRSA", 3, "abc")
        var before: CompletableDeferred<Unit>? = null; var after: CompletableDeferred<Unit>? = null
        var signBefore: CompletableDeferred<Unit>? = null; var signAfter: CompletableDeferred<Unit>? = null
        var notifications = 0; var uploads = 0; var signatures = 0; var closes = 0
        override fun certificateCompatible(identity: UnlockedIdentity) = true
        override suspend fun execute(identity: UnlockedIdentity, authorizeUpload: suspend () -> Unit): AfirmaDeliveryResult {
            signatures++; signBefore?.await(); authorizeUpload(); signAfter?.await(); return AfirmaDeliveryResult.ACKNOWLEDGED
        }
        override suspend fun notifyCancellation(authorizeUpload: suspend () -> Unit): AfirmaDeliveryResult {
            notifications++; before?.await(); authorizeUpload(); uploads++; after?.await(); return AfirmaDeliveryResult.ACKNOWLEDGED
        }
        override fun close() { closes++ }
    }
}

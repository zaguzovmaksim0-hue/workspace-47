package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.SigningAlgorithm
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** Integrator-owned tests combine the independent workers' REAL controller and
 * operation implementations, using a recording storage boundary, not a key. */
class AfirmaCancellationFlowTest {
    @Test fun lockedUserCancellationSendsOnlyTheSentinelForTheResultSession() = runTest {
        val owner = Any()
        var prompt: AfirmaConsentPrompt? = null
        var identityReads = 0
        val sent = mutableListOf<Triple<URI, String, String>>()
        val invocation = invocation()
        val operation = NativeAfirmaOperation(invocation, AfirmaResultTransport { url, id, value ->
            sent += Triple(url, id, value)
            AfirmaDeliveryResult.ACKNOWLEDGED
        })
        val controller = AfirmaConsentController<Any>(this,
            isCurrent = { candidate, epoch -> candidate === owner && epoch == 9L },
            identityProvider = { identityReads++; null }, canRespond = { true }, onPrompt = { prompt = it })
        assertTrue(controller.offer(owner, 9, operation))
        assertEquals(AfirmaConsentProblem.LOCKED, prompt!!.problem)
        val readsBeforeCancel = identityReads
        val token = prompt!!.token
        assertTrue(controller.requestCancellation(token))
        runCurrent()
        assertEquals(readsBeforeCancel, identityReads)
        assertEquals(AfirmaConsentPhase.FINISHED, prompt!!.phase)
        assertEquals(AfirmaConsentProblem.CANCEL_ACKNOWLEDGED, prompt!!.problem)
        assertEquals(listOf(Triple(URI("https://storage.synthetic.example/put"), "RESULT-123", "CANCEL")), sent)
        assertFalse(controller.confirm(token))
        assertFalse(controller.requestCancellation(token))
        assertThrows(IllegalStateException::class.java) { invocation.payloadCopy() }
        controller.close()
    }

    @Test fun lostCancellationReceiptNeverTriggersASecondWriteOrASignature() = runTest {
        val owner = Any(); var prompt: AfirmaConsentPrompt? = null; var requests = 0
        val operation = NativeAfirmaOperation(invocation(), AfirmaResultTransport { _, _, value ->
            requests++; assertEquals("CANCEL", value); AfirmaDeliveryResult.UNCERTAIN
        })
        val controller = AfirmaConsentController<Any>(this, { candidate, _ -> candidate === owner },
            { null }, { true }, { prompt = it })
        controller.offer(owner, 1, operation); val token = prompt!!.token
        assertTrue(controller.requestCancellation(token)); runCurrent()
        assertEquals(AfirmaConsentProblem.CANCEL_UNCERTAIN, prompt!!.problem)
        controller.onBackground(false); controller.onForeground(false); runCurrent()
        assertEquals(AfirmaConsentProblem.CANCEL_UNCERTAIN, prompt!!.problem)
        assertFalse(controller.requestCancellation(token)); assertFalse(controller.confirm(token))
        assertEquals(1, requests); controller.dismiss(token); assertNull(prompt)
    }

    @Test fun automaticLifecycleCancellationNeverCallsNativeStorage() = runTest {
        for (action in listOf("background", "invalidate", "close", "cancel")) {
            val owner = Any(); var prompt: AfirmaConsentPrompt? = null; var writes = 0
            val op = NativeAfirmaOperation(invocation(), AfirmaResultTransport { _, _, _ -> writes++; AfirmaDeliveryResult.ACKNOWLEDGED })
            val controller = AfirmaConsentController<Any>(this, { candidate, _ -> candidate === owner },
                { null }, { true }, { prompt = it })
            controller.offer(owner, 1, op)
            when (action) {
                "background" -> controller.onBackground(false)
                "invalidate" -> controller.invalidate()
                "close" -> controller.close()
                else -> controller.cancel(prompt!!.token)
            }
            runCurrent(); assertEquals(action, 0, writes); controller.close()
        }
    }

    @Test fun userStopsAnInFlightCancelWithoutOverwritingItsUncertainOutcome() = runTest {
        val owner = Any(); var prompt: AfirmaConsentPrompt? = null; var writes = 0
        val hold = CompletableDeferred<Unit>()
        val op = NativeAfirmaOperation(invocation(), AfirmaResultTransport { _, _, value ->
            writes++; assertEquals("CANCEL", value); hold.await(); AfirmaDeliveryResult.ACKNOWLEDGED
        })
        val controller = AfirmaConsentController<Any>(this, { candidate, _ -> candidate === owner },
            { null }, { true }, { prompt = it })
        controller.offer(owner, 1, op); val token = prompt!!.token
        assertTrue(controller.requestCancellation(token)); runCurrent(); assertEquals(1, writes)
        controller.cancel(token); runCurrent()
        assertEquals(AfirmaConsentProblem.CANCEL_UNCERTAIN, prompt!!.problem)
        hold.complete(Unit); runCurrent()
        assertEquals(AfirmaConsentProblem.CANCEL_UNCERTAIN, prompt!!.problem)
        assertEquals(1, writes); controller.close()
    }

    private fun invocation() = AfirmaServletInvocation(
        AfirmaServletOperation.SIGN, "https://page.synthetic.example", URI("https://storage.synthetic.example/put"),
        "RESULT-123", "12345678", SigningAlgorithm.SHA256_WITH_RSA, true, "not for transport".toByteArray(),
    )
}

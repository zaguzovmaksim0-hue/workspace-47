package dev.junta.firmamobile.afirma.servlet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AfirmaCancellationOutcomeTest {
    @Test
    fun interruptBeforeAuthorizationIsNotSent() {
        val outcome = AfirmaCancellationOutcome()
        assertEquals(null, outcome.result)
        assertEquals(AfirmaConsentProblem.CANCEL_NOT_SENT, outcome.interrupt())
        assertEquals(AfirmaConsentProblem.CANCEL_NOT_SENT, outcome.result)
    }

    @Test
    fun interruptAfterAuthorizationRemainsUncertainAfterLateAcknowledgment() {
        val outcome = AfirmaCancellationOutcome()
        assertTrue(outcome.markAuthorized())
        assertEquals(null, outcome.result)
        assertEquals(AfirmaConsentProblem.CANCEL_UNCERTAIN, outcome.interrupt())
        assertEquals(
            AfirmaConsentProblem.CANCEL_UNCERTAIN,
            outcome.complete(AfirmaDeliveryResult.ACKNOWLEDGED)
        )
        assertEquals(AfirmaConsentProblem.CANCEL_UNCERTAIN, outcome.result)
    }

    @Test
    fun allDeliveriesMapAfterAuthorization() {
        val cases = listOf(
            AfirmaDeliveryResult.ACKNOWLEDGED to AfirmaConsentProblem.CANCEL_ACKNOWLEDGED,
            AfirmaDeliveryResult.REJECTED to AfirmaConsentProblem.CANCEL_REJECTED,
            AfirmaDeliveryResult.NOT_SENT to AfirmaConsentProblem.CANCEL_NOT_SENT,
            AfirmaDeliveryResult.UNCERTAIN to AfirmaConsentProblem.CANCEL_UNCERTAIN
        )
        for ((delivery, expected) in cases) {
            val outcome = AfirmaCancellationOutcome()
            assertTrue(outcome.markAuthorized())
            assertTrue(outcome.markAuthorized())
            assertEquals(expected, outcome.complete(delivery))
            assertEquals(expected, outcome.result)
        }
    }

    @Test
    fun transportClaimsBeforeAuthorizationAreNotSent() {
        val claims = listOf(
            AfirmaDeliveryResult.ACKNOWLEDGED,
            AfirmaDeliveryResult.REJECTED,
            AfirmaDeliveryResult.UNCERTAIN
        )
        for (delivery in claims) {
            val outcome = AfirmaCancellationOutcome()
            assertEquals(AfirmaConsentProblem.CANCEL_NOT_SENT, outcome.complete(delivery))
            assertEquals(AfirmaConsentProblem.CANCEL_NOT_SENT, outcome.result)
            assertFalse(outcome.markAuthorized())
        }
    }

    @Test
    fun finishedOutcomeRejectsAuthorizationAndFurtherCompletion() {
        val outcome = AfirmaCancellationOutcome()
        assertTrue(outcome.markAuthorized())
        assertEquals(
            AfirmaConsentProblem.CANCEL_REJECTED,
            outcome.complete(AfirmaDeliveryResult.REJECTED)
        )
        assertFalse(outcome.markAuthorized())
        assertEquals(
            AfirmaConsentProblem.CANCEL_REJECTED,
            outcome.complete(AfirmaDeliveryResult.ACKNOWLEDGED)
        )
        assertEquals(AfirmaConsentProblem.CANCEL_REJECTED, outcome.interrupt())
        assertEquals(AfirmaConsentProblem.CANCEL_REJECTED, outcome.result)
    }
}

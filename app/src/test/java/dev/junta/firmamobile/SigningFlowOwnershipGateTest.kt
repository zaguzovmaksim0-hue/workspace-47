package dev.junta.firmamobile

import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SigningFlowOwnershipGateTest {
    @Test
    fun wrongOwnerCannotReleaseAndExactReleaseAdmitsNextFlow() {
        val gate = SigningFlowOwnershipGate()

        assertTrue(gate.acquire(SigningFlowKind.ORDINARY, ORDINARY_REQUEST))
        assertFalse(gate.acquire(SigningFlowKind.BATCH, BATCH_REQUEST))

        assertFalse(gate.release(SigningFlowKind.BATCH, ORDINARY_REQUEST))
        assertFalse(gate.release(SigningFlowKind.ORDINARY, BATCH_REQUEST))
        assertFalse(gate.acquire(SigningFlowKind.BATCH, BATCH_REQUEST))

        assertTrue(gate.release(SigningFlowKind.ORDINARY, ORDINARY_REQUEST))
        assertTrue(gate.acquire(SigningFlowKind.BATCH, BATCH_REQUEST))
    }

    private companion object {
        val ORDINARY_REQUEST: UUID = UUID.fromString("123e4567-e89b-42d3-a456-426614174100")
        val BATCH_REQUEST: UUID = UUID.fromString("123e4567-e89b-42d3-a456-426614174101")
    }
}

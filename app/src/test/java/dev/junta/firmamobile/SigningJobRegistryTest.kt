package dev.junta.firmamobile

import java.util.UUID
import kotlinx.coroutines.Job
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SigningJobRegistryTest {
    @Test
    fun onlyOneActiveRequestCanOwnAJobAndExactCompletionClearsIt() {
        val registry = SigningJobRegistry()
        val first = Job()
        val duplicate = Job()

        assertTrue(registry.register(FIRST_REQUEST, first))
        assertFalse(registry.register(FIRST_REQUEST, duplicate))

        first.complete()

        assertTrue(registry.register(SECOND_REQUEST, duplicate))
    }

    @Test
    fun staleOrRejectedSpecificCancelCannotTakeAnotherRequestsJob() {
        val registry = SigningJobRegistry()
        val job = Job()
        registry.register(FIRST_REQUEST, job)

        assertNull(
            registry.takeForCancellation(
                requestId = SECOND_REQUEST,
                coordinatorAccepted = false,
            ),
        )
        assertNull(
            registry.takeForCancellation(
                requestId = FIRST_REQUEST,
                coordinatorAccepted = false,
            ),
        )
        assertSame(
            job,
            registry.takeForCancellation(
                requestId = FIRST_REQUEST,
                coordinatorAccepted = true,
            ),
        )
    }

    @Test
    fun globalCancellationAlwaysTakesTheActivityOwnedJob() {
        val registry = SigningJobRegistry()
        val job = Job()
        registry.register(FIRST_REQUEST, job)

        assertSame(
            job,
            registry.takeForCancellation(
                requestId = null,
                coordinatorAccepted = false,
            ),
        )
    }

    private companion object {
        val FIRST_REQUEST: UUID = UUID.fromString("123e4567-e89b-42d3-a456-426614174000")
        val SECOND_REQUEST: UUID = UUID.fromString("123e4567-e89b-42d3-a456-426614174001")
    }
}

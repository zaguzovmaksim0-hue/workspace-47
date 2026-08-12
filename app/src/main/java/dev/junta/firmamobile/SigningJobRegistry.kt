package dev.junta.firmamobile

import java.util.UUID
import kotlinx.coroutines.Job

internal class SigningJobRegistry {
    private var activeRequestId: UUID? = null
    private var activeJob: Job? = null

    @Synchronized
    fun register(requestId: UUID, job: Job): Boolean {
        if (activeJob?.isActive == true) return false
        activeRequestId = requestId
        activeJob = job
        job.invokeOnCompletion {
            clearIfOwnedBy(job)
        }
        return true
    }

    @Synchronized
    fun takeForCancellation(
        requestId: UUID?,
        coordinatorAccepted: Boolean,
    ): Job? {
        val job = activeJob ?: return null
        if (requestId != null &&
            (!coordinatorAccepted || activeRequestId != requestId)
        ) {
            return null
        }
        activeRequestId = null
        activeJob = null
        return job
    }

    @Synchronized
    private fun clearIfOwnedBy(job: Job) {
        if (activeJob === job) {
            activeRequestId = null
            activeJob = null
        }
    }
}

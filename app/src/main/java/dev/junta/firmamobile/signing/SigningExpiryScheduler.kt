package dev.junta.firmamobile.signing

import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun interface SigningExpiryHandle {
    fun cancel()
}

internal fun interface SigningExpiryScheduler {
    fun schedule(
        delay: Duration,
        action: () -> Unit,
    ): SigningExpiryHandle
}

internal class CoroutineSigningExpiryScheduler(
    private val scope: CoroutineScope,
) : SigningExpiryScheduler {
    override fun schedule(
        delay: Duration,
        action: () -> Unit,
    ): SigningExpiryHandle {
        require(!delay.isNegative && !delay.isZero)
        val job = scope.launch {
            delay(delay.toMillis())
            action()
        }
        return SigningExpiryHandle(job::cancel)
    }
}

package dev.junta.firmamobile.ui.offlineverification

import dev.junta.firmamobile.verification.OfflineCmsResult
import dev.junta.firmamobile.verification.OfflineCmsStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val MAX_OFFLINE_UI_SIGNERS = 8

internal object OfflineVerificationWorker {
    // Process-wide, with no input stored in it. Even repeated dialog dismissal/reopening
    // cannot accumulate blocking provider/crypto calls. A nonpreemptible old call may
    // delay the next one, but never gets permission to publish its stale result.
    private val operationGate = Mutex()

    suspend fun verify(
        cmsSource: OfflineDocumentSource,
        originalSource: OfflineDocumentSource?,
        verifyCms: (ByteArray, ByteArray?) -> OfflineCmsResult,
    ): OfflineVerificationCompletion {
        val context = currentCoroutineContext()
        return operationGate.withLock {
            var cms: ByteArray? = null
            var original: ByteArray? = null
            try {
                cms = BoundedOfflineDocument.read(cmsSource, OfflineInputKind.CMS) { context.ensureActive() }
                original = originalSource?.let { source ->
                    BoundedOfflineDocument.read(source, OfflineInputKind.ORIGINAL) { context.ensureActive() }
                }
                context.ensureActive()
                val result = verifyCms(cms, original)
                context.ensureActive()
                completion(result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: OfflineInputTooLargeException) {
                OfflineVerificationCompletion(OfflineVerificationOutcome.LIMIT_EXCEEDED)
            } catch (_: Exception) {
                // Provider/crypto exception messages can contain document or certificate data.
                OfflineVerificationCompletion(OfflineVerificationOutcome.READ_FAILED)
            } finally {
                cms?.fill(0)
                original?.fill(0)
            }
        }
    }

    private fun completion(result: OfflineCmsResult): OfflineVerificationCompletion {
        if (result.status == OfflineCmsStatus.VERIFIED && result.verifiedSigners !in 1..MAX_OFFLINE_UI_SIGNERS) {
            return OfflineVerificationCompletion(OfflineVerificationOutcome.INVALID)
        }
        val outcome = when (result.status) {
            OfflineCmsStatus.VERIFIED -> OfflineVerificationOutcome.VERIFIED
            OfflineCmsStatus.INVALID -> OfflineVerificationOutcome.INVALID
            OfflineCmsStatus.UNSUPPORTED -> OfflineVerificationOutcome.UNSUPPORTED
            OfflineCmsStatus.LIMIT_EXCEEDED -> OfflineVerificationOutcome.LIMIT_EXCEEDED
            OfflineCmsStatus.DETACHED_CONTENT_REQUIRED -> OfflineVerificationOutcome.DETACHED_CONTENT_REQUIRED
        }
        val count = if (outcome == OfflineVerificationOutcome.VERIFIED) result.verifiedSigners else 0
        return OfflineVerificationCompletion(outcome, count)
    }
}

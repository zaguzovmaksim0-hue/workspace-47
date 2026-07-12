package dev.junta.firmamobile.signing

import dev.junta.firmamobile.browser.NavigationId
import dev.junta.firmamobile.network.TrustedOrigin
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

enum class ConsumeError {
    NOT_FOUND,
    ALREADY_CONSUMED,
    REQUEST_EXPIRED,
    PROFILE_CHANGED,
    ORIGIN_CHANGED,
    NAVIGATION_CHANGED,
    PAYLOAD_CHANGED,
    CANCELLED,
}

data class PendingValidationContext(
    val requestId: UUID,
    val profileId: String,
    val profileVersion: Int,
    val origin: TrustedOrigin,
    val navigationId: NavigationId,
    internal val payload: ByteArray,
)

sealed interface PendingConsumeResult {
    data class Accepted(val request: NormalizedSignRequest) : PendingConsumeResult

    data class Rejected(val error: ConsumeError) : PendingConsumeResult
}

data class PendingSignSummary(
    val requestId: UUID,
    val context: SigningContext,
    val algorithm: SigningAlgorithm,
    val format: SigningFormat,
    val safeDescription: String,
    val expiresAt: Instant,
)

internal fun interface SensitiveSigningCopyObserver {
    fun onCleared(allZero: Boolean)
}

class PendingSignRequestStore internal constructor(
    private val clock: Clock = Clock.systemUTC(),
    private val lifetime: Duration = Duration.ofMinutes(2),
    private val observer: SensitiveSigningCopyObserver = SensitiveSigningCopyObserver {},
) {
    private var pending: StoredRequest? = null
    private val seenRequestIds = linkedSetOf<UUID>()
    private var lastTerminalRequestId: UUID? = null

    init {
        require(!lifetime.isNegative && !lifetime.isZero)
    }

    @Synchronized
    fun put(request: NormalizedSignRequest): PendingSignSummary {
        if (request.requestId in seenRequestIds) {
            request.close()
            clearPending(ConsumeError.ALREADY_CONSUMED)
            throw IllegalArgumentException("Duplicate signing request ID")
        }
        if (seenRequestIds.size >= MAX_TRACKED_REQUEST_IDS) {
            request.close()
            clearPending(ConsumeError.CANCELLED)
            throw IllegalArgumentException("Signing request replay window is full")
        }
        if (request.payloadSize > MAX_PAYLOAD_BYTES ||
            request.safeDescription.length > MAX_SAFE_DESCRIPTION_CHARS
        ) {
            request.close()
            throw IllegalArgumentException("Signing request exceeds a closed bound")
        }

        clearPending(ConsumeError.CANCELLED)
        val payload = try {
            request.payloadCopy()
        } finally {
            request.close()
        }
        val fingerprint = try {
            digest(payload)
        } catch (error: RuntimeException) {
            payload.clearAndReport()
            throw error
        }
        val summary = PendingSignSummary(
            requestId = request.requestId,
            context = request.context,
            algorithm = request.algorithm,
            format = request.format,
            safeDescription = request.safeDescription,
            expiresAt = clock.instant().plus(lifetime),
        )
        pending = StoredRequest(
            protocolId = request.protocolId,
            summary = summary,
            payload = payload,
            fingerprint = fingerprint,
        )
        seenRequestIds += request.requestId
        return summary
    }

    @Synchronized
    fun peek(): PendingSignSummary? {
        val current = pending ?: return null
        if (isExpired(current.summary.expiresAt)) {
            clearPending(ConsumeError.REQUEST_EXPIRED)
            return null
        }
        return current.summary
    }

    @Synchronized
    fun consume(expected: PendingValidationContext): PendingConsumeResult {
        val current = pending ?: return PendingConsumeResult.Rejected(
            if (expected.requestId == lastTerminalRequestId || expected.requestId in seenRequestIds) {
                ConsumeError.ALREADY_CONSUMED
            } else {
                ConsumeError.NOT_FOUND
            },
        )
        if (isExpired(current.summary.expiresAt)) {
            return rejectAndClear(ConsumeError.REQUEST_EXPIRED)
        }
        if (expected.requestId != current.summary.requestId) {
            return rejectAndClear(ConsumeError.NOT_FOUND)
        }
        if (expected.profileId != current.summary.context.profileId ||
            expected.profileVersion != current.summary.context.profileVersion
        ) {
            return rejectAndClear(ConsumeError.PROFILE_CHANGED)
        }
        if (expected.origin != current.summary.context.origin) {
            return rejectAndClear(ConsumeError.ORIGIN_CHANGED)
        }
        if (expected.navigationId != current.summary.context.navigationId) {
            return rejectAndClear(ConsumeError.NAVIGATION_CHANGED)
        }
        if (expected.payload.size > MAX_PAYLOAD_BYTES) {
            return rejectAndClear(ConsumeError.PAYLOAD_CHANGED)
        }

        val expectedFingerprint = digest(expected.payload)
        val payloadMatches = try {
            MessageDigest.isEqual(current.fingerprint, expectedFingerprint)
        } finally {
            expectedFingerprint.clearAndReport()
        }
        if (!payloadMatches) {
            return rejectAndClear(ConsumeError.PAYLOAD_CHANGED)
        }

        val acceptedPayload = current.payload.copyOf()
        val accepted = NormalizedSignRequest(
            requestId = current.summary.requestId,
            protocolId = current.protocolId,
            context = current.summary.context,
            algorithm = current.summary.algorithm,
            format = current.summary.format,
            safeDescription = current.summary.safeDescription,
            payload = acceptedPayload,
            payloadObserver = observer,
        )
        clearPending(ConsumeError.ALREADY_CONSUMED)
        return PendingConsumeResult.Accepted(accepted)
    }

    @Synchronized
    fun clear(reason: ConsumeError) {
        clearPending(reason)
    }

    private fun rejectAndClear(error: ConsumeError): PendingConsumeResult.Rejected {
        clearPending(error)
        return PendingConsumeResult.Rejected(error)
    }

    private fun clearPending(reason: ConsumeError) {
        val current = pending ?: return
        current.payload.clearAndReport()
        current.fingerprint.clearAndReport()
        lastTerminalRequestId = current.summary.requestId
        pending = null
    }

    private fun isExpired(expiresAt: Instant): Boolean = !clock.instant().isBefore(expiresAt)

    private fun digest(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance(SHA_256).digest(bytes)

    private fun ByteArray.clearAndReport() {
        fill(0)
        observer.onCleared(all { it == 0.toByte() })
    }

    private data class StoredRequest(
        val protocolId: SigningProtocolId,
        val summary: PendingSignSummary,
        val payload: ByteArray,
        val fingerprint: ByteArray,
    )

    companion object {
        const val MAX_PAYLOAD_BYTES = 524_288
        private const val MAX_SAFE_DESCRIPTION_CHARS = 256
        private const val MAX_TRACKED_REQUEST_IDS = 1_024
        private const val SHA_256 = "SHA-256"
    }
}

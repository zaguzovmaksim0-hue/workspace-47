package dev.junta.firmamobile.signing

import java.io.Closeable
import java.security.MessageDigest

/** Signing input semantics are not inferred from the signature format or a display label. */
enum class SigningPayloadKind { DOCUMENT_BYTES, CHALLENGE, DIGEST, ENVELOPE, UNKNOWN }

/** Identity of one request, retained only across the pending store's owned copy/consume. */
internal class DocumentConsentOwner

/** Display-only metadata of actual document bytes; never a challenge/envelope fingerprint. */
class ActualDocumentMetadata internal constructor(
    val byteCount: Int,
    digest: ByteArray,
    private val observer: SensitiveSigningCopyObserver,
) : Closeable {
    private var ownedDigest: ByteArray? = digest

    @Synchronized
    fun sha256ForDisplay(): String? = ownedDigest?.let { bytes ->
        buildString(bytes.size * 2) {
            for (byte in bytes) {
                val value = byte.toInt() and 0xff
                append(HEX[value ushr 4])
                append(HEX[value and 0x0f])
            }
        }
    }

    @Synchronized
    override fun close() {
        ownedDigest?.clearAndReport(observer)
        ownedDigest = null
    }

    override fun toString(): String = "ActualDocumentMetadata(redacted)"

    private companion object { const val HEX = "0123456789abcdef" }
}

/**
 * Adapter-owned, read-only consent evidence. No filenames are available in these contracts.
 * Hashes stay in clearable memory for this operation only, never in logs or history.
 */
class DocumentConsentEvidence private constructor(
    val payloadKind: SigningPayloadKind,
    binding: RequestBinding?,
    document: ActualDocumentMetadata?,
) : Closeable {
    private var ownedBinding = binding
    private var ownedDocument = document
    private var closed = false

    val actualDocument: ActualDocumentMetadata?
        @Synchronized get() = ownedDocument

    /** Pure local verification, including the exact envelope; never re-decode/refetch at consent. */
    @Synchronized
    internal fun matches(request: NormalizedSignRequest): Boolean =
        !closed && (ownedBinding?.matches(request) ?: true)

    /** Remove display hashes immediately when the consent UI is dismissed, before signing work. */
    @Synchronized
    internal fun dismissDisplay() {
        ownedDocument?.close()
        ownedDocument = null
    }

    @Synchronized
    override fun close() {
        closed = true
        dismissDisplay()
        ownedBinding?.close()
        ownedBinding = null
    }

    override fun toString(): String = "DocumentConsentEvidence(redacted)"

    companion object {
        fun unavailable(payloadKind: SigningPayloadKind = SigningPayloadKind.UNKNOWN): DocumentConsentEvidence =
            DocumentConsentEvidence(payloadKind, null, null)

        /** Called only by the exact adapter, inside withPayload, after decoding its envelope. */
        internal fun fromEnvelope(
            request: NormalizedSignRequest,
            envelope: ByteArray,
            document: ByteArray,
            observer: SensitiveSigningCopyObserver = SensitiveSigningCopyObserver {},
        ): DocumentConsentEvidence {
            require(envelope.size in 1..PendingSignRequestStore.MAX_PAYLOAD_BYTES)
            require(document.size in 1..envelope.size)
            val binding = RequestBinding(request, sha256(envelope), observer)
            return try {
                DocumentConsentEvidence(
                    SigningPayloadKind.ENVELOPE,
                    binding,
                    ActualDocumentMetadata(document.size, sha256(document), observer),
                )
            } catch (error: Exception) {
                binding.close()
                throw error
            }
        }
    }

    /** Full immutable request snapshot plus an unforgeable-per-request ownership identity. */
    private class RequestBinding(
        request: NormalizedSignRequest,
        fingerprint: ByteArray,
        private val observer: SensitiveSigningCopyObserver,
    ) : Closeable {
        private val owner = request.documentConsentOwner
        private val requestId = request.requestId
        private val protocolId = request.protocolId
        private val context = request.context
        private val algorithm = request.algorithm
        private val format = request.format
        private val description = request.safeDescription
        private val observedAtMonotonicNanos = request.observedAtMonotonicNanos
        private var ownedFingerprint: ByteArray? = fingerprint

        fun matches(request: NormalizedSignRequest): Boolean {
            val expected = ownedFingerprint ?: return false
            if (owner !== request.documentConsentOwner || requestId != request.requestId ||
                protocolId != request.protocolId || context != request.context ||
                algorithm != request.algorithm || format != request.format ||
                description != request.safeDescription ||
                observedAtMonotonicNanos != request.observedAtMonotonicNanos
            ) return false
            return runCatching {
                request.withPayload { payload ->
                    val current = sha256(payload)
                    try { MessageDigest.isEqual(expected, current) } finally { current.fill(0) }
                }
            }.getOrDefault(false)
        }

        override fun close() {
            ownedFingerprint?.clearAndReport(observer)
            ownedFingerprint = null
        }
    }
}

private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

private fun ByteArray.clearAndReport(observer: SensitiveSigningCopyObserver) {
    fill(0)
    observer.onCleared(all { it == 0.toByte() })
}

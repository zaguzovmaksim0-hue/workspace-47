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
                val value = byte.toInt() and BYTE_MASK
                append(HEX[value ushr NIBBLE_BITS])
                append(HEX[value and NIBBLE_MASK])
            }
        }
    }

    @Synchronized
    override fun close() {
        ownedDigest?.clearAndReport(observer)
        ownedDigest = null
    }

    override fun toString(): String = "ActualDocumentMetadata(redacted)"

    private companion object {
        const val HEX = "0123456789abcdef"
        const val BYTE_MASK = 0xff
        const val NIBBLE_BITS = 4
        const val NIBBLE_MASK = 0x0f
    }
}

/**
 * Adapter-owned, read-only consent evidence. No filenames are available in these contracts.
 * Hashes stay in clearable memory for this operation only, never in logs or history.
 */
class DocumentConsentEvidence private constructor(
    val payloadKind: SigningPayloadKind,
    binding: RequestBinding?,
    document: ActualDocumentMetadata?,
    preview: PdfPreviewSource? = null,
) : Closeable {
    private var ownedBinding = binding
    private var ownedDocument = document
    private var ownedPreview = preview
    private var closed = false

    val actualDocument: ActualDocumentMetadata?
        @Synchronized get() = ownedDocument

    val pdfPreview: PdfPreviewSource?
        @Synchronized get() = ownedPreview

    /** Pure local verification, including the exact envelope; never re-decode/refetch at consent. */
    @Synchronized
    internal fun matches(request: NormalizedSignRequest, lineage: DocumentConsentLineage? = null): Boolean =
        !closed && (ownedBinding?.matches(request, lineage) ?: true)

    /** Remove display hashes immediately when the consent UI is dismissed, before signing work. */
    @Synchronized
    internal fun dismissDisplay() {
        ownedPreview?.close()
        ownedPreview = null
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
            var retainedBinding = false
            return try {
                DocumentConsentEvidence(
                    SigningPayloadKind.ENVELOPE,
                    binding,
                    ActualDocumentMetadata(document.size, sha256(document), observer),
                    if (document.size <= AccedaPadesAdapter.MAX_PDF_BYTES && PdfPreviewSource.isPdf(document)) {
                        PdfPreviewSource.copyOf(document, AccedaPadesAdapter.MAX_PDF_BYTES)
                    } else null,
                ).also { retainedBinding = true }
            } finally {
                if (!retainedBinding) binding.close()
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

        fun matches(request: NormalizedSignRequest, lineage: DocumentConsentLineage?): Boolean {
            val expected = ownedFingerprint ?: return false
            val ownerMatches = if (lineage == null) {
                owner === request.documentConsentOwner
            } else {
                lineage.isFor(owner, request)
            }
            val requestMatches = ownerMatches && requestId == request.requestId &&
                protocolId == request.protocolId && context == request.context
            val operationMatches = algorithm == request.algorithm && format == request.format &&
                description == request.safeDescription && observedAtMonotonicNanos == request.observedAtMonotonicNanos
            return requestMatches && operationMatches && runCatching {
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

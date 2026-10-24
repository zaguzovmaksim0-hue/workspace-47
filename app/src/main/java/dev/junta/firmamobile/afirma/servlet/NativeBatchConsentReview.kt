package dev.junta.firmamobile.afirma.servlet

import java.security.MessageDigest
import java.util.Collections

/** Display-only snapshot of this prepared request. Never retains references or document bytes. */
internal class NativeBatchConsentReview private constructor(val documents: List<Document>) {
    class Document internal constructor(val identifier: String, val evidence: Evidence?)
    class Evidence internal constructor(val bytes: Int, val sha256: String)

    companion object {
        private const val MAX_DOCUMENTS = 32
        private const val MAX_IDENTIFIER_CODE_POINTS = 120
        private const val MAX_DOCUMENT_BYTES = 524_288

        fun from(batch: NativeBatchDescriptor, localBatch: Boolean): NativeBatchConsentReview {
            require(batch.items.size in 1..MAX_DOCUMENTS)
            val documents = batch.items.map { item ->
                Document(NativeBatchDisplayText.bounded(item.id, MAX_IDENTIFIER_CODE_POINTS),
                    evidence(item, localBatch))
            }
            return NativeBatchConsentReview(Collections.unmodifiableList(documents))
        }

        private fun evidence(item: NativeBatchItem, localBatch: Boolean): Evidence? {
            // A service reference, provided digest or existing signature is not original content.
            // Do not resolve references, hash descriptors/PRE, or guess from the byte count.
            if (!localBatch || item.operation != "sign" ||
                item.extraProperties.containsKey(NativePrecalculatedHash.PROPERTY)
            ) return null
            val bytes = runCatching {
                AfirmaServletInvocationParser.strictBase64(item.dataReference, MAX_DOCUMENT_BYTES)
            }.getOrNull()?.takeIf { it.isNotEmpty() }
            return bytes?.let { content ->
                try {
                    Evidence(content.size, MessageDigest.getInstance("SHA-256").digest(content)
                        .joinToString("") { "%02x".format(it) })
                } finally {
                    content.fill(0)
                }
            }
        }
    }
}

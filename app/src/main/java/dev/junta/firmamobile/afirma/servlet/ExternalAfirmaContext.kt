package dev.junta.firmamobile.afirma.servlet

import java.security.MessageDigest
import java.util.UUID

/** An unverified delivery, never a website origin or a profile authorization. */
internal class ExternalAfirmaContext private constructor(val requestSha256: String) {
    val deliveryId: UUID = UUID.randomUUID()

    override fun toString(): String = "ExternalAfirmaContext(deliveryId=$deliveryId)"

    companion object {
        private const val MAX_URI_CHARS = 1_048_576
        fun from(rawUri: String): ExternalAfirmaContext {
            require(rawUri.length <= MAX_URI_CHARS)
            val bytes = rawUri.toByteArray(Charsets.UTF_8)
            return try {
                ExternalAfirmaContext(MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString("") { "%02x".format(it) })
            } finally { bytes.fill(0) }
        }
    }
}

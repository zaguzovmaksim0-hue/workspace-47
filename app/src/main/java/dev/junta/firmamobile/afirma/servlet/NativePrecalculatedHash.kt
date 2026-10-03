package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.SigningAlgorithm
import java.util.Locale

/** Explicit protocol metadata for a site-provided document hash. This only
 * validates the representation; it cannot establish which absent document the
 * site hashed. Never infer this mode from the number of received bytes. */
internal enum class NativePrecalculatedHash(
    val digestName: String,
    val byteCount: Int,
    val signingAlgorithm: SigningAlgorithm,
) {
    SHA1("SHA-1", 20, SigningAlgorithm.SHA1_WITH_RSA),
    SHA256("SHA-256", 32, SigningAlgorithm.SHA256_WITH_RSA),
    SHA384("SHA-384", 48, SigningAlgorithm.SHA384_WITH_RSA),
    SHA512("SHA-512", 64, SigningAlgorithm.SHA512_WITH_RSA);

    fun accepts(bytes: ByteArray, algorithm: SigningAlgorithm?) = bytes.size == byteCount && algorithm == signingAlgorithm

    companion object {
        const val PROPERTY = "precalculatedHashAlgorithm"
        fun parse(value: String): NativePrecalculatedHash? = when (value.uppercase(Locale.ROOT)) {
            "SHA1", "SHA-1" -> SHA1
            "SHA256", "SHA-256" -> SHA256
            "SHA384", "SHA-384" -> SHA384
            "SHA512", "SHA-512" -> SHA512
            else -> null
        }
    }
}

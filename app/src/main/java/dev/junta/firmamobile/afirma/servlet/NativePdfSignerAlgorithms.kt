package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.SigningAlgorithm

internal object NativePdfSignerAlgorithms {
    fun fromOids(digest: String, signature: String): SigningAlgorithm? {
        val algorithm = when (digest) {
            "1.3.14.3.2.26" -> SigningAlgorithm.SHA1_WITH_RSA
            "2.16.840.1.101.3.4.2.1" -> SigningAlgorithm.SHA256_WITH_RSA
            "2.16.840.1.101.3.4.2.3" -> SigningAlgorithm.SHA512_WITH_RSA
            else -> return null
        }
        return when (signature) {
            "1.2.840.113549.1.1.1" -> algorithm
            "1.2.840.113549.1.1.5" -> algorithm.takeIf { it == SigningAlgorithm.SHA1_WITH_RSA }
            "1.2.840.113549.1.1.11" -> algorithm.takeIf { it == SigningAlgorithm.SHA256_WITH_RSA }
            "1.2.840.113549.1.1.13" -> algorithm.takeIf { it == SigningAlgorithm.SHA512_WITH_RSA }
            else -> null
        }
    }
}

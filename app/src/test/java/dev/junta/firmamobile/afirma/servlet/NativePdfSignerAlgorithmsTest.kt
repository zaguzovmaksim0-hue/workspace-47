package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.SigningAlgorithm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NativePdfSignerAlgorithmsTest {
    private val rsa = "1.2.840.113549.1.1.1"
    private val cases = listOf(
        Triple("1.3.14.3.2.26", "1.2.840.113549.1.1.5", SigningAlgorithm.SHA1_WITH_RSA),
        Triple("2.16.840.1.101.3.4.2.1", "1.2.840.113549.1.1.11", SigningAlgorithm.SHA256_WITH_RSA),
        Triple("2.16.840.1.101.3.4.2.2", "1.2.840.113549.1.1.12", SigningAlgorithm.SHA384_WITH_RSA),
        Triple("2.16.840.1.101.3.4.2.3", "1.2.840.113549.1.1.13", SigningAlgorithm.SHA512_WITH_RSA)
    )

    @Test
    fun allEightValidPairs() {
        for ((digest, signature, expected) in cases) {
            assertEquals("$digest / $rsa", expected, NativePdfSignerAlgorithms.fromOids(digest, rsa))
            assertEquals("$digest / $signature", expected, NativePdfSignerAlgorithms.fromOids(digest, signature))
        }
    }

    @Test
    fun allTwelveCrossDigestPairsReject() {
        for ((digest, _, _) in cases) {
            for ((otherDigest, signature, _) in cases) {
                if (digest != otherDigest) {
                    assertNull("$digest / $signature", NativePdfSignerAlgorithms.fromOids(digest, signature))
                }
            }
        }
    }

    @Test
    fun unsupportedAlgorithmsAndEmptyOidsReject() {
        val unsupportedSignatures = listOf(
            "1.2.840.113549.1.1.10",
            "1.2.840.10045.2.1",
            "1.2.840.10045.4.3.2",
            "1.2.840.113549.1.1.14",
            ""
        )
        for ((digest, _, _) in cases) {
            for (signature in unsupportedSignatures) {
                assertNull("$digest / $signature", NativePdfSignerAlgorithms.fromOids(digest, signature))
            }
        }
        val signatures = listOf(rsa) + cases.map { it.second } + unsupportedSignatures
        for (digest in listOf("2.16.840.1.101.3.4.2.4", "")) {
            for (signature in signatures) {
                assertNull("$digest / $signature", NativePdfSignerAlgorithms.fromOids(digest, signature))
            }
        }
    }

    @Test
    fun whitespaceAndPrefixLookalikesReject() {
        val lookalikes = { oid: String ->
            listOf(
                " $oid", "$oid ", "\t$oid", "$oid\n",
                "$oid.0", "0.$oid", "0$oid", oid.substringBeforeLast('.')
            )
        }
        for ((digest, digestSignature, _) in cases) {
            for (signature in listOf(rsa, digestSignature)) {
                for (badDigest in lookalikes(digest)) {
                    assertNull("$badDigest / $signature", NativePdfSignerAlgorithms.fromOids(badDigest, signature))
                }
                for (badSignature in lookalikes(signature)) {
                    assertNull("$digest / $badSignature", NativePdfSignerAlgorithms.fromOids(digest, badSignature))
                }
            }
        }
    }
}

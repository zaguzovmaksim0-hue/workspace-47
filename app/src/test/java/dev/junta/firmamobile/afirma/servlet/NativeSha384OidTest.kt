package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.SigningAlgorithm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NativeSha384OidTest {
    private data class AlgorithmOids(
        val enumName: String,
        val digestOid: String,
        val combinedSignatureOid: String
    )

    private val genericRsaOid = "1.2.840.113549.1.1.1"
    private val sha384DigestOid = "2.16.840.1.101.3.4.2.2"
    private val sha384SignatureOid = "1.2.840.113549.1.1.12"

    private val supportedAlgorithms = listOf(
        AlgorithmOids("SHA1_WITH_RSA", "1.3.14.3.2.26", "1.2.840.113549.1.1.5"),
        AlgorithmOids("SHA256_WITH_RSA", "2.16.840.1.101.3.4.2.1", "1.2.840.113549.1.1.11"),
        AlgorithmOids("SHA384_WITH_RSA", sha384DigestOid, sha384SignatureOid),
        AlgorithmOids("SHA512_WITH_RSA", "2.16.840.1.101.3.4.2.3", "1.2.840.113549.1.1.13")
    )

    @Test
    fun exactSha384PairsAreAccepted() {
        val expected = SigningAlgorithm.valueOf("SHA384_WITH_RSA")

        for (signatureOid in listOf(sha384SignatureOid, genericRsaOid)) {
            assertEquals(
                "SHA384 digest with signature OID $signatureOid",
                expected,
                NativePdfSignerAlgorithms.fromOids(sha384DigestOid, signatureOid)
            )
        }
    }

    @Test
    fun combinedSha384SignatureRejectsOtherSupportedDigests() {
        for (algorithm in supportedAlgorithms.filter { it.digestOid != sha384DigestOid }) {
            assertNull(
                "${algorithm.enumName} digest with combined SHA384 signature",
                NativePdfSignerAlgorithms.fromOids(
                    algorithm.digestOid,
                    sha384SignatureOid
                )
            )
        }
    }

    @Test
    fun sha384DigestRejectsIncompatibleOrBlankSignatures() {
        val incompatibleSignatures = listOf(
            "1.2.840.113549.1.1.5",  // SHA1 with RSA
            "1.2.840.113549.1.1.11", // SHA256 with RSA
            "1.2.840.113549.1.1.13", // SHA512 with RSA
            "1.2.840.113549.1.1.10", // RSASSA-PSS
            "1.2.840.10045.2.1",    // EC public key
            "1.2.840.10045.4.3.3",  // ECDSA with SHA384
            "",
            "   "
        )

        for (signatureOid in incompatibleSignatures) {
            assertNull(
                "SHA384 digest must reject signature OID '$signatureOid'",
                NativePdfSignerAlgorithms.fromOids(sha384DigestOid, signatureOid)
            )
        }
    }

    @Test
    fun supportedAlgorithmMatrixAcceptsGenericRsaAndMatchingCombinedPairsOnly() {
        for (digestCase in supportedAlgorithms) {
            val expected = SigningAlgorithm.valueOf(digestCase.enumName)
            assertEquals(
                "${digestCase.enumName} digest with generic RSA",
                expected,
                NativePdfSignerAlgorithms.fromOids(digestCase.digestOid, genericRsaOid)
            )

            for (signatureCase in supportedAlgorithms) {
                val description =
                    "${digestCase.enumName} digest / ${signatureCase.enumName} signature"
                val actual = NativePdfSignerAlgorithms.fromOids(
                    digestCase.digestOid,
                    signatureCase.combinedSignatureOid
                )

                if (digestCase.enumName == signatureCase.enumName) {
                    assertEquals(description, expected, actual)
                } else {
                    assertNull(description, actual)
                }
            }
        }
    }
}

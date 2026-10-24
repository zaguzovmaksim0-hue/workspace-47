package dev.junta.firmamobile.verification

import org.junit.Test

class OfflineCmsVerifierLimitsTest {
    @Test
    fun enforcesCmsAndContentByteCaps() {
        val signer = OfflineCmsFixtures.signer()
        val oversizedCms = ByteArray(OfflineCmsFixtures.MAX_CMS_BYTES + OVER_LIMIT_BYTES)
        val oversizedAttachedContent = OfflineCmsFixtures.signed(
            ByteArray(OfflineCmsFixtures.MAX_CONTENT_BYTES + OVER_LIMIT_BYTES),
            listOf(signer),
        )
        val detached = OfflineCmsFixtures.signed(
            CONTENT.toByteArray(Charsets.UTF_8),
            listOf(signer),
            OfflineCmsOptions(encapsulate = false),
        )

        OfflineCmsAssertions.assertRejected(OfflineCmsStatus.LIMIT_EXCEEDED, oversizedCms)
        OfflineCmsAssertions.assertRejected(
            OfflineCmsStatus.LIMIT_EXCEEDED,
            oversizedAttachedContent,
        )
        OfflineCmsAssertions.assertRejected(
            OfflineCmsStatus.LIMIT_EXCEEDED,
            detached,
            ByteArray(OfflineCmsFixtures.MAX_CONTENT_BYTES + OVER_LIMIT_BYTES),
        )
    }

    @Test
    fun enforcesMaximumSignerCount() {
        val signers = (0..OfflineCmsFixtures.MAX_SIGNERS).map { index ->
            OfflineCmsFixtures.signer(serial = SIGNER_SERIAL_BASE + index)
        }
        val cms = OfflineCmsFixtures.signed(CONTENT.toByteArray(Charsets.UTF_8), signers)

        OfflineCmsAssertions.assertRejected(OfflineCmsStatus.LIMIT_EXCEEDED, cms)
    }

    @Test
    fun enforcesMaximumCertificateCount() {
        val signer = OfflineCmsFixtures.signer()
        val certificates = (0 until OfflineCmsFixtures.MAX_CERTIFICATES)
            .map { index ->
                OfflineCmsFixtures.certificate(signer.keyPair, CERTIFICATE_SERIAL_BASE + index)
            } + signer.certificate
        val cms = OfflineCmsFixtures.signed(
            CONTENT.toByteArray(Charsets.UTF_8),
            listOf(signer),
            OfflineCmsOptions(certificates = certificates),
        )

        OfflineCmsAssertions.assertRejected(OfflineCmsStatus.LIMIT_EXCEEDED, cms)
    }

    private companion object {
        const val CONTENT = "limit test"
        const val OVER_LIMIT_BYTES = 1
        const val SIGNER_SERIAL_BASE = 200_000L
        const val CERTIFICATE_SERIAL_BASE = 100_000L
    }
}

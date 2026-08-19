package dev.junta.firmamobile.ui

import dev.junta.firmamobile.certificate.TestCertificateFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CertificateSelectionEligibilityTest {
    @Test
    fun `Valencia accepts only current RSA content-commitment certificate`() {
        assertTrue(
            certificateEligibleForSelection(
                VALENCIA_PROFILE,
                TestCertificateFactory.nonRepudiationCertificate(),
                TestCertificateFactory.now,
            ),
        )
        assertFalse(
            certificateEligibleForSelection(
                VALENCIA_PROFILE,
                TestCertificateFactory.digitalSignatureCertificate(),
                TestCertificateFactory.now,
            ),
        )
        assertFalse(
            certificateEligibleForSelection(
                VALENCIA_PROFILE,
                TestCertificateFactory.expiredNonRepudiationCertificate(),
                TestCertificateFactory.now,
            ),
        )
    }

    @Test
    fun `ISCIII certificate selection keeps its existing certificate semantics`() {
        assertTrue(
            certificateEligibleForSelection(
                "isciii-certificate-selection",
                TestCertificateFactory.digitalSignatureCertificate(),
                TestCertificateFactory.now,
            ),
        )
    }

    @Test
    fun `Xunta accepts current RSA certificate without Valencia content-commitment requirement`() {
        assertTrue(
            certificateEligibleForSelection(
                XUNTA_PROFILE,
                TestCertificateFactory.digitalSignatureCertificate(),
                TestCertificateFactory.now,
            ),
        )
        assertFalse(
            certificateEligibleForSelection(
                XUNTA_PROFILE,
                TestCertificateFactory.expiredNonRepudiationCertificate(),
                TestCertificateFactory.now,
            ),
        )
    }

    private companion object {
        const val VALENCIA_PROFILE = "diputacion-valencia-sede"
        const val XUNTA_PROFILE = "xunta-galicia-solicitude-xenerica"
    }
}

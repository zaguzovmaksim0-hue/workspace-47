package dev.junta.firmamobile.certificate

import org.junit.Assert.assertEquals
import org.junit.Test

class CertificateDisplayNamePolicyTest {
    @Test fun fallbackPresentationDoesNotChangeTheStoredToken() {
        val stored = CertificateDisplayNamePolicy.sanitize(null)
        assertEquals("Certificado seleccionado", stored)
        assertEquals(stored, CertificateRepository.DEFAULT_DISPLAY_NAME)
        assertEquals(CertificateDisplayName.Default, CertificateDisplayNamePolicy.presentation(stored))
    }
    @Test fun namedValuesAreNotTranslatedOrSanitizedDuringPresentation() {
        for (name in listOf("identity.p12", "  raw name  ", "Unnamed certificate")) {
            assertEquals(CertificateDisplayName.Named(name), CertificateDisplayNamePolicy.presentation(name))
        }
    }
    @Test fun filteringAndStorageLengthAreUnchanged() {
        assertEquals("abc", CertificateDisplayNamePolicy.sanitize("a\nb\u202ec"))
        assertEquals("x".repeat(256), CertificateDisplayNamePolicy.sanitize("x".repeat(300)))
        assertEquals("Certificado seleccionado", CertificateDisplayNamePolicy.sanitize("  "))
    }
}

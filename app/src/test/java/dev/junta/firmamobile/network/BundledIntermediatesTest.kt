package dev.junta.firmamobile.network

import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneOffset
import javax.naming.ldap.LdapName
import org.junit.Assert.assertEquals
import org.junit.Test

class BundledIntermediatesTest {
    @Test
    fun `bundled ACCOMP certificate matches pinned identity`() {
        val certificate = BundledIntermediates.certificates.single()
        assertEquals(BundledIntermediates.ACCOMP_SHA256, sha256(certificate.encoded))
        assertEquals("AC Componentes Informáticos", organizationalUnit(certificate.subjectX500Principal))
        assertEquals("AC RAIZ FNMT-RCM", organizationalUnit(certificate.issuerX500Principal))
        assertEquals(
            LocalDate.of(2028, 6, 24),
            certificate.notAfter.toInstant().atZone(ZoneOffset.UTC).toLocalDate(),
        )
    }

    private fun organizationalUnit(principal: javax.security.auth.x500.X500Principal): String =
        LdapName(principal.name).rdns.first { it.type == "OU" }.value.toString()

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = ":") { byte -> "%02X".format(byte) }
}

package dev.junta.firmamobile.afirma.servlet

import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.Base64
import java.util.Date
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeCertificateFilterTest {
    @Test fun authcertRejectsOnlyDniSigningCertificate() {
        val constraint = NativeCertificateConstraint.parse("authcert:")!!
        assertFalse(constraint.matches(dniSigningCertificate()))
        assertTrue(constraint.matches(ordinaryCertificate()))
        assertFalse(constraint.requiresExactCertificate)
    }

    @Test fun encodedCertificateFilterTypeIsCaseInsensitive() {
        val certificate = ordinaryCertificate()
        val encoded = Base64.getEncoder().encodeToString(certificate.encoded)
        val constraint = NativeCertificateConstraint.parse("ENCODEDCERT:$encoded")!!
        assertTrue(constraint.requiresExactCertificate)
        assertTrue(constraint.matches(certificate))
    }

    @Test fun predicateOnlyConstraintRejectsAfterClose() {
        val constraint = NativeCertificateConstraint.parse("authcert:true")!!
        assertTrue(constraint.matches(ordinaryCertificate()))
        constraint.close()
        assertFalse(constraint.matches(ordinaryCertificate()))
    }

    @Test fun encodedCertificateConstraintRejectsAndCannotCopyAfterClose() {
        val certificate = ordinaryCertificate()
        val encoded = Base64.getEncoder().encodeToString(certificate.encoded)
        val constraint = NativeCertificateConstraint.parse("encodedcert:$encoded")!!
        constraint.close()
        assertFalse(constraint.matches(certificate))
        assertThrows(IllegalStateException::class.java) { constraint.copy() }
    }

    @Test fun encodedCertificateConstraintKeepsExactRequirementAfterClose() {
        val certificate = ordinaryCertificate()
        val encoded = Base64.getEncoder().encodeToString(certificate.encoded)
        val constraint = NativeCertificateConstraint.parse("encodedcert:$encoded")!!
        assertTrue(constraint.requiresExactCertificate)
        constraint.close()
        assertTrue(constraint.requiresExactCertificate)
        assertFalse(constraint.matches(certificate))
    }

    @Test fun qualifiedSerialNumberNormalizesCaseSpacesHashAndLeadingZeroes() {
        val certificate = ordinaryCertificate(serial = BigInteger("123456789abcdef", 16))
        val constraint = NativeCertificateConstraint.parse(
            "qualified:  #123456789ABCDEF  ",
        )!!
        assertTrue(constraint.matches(certificate))
        assertFalse(constraint.matches(ordinaryCertificate(serial = BigInteger("123456789abcde0", 16))))
        assertFalse(constraint.requiresExactCertificate)
    }

    @Test fun qualifiedRejectsEmptySerialNumber() {
        assertNull(NativeCertificateConstraint.parse("qualified:"))
        assertNull(NativeCertificateConstraint.parse("qualified:  #  "))
    }

    @Test fun nonexpiredAcceptsCurrentCertificate() {
        val constraint = NativeCertificateConstraint.parse("nonexpired:")!!
        assertTrue(constraint.matches(ordinaryCertificate()))
        assertFalse(constraint.requiresExactCertificate)
    }

    @Test fun nonexpiredFalseKeepsExpiredCertificatesCompatibleAtConstraintLevel() {
        // Upstream maps any value other than true to showing expired
        // certificates. NativeAfirmaOperation still applies its own stricter
        // expiry check; this only documents the selection-constraint layer.
        val constraint = NativeCertificateConstraint.parse("nonexpired:false")!!
        assertTrue(constraint.matches(expiredCertificate()))
        assertFalse(constraint.requiresExactCertificate)
    }

    @Test fun nonexpiredRejectsExpiredCertificate() {
        val constraint = NativeCertificateConstraint.parse("nonexpired:true")!!
        assertFalse(constraint.matches(expiredCertificate()))
    }

    @Test fun malformedFilterAfterExactCertificateIsRejectedWithoutRetainingBytes() {
        val certificate = ordinaryCertificate()
        val encoded = Base64.getEncoder().encodeToString(certificate.encoded)
        assertNull(NativeCertificateConstraint.parse("encodedcert:$encoded;unknown:value"))
        assertNull(NativeCertificateConstraint.parse("encodedcert:$encoded;encodedcert:$encoded"))
    }

    private fun dniSigningCertificate(): X509Certificate = certificate(
        issuer = X500Name(
            "CN=AC DNIE Firma,OU=DNIE,O=DIRECCION GENERAL DE LA POLICIA,C=ES",
        ),
        keyUsage = KeyUsage.nonRepudiation,
    )

    private fun ordinaryCertificate(
        serial: BigInteger = BigInteger.ONE,
    ): X509Certificate = certificate(
        issuer = X500Name("CN=Test CA,O=Test,C=ES"),
        keyUsage = KeyUsage.digitalSignature,
        serial = serial,
    )

    private fun expiredCertificate(): X509Certificate = certificate(
        issuer = X500Name("CN=Test CA,O=Test,C=ES"),
        keyUsage = KeyUsage.digitalSignature,
        notBefore = Instant.now().minusSeconds(86_400 * 2),
        notAfter = Instant.now().minusSeconds(86_400),
    )

    private fun certificate(
        issuer: X500Name,
        keyUsage: Int,
        serial: BigInteger = BigInteger.ONE,
        notBefore: Instant = Instant.parse("2030-01-01T00:00:00Z").minusSeconds(86_400),
        notAfter: Instant = Instant.parse("2030-01-01T00:00:00Z").plusSeconds(86_400),
    ): X509Certificate {
        val keyPair: KeyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.generateKeyPair()
        val subject = X500Name("CN=Test Subject,O=Test,C=ES")
        val builder = JcaX509v3CertificateBuilder(
            issuer,
            serial,
            Date.from(notBefore),
            Date.from(notAfter),
            subject,
            keyPair.public,
        )
        builder.addExtension(Extension.keyUsage, true, KeyUsage(keyUsage))
        return JcaX509CertificateConverter().getCertificate(
            builder.build(JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)),
        ).also { certificate ->
            assertTrue(certificate.keyUsage!![if (keyUsage == KeyUsage.nonRepudiation) 1 else 0])
        }
    }
}

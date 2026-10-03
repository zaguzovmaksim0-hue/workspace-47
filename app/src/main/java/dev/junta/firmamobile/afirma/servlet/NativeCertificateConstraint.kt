package dev.junta.firmamobile.afirma.servlet

import java.io.ByteArrayInputStream
import java.io.Closeable
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/** Request-local public-certificate binding, not trust validation or permission
 * to use a key. The encoded certificate never becomes a diagnostic string. */
internal class NativeCertificateConstraint private constructor(bytes: ByteArray) : Closeable {
    private var encoded: ByteArray? = bytes.copyOf()

    @Synchronized fun copy(): NativeCertificateConstraint =
        NativeCertificateConstraint(checkNotNull(encoded) { "Certificate constraint is closed" })

    @Synchronized fun matches(certificate: X509Certificate): Boolean {
        val expected = encoded ?: return false
        return runCatching {
            val actual = certificate.encoded
            try { actual.size <= MAX_CERT_BYTES && MessageDigest.isEqual(expected, actual) }
            finally { actual.fill(0) }
        }.getOrDefault(false)
    }

    @Synchronized override fun close() { encoded?.fill(0); encoded = null }
    override fun toString(): String = "NativeCertificateConstraint(exactCertificate=true)"

    companion object {
        private const val MAX_CERT_BYTES = 16_384
        fun parse(filter: String): NativeCertificateConstraint? = runCatching {
            require(filter.startsWith("encodedcert:"))
            val bytes = AfirmaServletInvocationParser.strictBase64(filter.removePrefix("encodedcert:"), MAX_CERT_BYTES)
            try {
                require(bytes.isNotEmpty())
                val input = ByteArrayInputStream(bytes)
                val certificate = CertificateFactory.getInstance("X.509").generateCertificate(input) as X509Certificate
                val canonical = certificate.encoded
                try {
                    // CertificateFactory can otherwise ignore trailing material
                    // or accept a PEM wrapper. Bind exactly one DER certificate.
                    require(input.available() == 0 && bytes.contentEquals(canonical))
                } finally { canonical.fill(0) }
                NativeCertificateConstraint(bytes)
            } finally { bytes.fill(0) }
        }.getOrNull()
    }
}

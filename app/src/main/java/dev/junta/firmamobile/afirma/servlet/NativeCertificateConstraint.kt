package dev.junta.firmamobile.afirma.servlet

import java.io.ByteArrayInputStream
import java.io.Closeable
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal
import java.util.Date
import java.util.Locale

/** Request-local certificate-selection constraints, not trust validation or
 * permission to use a key. Certificate bytes and qualified serial numbers never
 * become diagnostic strings. */
internal class NativeCertificateConstraint private constructor(
    private val predicates: List<Predicate>,
    private val exactRequired: Boolean,
    private var exact: ByteArray?,
) : Closeable {
    private var closed = false

    val requiresExactCertificate: Boolean
        get() = exactRequired

    @Synchronized fun copy(): NativeCertificateConstraint {
        check(!closed) { "Certificate constraint is closed" }
        return NativeCertificateConstraint(predicates, exactRequired, exact?.copyOf())
    }

    @Synchronized fun matches(certificate: X509Certificate): Boolean {
        if (closed) return false
        val expected = exact
        return predicates.all { predicate -> runCatching { predicate.matches(certificate) }.getOrDefault(false) } &&
            (expected == null || runCatching {
                val actual = certificate.encoded
                try { actual.size <= MAX_CERT_BYTES && MessageDigest.isEqual(expected, actual) }
                finally { actual.fill(0) }
            }.getOrDefault(false))
    }

    @Synchronized override fun close() {
        closed = true
        exact?.fill(0)
        exact = null
    }

    override fun toString(): String =
        "NativeCertificateConstraint(exactCertificate=$requiresExactCertificate, filters=${predicates.size})"

    private sealed interface Predicate {
        fun matches(certificate: X509Certificate): Boolean
    }

    /** Upstream maps a bare or true nonexpired value to hiding expired
     * certificates. NativeAfirmaOperation.certificateCompatible also rejects
     * expired certificates; retain that stricter behavior rather than weaken it. */
    private object NonExpired : Predicate {
        override fun matches(certificate: X509Certificate): Boolean =
            certificate.notAfter.after(Date())
    }

    /** Upstream AuthCertificateFilter accepts every certificate except a DNIe
     * signing certificate. */
    private object AuthCertificate : Predicate {
        override fun matches(certificate: X509Certificate): Boolean =
            !certificate.isDniSigningCertificate()
    }

    /** Upstream can substitute a paired signing certificate. This local shortcut
     * deliberately does not: another certificate is incompatible (fail closed). */
    private class QualifiedSerialNumber(private val expected: String) : Predicate {
        override fun matches(certificate: X509Certificate): Boolean =
            normalizeSerialNumber(certificate.serialNumber.toString(SERIAL_NUMBER_RADIX)) == expected
    }

    companion object {
        private const val MAX_CERT_BYTES = 16_384
        private const val SERIAL_NUMBER_RADIX = 16
        private const val SIGN_CERT_USAGE_INDEX = 1

        fun parse(filter: String): NativeCertificateConstraint? {
            var exact: ByteArray? = null
            return runCatching {
                require(filter.isNotEmpty())
                val predicates = mutableListOf<Predicate>()
                var sawElement = false
                for (element in filter.split(';')) {
                    if (element.isEmpty()) continue
                    sawElement = true
                    val separator = requireNotNull(element.indexOf(':').takeIf { it >= 0 }) {
                        "Malformed certificate filter element"
                    }
                    val type = element.substring(0, separator).lowercase(Locale.ROOT)
                    val value = element.substring(separator + 1)
                    when (type) {
                        "encodedcert" -> {
                            require(exact == null)
                            exact = parseExactCertificate(value)
                        }
                        "nonexpired" -> if (element.equals("nonexpired:", true) || value.equals("true", true)) {
                            predicates += NonExpired
                        }
                        "authcert" -> predicates += AuthCertificate
                        "qualified" -> {
                            val serialNumber = normalizeSerialNumber(value)
                            require(serialNumber.isNotEmpty())
                            predicates += QualifiedSerialNumber(serialNumber)
                        }
                        else -> require(type in SUPPORTED_TYPES) {
                            "Unsupported certificate filter type"
                        }
                    }
                }
                require(sawElement) { "Empty certificate filter" }
                NativeCertificateConstraint(predicates, exact != null, exact)
            }.getOrElse {
                exact?.fill(0)
                exact = null
                null
            }
        }

        private val SUPPORTED_TYPES = setOf("encodedcert", "nonexpired", "authcert", "qualified")

        private fun parseExactCertificate(filter: String): ByteArray {
            val bytes = AfirmaServletInvocationParser.strictBase64(
                filter.removePrefix("encodedcert:"), MAX_CERT_BYTES,
            )
            return runCatching {
                require(bytes.isNotEmpty())
                val input = ByteArrayInputStream(bytes)
                val certificate = CertificateFactory.getInstance("X.509")
                    .generateCertificate(input) as X509Certificate
                val canonical = certificate.encoded
                try {
                    // CertificateFactory can otherwise ignore trailing material
                    // or accept a PEM wrapper. Bind exactly one DER certificate.
                    require(input.available() == 0 && bytes.contentEquals(canonical))
                } finally {
                    canonical.fill(0)
                }
                bytes
            }.getOrElse { error ->
                bytes.fill(0)
                throw error
            }
        }

        private fun normalizeSerialNumber(value: String): String =
            value.trim().replace(" ", "").replace("#", "")
                .trimStart('0').lowercase(Locale.ROOT)

        private fun X509Certificate.isDniSigningCertificate(): Boolean {
            val usages = keyUsage
            val hasSigningUsage = usages != null && usages.size > SIGN_CERT_USAGE_INDEX &&
                usages[SIGN_CERT_USAGE_INDEX]
            // Upstream KeyUsageFilter.SIGN_CERT_USAGE asserts nonRepudiation
            // (index 1); SignatureDNIeFilter additionally requires the issuer
            // pattern &(cn=AC DNIE *)(ou=DNIE)(o=DIRECCION GENERAL DE LA
            // POLICIA)(c=ES).
            val issuer = issuerAttributes()
            return hasSigningUsage &&
                issuer["cn"].orEmpty().any { it.startsWith("AC DNIE ", ignoreCase = true) } &&
                issuer["ou"].orEmpty().any { it.equals("DNIE", ignoreCase = true) } &&
                issuer["o"].orEmpty().any {
                    it.equals("DIRECCION GENERAL DE LA POLICIA", ignoreCase = true)
                } &&
                issuer["c"].orEmpty().any { it.equals("ES", ignoreCase = true) }
        }

        private fun X509Certificate.issuerAttributes(): Map<String, List<String>> {
            val distinguishedName = issuerX500Principal.getName(X500Principal.RFC2253)
            val attributes = linkedMapOf<String, MutableList<String>>()
            val parts = mutableListOf<String>()
            val current = StringBuilder()
            var escaped = false
            for (character in distinguishedName) {
                when {
                    escaped -> {
                        current.append(character); escaped = false
                    }
                    character == '\\' -> {
                        current.append(character); escaped = true
                    }
                    character == ',' -> {
                        parts += current.toString(); current.clear()
                    }
                    else -> current.append(character)
                }
            }
            parts += current.toString()
            for (part in parts) {
                val separator = part.indexOf('=')
                if (separator <= 0) continue
                val key = part.substring(0, separator).trim().lowercase(Locale.ROOT)
                val value = part.substring(separator + 1).trim()
                    .replace("\\,", ",").replace("\\=", "=")
                attributes.getOrPut(key) { mutableListOf() } += value
            }
            return attributes
        }
    }
}

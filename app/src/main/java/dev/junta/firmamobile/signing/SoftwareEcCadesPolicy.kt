package dev.junta.firmamobile.signing

import java.security.MessageDigest
import java.security.cert.X509Certificate
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter

internal class SoftwareEcCadesPolicy(algorithm: SoftwareEcSignatureAlgorithm) {
    val digestName: String = when (algorithm) {
        SoftwareEcSignatureAlgorithm.P256_SHA256 -> "SHA-256"
        SoftwareEcSignatureAlgorithm.P384_SHA384 -> "SHA-384"
    }
    val digestAlgorithm = AlgorithmIdentifier(when (algorithm) {
        SoftwareEcSignatureAlgorithm.P256_SHA256 -> NISTObjectIdentifiers.id_sha256
        SoftwareEcSignatureAlgorithm.P384_SHA384 -> NISTObjectIdentifiers.id_sha384
    })
    val signatureAlgorithm = AlgorithmIdentifier(when (algorithm) {
        SoftwareEcSignatureAlgorithm.P256_SHA256 -> X9ObjectIdentifiers.ecdsa_with_SHA256
        SoftwareEcSignatureAlgorithm.P384_SHA384 -> X9ObjectIdentifiers.ecdsa_with_SHA384
    })

    fun digest(bytes: ByteArray, buffers: SoftwareEcCadesBuffers): ByteArray =
        buffers.own(MessageDigest.getInstance(digestName, BundledBouncyCastle.provider).digest(bytes))

    fun certificate(
        source: X509Certificate,
        algorithm: SoftwareEcSignatureAlgorithm,
        limits: SoftwareEcCadesLimits,
        buffers: SoftwareEcCadesBuffers,
    ): Certificate {
        val expected = SoftwareEcCurvePolicy.parameters(algorithm)
        val original = SoftwareEcCurvePolicy.publicSpec(source, expected)
            ?: throw SoftwareEcCadesFailure(SoftwareEcCadesError.UNSUPPORTED_KEY)
        val encoded = ownedEncoding(source, limits.maxCertificateBytes, buffers)
        SoftwareEcCadesDerGate.requireBoundedTree(encoded)
        val holder = X509CertificateHolder(encoded)
        val canonical = buffers.encode(holder.toASN1Structure(), limits.maxCertificateBytes,
            SoftwareEcCadesError.CERTIFICATE_TOO_LARGE)
        require(encoded.contentEquals(canonical))
        val snapshot = JcaX509CertificateConverter().setProvider(BundledBouncyCastle.provider).getCertificate(holder)
        val parsed = SoftwareEcCurvePolicy.publicSpec(snapshot, expected)
        if (parsed == null || parsed.w != original.w) {
            throw SoftwareEcCadesFailure(SoftwareEcCadesError.UNSUPPORTED_KEY)
        }
        return Certificate(holder, snapshot, encoded)
    }

    private fun ownedEncoding(source: X509Certificate, maxBytes: Int, buffers: SoftwareEcCadesBuffers): ByteArray {
        val sourceEncoding = source.encoded
        if (sourceEncoding.size > maxBytes) {
            throw SoftwareEcCadesFailure(SoftwareEcCadesError.CERTIFICATE_TOO_LARGE)
        }
        return buffers.own(sourceEncoding.copyOf())
    }

    class Certificate(val holder: X509CertificateHolder, val snapshot: X509Certificate, val encoded: ByteArray)
}

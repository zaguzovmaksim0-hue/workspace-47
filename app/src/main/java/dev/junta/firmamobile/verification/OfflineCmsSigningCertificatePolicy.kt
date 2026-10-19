package dev.junta.firmamobile.verification

import dev.junta.firmamobile.signing.BundledBouncyCastle
import java.security.MessageDigest
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1Encoding
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.ASN1TaggedObject
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.cert.X509CertificateHolder

private const val OFFLINE_CMS_MAX_ESS_CERT_ID_FIELDS = 3

internal object OfflineCmsSigningCertificatePolicy {
    fun validate(
        value: ASN1Encodable,
        certificate: X509CertificateHolder,
        declaredDigestOids: Set<String>,
    ) {
        val binding = sequence(value)
        if (binding.size() > 1) {
            throw OfflineCmsUnsupportedProfileException()
        }
        require(binding.size() == 1) { "Invalid ESS signing certificate" }
        val certIds = sequence(binding.getObjectAt(0))
        if (certIds.size() > 1) {
            throw OfflineCmsUnsupportedProfileException()
        }
        require(certIds.size() == 1) { "Invalid ESS certificate identifiers" }
        validateCertId(sequence(certIds.getObjectAt(0)), certificate, declaredDigestOids)
    }

    private fun validateCertId(
        fields: ASN1Sequence,
        certificate: X509CertificateHolder,
        declaredDigestOids: Set<String>,
    ) {
        require(fields.size() in 1..OFFLINE_CMS_MAX_ESS_CERT_ID_FIELDS) { "Invalid ESS certificate identifier" }
        val hasAlgorithm = fields.getObjectAt(0).toASN1Primitive() is ASN1Sequence
        val hashIndex = if (hasAlgorithm) 1 else 0
        require(fields.size() in (hashIndex + 1)..(hashIndex + 2)) { "Invalid ESS certificate fields" }
        val digestOid = if (hasAlgorithm) {
            digestOid(sequence(fields.getObjectAt(0)), declaredDigestOids)
        } else {
            NISTObjectIdentifiers.id_sha256.getId()
        }
        val hash = requireNotNull(fields.getObjectAt(hashIndex).toASN1Primitive() as? ASN1OctetString) {
            "Invalid ESS certificate hash"
        }
        validateHash(hash.getOctets(), certificate, digestOid)
        if (fields.size() == hashIndex + 2) {
            validateIssuerSerial(sequence(fields.getObjectAt(hashIndex + 1)), certificate)
        }
    }

    private fun digestOid(fields: ASN1Sequence, declaredDigestOids: Set<String>): String {
        require(fields.size() in 1..2) { "Invalid ESS digest identifier" }
        require(fields.getObjectAt(0).toASN1Primitive() is ASN1ObjectIdentifier) { "Invalid ESS digest OID" }
        val identifier = AlgorithmIdentifier.getInstance(fields)
        OfflineCmsAlgorithmPolicy.validateDigestIdentifier(identifier)
        val oid = identifier.getAlgorithm().getId()
        if (oid != NISTObjectIdentifiers.id_sha256.getId() && !declaredDigestOids.contains(oid)) {
            throw OfflineCmsUnsupportedProfileException()
        }
        return oid
    }

    private fun validateHash(hash: ByteArray, certificate: X509CertificateHolder, digestOid: String) {
        val name = requireNotNull(OfflineCmsAlgorithmPolicy.digestName(digestOid)) { "Invalid ESS digest" }
        val encoded = certificate.toASN1Structure().getEncoded(ASN1Encoding.DER)
        try {
            val expected = MessageDigest.getInstance(name, BundledBouncyCastle.provider).digest(encoded)
            try {
                require(MessageDigest.isEqual(expected, hash)) { "ESS certificate hash mismatch" }
            } finally {
                expected.fill(0)
            }
        } finally {
            encoded.fill(0)
        }
    }

    private fun validateIssuerSerial(fields: ASN1Sequence, certificate: X509CertificateHolder) {
        if (fields.size() > 2) {
            throw OfflineCmsUnsupportedProfileException()
        }
        require(fields.size() == 2) { "Invalid ESS issuer and serial" }
        validateIssuer(sequence(fields.getObjectAt(0)), certificate)
        val serial = requireNotNull(fields.getObjectAt(1).toASN1Primitive() as? ASN1Integer) {
            "Invalid ESS certificate serial"
        }
        require(serial.getValue().signum() > 0 && serial.getValue() == certificate.getSerialNumber()) {
            "ESS certificate serial mismatch"
        }
    }

    private fun validateIssuer(names: ASN1Sequence, certificate: X509CertificateHolder) {
        if (names.size() > 1) {
            throw OfflineCmsUnsupportedProfileException()
        }
        require(names.size() == 1) { "Invalid ESS issuer names" }
        val name = requireNotNull(names.getObjectAt(0).toASN1Primitive() as? ASN1TaggedObject) {
            "Invalid ESS issuer name"
        }
        if (!name.hasContextTag(GeneralName.directoryName)) {
            throw OfflineCmsUnsupportedProfileException()
        }
        require(name.isExplicit()) { "Invalid ESS directory name tagging" }
        // Only the exact directoryName is supported to avoid name normalization ambiguities.
        require(name.getExplicitBaseObject().toASN1Primitive() == certificate.getIssuer().toASN1Primitive()) {
            "ESS certificate issuer mismatch"
        }
    }

    private fun sequence(value: ASN1Encodable): ASN1Sequence =
        requireNotNull(value.toASN1Primitive() as? ASN1Sequence) { "Invalid ESS sequence" }
}

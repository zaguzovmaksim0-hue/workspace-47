package dev.junta.firmamobile.verification

import java.math.BigInteger
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.X509Certificate
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1Encoding
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.ASN1Set
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.DERSet
import org.bouncycastle.asn1.DERTaggedObject
import org.bouncycastle.asn1.cms.Attribute
import org.bouncycastle.asn1.cms.SignerInfo
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.cert.X509CertificateHolder
import org.junit.Assert.assertTrue

internal class OfflineCadesFixtures(
    val material: OfflineCmsSignerMaterial = OfflineCmsFixtures.signer(
        signatureAlgorithm = "SHA384withECDSA",
        keyAlgorithm = "EC",
        curve = "secp384r1",
    ),
    certificates: Collection<X509Certificate>? = null,
) {
    val content = "offline CAdES synthetic payload".toByteArray(Charsets.UTF_8)
    val certificate = X509CertificateHolder(material.certificate.encoded)
    private val base = OfflineCmsFixtures.signed(
        content,
        listOf(material),
        OfflineCmsOptions(certificates = certificates),
    )

    fun signed(vararg extras: ASN1Encodable): ByteArray =
        OfflineCmsSignerInfoFixtures.changeSigner(base, 0) { signer ->
            val original = requireNotNull(signer.getAuthenticatedAttributes()) { "Missing base fixture attributes" }
            val values = ASN1EncodableVector()
            for (index in 0 until original.size()) {
                values.add(original.getObjectAt(index))
            }
            extras.forEach { values.add(it) }
            signedSigner(signer, DERSet(values))
        }

    fun resign(cms: ByteArray): ByteArray =
        OfflineCmsSignerInfoFixtures.changeSigner(cms, 0) { signer ->
            val attributes = requireNotNull(signer.getAuthenticatedAttributes()) { "Missing attributes to re-sign" }
            signedSigner(signer, attributes)
        }

    private fun signedSigner(signer: SignerInfo, attributes: ASN1Set): SignerInfo {
        val signature = Signature.getInstance(material.signatureAlgorithm, OfflineCmsFixtures.provider).run {
            initSign(material.keyPair.private)
            update(attributes.getEncoded(ASN1Encoding.DER))
            sign()
        }
        return SignerInfo(
            signer.getSID(),
            signer.getDigestAlgorithm(),
            attributes,
            signer.getDigestEncryptionAlgorithm(),
            DEROctetString(signature),
            signer.getUnauthenticatedAttributes(),
        )
    }

    fun assertRejected(expected: OfflineCmsStatus, cms: ByteArray) {
        val signer = OfflineCmsSignerInfoFixtures.signerInfos(cms).single()
        val attributes = requireNotNull(signer.getAuthenticatedAttributes()) { "Missing signed fixture attributes" }
        val valid = Signature.getInstance(material.signatureAlgorithm, OfflineCmsFixtures.provider).run {
            initVerify(material.keyPair.public)
            update(attributes.getEncoded(ASN1Encoding.DER))
            verify(signer.getEncryptedDigest().getOctets())
        }
        assertTrue("Synthetic CMS signature must verify independently", valid)
        OfflineCmsAssertions.assertRejected(expected, cms)
    }

    fun binding(identifier: ASN1Encodable = certId()): Attribute =
        attribute(PKCSObjectIdentifiers.id_aa_signingCertificateV2, sequence(sequence(identifier)))

    fun certId(
        algorithm: ASN1Encodable? = null,
        hash: ByteArray = certificateHash(),
        issuerSerial: ASN1Encodable? = null,
    ): DERSequence {
        val fields = ASN1EncodableVector()
        algorithm?.let { fields.add(it) }
        fields.add(DEROctetString(hash))
        issuerSerial?.let { fields.add(it) }
        return DERSequence(fields)
    }

    fun certificateHash(name: String = "SHA-256"): ByteArray =
        MessageDigest.getInstance(name, OfflineCmsFixtures.provider).digest(certificate.getEncoded())

    fun issuerSerial(
        issuer: X500Name = certificate.getIssuer(),
        serial: BigInteger = certificate.getSerialNumber(),
    ): DERSequence = sequence(
        sequence(DERTaggedObject(true, GeneralName.directoryName, issuer)),
        ASN1Integer(serial),
    )

    fun attribute(oid: ASN1ObjectIdentifier, vararg values: ASN1Encodable): Attribute =
        Attribute(oid, DERSet(values))

    fun sequence(vararg values: ASN1Encodable): DERSequence = DERSequence(values)
}

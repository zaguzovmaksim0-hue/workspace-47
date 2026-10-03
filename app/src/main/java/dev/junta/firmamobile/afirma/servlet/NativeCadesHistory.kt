package dev.junta.firmamobile.afirma.servlet

import java.io.Closeable
import java.security.MessageDigest
import org.bouncycastle.asn1.ASN1Encoding
import org.bouncycastle.asn1.ASN1Null
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.cms.Attribute
import org.bouncycastle.asn1.cms.CMSAttributes
import org.bouncycastle.asn1.cms.ContentInfo
import org.bouncycastle.asn1.cms.SignedData
import org.bouncycastle.asn1.ess.SigningCertificate
import org.bouncycastle.asn1.ess.SigningCertificateV2
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.IssuerSerial
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.SignerInformation
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider

/** Supported ordinary CAdES history, not certificate-chain trust, revocation,
 * a trusted signing time, or validation of an absent detached original file. */
internal class NativeCadesHistory private constructor(
    val envelope: CMSSignedData,
    val structure: SignedData,
    val content: ByteArray?,
    val hashes: Map<String, ByteArray>,
    val signerRecords: List<ByteArray>,
    val certificates: List<X509CertificateHolder>,
) : Closeable {
    val detached: Boolean get() = content == null
    override fun close() {
        content?.fill(0); hashes.values.forEach { it.fill(0) }; signerRecords.forEach { it.fill(0) }
    }
    override fun toString() = "NativeCadesHistory(detached=$detached, signers=${signerRecords.size})"

    companion object {
        const val MAX_SIGNERS = 8
        const val MAX_CERTIFICATES = 32
        private val provider = BouncyCastleProvider()

        /** Shape-only admission; cryptographic history verification remains
         * mandatory immediately before creating a new signature. */
        fun isDetached(encoded: ByteArray, maxBytes: Int = NativeCadesEngine.MAX_INPUT_BYTES): Boolean? = runCatching {
            val (_, data) = read(encoded, maxBytes)
            data.encapContentInfo.content == null
        }.getOrNull()

        fun inspect(encoded: ByteArray, maxBytes: Int): NativeCadesHistory? = runCatching {
            val (envelope, data) = read(encoded, maxBytes)
            val payload = (data.encapContentInfo.content as? ASN1OctetString)?.octets?.copyOf()
            val hashes = linkedMapOf<String, ByteArray>()
            val records = mutableListOf<ByteArray>()
            try {
                val signers = envelope.signerInfos.signers.toList()
                val certs = envelope.certificates.getMatches(null).toList()
                require(certs.size == data.certificates.size() && certs.size in 1..MAX_CERTIFICATES)
                require(certs.map { digest(it.encoded) }.toSet().size == certs.size)
                val declaredAlgorithms = (0 until data.digestAlgorithms.size()).map {
                    val algorithm = AlgorithmIdentifier.getInstance(data.digestAlgorithms.getObjectAt(it))
                    require(simpleParameters(algorithm)); require(digestName(algorithm.algorithm.id) != null)
                    algorithm.algorithm.id
                }
                require(declaredAlgorithms.size == declaredAlgorithms.toSet().size)
                require(declaredAlgorithms.toSet() == signers.map { it.digestAlgOID }.toSet())
                for (signer in signers) {
                    require(!signer.isCounterSignature && (signer.unsignedAttributes?.size() ?: 0) == 0)
                    require(simpleParameters(signer.digestAlgorithmID) && simpleParameters(signer.toASN1Structure().digestEncryptionAlgorithm))
                    require(NativePdfSignerAlgorithms.fromOids(signer.digestAlgOID, signer.encryptionAlgOID) != null)
                    val cert = certs.filter(signer.sid::match).singleOrNull() ?: error("Ambiguous signing certificate")
                    val attrs = signer.signedAttributes ?: error("Missing signed attributes")
                    val all = attrs.toASN1EncodableVector()
                    require(all.size() in 3..32)
                    val oids = (0 until all.size()).map { Attribute.getInstance(all.get(it)).attrType.id }
                    require(oids.size == oids.toSet().size)
                    val contentType = attrs[CMSAttributes.contentType] ?: error("Missing content type")
                    require(contentType.attrValues.size() == 1 && ASN1ObjectIdentifier.getInstance(contentType.attrValues.getObjectAt(0)) == PKCSObjectIdentifiers.data)
                    val messageDigest = attrs[CMSAttributes.messageDigest] ?: error("Missing message digest")
                    require(messageDigest.attrValues.size() == 1)
                    val hash = ASN1OctetString.getInstance(messageDigest.attrValues.getObjectAt(0)).octets.copyOf()
                    try {
                        require(hash.size == digestLength(signer.digestAlgOID))
                        val previous = hashes[signer.digestAlgOID]
                        if (previous == null) hashes[signer.digestAlgOID] = hash.copyOf()
                        else require(MessageDigest.isEqual(previous, hash))
                        if (payload != null) {
                            val calculated = MessageDigest.getInstance(checkNotNull(digestName(signer.digestAlgOID))).digest(payload)
                            try { require(MessageDigest.isEqual(calculated, hash)) } finally { calculated.fill(0) }
                        }
                    } finally { hash.fill(0) }
                    require(certificateReference(signer, cert))
                    val record = signer.toASN1Structure().getEncoded(ASN1Encoding.DER)
                    require(records.none { it.contentEquals(record) }); records += record
                }
                // With no original bytes, unrelated digest families cannot be
                // proven to describe the same content. Do not infer that link.
                if (payload == null) require(hashes.size == 1)
                val verified = if (payload != null) CMSSignedData(CMSProcessableByteArray(payload), encoded) else CMSSignedData(hashes, encoded)
                for (signer in verified.signerInfos.signers) {
                    val certificate = certs.filter(signer.sid::match).single()
                    require(signer.verify(JcaSimpleSignerInfoVerifierBuilder().setProvider(provider).build(certificate)))
                }
                NativeCadesHistory(envelope, data, payload, hashes, records, certs)
            } catch (error: Exception) {
                payload?.fill(0); hashes.values.forEach { it.fill(0) }; records.forEach { it.fill(0) }
                throw error
            }
        }.getOrNull()

        private fun read(encoded: ByteArray, maxBytes: Int): Pair<CMSSignedData, SignedData> {
            require(NativeCmsBounds.accepts(encoded, maxBytes))
            val content = ContentInfo.getInstance(ASN1Primitive.fromByteArray(encoded))
            require(content.contentType == PKCSObjectIdentifiers.signedData)
            val data = SignedData.getInstance(content.content)
            require(data.encapContentInfo.contentType == PKCSObjectIdentifiers.data)
            require(data.encapContentInfo.content == null || data.encapContentInfo.content is ASN1OctetString)
            require(data.signerInfos.size() in 1..MAX_SIGNERS && data.certificates != null && data.certificates.size() in 1..MAX_CERTIFICATES)
            require(data.digestAlgorithms.size() in 1..4 && (data.crLs?.size() ?: 0) == 0)
            return CMSSignedData(content) to data
        }

        private fun certificateReference(signer: SignerInformation, certificate: X509CertificateHolder): Boolean {
            val attrs = checkNotNull(signer.signedAttributes)
            val v1 = attrs[PKCSObjectIdentifiers.id_aa_signingCertificate]
            val v2 = attrs[PKCSObjectIdentifiers.id_aa_signingCertificateV2]
            if ((v1 == null) == (v2 == null)) return false
            val hash: ByteArray; val algorithm: String; val issuer: IssuerSerial?
            if (v2 != null) {
                if (v2.attrValues.size() != 1) return false
                val ref = SigningCertificateV2.getInstance(v2.attrValues.getObjectAt(0)).certs.singleOrNull() ?: return false
                if (!simpleParameters(ref.hashAlgorithm)) return false
                algorithm = digestName(ref.hashAlgorithm.algorithm.id) ?: return false
                hash = ref.certHash; issuer = ref.issuerSerial
            } else {
                if (v1!!.attrValues.size() != 1) return false
                val ref = SigningCertificate.getInstance(v1.attrValues.getObjectAt(0)).certs.singleOrNull() ?: return false
                algorithm = "SHA-1"; hash = ref.certHash; issuer = ref.issuerSerial
            }
            if (issuer != null) {
                val names = issuer.issuer.names
                if (issuer.serial.value != certificate.serialNumber || names.size != 1 || names.single().tagNo != GeneralName.directoryName ||
                    names.single().name.toASN1Primitive() != certificate.issuer.toASN1Primitive()) return false
            }
            val calculated = MessageDigest.getInstance(algorithm).digest(certificate.encoded)
            return try { MessageDigest.isEqual(calculated, hash) } finally { calculated.fill(0) }
        }
        private fun simpleParameters(algorithm: AlgorithmIdentifier) = algorithm.parameters == null || algorithm.parameters is ASN1Null
        internal fun digestName(oid: String) = when (oid) {
            "1.3.14.3.2.26" -> "SHA-1"
            "2.16.840.1.101.3.4.2.1" -> "SHA-256"
            "2.16.840.1.101.3.4.2.2" -> "SHA-384"
            "2.16.840.1.101.3.4.2.3" -> "SHA-512"
            else -> null
        }
        internal fun digestLength(oid: String) = when (oid) {
            "1.3.14.3.2.26" -> 20
            "2.16.840.1.101.3.4.2.1" -> 32
            "2.16.840.1.101.3.4.2.2" -> 48
            "2.16.840.1.101.3.4.2.3" -> 64
            else -> 0
        }
        internal fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

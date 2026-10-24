package dev.junta.firmamobile.verification

import java.security.PublicKey
import java.security.interfaces.RSAPublicKey
import org.bouncycastle.asn1.ASN1Null
import org.bouncycastle.asn1.cms.SignedData
import org.bouncycastle.asn1.cms.SignerInfo
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers
import org.bouncycastle.asn1.x509.AlgorithmIdentifier

internal object OfflineCmsAlgorithmPolicy {
    fun validateSignedData(signedData: SignedData) {
        val declared = mutableSetOf<String>()
        val digestAlgorithms = signedData.getDigestAlgorithms()
        for (index in 0 until digestAlgorithms.size()) {
            val identifier = AlgorithmIdentifier.getInstance(digestAlgorithms.getObjectAt(index))
            validateDigestIdentifier(identifier)
            require(declared.add(identifier.getAlgorithm().getId())) {
                "Duplicate CMS digest algorithm"
            }
        }

        val used = mutableSetOf<String>()
        val signerInfos = signedData.getSignerInfos()
        for (index in 0 until signerInfos.size()) {
            val signer = SignerInfo.getInstance(signerInfos.getObjectAt(index))
            val digest = signer.getDigestAlgorithm()
            validateDigestIdentifier(digest)
            val digestOid = digest.getAlgorithm().getId()
            validateSignatureIdentifier(digestOid, signer.getDigestEncryptionAlgorithm())
            require(declared.contains(digestOid)) {
                "Undeclared CMS digest algorithm"
            }
            used.add(digestOid)
        }
        require(declared == used) {
            "Inconsistent CMS digest algorithms"
        }
    }

    fun validateSignerAlgorithms(digestOid: String, signatureOid: String) {
        if (digestName(digestOid) == null || !supportsSignaturePair(digestOid, signatureOid)) {
            throw OfflineCmsUnsupportedProfileException()
        }
    }

    fun validatePublicKey(digestOid: String, signatureOid: String, publicKey: PublicKey) {
        validateSignerAlgorithms(digestOid, signatureOid)
        if (isRsaSignature(signatureOid)) {
            if (publicKey !is RSAPublicKey) {
                throw OfflineCmsUnsupportedProfileException()
            }
        } else {
            OfflineCmsEcDomainPolicy.validatePublicKey(digestOid, signatureOid, publicKey)
        }
    }

    fun digestName(oid: String): String? {
        return when (oid) {
            NISTObjectIdentifiers.id_sha256.getId() -> "SHA-256"
            NISTObjectIdentifiers.id_sha384.getId() -> "SHA-384"
            NISTObjectIdentifiers.id_sha512.getId() -> "SHA-512"
            else -> null
        }
    }

    fun validateDigestIdentifier(identifier: AlgorithmIdentifier) {
        if (digestName(identifier.getAlgorithm().getId()) == null ||
            !hasNullOrAbsentParameters(identifier)
        ) {
            throw OfflineCmsUnsupportedProfileException()
        }
    }

    private fun validateSignatureIdentifier(
        digestOid: String,
        identifier: AlgorithmIdentifier,
    ) {
        val signatureOid = identifier.getAlgorithm().getId()
        validateSignerAlgorithms(digestOid, signatureOid)
        val parameters = identifier.getParameters()
        if (isRsaSignature(signatureOid)) {
            if (parameters != null && parameters.toASN1Primitive() !is ASN1Null) {
                throw OfflineCmsUnsupportedProfileException()
            }
        } else if (parameters != null) {
            throw OfflineCmsUnsupportedProfileException()
        }
    }

    private fun supportsSignaturePair(digestOid: String, signatureOid: String): Boolean {
        return when (signatureOid) {
            PKCSObjectIdentifiers.rsaEncryption.getId() ->
                digestOid == NISTObjectIdentifiers.id_sha256.getId() ||
                    digestOid == NISTObjectIdentifiers.id_sha384.getId() ||
                    digestOid == NISTObjectIdentifiers.id_sha512.getId()
            PKCSObjectIdentifiers.sha256WithRSAEncryption.getId() ->
                digestOid == NISTObjectIdentifiers.id_sha256.getId()
            PKCSObjectIdentifiers.sha384WithRSAEncryption.getId() ->
                digestOid == NISTObjectIdentifiers.id_sha384.getId()
            PKCSObjectIdentifiers.sha512WithRSAEncryption.getId() ->
                digestOid == NISTObjectIdentifiers.id_sha512.getId()
            X9ObjectIdentifiers.ecdsa_with_SHA256.getId() ->
                digestOid == NISTObjectIdentifiers.id_sha256.getId()
            X9ObjectIdentifiers.ecdsa_with_SHA384.getId() ->
                digestOid == NISTObjectIdentifiers.id_sha384.getId()
            else -> false
        }
    }

    private fun isRsaSignature(signatureOid: String): Boolean {
        return signatureOid == PKCSObjectIdentifiers.rsaEncryption.getId() ||
            signatureOid == PKCSObjectIdentifiers.sha256WithRSAEncryption.getId() ||
            signatureOid == PKCSObjectIdentifiers.sha384WithRSAEncryption.getId() ||
            signatureOid == PKCSObjectIdentifiers.sha512WithRSAEncryption.getId()
    }

    private fun hasNullOrAbsentParameters(identifier: AlgorithmIdentifier): Boolean {
        val parameters = identifier.getParameters() ?: return true
        return parameters.toASN1Primitive() is ASN1Null
    }
}

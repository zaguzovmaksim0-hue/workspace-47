package dev.junta.firmamobile.verification

import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1Encoding
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.ASN1Set
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.DERSet
import org.bouncycastle.asn1.cms.ContentInfo
import org.bouncycastle.asn1.cms.SignedData
import org.bouncycastle.asn1.cms.SignerInfo

internal object OfflineCmsSignerInfoFixtures {
    fun signerInfos(cms: ByteArray): List<SignerInfo> {
        val signerSet = decodeSignedData(cms).getSignerInfos()
        return (0 until signerSet.size()).map {
            SignerInfo.getInstance(signerSet.getObjectAt(it))
        }
    }

    fun duplicateFirstSigner(cms: ByteArray): ByteArray {
        val signer = signerInfos(cms).first()
        return replaceSignerInfos(cms, listOf(signer, signer))
    }

    fun tamperSignature(cms: ByteArray, index: Int = 0): ByteArray =
        changeSigner(cms, index) { signer ->
            val signature = signer.getEncryptedDigest().getOctets().clone()
            signature[0] = (signature[0].toInt() xor SIGNATURE_BIT_MASK).toByte()
            copySigner(signer, encryptedDigest = DEROctetString(signature))
        }

    fun changeSigner(
        cms: ByteArray,
        index: Int,
        transform: (SignerInfo) -> SignerInfo,
    ): ByteArray {
        val signers = signerInfos(cms).toMutableList()
        signers[index] = transform(signers[index])
        return replaceSignerInfos(cms, signers)
    }

    fun replaceSignerInfos(cms: ByteArray, signers: List<SignerInfo>): ByteArray =
        changeSignedData(cms) { data ->
            val vector = ASN1EncodableVector()
            signers.forEach { vector.add(it) }
            copySignedData(data, signerInfos = DERSet(vector))
        }

    private fun changeSignedData(
        cms: ByteArray,
        transform: (SignedData) -> SignedData,
    ): ByteArray {
        val contentInfo = ContentInfo.getInstance(ASN1Primitive.fromByteArray(cms))
        return ContentInfo(
            contentInfo.getContentType(),
            transform(decodeSignedData(cms)),
        ).getEncoded(ASN1Encoding.DER)
    }

    private fun decodeSignedData(cms: ByteArray): SignedData {
        val contentInfo = ContentInfo.getInstance(ASN1Primitive.fromByteArray(cms))
        return SignedData.getInstance(contentInfo.getContent())
    }

    private fun copySignedData(
        original: SignedData,
        signerInfos: ASN1Set = original.getSignerInfos(),
    ): SignedData = SignedData(
        original.getVersion(),
        original.getDigestAlgorithms(),
        original.getEncapContentInfo(),
        original.getCertificates(),
        original.getCRLs(),
        signerInfos,
    )

    private fun copySigner(
        signer: SignerInfo,
        signedAttributes: ASN1Set? = signer.getAuthenticatedAttributes(),
        unsignedAttributes: ASN1Set? = signer.getUnauthenticatedAttributes(),
        encryptedDigest: org.bouncycastle.asn1.ASN1OctetString = signer.getEncryptedDigest(),
    ): SignerInfo = SignerInfo(
        signer.getSID(),
        signer.getDigestAlgorithm(),
        signedAttributes,
        signer.getDigestEncryptionAlgorithm(),
        encryptedDigest,
        unsignedAttributes,
    )

    private const val SIGNATURE_BIT_MASK = 1
}

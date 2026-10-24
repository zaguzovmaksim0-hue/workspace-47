package dev.junta.firmamobile.verification

import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1Encoding
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.cms.ContentInfo
import org.bouncycastle.asn1.cms.SignedData
import org.bouncycastle.asn1.cms.SignerInfo

internal object OfflineCmsVersionFixtures {
    fun withSignedDataVersion(cms: ByteArray, version: Long): ByteArray {
        val contentInfo = ContentInfo.getInstance(ASN1Primitive.fromByteArray(cms))
        val data = SignedData.getInstance(contentInfo.getContent())
        val changed = SignedData(
            ASN1Integer(version),
            data.getDigestAlgorithms(),
            data.getEncapContentInfo(),
            data.getCertificates(),
            data.getCRLs(),
            data.getSignerInfos(),
        )
        return ContentInfo(contentInfo.getContentType(), changed).getEncoded(ASN1Encoding.DER)
    }

    fun withSignerVersion(cms: ByteArray, version: Long): ByteArray =
        OfflineCmsSignerInfoFixtures.changeSigner(cms, SIGNER_INDEX) { signer ->
            val sequence = ASN1Sequence.getInstance(signer.toASN1Primitive())
            val vector = ASN1EncodableVector()
            vector.add(ASN1Integer(version))
            for (index in SIGNER_FIELDS_START until sequence.size()) {
                vector.add(sequence.getObjectAt(index))
            }
            SignerInfo.getInstance(DERSequence(vector))
        }

    private const val SIGNER_INDEX = 0
    private const val SIGNER_FIELDS_START = 1
}

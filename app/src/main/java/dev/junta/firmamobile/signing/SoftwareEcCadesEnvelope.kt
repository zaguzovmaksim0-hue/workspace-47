package dev.junta.firmamobile.signing

import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.DERSet
import org.bouncycastle.asn1.cms.Attribute
import org.bouncycastle.asn1.cms.CMSAttributes
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers
import org.bouncycastle.asn1.cms.ContentInfo
import org.bouncycastle.asn1.cms.IssuerAndSerialNumber
import org.bouncycastle.asn1.cms.SignedData
import org.bouncycastle.asn1.cms.SignerIdentifier
import org.bouncycastle.asn1.cms.SignerInfo
import org.bouncycastle.asn1.ess.ESSCertIDv2
import org.bouncycastle.asn1.ess.SigningCertificateV2
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers

internal object SoftwareEcCadesEnvelope {
    fun readSigner(cmsDer: ByteArray): SignerInfo {
        SoftwareEcCadesDerGate.requireBoundedTree(cmsDer)
        val content = ContentInfo.getInstance(ASN1Primitive.fromByteArray(cmsDer))
        require(content.contentType == CMSObjectIdentifiers.signedData)
        val data = SignedData.getInstance(content.content)
        require(data.signerInfos.size() == 1)
        return SignerInfo.getInstance(data.signerInfos.getObjectAt(0))
    }

    fun attributes(
        input: ByteArray,
        certificate: SoftwareEcCadesPolicy.Certificate,
        policy: SoftwareEcCadesPolicy,
        buffers: SoftwareEcCadesBuffers,
    ): DERSet = DERSet(arrayOf(
        Attribute(CMSAttributes.contentType, DERSet(CMSObjectIdentifiers.data)),
        Attribute(CMSAttributes.messageDigest, DERSet(DEROctetString(policy.digest(input, buffers)))),
        Attribute(PKCSObjectIdentifiers.id_aa_signingCertificateV2, DERSet(SigningCertificateV2(
            ESSCertIDv2(policy.digestAlgorithm, policy.digest(certificate.encoded, buffers)),
        ))),
    ))

    fun create(
        attributes: DERSet,
        signature: ByteArray,
        certificate: SoftwareEcCadesPolicy.Certificate,
        policy: SoftwareEcCadesPolicy,
    ): ContentInfo {
        val holder = certificate.holder
        val sid = SignerIdentifier(IssuerAndSerialNumber(holder.issuer, holder.serialNumber))
        val signer = SignerInfo(sid, policy.digestAlgorithm, attributes, policy.signatureAlgorithm,
            DEROctetString(signature), null)
        val signed = SignedData(DERSet(policy.digestAlgorithm), ContentInfo(CMSObjectIdentifiers.data, null),
            DERSet(holder.toASN1Structure()), null, DERSet(signer))
        return ContentInfo(CMSObjectIdentifiers.signedData, signed)
    }
}

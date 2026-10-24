package dev.junta.firmamobile.signing

import java.math.BigInteger
import java.security.Signature
import org.bouncycastle.asn1.ASN1Encoding
import org.bouncycastle.asn1.ASN1Set
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.DERSet
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers
import org.bouncycastle.asn1.cms.ContentInfo
import org.bouncycastle.asn1.cms.SignedData
import org.bouncycastle.asn1.cms.SignerInfo
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

internal class SoftwareEcCadesTestFixtures(
    val algorithm: SoftwareEcSignatureAlgorithm = SoftwareEcSignatureAlgorithm.P256_SHA256,
) {
    val keys = SoftwareEcTestFixtures.keys(algorithm.namedCurve)
    val certificate = SoftwareEcTestFixtures.certificate(keys)
    val input = byteArrayOf(0, 1, -1, 127, -128, 10, 13, 42)
    val request = SoftwareEcSignatureRequest(keys.private, certificate, algorithm)
    val engine = SoftwareEcCadesEngine()
    val encoded: ByteArray = sign()
    val signedData: SignedData get() = SignedData.getInstance(ContentInfo.getInstance(encoded).content)
    val signer: SignerInfo get() = SignerInfo.getInstance(signedData.signerInfos.getObjectAt(0))

    fun sign(engine: SoftwareEcCadesEngine = this.engine, input: ByteArray = this.input): ByteArray {
        val result = engine.signDetached(input, request)
        check(result is SoftwareEcCadesResult.Success) { "Expected successful fixture signature: $result" }
        return result.cmsDer
    }

    fun verifies(cms: ByteArray = encoded, input: ByteArray = this.input): Boolean =
        engine.verifyDetached(input, cms, certificate, algorithm)

    fun replaceSigner(signer: SignerInfo): ByteArray = replaceData(SignedData(signedData.digestAlgorithms,
        signedData.encapContentInfo, signedData.certificates, signedData.crLs, DERSet(signer)))

    fun replaceData(signedData: SignedData): ByteArray =
        ContentInfo(CMSObjectIdentifiers.signedData, signedData).getEncoded(ASN1Encoding.DER)

    fun resign(
        attributes: ASN1Set,
        digest: AlgorithmIdentifier = signer.digestAlgorithm,
        signatureAlgorithm: AlgorithmIdentifier = signer.digestEncryptionAlgorithm,
    ): ByteArray {
        val bytes = Signature.getInstance(algorithm.jcaName).run {
            initSign(keys.private)
            update(attributes.getEncoded(ASN1Encoding.DER))
            sign()
        }
        return replaceSigner(SignerInfo(signer.sid, digest, attributes, signatureAlgorithm, DEROctetString(bytes), null))
    }

    fun otherCertificateWithSameKey(): java.security.cert.X509Certificate {
        val provider = BouncyCastleProvider()
        val holder = X509CertificateHolder(certificate.encoded)
        val builder = JcaX509v3CertificateBuilder(holder.issuer, BigInteger.TWO, certificate.notBefore,
            certificate.notAfter, holder.subject, keys.public)
        return JcaX509CertificateConverter().setProvider(provider).getCertificate(builder.build(
            JcaContentSignerBuilder(algorithm.jcaName).setProvider(provider).build(keys.private)))
    }

    fun independentSignatureValid(cms: ByteArray): Boolean {
        val parsed = SignerInfo.getInstance(SignedData.getInstance(ContentInfo.getInstance(cms).content)
            .signerInfos.getObjectAt(0))
        return Signature.getInstance(algorithm.jcaName).run {
            initVerify(certificate.publicKey)
            update(parsed.authenticatedAttributes.getEncoded(ASN1Encoding.DER))
            verify(parsed.encryptedDigest.octets)
        }
    }
}

package dev.junta.firmamobile.signing

import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.cms.CMSAttributes
import org.bouncycastle.asn1.ess.SigningCertificateV2
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftwareEcCadesEngineTest {
    @Test
    fun p256CreatesRealDetachedCadesFromOriginalBytes() {
        assertDetachedCades(SoftwareEcSignatureAlgorithm.P256_SHA256, "SHA-256",
            "2.16.840.1.101.3.4.2.1", "1.2.840.10045.4.3.2")
    }

    @Test
    fun p384CreatesRealDetachedCadesFromOriginalBytes() {
        assertDetachedCades(SoftwareEcSignatureAlgorithm.P384_SHA384, "SHA-384",
            "2.16.840.1.101.3.4.2.2", "1.2.840.10045.4.3.3")
    }

    private fun assertDetachedCades(
        algorithm: SoftwareEcSignatureAlgorithm,
        digestName: String,
        digestOid: String,
        signatureOid: String,
    ) {
        val keys = SoftwareEcTestFixtures.keys(algorithm.namedCurve)
        val key = SoftwareEcTestFixtures.EncodingGuard(keys.private as ECPrivateKey)
        val certificate = SoftwareEcTestFixtures.certificate(keys)
        val input = byteArrayOf(0, 1, -1, 127, -128, 10, 13, 42)
        val result = SoftwareEcCadesEngine().signDetached(
            input, SoftwareEcSignatureRequest(key, certificate, algorithm),
        )
        assertTrue("Expected detached CAdES, got $result", result is SoftwareEcCadesResult.Success)
        val encoded = (result as SoftwareEcCadesResult.Success).cmsDer
        val detached = CMSSignedData(encoded)
        assertTrue(detached.isDetachedSignature)
        assertNull(detached.signedContent)
        assertEquals(1, detached.certificates.getMatches(null).size)
        val cms = CMSSignedData(CMSProcessableByteArray(input), encoded)
        val signer = cms.signerInfos.signers.single()
        assertTrue(signer.verify(JcaSimpleSignerInfoVerifierBuilder()
            .setProvider(BouncyCastleProvider()).build(certificate)))
        assertTrue(Signature.getInstance(algorithm.jcaName).run {
            initVerify(certificate.publicKey)
            update(signer.encodedSignedAttributes)
            verify(signer.signature)
        })
        assertEquals(digestOid, signer.digestAlgOID)
        assertEquals(signatureOid, signer.encryptionAlgOID)
        assertNull(signer.digestAlgParams)
        assertNull(signer.encryptionAlgParams)
        assertEquals(digestOid, cms.digestAlgorithmIDs.single().algorithm.id)
        assertNull(cms.digestAlgorithmIDs.single().parameters)
        val attributes = signer.signedAttributes
        assertEquals(3, attributes.size())
        val digest = ASN1OctetString.getInstance(attributes.get(CMSAttributes.messageDigest).attrValues.getObjectAt(0))
        assertArrayEquals(MessageDigest.getInstance(digestName).digest(input), digest.octets)
        val binding = SigningCertificateV2.getInstance(attributes.get(
            PKCSObjectIdentifiers.id_aa_signingCertificateV2).attrValues.getObjectAt(0))
        assertEquals(1, binding.certs.size)
        assertEquals(digestOid, binding.certs.single().hashAlgorithm.algorithm.id)
        assertArrayEquals(MessageDigest.getInstance(digestName).digest(certificate.encoded), binding.certs.single().certHash)
        assertEquals(0, key.encodingReads.get())
        assertEquals(1, key.scalarReads.get())
    }
}

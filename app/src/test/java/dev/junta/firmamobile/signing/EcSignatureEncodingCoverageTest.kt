package dev.junta.firmamobile.signing

import dev.junta.firmamobile.afirma.servlet.NativePdfSignerAlgorithms
import java.math.BigInteger
import java.security.Signature
import org.bouncycastle.asn1.ASN1Encoding
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.cms.SignerInfo
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** The XMLDSig r||s representation must never be mistaken for the existing DER-only API. */
@RunWith(Parameterized::class)
class EcSignatureEncodingCoverageTest(
    private val algorithmName: String,
    private val jcaName: String,
    private val digestOid: String,
    private val signatureOid: String,
    private val scalarBytes: Int,
) {
    private val algorithm = SoftwareEcSignatureAlgorithm.valueOf(algorithmName)
    private val fixture = SoftwareEcCadesTestFixtures(algorithm)

    @Test
    fun rawEngineReturnsCanonicalDerAndRejectsFixedWidthXmlEncoding() {
        val engine = SoftwareEcSignatureEngine()
        val result = engine.sign(fixture.input, fixture.request)
        assertTrue("Expected DER signature: $result", result is SoftwareEcSignatureResult.Success)
        val der = (result as SoftwareEcSignatureResult.Success).derSignature
        assertTrue(Signature.getInstance(jcaName).run {
            initVerify(fixture.certificate.publicKey); update(fixture.input); verify(der)
        })
        val raw = xmlSignatureValue(der)
        assertEquals(scalarBytes * 2, raw.size)
        assertTrue(engine.verify(fixture.input, der, fixture.certificate, algorithm))
        assertFalse(engine.verify(fixture.input, raw, fixture.certificate, algorithm))
        assertFalse(engine.verify(fixture.input + byteArrayOf(1), der, fixture.certificate, algorithm))
    }

    @Test
    fun cmsBindsDigestAndEcdsaOidsAndDoesNotAcceptXmlEncodingInSignatureOctets() {
        val cms = CMSSignedData(CMSProcessableByteArray(fixture.input), fixture.encoded)
        val signer = cms.signerInfos.signers.single()
        assertEquals(digestOid, signer.digestAlgOID)
        assertEquals(signatureOid, signer.encryptionAlgOID)
        assertNull(signer.digestAlgParams)
        assertNull(signer.encryptionAlgParams)
        assertTrue(signer.verify(JcaSimpleSignerInfoVerifierBuilder().build(fixture.certificate)))
        val raw = xmlSignatureValue(signer.signature)
        val original = fixture.signer
        val changed = fixture.replaceSigner(SignerInfo(original.sid, original.digestAlgorithm,
            original.authenticatedAttributes, original.digestEncryptionAlgorithm, DEROctetString(raw), null))
        assertTrue(fixture.verifies())
        assertFalse(fixture.verifies(changed))
    }

    @Test
    fun pdfHistoryDoesNotMislabelAnEcdsaOidAsRsa() {
        assertNull(NativePdfSignerAlgorithms.fromOids(digestOid, signatureOid))
        val rsa = if (scalarBytes == 32) SigningAlgorithm.SHA256_WITH_RSA else SigningAlgorithm.SHA384_WITH_RSA
        val combinedRsaOid = if (scalarBytes == 32) "1.2.840.113549.1.1.11" else "1.2.840.113549.1.1.12"
        assertEquals(rsa, NativePdfSignerAlgorithms.fromOids(digestOid, "1.2.840.113549.1.1.1"))
        assertEquals(rsa, NativePdfSignerAlgorithms.fromOids(digestOid, combinedRsaOid))
    }

    private fun xmlSignatureValue(der: ByteArray): ByteArray {
        val sequence = ASN1Sequence.getInstance(der)
        assertEquals(2, sequence.size())
        assertArrayEquals(der, sequence.getEncoded(ASN1Encoding.DER))
        val r = ASN1Integer.getInstance(sequence.getObjectAt(0)).value
        val s = ASN1Integer.getInstance(sequence.getObjectAt(1)).value
        assertTrue(r.signum() > 0 && s.signum() > 0)
        return fixedWidth(r) + fixedWidth(s)
    }

    private fun fixedWidth(value: BigInteger): ByteArray {
        val signed = value.toByteArray()
        val unsigned = if (signed[0] == 0.toByte()) signed.copyOfRange(1, signed.size) else signed
        assertTrue(unsigned.size <= scalarBytes)
        return ByteArray(scalarBytes).also { unsigned.copyInto(it, scalarBytes - unsigned.size) }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = listOf(
            arrayOf("P256_SHA256", "SHA256withECDSA", "2.16.840.1.101.3.4.2.1", "1.2.840.10045.4.3.2", 32),
            arrayOf("P384_SHA384", "SHA384withECDSA", "2.16.840.1.101.3.4.2.2", "1.2.840.10045.4.3.3", 48),
        )
    }
}

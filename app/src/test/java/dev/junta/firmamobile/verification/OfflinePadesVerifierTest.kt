package dev.junta.firmamobile.verification

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.util.Date
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.junit.Assert.*
import org.junit.Test

/** Generated key/certificate and hand-built PDF: no personal document or signer. */
class OfflinePadesVerifierTest {
    @Test fun verifiesSignedRevisionAndDisplaysCertificateSubject() {
        val pdf = signedPdf()
        val result = OfflinePadesVerifier.verify(pdf)
        assertEquals(OfflineCmsStatus.VERIFIED, result.status)
        assertEquals(1, result.verifiedSigners)
        assertTrue(result.document!!.signatures.single().signer.contains("Synthetic PDF signer"))
        assertNotNull(result.document.signatures.single().claimedTime)
    }

    @Test fun rejectsChangedSignedByteWithoutErasingCallerInput() {
        val pdf = signedPdf()
        val offset = pdf.toString(Charsets.US_ASCII).indexOf("synthetic")
        pdf[offset] = 'S'.code.toByte()
        val before = pdf.copyOf()
        val result = OfflinePadesVerifier.verify(pdf)
        assertEquals(OfflineCmsStatus.INVALID, result.status)
        assertFalse(result.document!!.signatures.single().verified)
        assertArrayEquals(before, pdf)
    }

    @Test fun unsignedTrailingBytesPreserveRevisionResultButNeverVerifyCurrentDocument() {
        val result = OfflinePadesVerifier.verify(signedPdf() + "\n% unsigned change\n".toByteArray())
        assertEquals(OfflineCmsStatus.INVALID, result.status)
        assertTrue(result.document!!.unsignedChanges)
        assertTrue(result.document.signatures.single().verified)
        assertTrue(result.document.signatures.single().laterChanges)
    }

    @Test fun unsignedIncrementalMetadataIsReportedAndSigningHistoryStillRejectsIt() {
        val pdf = appendRevision(signedPdf(), listOf(6 to "<< /Title (Unsigned change) >>"), "/Info 6 0 R", 7)
        val result = OfflinePadesVerifier.verify(pdf)
        assertEquals(OfflineCmsStatus.INVALID, result.status)
        assertTrue(result.document!!.unsignedChanges)
        assertTrue(result.document.signatures.single().verified)
        com.tom_roush.pdfbox.pdmodel.PDDocument.load(pdf).use {
            assertNull(dev.junta.firmamobile.afirma.servlet.NativePdfSignatureHistory.inspect(pdf, it))
        }
    }

    @Test fun showsEveryIncrementalSignatureAndItsCoverage() {
        val result = OfflinePadesVerifier.verify(signedPdf(signedPdf()))
        assertEquals(OfflineCmsStatus.VERIFIED, result.status)
        assertEquals(2, result.verifiedSigners)
        assertEquals(listOf(true, false), result.document!!.signatures.map { it.laterChanges })
        assertTrue(result.document.signatures.all { it.verified })
        assertFalse(result.document.unsignedChanges)
    }

    @Test fun noSignatureAndOversizedInputCannotClaimIntegrity() {
        val unsigned = OfflinePadesVerifier.verify(fixture())
        assertEquals(OfflineCmsStatus.INVALID, unsigned.status)
        assertTrue(unsigned.document!!.signatures.isEmpty())
        assertEquals(OfflineCmsStatus.LIMIT_EXCEEDED, OfflinePadesVerifier.verify(ByteArray(2_097_153)).status)
    }

    @Test fun malformedRangeAndHiddenOldFieldFailClosed() {
        val signed = signedPdf()
        val text = signed.toString(Charsets.US_ASCII)
        val malformed = signed.copyOf()
        malformed[text.lastIndexOf("/ByteRange [") + 12] = '1'.code.toByte()
        assertEquals(OfflineCmsStatus.INVALID, OfflinePadesVerifier.verify(malformed).status)
        val hidden = appendRevision(signed, listOf(1 to "<< /Type /Catalog /Pages 2 0 R >>"), "", 6)
        val result = OfflinePadesVerifier.verify(hidden)
        assertEquals(OfflineCmsStatus.INVALID, result.status)
        assertNull(result.document)
    }

    @Test fun rejectsSignedCertificateDigestMismatch() {
        val result = OfflinePadesVerifier.verify(signedPdf(wrongCertificateDigest = true))
        assertEquals(OfflineCmsStatus.INVALID, result.status)
        assertFalse(result.document!!.signatures.single().verified)
    }

    companion object {
        private val key by lazy { KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair() }
        private val certificate by lazy {
            val name = X500Name("CN=Synthetic PDF signer")
            JcaX509v3CertificateBuilder(name, BigInteger.ONE, Date(0), Date(4_102_444_800_000L), name, key.public)
                .build(JcaContentSignerBuilder("SHA256withRSA").build(key.private))
        }
        internal fun signedPdf(previous: ByteArray? = null, wrongCertificateDigest: Boolean = false): ByteArray {
            val placeholder = "0".repeat(16_384)
            val dictionary = "<< /Type /Sig /Filter /Adobe.PPKLite /SubFilter /adbe.pkcs7.detached " +
                "/M (D:20260101000000Z) /ByteRange [${"0".repeat(43)}] /Contents <$placeholder> >>"
            val pdf = if (previous == null) fixture(dictionary) else appendRevision(previous, listOf(
                1 to "<< /Type /Catalog /Pages 2 0 R /AcroForm << /Fields [5 0 R 7 0 R] /SigFlags 3 >> >>",
                6 to dictionary,
                7 to "<< /Type /Annot /Subtype /Widget /FT /Sig /T (Signature2) /V 6 0 R /Rect [0 0 0 0] >>",
            ), "", 8)
            val text = pdf.toString(Charsets.US_ASCII)
            val gap = text.lastIndexOf("<$placeholder>")
            val end = gap + placeholder.length + 2
            val ranges = "0 $gap $end ${pdf.size - end}".padEnd(43)
            val rangeStart = text.lastIndexOf("/ByteRange [") + 12
            ranges.toByteArray().copyInto(pdf, rangeStart)
            val generator = CMSSignedDataGenerator().apply {
                addCertificate(certificate)
                val builder = JcaSignerInfoGeneratorBuilder(JcaDigestCalculatorProviderBuilder().build())
                run {
                    val binding = org.bouncycastle.asn1.cms.Attribute(
                        org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers.id_aa_signingCertificateV2,
                        org.bouncycastle.asn1.DERSet(org.bouncycastle.asn1.ess.SigningCertificateV2(
                            org.bouncycastle.asn1.ess.ESSCertIDv2(if (wrongCertificateDigest) ByteArray(32) else
                                java.security.MessageDigest.getInstance("SHA-256").digest(certificate.encoded)),
                        )),
                    )
                    builder.setSignedAttributeGenerator(org.bouncycastle.cms.DefaultSignedAttributeTableGenerator(
                        org.bouncycastle.asn1.cms.AttributeTable(binding),
                    ))
                }
                addSignerInfoGenerator(builder.build(JcaContentSignerBuilder("SHA256withRSA").build(key.private), certificate))
            }
            val der = generator.generate(CMSProcessableByteArray(pdf.copyOfRange(0, gap) + pdf.copyOfRange(end, pdf.size)), false).encoded
            val hex = der.joinToString("") { "%02x".format(it) }.padEnd(placeholder.length, '0')
            hex.toByteArray().copyInto(pdf, gap + 1)
            return pdf
        }
        internal fun appendRevision(previous: ByteArray, objects: List<Pair<Int, String>>, trailer: String, size: Int): ByteArray {
            val out = ByteArrayOutputStream()
            out.write(previous)
            fun put(value: String) { out.write(value.toByteArray(Charsets.US_ASCII)) }
            put("\n")
            val offsets = objects.map { (id, body) -> (id to out.size()).also { put("$id 0 obj\n$body\nendobj\n") } }
            val xref = out.size()
            put("xref\n")
            offsets.forEach { (id, offset) -> put("$id 1\n%010d 00000 n \n".format(offset)) }
            val oldXref = Regex("startxref\\s+([0-9]+)").findAll(previous.toString(Charsets.US_ASCII)).last().groupValues[1]
            put("trailer\n<< /Size $size /Root 1 0 R /Prev $oldXref $trailer >>\nstartxref\n$xref\n%%EOF\n")
            return out.toByteArray()
        }

        internal fun fixture(signature: String? = null): ByteArray {
            val bodies = mutableListOf(
                "<< /Type /Catalog /Pages 2 0 R ${if (signature != null) "/AcroForm << /Fields [5 0 R] /SigFlags 3 >>" else ""} >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] >>",
                signature ?: "<< >>",
            )
            if (signature != null) bodies.add("<< /Type /Annot /Subtype /Widget /FT /Sig /T (Signature1) /V 4 0 R /Rect [0 0 0 0] >>")
            val out = ByteArrayOutputStream()
            fun put(value: String) { out.write(value.toByteArray(Charsets.US_ASCII)) }
            put("%PDF-1.4\n% synthetic\n")
            val offsets = bodies.mapIndexed { index, body -> out.size().also { put("${index + 1} 0 obj\n$body\nendobj\n") } }
            val xref = out.size()
            put("xref\n0 ${bodies.size + 1}\n0000000000 65535 f \n")
            offsets.forEach { put("%010d 00000 n \n".format(it)) }
            put("trailer\n<< /Size ${bodies.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
            return out.toByteArray()
        }
    }
}

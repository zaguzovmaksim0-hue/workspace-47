package dev.junta.firmamobile.afirma.servlet

import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDSignatureField
import dev.junta.firmamobile.signing.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.ZoneOffset
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.cms.CMSAttributes
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.*
import org.junit.Test

/** Real PDFBox/CMS signatures, synthetic non-exportable RSA only. */
class NativePadesEngineTest {
    @Test fun pdfSignaturesBindEverySupportedDigestAndSubfilterWithoutExportingTheKey() {
        val identity = nonExportableSyntheticIdentity()
        val clock = Clock.fixed(identity.identity.certificate.notBefore.toInstant().plusSeconds(60), ZoneOffset.UTC)
        val engine = NativePadesEngine(clock = clock)
        val input = nativePadesFixture()
        for (algorithm in SigningAlgorithm.entries) for (filter in listOf("ETSI.CAdES.detached", "adbe.pkcs7.detached")) {
            val options = NativePadesOptions(filter, "Synthetic reason", "Test place", "fixture@example.invalid")
            val signed = sign(engine, input, identity.identity, algorithm, options)
            try {
                assertTrue(engine.verify(signed, input, identity.identity.certificate, algorithm, options))
                assertArrayEquals(input, signed.copyOf(input.size))
                PDDocument.load(signed).use { pdf ->
                    assertEquals(1, pdf.numberOfPages)
                    val signature = pdf.signatureDictionaries.single()
                    assertEquals(filter, signature.subFilter)
                    assertEquals(clock.millis() / 1000, signature.signDate.timeInMillis / 1000)
                    val padded = signature.contents
                    val cmsBytes = ASN1InputStream(padded).use { it.readObject().encoded }
                    val envelope = CMSSignedData(CMSProcessableByteArray(signature.getSignedContent(signed)), cmsBytes)
                    val signer = envelope.signerInfos.signers.single()
                    assertNull(signer.signedAttributes[CMSAttributes.signingTime])
                    assertTrue(signer.verify(JcaSimpleSignerInfoVerifierBuilder().setProvider(BouncyCastleProvider()).build(identity.identity.certificate)))
                    assertEquals(0f, pdf.signatureFields.single().widgets.single().rectangle.width)
                    padded.fill(0); cmsBytes.fill(0)
                }
            } finally { signed.fill(0) }
        }
        assertEquals(0, identity.encodedReads.get())
        assertArrayEquals(nativePadesFixture(), input)
    }

    @Test fun wrongContentCertificateDigestAndTamperedPdfAreRejected() {
        val identity = freshSyntheticIdentity(); val other = freshSyntheticIdentity()
        val engine = NativePadesEngine(clock = Clock.fixed(identity.certificate.notBefore.toInstant().plusSeconds(60), ZoneOffset.UTC))
        val original = nativePadesFixture(); val signed = sign(engine, original, identity)
        val wrongSource = original.copyOf().also { it[15] = (it[15].toInt() xor 1).toByte() }
        assertFalse(engine.verify(signed, wrongSource, identity.certificate, SigningAlgorithm.SHA256_WITH_RSA))
        assertFalse(engine.verify(signed, original, other.certificate, SigningAlgorithm.SHA256_WITH_RSA))
        assertFalse(engine.verify(signed, original, identity.certificate, SigningAlgorithm.SHA512_WITH_RSA))
        val modified = signed.copyOf().also { it[15] = (it[15].toInt() xor 1).toByte() }
        assertFalse(engine.verify(modified, original, identity.certificate, SigningAlgorithm.SHA256_WITH_RSA))
        assertFalse(engine.verify(signed + byteArrayOf(10), original, identity.certificate, SigningAlgorithm.SHA256_WITH_RSA))
        signed.fill(0); modified.fill(0); wrongSource.fill(0)
    }

    @Test fun ciphertextMalformedEmptyAndOversizedSourcesDoNotProduceSignatures() {
        val identity = freshSyntheticIdentity(); val engine = NativePadesEngine()
        val encrypted = ByteArrayOutputStream().use { out ->
            PDDocument().use { doc -> doc.addPage(PDPage()); doc.protect(StandardProtectionPolicy("owner-test", "user-test", AccessPermission())); doc.save(out) }
            out.toByteArray()
        }
        for (input in listOf(byteArrayOf(), "not a PDF".toByteArray(), "%PDF-1.7 broken".toByteArray(), encrypted, ByteArray(524_289))) {
            assertTrue(engine.sign(input, identity, SigningAlgorithm.SHA256_WITH_RSA) is LocalSignatureResult.Failure)
        }
    }

    @Test fun existingSignaturesCertificationAndUnselectedSignatureFieldsAreNotSilentlyChanged() {
        val identity = freshSyntheticIdentity()
        val engine = NativePadesEngine(clock = Clock.fixed(identity.certificate.notBefore.toInstant().plusSeconds(60), ZoneOffset.UTC))
        val input = nativePadesFixture(); val signed = sign(engine, input, identity)
        assertTrue(engine.sign(signed, identity, SigningAlgorithm.SHA256_WITH_RSA) is LocalSignatureResult.Failure)
        for (kind in listOf("permission", "signature-field", "xfa")) {
            val altered = ByteArrayOutputStream().use { out ->
                PDDocument.load(input).use { doc ->
                    when (kind) {
                        "permission" -> doc.documentCatalog.cosObject.setItem(COSName.PERMS, COSDictionary())
                        "signature-field" -> {
                            val form = PDAcroForm(doc); doc.documentCatalog.acroForm = form; form.fields = listOf(PDSignatureField(form))
                        }
                        else -> { val form = PDAcroForm(doc); doc.documentCatalog.acroForm = form; form.cosObject.setString(COSName.XFA, "unsupported XFA") }
                    }
                    doc.save(out)
                };out.toByteArray()
            }
            assertTrue(kind, engine.sign(altered, identity, SigningAlgorithm.SHA256_WITH_RSA) is LocalSignatureResult.Failure)
            altered.fill(0)
        }
        signed.fill(0)
    }

    @Test fun outputBudgetIsEnforcedBeforeReturningAnyPdf() {
        val identity = freshSyntheticIdentity()
        val clock = Clock.fixed(identity.certificate.notBefore.toInstant().plusSeconds(60), ZoneOffset.UTC)
        val result = NativePadesEngine(clock = clock, maxOutputBytes = 1000).sign(nativePadesFixture(), identity, SigningAlgorithm.SHA256_WITH_RSA)
        assertTrue(result is LocalSignatureResult.Failure)
    }

    @Test fun standaloneCadesStillHasItsOriginalTimeAttribute() {
        val identity = freshSyntheticIdentity(); val clock = Clock.fixed(identity.certificate.notBefore.toInstant().plusSeconds(60), ZoneOffset.UTC)
        val result = NativeCadesEngine(clock = clock).sign("unchanged CMS".toByteArray(), identity, SigningAlgorithm.SHA256_WITH_RSA, true) as LocalSignatureResult.Success
        result.signature.use { it.withBytes { bytes -> assertNotNull(CMSSignedData(bytes).signerInfos.signers.single().signedAttributes[CMSAttributes.signingTime]) } }
    }

    private fun sign(engine: NativePadesEngine, input: ByteArray, identity: dev.junta.firmamobile.certificate.UnlockedIdentity,
        algorithm: SigningAlgorithm = SigningAlgorithm.SHA256_WITH_RSA, options: NativePadesOptions = NativePadesOptions()): ByteArray {
        val result = engine.sign(input, identity, algorithm, options)
        assertTrue(result.toString(), result is LocalSignatureResult.Success)
        return (result as LocalSignatureResult.Success).signature.use { it.withBytes { bytes -> bytes.copyOf() } }
    }
}

/** Stable simple PDF with an actual content stream; not a government document. */
internal fun nativePadesFixture(): ByteArray {
    val bodies = listOf(
        "<< /Type /Catalog /Pages 2 0 R >>",
        "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Contents 4 0 R >>",
        "0 0 1 RG 2 w 20 20 260 160 re S\n".let { "<< /Length ${it.length} >>\nstream\n${it}endstream" },
    )
    val out = ByteArrayOutputStream(); fun put(text: String) { out.write(text.toByteArray(Charsets.US_ASCII)) }
    put("%PDF-1.4\n% synthetic native PAdES test\n"); val offsets = mutableListOf<Int>()
    bodies.forEachIndexed { index, body -> offsets.add(out.size()); put("${index+1} 0 obj\n$body\nendobj\n") }
    val xref = out.size(); put("xref\n0 5\n0000000000 65535 f \n")
    for (offset in offsets) put("%010d 00000 n \n".format(java.util.Locale.ROOT, offset))
    put("trailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
    return out.toByteArray()
}

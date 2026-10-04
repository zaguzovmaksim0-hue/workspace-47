package dev.junta.firmamobile.afirma.servlet

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions
import dev.junta.firmamobile.certificate.UnlockedIdentity
import dev.junta.firmamobile.signing.freshSyntheticIdentity
import dev.junta.firmamobile.signing.nonExportableSyntheticIdentity
import dev.junta.firmamobile.signing.LocalSignatureResult
import dev.junta.firmamobile.signing.SigningAlgorithm
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.ZoneOffset
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.*
import org.junit.Test

/** Real incremental PDFs/CMS and generated keys, never a user identity. */
class NativePadesCoSignTest {
    @Test fun secondSignerRetainsFirstBytesCertificateAndCryptographicSignature() {
        val first = freshSyntheticIdentity(); val second = nonExportableSyntheticIdentity()
        val engine = NativePadesEngine(clock = clock(second.identity))
        val original = nativePadesFixture()
        val one = signed(engine, original, first)
        val before = history(one)
        val options = NativePadesOptions("adbe.pkcs7.detached", "Second approval")
        val two = signed(engine, one, second.identity, SigningAlgorithm.SHA512_WITH_RSA, options, true)
        try {
            assertArrayEquals(one, two.copyOf(one.size))
            assertEquals(before, history(two).dropLast(1))
            assertEquals(2, history(two).size)
            assertTrue(engine.verify(two, one, second.identity.certificate, SigningAlgorithm.SHA512_WITH_RSA, options))
            verifyAllIndependently(two)
            assertEquals(0, second.encodedReads.get())
        } finally { one.fill(0); two.fill(0) }
    }

    @Test fun threeIncrementalApprovalsRemainDistinctEvenWithTheSameCertificate() {
        val key = freshSyntheticIdentity(); val engine = NativePadesEngine(clock = clock(key))
        val first = signed(engine, nativePadesFixture(), key)
        val second = signed(engine, first, key)
        val third = signed(engine, second, key)
        try {
            assertEquals(3, history(third).size)
            assertEquals(history(second), history(third).dropLast(1))
            assertArrayEquals(second, third.copyOf(second.size))
            verifyAllIndependently(third)
        } finally { first.fill(0); second.fill(0); third.fill(0) }
    }

    @Test fun inspectingHistoryNeverErasesTheLoadedSignatureDictionary() {
        val key = freshSyntheticIdentity(); val engine = NativePadesEngine(clock = clock(key))
        val input = signed(engine, nativePadesFixture(), key)
        PDDocument.load(input).use { document ->
            val before = document.signatureDictionaries.single().contents.copyOf()
            assertNotNull(NativePdfSignatureHistory.inspect(input, document))
            assertArrayEquals(before, document.signatureDictionaries.single().contents)
            assertNotNull(NativePdfSignatureHistory.inspect(input, document))
            before.fill(0)
        }
        input.fill(0)
    }

    @Test fun explicitCoSignRequiresAtLeastOneExistingValidSignature() {
        val key = freshSyntheticIdentity(); val engine = NativePadesEngine(clock = clock(key))
        assertTrue(engine.sign(nativePadesFixture(), key, SigningAlgorithm.SHA256_WITH_RSA,
            requireExistingSignature = true) is LocalSignatureResult.Failure)
    }

    @Test fun alteredOriginalBytesAndOldSignatureContainersCannotBeApprovedAgain() {
        val key = freshSyntheticIdentity(); val engine = NativePadesEngine(clock = clock(key))
        val one = signed(engine, nativePadesFixture(), key)
        val contentChanged = one.copyOf().also { it[15] = (it[15].toInt() xor 1).toByte() }
        val signatureChanged = one.copyOf()
        PDDocument.load(one).use { document ->
            val offset = document.signatureDictionaries.single().byteRange[1] + 50
            signatureChanged[offset] = if (signatureChanged[offset] == 48.toByte()) 49 else 48
        }
        for (bad in listOf(contentChanged, signatureChanged)) {
            assertTrue(engine.sign(bad, key, SigningAlgorithm.SHA256_WITH_RSA) is LocalSignatureResult.Failure)
            bad.fill(0)
        }
        one.fill(0)
    }

    @Test fun unsignedBytesOrAnUnsignedIncrementalRevisionAreRejected() {
        val key = freshSyntheticIdentity(); val engine = NativePadesEngine(clock = clock(key))
        val one = signed(engine, nativePadesFixture(), key)
        val unsignedRevision = ByteArrayOutputStream().use { output ->
            PDDocument.load(one).use { document ->
                document.documentInformation.title = "Unsigned changed metadata"
                document.documentInformation.cosObject.isNeedToBeUpdated = true
                document.saveIncremental(output)
            }
            output.toByteArray()
        }
        for (bad in listOf(one + byteArrayOf(10), unsignedRevision)) {
            assertTrue(engine.sign(bad, key, SigningAlgorithm.SHA256_WITH_RSA) is LocalSignatureResult.Failure)
            bad.fill(0)
        }
        one.fill(0)
    }

    @Test fun compatibleOtherCmsProducerMayHaveItsOwnClaimedTimeAttribute() {
        val key = freshSyntheticIdentity(); val engine = NativePadesEngine(clock = clock(key))
        val input = independentlySigned(key)
        assertEquals(1, history(input).size)
        val output = signed(engine, input, key, requireExisting = true)
        try { assertEquals(2, history(output).size); verifyAllIndependently(output) }
        finally { input.fill(0); output.fill(0) }
    }

    @Test fun certifiedOrFieldLockedSignaturesRemainUnsupportedEvenWhenCryptographicallyValid() {
        val key = freshSyntheticIdentity(); val engine = NativePadesEngine(clock = clock(key))
        for (constraint in listOf("certification", "reference", "field-lock")) {
            val input = independentlySigned(key, constraint)
            verifyAllIndependently(input) // Rejection is policy, not broken test CMS.
            assertTrue(constraint, engine.sign(input, key, SigningAlgorithm.SHA256_WITH_RSA) is LocalSignatureResult.Failure)
            input.fill(0)
        }
    }

    @Test fun wrongRequestedNewIdentityDigestAndChangedPriorMetadataDoNotVerify() {
        val key = freshSyntheticIdentity(); val other = freshSyntheticIdentity(); val engine = NativePadesEngine(clock = clock(key))
        val one = signed(engine, nativePadesFixture(), key); val two = signed(engine, one, key)
        assertFalse(engine.verify(two, one, other.certificate, SigningAlgorithm.SHA256_WITH_RSA))
        assertFalse(engine.verify(two, one, key.certificate, SigningAlgorithm.SHA512_WITH_RSA))
        assertFalse(engine.verify(two + byteArrayOf(10), one, key.certificate, SigningAlgorithm.SHA256_WITH_RSA))
        assertTrue(engine.verify(two, one, key.certificate, SigningAlgorithm.SHA256_WITH_RSA))
        one.fill(0); two.fill(0)
    }

    @Test fun hidingPreviousSignatureFieldsDoesNotTurnTheDocumentIntoAnUnsignedSource() {
        val key = freshSyntheticIdentity(); val engine = NativePadesEngine(clock = clock(key))
        val one = signed(engine, nativePadesFixture(), key)
        val hidden = ByteArrayOutputStream().use { output ->
            PDDocument.load(one).use { document ->
                document.documentCatalog.cosObject.removeItem(COSName.ACRO_FORM)
                document.documentCatalog.cosObject.isNeedToBeUpdated = true
                document.saveIncremental(output)
            }
            output.toByteArray()
        }
        PDDocument.load(hidden).use { document ->
            assertTrue("Fixture really hides the old field", document.signatureFields.isEmpty())
            assertNull(NativePdfSignatureHistory.inspect(hidden, document))
        }
        assertTrue(engine.sign(hidden, key, SigningAlgorithm.SHA256_WITH_RSA) is LocalSignatureResult.Failure)
        one.fill(0); hidden.fill(0)
    }

    @Test fun anUnsignedEditedPdfIsNotMistakenForAHiddenSignedRevision() {
        val key = freshSyntheticIdentity(); val engine = NativePadesEngine(clock = clock(key))
        val edited = ByteArrayOutputStream().use { output ->
            PDDocument.load(nativePadesFixture()).use { document ->
                document.documentInformation.title = "Unsigned local draft"
                document.documentInformation.cosObject.isNeedToBeUpdated = true
                document.saveIncremental(output)
            }
            output.toByteArray()
        }
        val result = signed(engine, edited, key)
        assertTrue(engine.verify(result, edited, key.certificate, SigningAlgorithm.SHA256_WITH_RSA))
        result.fill(0); edited.fill(0)
    }

    @Test fun inputAndOutputBudgetsAreStillAppliedToSequentialSigning() {
        val key = freshSyntheticIdentity(); val engine = NativePadesEngine(clock = clock(key))
        val one = signed(engine, nativePadesFixture(), key)
        assertTrue(NativePadesEngine(clock = clock(key), maxInputBytes = one.size - 1)
            .sign(one, key, SigningAlgorithm.SHA256_WITH_RSA) is LocalSignatureResult.Failure)
        assertTrue(NativePadesEngine(clock = clock(key), maxOutputBytes = one.size + 1000)
            .sign(one, key, SigningAlgorithm.SHA256_WITH_RSA) is LocalSignatureResult.Failure)
        one.fill(0)
    }

    private fun independentlySigned(key: UnlockedIdentity, constraint: String? = null): ByteArray {
        val out = ByteArrayOutputStream()
        PDDocument.load(nativePadesFixture()).use { document ->
            val signature = PDSignature().apply {
                setFilter(PDSignature.FILTER_ADOBE_PPKLITE); setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED)
                signDate = java.util.Calendar.getInstance().apply { timeInMillis = clock(key).millis() }
            }
            if (constraint == "reference") signature.cosObject.setItem(COSName.getPDFName("Reference"), COSArray())
            SignatureOptions().use { options ->
                options.setPreferredSignatureSize(16_384); document.addSignature(signature, options)
                if (constraint == "certification") document.documentCatalog.cosObject.setItem(COSName.PERMS,
                    COSDictionary().apply { setItem(COSName.getPDFName("DocMDP"), signature) })
                if (constraint == "field-lock") document.signatureFields.single().cosObject.setItem(COSName.getPDFName("Lock"), COSDictionary())
                val external = document.saveIncrementalForExternalSigning(out)
                val bytes = external.content.use { it.readBytes() }
                try {
                    val result = NativeCadesEngine(clock = clock(key), maxInputBytes = 2_097_152)
                        .sign(bytes, key, SigningAlgorithm.SHA256_WITH_RSA, true) as LocalSignatureResult.Success
                    result.signature.use { it.withBytes(external::setSignature) }
                } finally { bytes.fill(0) }
            }
        }
        return out.toByteArray()
    }
    private fun history(bytes: ByteArray) = PDDocument.load(bytes).use { document ->
        checkNotNull(NativePdfSignatureHistory.inspect(bytes, document)) { "History rejected the fixture" }
    }
    private fun verifyAllIndependently(pdf: ByteArray) {
        PDDocument.load(pdf).use { document ->
            for (signature in document.signatureDictionaries) {
                val der = ASN1InputStream(signature.contents).use { it.readObject().encoded }
                val cms = CMSSignedData(CMSProcessableByteArray(signature.getSignedContent(pdf)), der)
                val signer = cms.signerInfos.signers.single()
                val certificate = cms.certificates.getMatches(null).filter(signer.sid::match).single()
                assertTrue(signer.verify(JcaSimpleSignerInfoVerifierBuilder().setProvider(BouncyCastleProvider()).build(certificate)))
            }
        }
    }
    private fun clock(key: UnlockedIdentity) = Clock.fixed(key.certificate.notBefore.toInstant().plusSeconds(60), ZoneOffset.UTC)
    private fun signed(engine: NativePadesEngine, input: ByteArray, key: UnlockedIdentity,
        algorithm: SigningAlgorithm = SigningAlgorithm.SHA256_WITH_RSA, options: NativePadesOptions = NativePadesOptions(),
        requireExisting: Boolean = false): ByteArray {
        val result = engine.sign(input, key, algorithm, options, requireExisting)
        assertTrue("Signing failed: $result", result is LocalSignatureResult.Success)
        return (result as LocalSignatureResult.Success).signature.use { it.withBytes { bytes -> bytes.copyOf() } }
    }
}

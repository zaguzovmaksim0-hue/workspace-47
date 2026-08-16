package dev.junta.firmamobile.signing

import dev.junta.firmamobile.browser.NavigationId
import dev.junta.firmamobile.network.TrustedOrigin
import java.net.URI
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccedaPadesAdapterTest {
    private val identity = syntheticIdentity()
    private val clock = Clock.fixed(Instant.parse("2030-01-02T03:04:05Z"), ZoneOffset.UTC)
    private val adapter = AccedaPadesAdapter(clock)

    private val samplePdf = (
        "%PDF-1.4\n" +
            "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n" +
            "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n" +
            "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>\nendobj\n" +
            "xref\n0 4\n0000000000 65535 f \n0000000009 00000 n \n0000000058 00000 n \n0000000115 00000 n \n" +
            "trailer\n<< /Size 4 /Root 1 0 R >>\nstartxref\n186\n%%EOF\n"
        ).toByteArray(Charsets.US_ASCII)

    private val samplePdfWithExistingAcroForm = (
        "%PDF-1.4\n" +
            "1 0 obj\n<< /Type /Catalog /Pages 2 0 R /AcroForm 4 0 R >>\nendobj\n" +
            "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n" +
            "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Annots [ 5 0 R ] >>\nendobj\n" +
            "4 0 obj\n<< /Fields [ 5 0 R ] /SigFlags 1 >>\nendobj\n" +
            "5 0 obj\n<< /FT /Tx /Type /Annot /Subtype /Widget /Rect [ 10 10 100 30 ] /T (ExistingTextField) >>\nendobj\n" +
            "xref\n0 6\n0000000000 65535 f \n0000000009 00000 n \n0000000074 00000 n \n0000000131 00000 n \n0000000219 00000 n \n0000000277 00000 n \n" +
            "trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n388\n%%EOF\n"
        ).toByteArray(Charsets.US_ASCII)

    @Test
    fun producesSha1PadesDetachedSignatureForPdfPayload() = runTest {
        val document = samplePdf.copyOf()
        val request = request(document.copyOf())
        val prepared = adapter.prepare(request, identity.chain) as ProtocolPrepareResult.Success
        val local = prepared.preSign.withBytesToSign { signedAttributes ->
            JcaLocalSignatureEngine().sign(
                signedAttributes,
                identity,
                SigningAlgorithm.SHA1_WITH_RSA,
            )
        } as LocalSignatureResult.Success

        val completed = adapter.complete(request, prepared.preSign, local.signature)
            as ProtocolCompletionResult.Success
        val result = completed.signature.withBytes { it.copyOf() }
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(identity.certificate.encoded)

        assertTrue(result.isNotEmpty())
        assertTrue(result.size > document.size)
        assertTrue(
            PadesDetachedCodec.validate(
                signatureDocument = result,
                expectedCertificateFingerprint = fingerprint,
                signingAlgorithm = SigningAlgorithm.SHA1_WITH_RSA,
            ),
        )

        val resultText = String(result, Charsets.ISO_8859_1)
        assertTrue(resultText.contains("/AcroForm"))
        assertTrue(resultText.contains("/Fields"))
        assertTrue(resultText.contains("/SigFlags 3"))
        assertTrue(resultText.contains("/FT /Sig"))
        assertTrue(resultText.contains("/Widget"))
        assertTrue(resultText.contains("/Annots"))
        assertTrue(resultText.contains("/Type /Sig"))
        assertTrue(resultText.contains("/Filter /Adobe.PPKLite"))
        assertTrue(resultText.contains("/SubFilter /ETSI.CAdES.detached"))
        assertTrue(resultText.contains("/ByteRange [ 0 "))

        completed.signature.close()
        local.signature.close()
        prepared.preSign.close()
        request.close()
        result.fill(0)
        document.fill(0)
        fingerprint.fill(0)
    }

    @Test
    fun supportsPdfWithExistingAcroFormAndAnnots() = runTest {
        val document = samplePdfWithExistingAcroForm.copyOf()
        val request = request(document.copyOf())
        val prepared = adapter.prepare(request, identity.chain) as ProtocolPrepareResult.Success
        val local = prepared.preSign.withBytesToSign { signedAttributes ->
            JcaLocalSignatureEngine().sign(
                signedAttributes,
                identity,
                SigningAlgorithm.SHA1_WITH_RSA,
            )
        } as LocalSignatureResult.Success

        val completed = adapter.complete(request, prepared.preSign, local.signature)
            as ProtocolCompletionResult.Success
        val result = completed.signature.withBytes { it.copyOf() }
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(identity.certificate.encoded)

        assertTrue(result.isNotEmpty())
        assertTrue(
            PadesDetachedCodec.validate(
                signatureDocument = result,
                expectedCertificateFingerprint = fingerprint,
                signingAlgorithm = SigningAlgorithm.SHA1_WITH_RSA,
            ),
        )

        val resultText = String(result, Charsets.ISO_8859_1)
        assertTrue(resultText.contains("/Fields [ 5 0 R"))
        assertTrue(resultText.contains("/Annots [ 5 0 R"))
        assertTrue(resultText.contains("/SigFlags 3"))

        completed.signature.close()
        local.signature.close()
        prepared.preSign.close()
        request.close()
        result.fill(0)
        document.fill(0)
        fingerprint.fill(0)
    }

    @Test
    fun rejectsOrphanSignatureDictionaryWithoutAcroform() {
        val orphanPdf = (
            "%PDF-1.4\n" +
                "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n" +
                "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n" +
                "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>\nendobj\n" +
                "4 0 obj\n<< /Type /Sig /Filter /Adobe.PPKLite /SubFilter /ETSI.CAdES.detached /ByteRange [ 0 0000000300 0000000400 0000000100 ] /Contents <0000> >>\nendobj\n" +
                "xref\n0 5\n0000000000 65535 f \n0000000009 00000 n \n0000000058 00000 n \n0000000115 00000 n \n0000000193 00000 n \n" +
                "trailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n340\n%%EOF\n"
            ).toByteArray(Charsets.US_ASCII)

        assertFalse(PadesDetachedCodec.validate(orphanPdf))
    }

    @Test
    fun rejectsMalformedXrefAndMissingAcroform() {
        assertFalse(PadesDetachedCodec.validate(ByteArray(0)))
        assertFalse(PadesDetachedCodec.validate("not a valid pdf".toByteArray()))

        val malformedXrefPdf = (
            "%PDF-1.4\n" +
                "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n" +
                "trailer\n<< /Size 2 /Root 1 0 R >>\nstartxref\n99999\n%%EOF\n"
            ).toByteArray(Charsets.US_ASCII)
        assertFalse(PadesDetachedCodec.validate(malformedXrefPdf))
    }

    @Test
    fun rejectsEveryContractMismatchAndInvalidPdfPayload() = runTest {
        assertFailure(request(profileId = "other-profile"), SigningErrorCode.UNSUPPORTED_PROTOCOL)
        assertFailure(request(origin = "https://sede.administracionespublicas.gob.es.evil.example"), SigningErrorCode.UNSUPPORTED_PROTOCOL)
        assertFailure(request(algorithm = SigningAlgorithm.SHA256_WITH_RSA), SigningErrorCode.UNSUPPORTED_PROTOCOL)
        assertFailure(request(algorithm = SigningAlgorithm.SHA512_WITH_RSA), SigningErrorCode.UNSUPPORTED_PROTOCOL)
        assertFailure(request(format = SigningFormat.CADES), SigningErrorCode.UNSUPPORTED_PROTOCOL)
        assertFailure(request(format = SigningFormat.XADES), SigningErrorCode.UNSUPPORTED_PROTOCOL)
        assertFailure(request(extraProperties = "format=CAdES"), SigningErrorCode.PROTOCOL_FAILED)
        assertFailure(request(ByteArray(0)), SigningErrorCode.PROTOCOL_FAILED)
        assertFailure(request("not a pdf document".toByteArray()), SigningErrorCode.PROTOCOL_FAILED)
    }

    @Test
    fun preSignStateZeroizesBuffersOnClose() {
        val dummyTemplate = byteArrayOf(1, 2, 3, 4)
        val dummyRange = byteArrayOf(5, 6, 7, 8)
        val dummyCms = byteArrayOf(9, 10, 11, 12)
        val dummyFp = byteArrayOf(13, 14, 15, 16)

        val state = PadesPreSignState(
            pdfTemplate = dummyTemplate,
            contentsStartOffset = 0,
            contentsHexLength = 4,
            byteRangeData = dummyRange,
            placeholderCms = dummyCms,
            signingCertificateFingerprint = dummyFp,
        )

        state.close()
        assertTrue(dummyTemplate.all { it == 0.toByte() })
        assertTrue(dummyRange.all { it == 0.toByte() })
        assertTrue(dummyCms.all { it == 0.toByte() })
        assertTrue(dummyFp.all { it == 0.toByte() })
    }

    private suspend fun assertFailure(request: NormalizedSignRequest, code: SigningErrorCode) {
        val result = adapter.prepare(request, identity.chain) as ProtocolPrepareResult.Failure
        assertEquals(code, result.code)
        request.close()
    }

    private fun request(
        payload: ByteArray = samplePdf.copyOf(),
        extraProperties: String = AccedaPadesAdapter.EXPECTED_EXTRA_PROPERTIES,
        protocolId: SigningProtocolId = AccedaPadesAdapter.ID,
        profileId: String = AccedaPadesAdapter.PROFILE_ID,
        profileVersion: Int = AccedaPadesAdapter.PROFILE_VERSION,
        origin: String = AccedaPadesAdapter.INITIATOR_ORIGIN,
        algorithm: SigningAlgorithm = SigningAlgorithm.SHA1_WITH_RSA,
        format: SigningFormat = SigningFormat.PADES,
    ) = NormalizedSignRequest(
        requestId = UUID.fromString("123e4567-e89b-42d3-a456-426614174000"),
        protocolId = protocolId,
        context = SigningContext(
            profileId = profileId,
            profileVersion = profileVersion,
            origin = URI(origin).let { TrustedOrigin(it.scheme, it.host, 443) },
            navigationId = NavigationId("123e4567-e89b-42d3-a456-426614174001"),
            navigationEpoch = 7,
            observedAt = clock.instant(),
        ),
        algorithm = algorithm,
        format = format,
        safeDescription = AccedaPadesAdapter.SAFE_DESCRIPTION,
        payload = MiniAppletPayloadCodec.encode(payload, extraProperties),
    ).also { payload.fill(0) }
}

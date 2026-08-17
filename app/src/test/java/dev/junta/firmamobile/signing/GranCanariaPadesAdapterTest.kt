package dev.junta.firmamobile.signing

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import dev.junta.firmamobile.browser.NavigationId
import dev.junta.firmamobile.network.TrustedOrigin
import java.io.ByteArrayOutputStream
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GranCanariaPadesAdapterTest {
    private val identity = syntheticIdentity()
    private val clock = Clock.fixed(Instant.parse("2030-01-02T03:04:05Z"), ZoneOffset.UTC)
    private val adapter = GranCanariaPadesAdapter(clock)

    @Test
    fun exactContractIdentifiersArePinned() {
        assertEquals("gran-canaria-sede-electronica", GranCanariaPadesAdapter.PROFILE_ID)
        assertEquals("gran-canaria-direct-pades-v1", GranCanariaPadesAdapter.ID.value)
        assertEquals("headless=true\nfilters=nonexpired:", GranCanariaPadesAdapter.EXPECTED_EXTRA_PROPERTIES)
        assertEquals(SigningFormat.PADES, SigningFormat.valueOf("PADES"))
    }

    @Test
    fun producesSha512PadesForTheExactPublicContract() = runTest {
        val sourcePdf = syntheticPdf()
        val request = request(sourcePdf.copyOf())
        val prepared = adapter.prepare(request, identity.chain) as ProtocolPrepareResult.Success
        val local = prepared.preSign.withBytesToSign { signedAttributes ->
            JcaLocalSignatureEngine().sign(
                signedAttributes,
                identity,
                SigningAlgorithm.SHA512_WITH_RSA,
            )
        } as LocalSignatureResult.Success
        val completed = adapter.complete(request, prepared.preSign, local.signature)
            as ProtocolCompletionResult.Success
        val signedPdf = completed.signature.withBytes { it.copyOf() }

        assertArrayEquals("%PDF-".encodeToByteArray(), signedPdf.copyOfRange(0, 5))
        PDDocument.load(signedPdf).use { document ->
            val signatures = document.signatureDictionaries
            assertEquals(1, signatures.size)
            assertEquals(PDSignature.FILTER_ADOBE_PPKLITE.name, signatures.single().filter)
            assertEquals(PDSignature.SUBFILTER_ETSI_CADES_DETACHED.name, signatures.single().subFilter)
            assertTrue(signatures.single().contents.isNotEmpty())
        }

        completed.signature.close()
        request.close()
        signedPdf.fill(0)
        sourcePdf.fill(0)
    }

    @Test
    fun rejectsContractMismatchesAndNonPdfPayload() = runTest {
        assertFailure(request(profileId = "other-profile"), SigningErrorCode.UNSUPPORTED_PROTOCOL)
        assertFailure(request(origin = "https://sede.grancanaria.com.evil.example"), SigningErrorCode.UNSUPPORTED_PROTOCOL)
        assertFailure(request(pageUrl = GranCanariaPadesAdapter.PUBLIC_START_URL), SigningErrorCode.UNSUPPORTED_PROTOCOL)
        assertFailure(request(algorithm = SigningAlgorithm.SHA256_WITH_RSA), SigningErrorCode.UNSUPPORTED_PROTOCOL)
        assertFailure(request(format = SigningFormat.CADES), SigningErrorCode.UNSUPPORTED_PROTOCOL)
        assertFailure(request(extraProperties = "headless=true\nfilters=expired:"), SigningErrorCode.PROTOCOL_FAILED)
        assertFailure(request("not a pdf".encodeToByteArray()), SigningErrorCode.PROTOCOL_FAILED)
    }

    private suspend fun assertFailure(request: NormalizedSignRequest, expected: SigningErrorCode) {
        val result = adapter.prepare(request, identity.chain) as ProtocolPrepareResult.Failure
        assertEquals(expected, result.code)
        request.close()
    }

    private fun syntheticPdf(): ByteArray = ByteArrayOutputStream().use { output ->
        PDDocument().use { document ->
            document.addPage(PDPage())
            document.save(output)
        }
        output.toByteArray()
    }

    private fun request(
        source: ByteArray = syntheticPdf(),
        extraProperties: String = GranCanariaPadesAdapter.EXPECTED_EXTRA_PROPERTIES,
        profileId: String = GranCanariaPadesAdapter.PROFILE_ID,
        origin: String = GranCanariaPadesAdapter.INITIATOR_ORIGIN,
        pageUrl: String = GranCanariaPadesAdapter.SIGNING_PAGE_URL,
        algorithm: SigningAlgorithm = SigningAlgorithm.SHA512_WITH_RSA,
        format: SigningFormat = SigningFormat.PADES,
    ): NormalizedSignRequest {
        val payload = MiniAppletPayloadCodec.encode(source, extraProperties)
        source.fill(0)
        val uri = URI(origin)
        return NormalizedSignRequest(
            requestId = UUID.fromString("123e4567-e89b-42d3-a456-426614174000"),
            protocolId = GranCanariaPadesAdapter.ID,
            context = SigningContext(
                profileId = profileId,
                profileVersion = GranCanariaPadesAdapter.PROFILE_VERSION,
                origin = TrustedOrigin(uri.scheme, uri.host, 443),
                navigationId = NavigationId("123e4567-e89b-42d3-a456-426614174001"),
                navigationEpoch = 7,
                observedAt = clock.instant(),
                pageUrl = pageUrl,
            ),
            algorithm = algorithm,
            format = format,
            safeDescription = GranCanariaPadesAdapter.SAFE_DESCRIPTION,
            payload = payload,
        )
    }
}

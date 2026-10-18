package dev.junta.firmamobile.signing

import dev.junta.firmamobile.browser.NavigationId
import dev.junta.firmamobile.network.TrustedOrigin
import java.net.URI
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniAppletPadesDocumentConsentTest {
    @Test
    fun allFourExactContractsDescribeDecodedPdfBytesAndKeepEnvelopeUntouched() {
        fixtures().forEach { fixture ->
            val envelope = MiniAppletPayloadCodec.encode(PDF, fixture.properties)
            val before = envelope.copyOf()
            request(fixture, envelope).use { request ->
                fixture.adapter.documentConsent(request).use { evidence ->
                    val document = evidence.actualDocument
                    assertNotNull(fixture.profileId, document)
                    assertEquals(SigningPayloadKind.ENVELOPE, evidence.payloadKind)
                    assertEquals(PDF.size, document!!.byteCount)
                    assertEquals(EXPECTED_PDF_SHA256, document.sha256ForDisplay())
                    assertTrue(evidence.matches(request))
                    assertFalse(evidence.toString().contains(EXPECTED_PDF_SHA256))
                    request.withPayload { assertArrayEquals(before, it) }
                }
            }
            assertTrue(envelope.all { it == 0.toByte() })
            before.fill(0)
        }
    }

    @Test
    fun noContractFieldCanBeSubstitutedToObtainDocumentEvidence() {
        fixtures().forEach { fixture ->
            val variants = listOf(
                request(fixture, protocolId = SigningProtocolId("not-the-adapter-v1")),
                request(fixture, profileId = "not-the-profile"),
                request(fixture, profileVersion = 2),
                request(fixture, origin = TrustedOrigin("https", "other.invalid", 443)),
                request(fixture, pageUrl = null),
                request(fixture, description = "untrusted\u202Ename.pdf"),
                request(fixture, algorithm = SigningAlgorithm.SHA1_WITH_RSA),
                request(fixture, format = SigningFormat.CADES),
            )
            variants.forEach { request ->
                request.use {
                    fixture.adapter.documentConsent(it).use { evidence -> assertNull(evidence.actualDocument) }
                }
            }
        }
    }

    @Test
    fun challengeDigestRawPdfAndNestedEnvelopeAreNotActualPdfEvidence() {
        fixtures().forEach { fixture ->
            val nonDocuments = listOf(
                "challenge".encodeToByteArray(), ByteArray(20), ByteArray(32), ByteArray(64),
                MiniAppletPayloadCodec.encode(PDF, fixture.properties),
            )
            val inputs = nonDocuments.map { MiniAppletPayloadCodec.encode(it, fixture.properties) } +
                listOf(PDF.copyOf(), ByteArray(0), byteArrayOf(1, 2, 3))
            inputs.forEach { payload ->
                request(fixture, payload).use { request ->
                    fixture.adapter.documentConsent(request).use { evidence -> assertNull(evidence.actualDocument) }
                }
            }
        }
    }

    @Test
    fun alteredPropertiesMalformedLengthsAndClosedBoundsNeverProduceEvidence() {
        fixtures().forEach { fixture ->
            val oversizedPdf = ByteArray(MiniAppletPadesAdapter.MAX_PDF_BYTES + 1)
                .also { "%PDF-".encodeToByteArray().copyInto(it) }
            val inputs = listOf(
                MiniAppletPayloadCodec.encode(PDF, fixture.properties + "\nfilename=\u202Etrusted.pdf"),
                MiniAppletPayloadCodec.encode(PDF, fixture.properties).also { it[7] = 0x7f },
                MiniAppletPayloadCodec.encode(PDF, fixture.properties).dropLast(1).toByteArray(),
                MiniAppletPayloadCodec.encode(oversizedPdf, fixture.properties),
                ByteArray(PendingSignRequestStore.MAX_PAYLOAD_BYTES + 1),
            )
            inputs.forEach { payload ->
                request(fixture, payload).use { request ->
                    fixture.adapter.documentConsent(request).use { evidence -> assertNull(evidence.actualDocument) }
                }
            }
        }
    }

    @Test
    fun decodedCopiesAreClearedOnSuccessAndFailureWithoutClearingTheRequest() {
        val fixture = fixtures().first()
        val envelope = MiniAppletPayloadCodec.encode(PDF, fixture.properties)
        var decoded: ByteArray? = null
        MiniAppletPayloadCodec.withDecoded(envelope) { bytes, _ -> decoded = bytes }
        assertTrue(decoded!!.all { it == 0.toByte() })
        org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            MiniAppletPayloadCodec.withDecoded(envelope) { bytes, _ ->
                decoded = bytes
                error("synthetic failure")
            }
        }
        assertTrue(decoded!!.all { it == 0.toByte() })
        request(fixture, envelope).use { request ->
            fixture.adapter.documentConsent(request).use { assertNotNull(it.actualDocument) }
        }
    }

    @Test
    fun existingChallengeAdapterDoesNotAcquireDocumentSemanticsFromPadesFormat() {
        val adapter = LocalCadesDetachedAdapter()
        request(fixtures().first(), format = SigningFormat.PADES).use { request ->
            adapter.documentConsent(request).use { assertNull(it.actualDocument) }
        }
    }

    private fun request(
        fixture: Fixture,
        payload: ByteArray = MiniAppletPayloadCodec.encode(PDF, fixture.properties),
        protocolId: SigningProtocolId = fixture.adapter.id,
        profileId: String = fixture.profileId,
        profileVersion: Int = 1,
        origin: TrustedOrigin = TrustedOrigin("https", URI(fixture.origin).host, 443),
        pageUrl: String? = fixture.pageUrl,
        description: String = fixture.description,
        algorithm: SigningAlgorithm = fixture.algorithm,
        format: SigningFormat = SigningFormat.PADES,
    ) = NormalizedSignRequest(
        requestId = UUID.randomUUID(), protocolId = protocolId,
        context = SigningContext(
            profileId = profileId, profileVersion = profileVersion, origin = origin,
            navigationId = NavigationId("synthetic-document-navigation"),
            observedAt = Instant.parse("2026-10-18T18:00:00Z"), pageUrl = pageUrl,
        ),
        algorithm = algorithm, format = format, safeDescription = description, payload = payload,
    )

    private fun fixtures(): List<Fixture> = listOf(
        Fixture(GranCanariaPadesAdapter(), GranCanariaPadesAdapter.PROFILE_ID,
            GranCanariaPadesAdapter.INITIATOR_ORIGIN, GranCanariaPadesAdapter.SIGNING_PAGE_URL,
            GranCanariaPadesAdapter.SAFE_DESCRIPTION, GranCanariaPadesAdapter.EXPECTED_EXTRA_PROPERTIES,
            SigningAlgorithm.SHA512_WITH_RSA),
        Fixture(FuerteventuraPadesAdapter(), FuerteventuraPadesAdapter.PROFILE_ID,
            FuerteventuraPadesAdapter.INITIATOR_ORIGIN, FuerteventuraPadesAdapter.SIGNING_PAGE_URL,
            FuerteventuraPadesAdapter.SAFE_DESCRIPTION, FuerteventuraPadesAdapter.EXPECTED_EXTRA_PROPERTIES,
            SigningAlgorithm.SHA256_WITH_RSA),
        Fixture(TransparenciaPadesAdapter(), TransparenciaPadesAdapter.PROFILE_ID,
            TransparenciaPadesAdapter.INITIATOR_ORIGIN,
            TransparenciaPadesAdapter.SIGNING_PAGE_PREFIX + "idProc=133628&idBorr=1&idAmb=101524",
            TransparenciaPadesAdapter.SAFE_DESCRIPTION, TransparenciaPadesAdapter.EXPECTED_EXTRA_PROPERTIES,
            SigningAlgorithm.SHA512_WITH_RSA),
        Fixture(MinecoPadesAdapter(), MinecoPadesAdapter.PROFILE_ID,
            MinecoPadesAdapter.INITIATOR_ORIGIN, MinecoPadesAdapter.SIGNING_PAGE_URL,
            MinecoPadesAdapter.SAFE_DESCRIPTION, MinecoPadesAdapter.EXPECTED_EXTRA_PROPERTIES,
            SigningAlgorithm.SHA512_WITH_RSA),
    )

    private data class Fixture(
        val adapter: SigningProtocolAdapter, val profileId: String, val origin: String,
        val pageUrl: String, val description: String, val properties: String, val algorithm: SigningAlgorithm,
    )

    private companion object {
        val PDF = "%PDF-1.7\nsynthetic consent document\n%%EOF\n".encodeToByteArray()
        const val EXPECTED_PDF_SHA256 = "e5fe40f17d151fafa3c4d3142e359b308694d512ff3227d48cfd6fb092e7cc8e"
    }
}

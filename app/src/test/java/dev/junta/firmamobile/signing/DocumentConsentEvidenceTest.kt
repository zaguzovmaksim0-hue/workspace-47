package dev.junta.firmamobile.signing

import dev.junta.firmamobile.browser.NavigationId
import dev.junta.firmamobile.network.TrustedOrigin
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentConsentEvidenceTest {
    @Test
    fun envelopeMetadataDescribesOnlyActualDocumentBytes() {
        val document = "%PDF-1.7\nsynthetic consent document\n%%EOF\n".encodeToByteArray()
        val envelope = MiniAppletPayloadCodec.encode(document, "headless=true")
        val request = request(envelope)
        request.use {
            DocumentConsentEvidence.fromEnvelope(request, envelope, document).use { evidence ->
                assertEquals(SigningPayloadKind.ENVELOPE, evidence.payloadKind)
                val metadata = evidence.actualDocument
                assertNotNull(metadata)
                assertEquals(document.size, metadata!!.byteCount)
                assertEquals("e5fe40f17d151fafa3c4d3142e359b308694d512ff3227d48cfd6fb092e7cc8e", metadata.sha256ForDisplay())
                assertFalse(sha256(envelope) == metadata.sha256ForDisplay())
                assertTrue(evidence.matches(request))
                assertFalse(evidence.toString().contains(metadata.sha256ForDisplay()!!))
                assertFalse(metadata.toString().contains(metadata.sha256ForDisplay()!!))
            }
        }
    }

    @Test
    fun sameFieldsAndRequestIdCannotTransferEvidenceToAnotherRequest() {
        val document = "%PDF-synthetic".encodeToByteArray()
        val envelope = MiniAppletPayloadCodec.encode(document, "fixed=true")
        request(envelope).use { original ->
            DocumentConsentEvidence.fromEnvelope(original, envelope, document).use { evidence ->
                request(envelope.copyOf()).use { copy -> assertFalse(evidence.matches(copy)) }
                assertTrue(evidence.matches(original))
            }
        }
    }

    @Test
    fun changesToAnyBoundFieldOrEnvelopeInvalidateEvidence() {
        val document = "%PDF-synthetic".encodeToByteArray()
        val envelope = MiniAppletPayloadCodec.encode(document, "fixed=true")
        request(envelope).use { original ->
            DocumentConsentEvidence.fromEnvelope(original, envelope, document).use { evidence ->
                // Synthetic memory-tamper only: no constructor/setter can reassign request ownership.
                val variants = listOf(
                    "requestId" to UUID.randomUUID(),
                    "protocolId" to "other-v1",
                    "algorithm" to SigningAlgorithm.SHA512_WITH_RSA,
                    "format" to SigningFormat.CADES,
                    "safeDescription" to "different",
                    "observedAtMonotonicNanos" to 1_001L,
                    "context" to CONTEXT.copy(profileId = "other"),
                    "context" to CONTEXT.copy(profileVersion = 2),
                    "context" to CONTEXT.copy(origin = TrustedOrigin("https", "other.invalid", 443)),
                    "context" to CONTEXT.copy(navigationId = NavigationId("other")),
                    "context" to CONTEXT.copy(navigationEpoch = 1L),
                    "context" to CONTEXT.copy(observedAt = CONTEXT.observedAt.plusSeconds(1)),
                    "context" to CONTEXT.copy(pageUrl = "https://document.invalid/other"),
                )
                variants.forEachIndexed { index, (name, changed) ->
                    val field = NormalizedSignRequest::class.java.getDeclaredField(name).apply { isAccessible = true }
                    val before = field.get(original)
                    try {
                        field.set(original, changed)
                        assertFalse("bound field $index", evidence.matches(original))
                    } finally {
                        field.set(original, before)
                    }
                    assertTrue(evidence.matches(original))
                }
                original.withPayload { it[it.lastIndex] = 0 }
                assertFalse(evidence.matches(original))
            }
        }
    }

    @Test
    fun pendingStorePreservesOnlyTheOriginalRequestLineageAndExactBytes() {
        val document = "%PDF-synthetic".encodeToByteArray()
        val envelope = MiniAppletPayloadCodec.encode(document, "fixed=true")
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(envelope)
        val original = request(envelope)
        val store = PendingSignRequestStore(monotonicNanos = { 1_000L })
        DocumentConsentEvidence.fromEnvelope(original, envelope, document).use { evidence ->
            val summary = store.put(original)
            assertTrue(envelope.all { it == 0.toByte() })
            assertFalse(evidence.matches(original))
            val result = store.consume(PendingValidationContext(
                requestId = summary.requestId,
                profileId = summary.context.profileId,
                profileVersion = summary.context.profileVersion,
                origin = summary.context.origin,
                navigationId = summary.context.navigationId,
                payloadFingerprint = fingerprint,
            )) as PendingConsumeResult.Accepted
            result.request.use { consumed ->
                assertFalse(evidence.matches(consumed))
                assertTrue(evidence.matches(consumed, result.documentConsentLineage))
                assertFalse(summary.toString().contains(evidence.actualDocument!!.sha256ForDisplay()!!))
                consumed.withPayload { it[it.lastIndex] = 0 }
                assertFalse(evidence.matches(consumed, result.documentConsentLineage))
            }
        }
        fingerprint.fill(0)
        assertNull(store.peek())
    }

    @Test
    fun forgedForeignCopiedAndReplayedLineageFailClosed() {
        val (evidence, accepted) = pendingEvidence()
        val (foreignEvidence, foreignAccepted) = pendingEvidence()
        try {
            val consumed = accepted.request
            val lineage = checkNotNull(accepted.documentConsentLineage)
            assertTrue(evidence.matches(consumed, lineage))
            assertFalse(evidence.matches(consumed, object : DocumentConsentLineage {}))
            assertFalse(evidence.matches(consumed, foreignAccepted.documentConsentLineage))
            assertFalse(evidence.matches(foreignAccepted.request, lineage))
            request(consumed.payloadCopy()).use { reconstructed ->
                val copied = accepted.copy(request = reconstructed)
                assertFalse(evidence.matches(copied.request, copied.documentConsentLineage))
            }
            consumed.withPayload { envelope ->
                MiniAppletPayloadCodec.withDecoded(envelope) { document, _ ->
                    DocumentConsentEvidence.fromEnvelope(consumed, envelope, document).use { freshEvidence ->
                        assertTrue(freshEvidence.matches(consumed))
                        assertFalse(freshEvidence.matches(consumed, lineage))
                    }
                }
            }
            evidence.close()
            assertFalse(evidence.matches(consumed, lineage))
            consumed.close()
            assertFalse(evidence.matches(consumed, lineage))
        } finally {
            evidence.close()
            foreignEvidence.close()
            accepted.request.close()
            foreignAccepted.request.close()
        }
    }

    @Test
    fun displayDismissalClearsTheDocumentHashBeforeSigningAndCloseClearsBinding() {
        val document = "%PDF-synthetic".encodeToByteArray()
        val envelope = MiniAppletPayloadCodec.encode(document, "fixed=true")
        val cleared = mutableListOf<Boolean>()
        request(envelope).use { request ->
            val evidence = DocumentConsentEvidence.fromEnvelope(
                request, envelope, document, SensitiveSigningCopyObserver { cleared += it },
            )
            val metadata = evidence.actualDocument!!
            evidence.dismissDisplay()
            assertNull(metadata.sha256ForDisplay())
            assertNull(evidence.actualDocument)
            assertTrue(evidence.matches(request))
            assertEquals(listOf(true), cleared)
            evidence.close()
            evidence.close()
            assertFalse(evidence.matches(request))
            assertEquals(listOf(true, true), cleared)
        }
    }

    @Test
    fun unknownChallengeDigestAndEnvelopeNeverBecomeDocumentMetadataEvenForPades() {
        val adapter = object : SigningProtocolAdapter {
            override val id = SigningProtocolId("synthetic-unsupported-v1")
            override suspend fun prepare(request: NormalizedSignRequest, certificateChain: List<java.security.cert.X509Certificate>): ProtocolPrepareResult =
                error("Signing must not run during consent")
            override suspend fun complete(request: NormalizedSignRequest, preSign: PreSignResult, localSignature: LocalSignature): ProtocolCompletionResult =
                error("Signing must not run during consent")
        }
        val inputs = listOf(
            "challenge".encodeToByteArray(),
            MessageDigest.getInstance("SHA-256").digest("document".encodeToByteArray()),
            MiniAppletPayloadCodec.encode("%PDF-fake".encodeToByteArray(), "fixed=true"),
        )
        inputs.forEach { input ->
            request(input, format = SigningFormat.PADES).use { request ->
                adapter.documentConsent(request).use { evidence ->
                    assertEquals(SigningPayloadKind.UNKNOWN, evidence.payloadKind)
                    assertNull(evidence.actualDocument)
                }
            }
        }
        listOf(SigningPayloadKind.CHALLENGE, SigningPayloadKind.DIGEST, SigningPayloadKind.ENVELOPE).forEach { kind ->
            DocumentConsentEvidence.unavailable(kind).use { evidence ->
                assertEquals(kind, evidence.payloadKind)
                assertNull(evidence.actualDocument)
            }
        }
    }

    @Test
    fun evidenceHasClosedBoundsAndNeverOwnsTheDocumentInput() {
        val document = "%PDF-synthetic".encodeToByteArray()
        val envelope = MiniAppletPayloadCodec.encode(document, "fixed=true")
        request(envelope).use { request ->
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                DocumentConsentEvidence.fromEnvelope(request, ByteArray(PendingSignRequestStore.MAX_PAYLOAD_BYTES + 1), document)
            }
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                DocumentConsentEvidence.fromEnvelope(request, envelope, ByteArray(0))
            }
            DocumentConsentEvidence.fromEnvelope(request, envelope, document).close()
            assertEquals("%PDF-synthetic", document.decodeToString())
        }
    }

    private fun pendingEvidence(): Pair<DocumentConsentEvidence, PendingConsumeResult.Accepted> {
        val document = "%PDF-synthetic".encodeToByteArray()
        val envelope = MiniAppletPayloadCodec.encode(document, "fixed=true")
        val request = request(envelope)
        val evidence = DocumentConsentEvidence.fromEnvelope(request, envelope, document)
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(envelope)
        val store = PendingSignRequestStore(monotonicNanos = { 1_000L })
        val summary = store.put(request)
        return try {
            val accepted = store.consume(PendingValidationContext(
                requestId = summary.requestId,
                profileId = summary.context.profileId,
                profileVersion = summary.context.profileVersion,
                origin = summary.context.origin,
                navigationId = summary.context.navigationId,
                payloadFingerprint = fingerprint,
            )) as PendingConsumeResult.Accepted
            evidence to accepted
        } finally {
            fingerprint.fill(0)
        }
    }

    private fun request(
        payload: ByteArray,
        id: UUID = REQUEST_ID,
        context: SigningContext = CONTEXT,
        protocolId: SigningProtocolId = SigningProtocolId("synthetic-document-v1"),
        algorithm: SigningAlgorithm = SigningAlgorithm.SHA256_WITH_RSA,
        format: SigningFormat = SigningFormat.PADES,
        description: String = "Synthetic document operation",
        monotonicNanos: Long = 1_000L,
    ) = NormalizedSignRequest(
        requestId = id,
        protocolId = protocolId,
        context = context,
        algorithm = algorithm,
        format = format,
        safeDescription = description,
        payload = payload,
        observedAtMonotonicNanos = monotonicNanos,
    )

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        val REQUEST_ID: UUID = UUID.fromString("123e4567-e89b-42d3-a456-426614174000")
        val CONTEXT = SigningContext(
            profileId = "synthetic-document",
            profileVersion = 1,
            origin = TrustedOrigin("https", "document.invalid", 443),
            navigationId = NavigationId("synthetic-document-navigation"),
            observedAt = Instant.parse("2026-10-18T18:00:00Z"),
            pageUrl = "https://document.invalid/sign",
        )
    }
}

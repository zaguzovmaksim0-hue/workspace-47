package dev.junta.firmamobile.signing

import dev.junta.firmamobile.browser.NavigationId
import dev.junta.firmamobile.certificate.CertificateSession
import dev.junta.firmamobile.certificate.UnlockedIdentity
import dev.junta.firmamobile.network.TrustedOrigin
import java.security.cert.X509Certificate
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchSigningCoordinatorTest {
    private val clock = Clock.fixed(NOW, ZoneOffset.UTC)
    private val identity = syntheticIdentity()
    private val session = CertificateSession(clock).apply { unlock(identity) }
    private val adapter = RecordingBatchAdapter()
    private val engine = RecordingBatchEngine()
    private val expiryScheduler = RecordingExpiryScheduler()
    private val coordinator = BatchSigningCoordinator(
        certificateSession = session,
        adapter = adapter,
        localSignatureEngine = engine,
        currentOrigin = { MELILLA_ORIGIN },
        currentNavigationEpoch = { NAVIGATION_EPOCH },
        expiryScheduler = expiryScheduler,
        profileDisplayName = "Sede Electrónica de Melilla",
        supportLevel = "VERIFIED_CONTRACT",
    )

    @Test
    fun preparePublishesSafeBatchConfirmationWithoutNetworkOrPrivateKeyUse() {
        val reply = RecordingBatchReply(REQUEST_ID)

        val result = coordinator.prepare(request(), reply)

        assertEquals(SigningPreparationResult.Ready(REQUEST_ID), result)
        val state = coordinator.state.value as SigningUiState.AwaitingConfirmation
        assertEquals(REQUEST_ID, state.requestId)
        assertEquals("sede.melilla.es", state.siteHost)
        assertEquals("Sede Electrónica de Melilla", state.profileName)
        assertEquals("VERIFIED_CONTRACT", state.supportLevel)
        assertEquals("Firma por lotes (2 documentos)", state.safeDescription)
        assertEquals("CAdES", state.format)
        assertEquals("SHA256withRSA", state.algorithm)
        assertEquals(identity.summary.ownerName, state.certificateOwner)
        assertFalse(state.requiresLegacySha1Warning)
        assertTrue(adapter.events.isEmpty())
        assertTrue(engine.events.isEmpty())
        assertTrue(reply.events.isEmpty())
        assertEquals(1, expiryScheduler.scheduled.size)
        val rendered = state.toString()
        assertFalse(rendered.contains(OPERATION_ID))
        assertFalse(rendered.contains(PRE_URL))
        assertFalse(rendered.contains(POST_URL))
        assertFalse(rendered.contains("doc-a"))
        assertFalse(rendered.contains("doc-b"))
    }

    private fun request(): NormalizedBatchSigningRequest = NormalizedBatchSigningRequest(
        requestId = REQUEST_ID,
        protocolId = MelillaBatchProtocolAdapter.ID,
        context = SigningContext(
            profileId = "melilla-sede",
            profileVersion = 1,
            origin = MELILLA_ORIGIN,
            navigationId = NavigationId(DOCUMENT_ID.toString()),
            navigationEpoch = NAVIGATION_EPOCH,
            observedAt = NOW,
        ),
        algorithm = SigningAlgorithm.SHA256_WITH_RSA,
        format = BatchSigningFormat.CADES,
        suboperation = "sign",
        stopOnError = false,
        operationId = OPERATION_ID,
        preSignerUrl = PRE_URL,
        postSignerUrl = POST_URL,
        documents = listOf(
            NormalizedBatchSigningDocument(
                id = "doc-a",
                dataReference = "https://sede.melilla.es/afirma-signature-storage/${OPERATION_ID}/getdata/doc-a",
                format = BatchSigningFormat.CADES,
            ),
            NormalizedBatchSigningDocument(
                id = "doc-b",
                dataReference = "https://sede.melilla.es/afirma-signature-storage/${OPERATION_ID}/getdata/doc-b",
                format = BatchSigningFormat.PADES,
            ),
        ),
    )

    private class RecordingBatchAdapter : BatchSigningProtocolAdapter {
        val events = mutableListOf<String>()
        override val id: SigningProtocolId = MelillaBatchProtocolAdapter.ID

        override fun prepare(
            request: NormalizedBatchSigningRequest,
            certificateChain: List<X509Certificate>,
        ): BatchProtocolPrepareResult {
            events += "prepare"
            error("prepare must not run before explicit confirmation")
        }

        override fun complete(
            request: NormalizedBatchSigningRequest,
            preSign: BatchPreSignResult,
            localSignatures: List<LocalSignature>,
        ): BatchProtocolCompletionResult {
            events += "complete"
            error("complete must not run before explicit confirmation")
        }
    }

    private class RecordingBatchEngine : LocalSignatureEngine {
        val events = mutableListOf<String>()

        override fun sign(
            input: ByteArray,
            identity: UnlockedIdentity,
            algorithm: SigningAlgorithm,
        ): LocalSignatureResult {
            events += "sign"
            error("private-key use must not run before explicit confirmation")
        }
    }

    private class RecordingBatchReply(
        override val requestId: UUID,
    ) : BatchSigningReplySink {
        val events = mutableListOf<String>()

        override fun success(response: BatchProtocolResponse): Boolean {
            events += "success"
            return true
        }

        override fun failure(code: SigningErrorCode): Boolean {
            events += "failure:$code"
            return true
        }

        override fun abandon(): Boolean {
            events += "abandon"
            return true
        }
    }

    private class RecordingExpiryScheduler : SigningExpiryScheduler {
        val scheduled = mutableListOf<Duration>()

        override fun schedule(delay: Duration, action: () -> Unit): SigningExpiryHandle {
            scheduled += delay
            return SigningExpiryHandle {}
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-11T12:00:00Z")
        val REQUEST_ID: UUID = UUID.fromString("123e4567-e89b-42d3-a456-426614174100")
        val DOCUMENT_ID: UUID = UUID.fromString("123e4567-e89b-42d3-a456-426614174101")
        val MELILLA_ORIGIN = TrustedOrigin("https", "sede.melilla.es", 443)
        const val NAVIGATION_EPOCH = 7L
        const val OPERATION_ID = "op-g53-a"
        const val PRE_URL = "https://sede.melilla.es/afirma-signature-storage/op-g53-a/presign"
        const val POST_URL = "https://sede.melilla.es/afirma-signature-storage/op-g53-a/postsign"
    }
}

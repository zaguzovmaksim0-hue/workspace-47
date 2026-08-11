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
import kotlinx.coroutines.runBlocking

class BatchSigningCoordinatorTest {
    private val clock = Clock.fixed(NOW, ZoneOffset.UTC)
    private val identity = syntheticIdentity()
    private val session = CertificateSession(clock).apply { unlock(identity) }
    private val timeline = mutableListOf<String>()
    private val adapter = RecordingBatchAdapter(timeline)
    private val engine = RecordingBatchEngine(timeline)
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
        val reply = RecordingBatchReply(REQUEST_ID, timeline)

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

    @Test
    fun confirmSignsEveryPresignInputInOrderAndDeliversOneFinalResponse() = runBlocking {
        val reply = RecordingBatchReply(REQUEST_ID, timeline)
        assertEquals(SigningPreparationResult.Ready(REQUEST_ID), coordinator.prepare(request(), reply))

        val result = coordinator.confirm(REQUEST_ID)

        assertEquals(SigningExecutionResult.Delivered(REQUEST_ID), result)
        assertEquals(
            listOf("prepare", "sign:pre-one", "sign:pre-two", "complete", "success"),
            timeline,
        )
        assertEquals(
            listOf("pk1-pre-one", "pk1-pre-two"),
            adapter.completedSignatures.single(),
        )
        assertTrue(engine.identities.all { it === identity })
        assertEquals(
            listOf(SigningAlgorithm.SHA256_WITH_RSA, SigningAlgorithm.SHA256_WITH_RSA),
            engine.algorithms,
        )
        assertEquals(FINAL_RESPONSE, reply.deliveredResponse)
        assertEquals(SigningUiState.Completed(REQUEST_ID), coordinator.state.value)
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
                dataReference = "https://sede.melilla.es/sta/AutofirmaLote/getdata/${OPERATION_ID}/doc-a",
                format = BatchSigningFormat.CADES,
            ),
            NormalizedBatchSigningDocument(
                id = "doc-b",
                dataReference = "https://sede.melilla.es/sta/AutofirmaLote/getdata/${OPERATION_ID}/doc-b",
                format = BatchSigningFormat.PADES,
            ),
        ),
    )

    private class RecordingBatchAdapter(
        private val timeline: MutableList<String>,
    ) : BatchSigningProtocolAdapter {
        val events = mutableListOf<String>()
        val completedSignatures = mutableListOf<List<String>>()
        override val id: SigningProtocolId = MelillaBatchProtocolAdapter.ID

        override fun prepare(
            request: NormalizedBatchSigningRequest,
            certificateChain: List<X509Certificate>,
        ): BatchProtocolPrepareResult {
            events += "prepare"
            timeline += "prepare"
            return BatchProtocolPrepareResult.Success(
                BatchPreSignResult(
                    requestOwner = request,
                    inputs = listOf(PRE_ONE.encodeToByteArray(), PRE_TWO.encodeToByteArray()),
                    state = RecordingBatchPreSignState(),
                ),
            )
        }

        override fun complete(
            request: NormalizedBatchSigningRequest,
            preSign: BatchPreSignResult,
            localSignatures: List<LocalSignature>,
        ): BatchProtocolCompletionResult {
            events += "complete"
            timeline += "complete"
            completedSignatures += localSignatures.map { signature ->
                signature.withBytes { it.decodeToString() }
            }
            return BatchProtocolCompletionResult.Success(
                BatchProtocolResponse(FINAL_RESPONSE.encodeToByteArray()),
            )
        }
    }

    private class RecordingBatchPreSignState : BatchPreSignState {
        override fun close() = Unit
    }

    private class RecordingBatchEngine(
        private val timeline: MutableList<String>,
    ) : LocalSignatureEngine {
        val events = mutableListOf<String>()
        val identities = mutableListOf<UnlockedIdentity>()
        val algorithms = mutableListOf<SigningAlgorithm>()

        override fun sign(
            input: ByteArray,
            identity: UnlockedIdentity,
            algorithm: SigningAlgorithm,
        ): LocalSignatureResult {
            val value = input.decodeToString()
            events += "sign:$value"
            identities += identity
            algorithms += algorithm
            timeline += "sign:$value"
            return LocalSignatureResult.Success(
                LocalSignature("pk1-$value".encodeToByteArray()),
            )
        }
    }

    private class RecordingBatchReply(
        override val requestId: UUID,
        private val timeline: MutableList<String>,
    ) : BatchSigningReplySink {
        val events = mutableListOf<String>()
        var deliveredResponse: String? = null

        override fun success(response: BatchProtocolResponse): Boolean {
            events += "success"
            timeline += "success"
            deliveredResponse = response.withBytes { it.decodeToString() }
            return true
        }

        override fun failure(code: SigningErrorCode): Boolean {
            events += "failure:$code"
            timeline += "failure:$code"
            return true
        }

        override fun abandon(): Boolean {
            events += "abandon"
            timeline += "abandon"
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
        const val OPERATION_ID = "op-g54-a"
        const val PRE_URL = "https://sede.melilla.es/sta/AutofirmaLote/presign/op-g54-a"
        const val POST_URL = "https://sede.melilla.es/sta/AutofirmaLote/postsign/op-g54-a"
        const val PRE_ONE = "pre-one"
        const val PRE_TWO = "pre-two"
        const val FINAL_RESPONSE = "{\"result\":\"DONE_AND_SAVED\"}"
    }
}

package dev.junta.firmamobile

import dev.junta.firmamobile.browser.BatchBridgeSigningRoutes
import dev.junta.firmamobile.browser.MiniAppletBridgeRequest
import dev.junta.firmamobile.browser.NavigationId
import dev.junta.firmamobile.certificate.CertificateSession
import dev.junta.firmamobile.certificate.UnlockedIdentity
import dev.junta.firmamobile.network.TrustedOrigin
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.CompatibilityStatus
import dev.junta.firmamobile.signing.BatchPreSignResult
import dev.junta.firmamobile.signing.BatchProtocolCompletionResult
import dev.junta.firmamobile.signing.BatchProtocolPrepareResult
import dev.junta.firmamobile.signing.BatchSigningCoordinator
import dev.junta.firmamobile.signing.BatchSigningProtocolAdapter
import dev.junta.firmamobile.signing.JuntaTriPhaseAdapter
import dev.junta.firmamobile.signing.LocalSignature
import dev.junta.firmamobile.signing.LocalSignatureEngine
import dev.junta.firmamobile.signing.LocalSignatureResult
import dev.junta.firmamobile.signing.NormalizedBatchSigningRequest
import dev.junta.firmamobile.signing.NormalizedSignRequest
import dev.junta.firmamobile.signing.PreSignResult
import dev.junta.firmamobile.signing.PreSignState
import dev.junta.firmamobile.signing.ProtocolCompletionResult
import dev.junta.firmamobile.signing.ProtocolPrepareResult
import dev.junta.firmamobile.signing.SigningAlgorithm
import dev.junta.firmamobile.signing.SigningCancelReason
import dev.junta.firmamobile.signing.SigningContext
import dev.junta.firmamobile.signing.SigningCoordinator
import dev.junta.firmamobile.signing.SigningErrorCode
import dev.junta.firmamobile.signing.SigningExpiryHandle
import dev.junta.firmamobile.signing.SigningFormat
import dev.junta.firmamobile.signing.SigningProtocolAdapter
import dev.junta.firmamobile.signing.SigningProtocolId
import dev.junta.firmamobile.signing.SigningReplySink
import dev.junta.firmamobile.signing.SigningUiState
import dev.junta.firmamobile.signing.syntheticIdentity
import java.security.cert.X509Certificate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SigningOrchestratorTest {
    private val clock = Clock.fixed(NOW, ZoneOffset.UTC)
    private val session = CertificateSession(clock).apply { unlock(syntheticIdentity()) }
    private val adapter = BlockingPrepareAdapter()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private var externalActive = false
    private var activeOrigin: TrustedOrigin? = PORTAL_ORIGIN
    private val stages = mutableListOf<String>()
    private val ordinary = SigningCoordinator(
        certificateSession = session,
        adapter = adapter,
        localSignatureEngine = NoopEngine,
        currentOrigin = { activeOrigin },
        clock = clock,
        expiryScheduler = { _, _ -> SigningExpiryHandle {} },
    )
    private val batch = BatchSigningCoordinator(
        certificateSession = session,
        adapter = UnusedBatchAdapter,
        localSignatureEngine = NoopEngine,
        currentOrigin = { null },
        expiryScheduler = { _, _ -> SigningExpiryHandle {} },
        profileDisplayName = "unused",
        compatibilityStatus = CompatibilityStatus.VERIFIED_CONTRACT,
    )
    private val orchestrator = SigningOrchestrator(
        signingCoordinator = ordinary,
        batchSigningCoordinator = batch,
        batchBridgeRoutes = BatchBridgeSigningRoutes(BuiltInSiteProfiles.runtimeRegistry),
        scope = scope,
        externalAfirmaActive = { externalActive },
        recordMiniAppletStage = { _, stage, _ -> stages += stage },
    )

    @After
    fun tearDown() {
        adapter.releasePrepare.countDown()
        orchestrator.close()
        scope.cancel()
    }

    @Test
    fun preparedOrdinaryRequestOwnsTheFlowAndIsTheVisibleState() {
        orchestrator.prepareMiniAppletSigning(MiniAppletBridgeRequest(request()), RecordingReply())

        assertTrue(orchestrator.hasOwner())
        assertEquals(listOf("PREPARE_READY"), stages)
        val visible = orchestrator.visibleState(orchestrator.ordinaryState.value, SigningUiState.Idle)
        assertEquals(REQUEST_ID, (visible as SigningUiState.AwaitingConfirmation).requestId)
        assertEquals(
            SigningUiState.Idle,
            orchestrator.visibleState(SigningUiState.Idle, orchestrator.ordinaryState.value),
        )
    }

    @Test
    fun secondRequestWhileOwnedIsClosedAndRepliedWithProtocolFailure() {
        orchestrator.prepareMiniAppletSigning(MiniAppletBridgeRequest(request()), RecordingReply())
        val second = request(OTHER_REQUEST_ID)
        val reply = RecordingReply(OTHER_REQUEST_ID)

        orchestrator.prepareMiniAppletSigning(MiniAppletBridgeRequest(second), reply)

        assertEquals(listOf("failure:PROTOCOL_FAILED"), reply.events)
        assertEquals(listOf("PREPARE_READY", "OWNERSHIP_REJECTED"), stages)
        assertTrue(runCatching { second.withPayload { } }.isFailure)
    }

    @Test
    fun activeExternalAfirmaRejectsBeforeTakingOwnership() {
        externalActive = true
        val reply = RecordingReply()

        orchestrator.prepareMiniAppletSigning(MiniAppletBridgeRequest(request()), reply)

        assertFalse(orchestrator.hasOwner())
        assertEquals(listOf("failure:PROTOCOL_FAILED"), reply.events)
    }

    @Test
    fun rejectedPrepareShowsItsFailureUntilDismissedThenReleasesOwnership() {
        activeOrigin = null
        val reply = RecordingReply()

        orchestrator.prepareMiniAppletSigning(MiniAppletBridgeRequest(request()), reply)

        assertEquals(listOf("PREPARE_REJECTED"), stages)
        assertEquals(listOf("failure:ORIGIN_NOT_ALLOWED"), reply.events)
        assertTrue(orchestrator.ordinaryState.value is SigningUiState.Failed)
        assertTrue("the visible failure keeps the single owner", orchestrator.hasOwner())

        orchestrator.dismissSigningState()

        assertFalse(orchestrator.hasOwner())
    }

    @Test
    fun terminalFailureKeepsOwnershipUntilTheUserDismissesIt() {
        orchestrator.prepareMiniAppletSigning(MiniAppletBridgeRequest(request()), RecordingReply())
        orchestrator.confirmSigning(REQUEST_ID)
        adapter.releasePrepare.countDown()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (orchestrator.ordinaryState.value !is SigningUiState.Failed && System.nanoTime() < deadline) {
            Thread.sleep(10)
        }
        assertTrue(orchestrator.ordinaryState.value is SigningUiState.Failed)
        assertTrue(orchestrator.hasOwner())

        orchestrator.dismissSigningState()

        assertFalse(orchestrator.hasOwner())
    }

    @Test
    fun repeatedConfirmForOneRequestStartsExactlyOneSigningJob() {
        orchestrator.prepareMiniAppletSigning(MiniAppletBridgeRequest(request()), RecordingReply())

        orchestrator.confirmSigning(REQUEST_ID)
        assertTrue(adapter.prepareEntered.await(5, TimeUnit.SECONDS))
        orchestrator.confirmSigning(REQUEST_ID)
        orchestrator.confirmSigning(OTHER_REQUEST_ID)

        assertEquals(1, adapter.prepareCalls.get())
    }

    @Test
    fun cancellationDuringConfirmKeepsOwnershipUntilTheSigningJobCompletes() {
        orchestrator.prepareMiniAppletSigning(MiniAppletBridgeRequest(request()), RecordingReply())
        orchestrator.confirmSigning(REQUEST_ID)
        assertTrue(adapter.prepareEntered.await(5, TimeUnit.SECONDS))

        orchestrator.cancelSigning(SigningCancelReason.JAVASCRIPT, OTHER_REQUEST_ID)
        assertTrue("a foreign request id must not cancel", orchestrator.hasOwner())

        orchestrator.cancelSigning(SigningCancelReason.JAVASCRIPT, REQUEST_ID)
        assertTrue("ownership must outlive the still running job", orchestrator.hasOwner())

        adapter.releasePrepare.countDown()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (orchestrator.hasOwner() && System.nanoTime() < deadline) Thread.sleep(10)
        assertFalse(orchestrator.hasOwner())
    }

    @Test
    fun idleCancellationReleasesOwnershipImmediately() {
        orchestrator.prepareMiniAppletSigning(MiniAppletBridgeRequest(request()), RecordingReply())

        orchestrator.cancelSigning(SigningCancelReason.NAVIGATION)

        assertFalse(orchestrator.hasOwner())
    }

    @Test
    fun closeCancelsTheActiveFlowAndIsIdempotent() {
        val reply = RecordingReply()
        orchestrator.prepareMiniAppletSigning(MiniAppletBridgeRequest(request()), reply)

        orchestrator.close()
        orchestrator.close()

        assertFalse(orchestrator.hasOwner())
        assertEquals(1, reply.events.size)
    }

    private fun request(requestId: UUID = REQUEST_ID) = NormalizedSignRequest(
        requestId = requestId,
        protocolId = JuntaTriPhaseAdapter.ID,
        context = SigningContext(
            profileId = "junta-andalucia",
            profileVersion = 1,
            origin = PORTAL_ORIGIN,
            navigationId = NavigationId("123e4567-e89b-42d3-a456-426614174001"),
            navigationEpoch = 0L,
            observedAt = NOW,
        ),
        algorithm = SigningAlgorithm.SHA256_WITH_RSA,
        format = SigningFormat.CADES,
        safeDescription = "Autenticación con certificado",
        payload = "synthetic-orchestrator-payload".encodeToByteArray(),
    )

    private class RecordingReply(override val requestId: UUID = REQUEST_ID) : SigningReplySink {
        val events = mutableListOf<String>()

        override fun success(signature: LocalSignature, certificateDer: ByteArray): Boolean {
            signature.close()
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

    private class BlockingPrepareAdapter : SigningProtocolAdapter {
        override val id = JuntaTriPhaseAdapter.ID
        val prepareCalls = AtomicInteger()
        val prepareEntered = CountDownLatch(1)
        val releasePrepare = CountDownLatch(1)

        override suspend fun prepare(
            request: NormalizedSignRequest,
            certificateChain: List<X509Certificate>,
        ): ProtocolPrepareResult {
            prepareCalls.incrementAndGet()
            prepareEntered.countDown()
            check(releasePrepare.await(5, TimeUnit.SECONDS))
            return ProtocolPrepareResult.Success(PreSignResult(request, byteArrayOf(1), EmptyPreSignState))
        }

        override suspend fun complete(
            request: NormalizedSignRequest,
            preSign: PreSignResult,
            localSignature: LocalSignature,
        ): ProtocolCompletionResult = ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
    }

    private object EmptyPreSignState : PreSignState {
        override fun close() = Unit
    }

    private object NoopEngine : LocalSignatureEngine {
        override fun sign(
            input: ByteArray,
            identity: UnlockedIdentity,
            algorithm: SigningAlgorithm,
        ): LocalSignatureResult = LocalSignatureResult.Success(LocalSignature(byteArrayOf(2)))
    }

    private object UnusedBatchAdapter : BatchSigningProtocolAdapter {
        override val id = SigningProtocolId("unused-batch")

        override fun prepare(
            request: NormalizedBatchSigningRequest,
            certificateChain: List<X509Certificate>,
        ): BatchProtocolPrepareResult = error("batch is not exercised here")

        override fun complete(
            request: NormalizedBatchSigningRequest,
            preSign: BatchPreSignResult,
            localSignatures: List<LocalSignature>,
        ): BatchProtocolCompletionResult = error("batch is not exercised here")
    }

    private companion object {
        val NOW: Instant = Instant.parse("2030-01-01T00:00:00Z")
        val REQUEST_ID: UUID = UUID.fromString("123e4567-e89b-42d3-a456-426614174000")
        val OTHER_REQUEST_ID: UUID = UUID.fromString("123e4567-e89b-42d3-a456-426614174099")
        val PORTAL_ORIGIN = TrustedOrigin("https", "www.juntadeandalucia.es", 443)
    }
}
